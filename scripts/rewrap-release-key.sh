#!/usr/bin/env bash
#
# Re-wraps the release key under its own password, without changing the certificate.
#
# The project signs every release with the Android debug certificate, whose keystore password is
# the public string `android`: any copy of that file let its holder sign an update Android would
# accept. A keystore password protects the container, not the identity, so the fix is to copy the
# key into a PKCS12 container with a real password. The certificate is untouched, which is why
# nobody has to reinstall: Android compares certificates, never passwords.
#
# Usage:  scripts/rewrap-release-key.sh [source-keystore] [destination.p12]
#
# The password is read from the terminal with echo off. It never reaches the shell history, the
# process list (keytool gets it through -deststorepass:env) or this script's arguments.
#
# Exit codes: 0 done, 1 refused (destination exists, passwords differ), 2 missing input.

set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$here/.." && pwd)"

source_keystore="${1:-$HOME/.android/debug.keystore}"
destination="${2:-$HOME/.android/release.p12}"

die() {
    echo "error: $*" >&2
    exit 1
}

refuse() {
    echo "error: $*" >&2
    exit 1
}

# Git Bash hands MSYS paths to native binaries; keytool is native and wants C:/... spellings.
# cygpath -m produces exactly that, and it is also what keystore.properties needs to contain.
to_win_path() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -m "$1"
    else
        printf '%s' "$1"
    fi
}

[[ -f "$source_keystore" ]] || {
    echo "error: source keystore not found: $source_keystore" >&2
    echo "       if that is the release key and it is gone, read RELEASING.md before anything else" >&2
    exit 2
}

if ! command -v keytool >/dev/null 2>&1; then
    echo "error: keytool not found; run this from the JDK that builds the app" >&2
    exit 2
fi

if [[ -e "$destination" ]]; then
    refuse "$destination already exists; delete it deliberately if you want to start over"
fi

# Deliberately outside the repository. The repository usually lives inside a synced folder
# (OneDrive, Dropbox, iCloud Drive) and a password written in there is uploaded by the sync
# client: git ignores the file, the sync client does not. ~/.android is where the key already
# lives and it is not synced.
properties="$HOME/.android/wearsync-keystore.properties"
if [[ -e "$properties" ]]; then
    refuse "$properties already exists; move it aside first, it points at the current key"
fi

src_win="$(to_win_path "$source_keystore")"
dest_win="$(to_win_path "$destination")"

echo "source:      $src_win"
echo "destination: $dest_win"
echo

password="${RELEASE_KEY_PASSWORD:-}"
if [[ -z "$password" ]]; then
    read -r -s -p "New password for the release key: " password || true
    echo
    read -r -s -p "Repeat it: " again || true
    echo
    if [[ "$password" != "$again" ]]; then
        refuse "the passwords do not match; nothing was written"
    fi
fi

if [[ ${#password} -lt 6 ]]; then
    refuse "keytool requires at least six characters; nothing was written"
fi

export RSW_KEYSTORE_PASSWORD="$password"
trap 'unset RSW_KEYSTORE_PASSWORD' EXIT

keytool -importkeystore \
    -srckeystore "$src_win" -srcstorepass android -srcalias androiddebugkey \
    -destkeystore "$dest_win" -deststoretype PKCS12 \
    -deststorepass:env RSW_KEYSTORE_PASSWORD -destalias wearsync

echo
# The identity is the only thing a user can feel, so it is proven here rather than assumed.
KEYSTORE_PASSWORD="$password" KEY_ALIAS=wearsync \
    bash "$here/check-release-signing-identity.sh" "$dest_win"

umask 077
cat > "$properties" <<EOF
# Written by scripts/rewrap-release-key.sh. Local only: gitignored, never leaves this machine.
storeFile=$dest_win
storePassword=$password
keyAlias=wearsync
keyPassword=$password
EOF

echo
echo "wrote $properties"
echo
echo "This file is outside the repository on purpose: a password inside a synced folder ends up"
echo "in that cloud. Gradle reads it from there; nothing is needed in the working tree."
echo
echo "Next:"
echo "  1. Back up $dest_win and this password together, in the same password-manager item."
echo "  2. ./gradlew :wear:assembleRelease :mobile:assembleRelease"
echo "     the 'throwaway debug key' warning must be gone from that output."
echo "  3. Keep $src_win: same identity, still what debug builds use, and the way back in if"
echo "     the password you just set is ever lost."
