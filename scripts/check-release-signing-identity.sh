#!/usr/bin/env bash
#
# Fails when the key that signs a release is not the key users already have.
#
# Every published APK is signed with one certificate. Android refuses to update
# an installed app whose new APK carries a different signature: the user has to
# uninstall first, which erases their Health Connect grants and the tiles they
# configured. That makes the signing identity the one part of this project that
# cannot be fixed after the fact, which is exactly the kind of thing a build
# should refuse to get wrong quietly.
#
# Usage:  scripts/check-release-signing-identity.sh <keystore|apk> [properties]
#         KEYSTORE_PASSWORD=... KEY_ALIAS=... scripts/check-... <keystore>
#         APKSIGNER=/path/to/apksigner scripts/check-... <apk>
#
# The expected digest lives in signing-identity.properties so the guard, the
# README and the release notes cannot disagree about it.
#
# Exit codes: 0 match, 1 different identity, 2 usage or missing input.

set -euo pipefail

target="${1:-}"
properties="${2:-signing-identity.properties}"

usage() {
    echo "usage: $0 <keystore|apk> [properties]" >&2
}

if [[ -z "$target" ]]; then
    echo "error: no keystore or APK given" >&2
    usage
    exit 2
fi

if [[ ! -f "$target" ]]; then
    echo "error: $target not found" >&2
    echo "       if this is the release key, it was lost: read the backup section of RELEASING.md" >&2
    exit 2
fi

if [[ ! -f "$properties" ]]; then
    echo "error: $properties not found" >&2
    exit 2
fi

expected="$(sed -n 's/^[[:space:]]*releaseCertSha256[[:space:]]*=[[:space:]]*//p' "$properties" | tr -d '[:space:]')"

if [[ -z "$expected" ]]; then
    echo "error: releaseCertSha256 is missing from $properties" >&2
    exit 2
fi

# Normalise to lowercase hex with no separators so a digest copied with colons
# from keytool compares equal to one copied without them from README.md.
normalise() {
    printf '%s' "$1" | tr -d ':[:space:]-' | tr '[:upper:]' '[:lower:]'
}

digest_of_keystore() {
    local keystore="$1"
    local password="${KEYSTORE_PASSWORD:-android}"
    local alias="${KEY_ALIAS:-androiddebugkey}"

    if ! command -v keytool >/dev/null 2>&1; then
        echo "error: keytool not found; run this where the JDK that builds the app is installed" >&2
        return 2
    fi

    local listing
    # `-storepass:env` instead of `-storepass`: an argument is visible to every other process on
    # the machine through `ps`, and a password on a command line is a habit worth not having even
    # when this particular one is the well-known `android`.
    if ! listing="$(KEYSTORE_PASSWORD="$password" keytool -list -v -keystore "$keystore" \
        -storepass:env KEYSTORE_PASSWORD -alias "$alias" 2>/dev/null)"; then
        echo "error: could not read alias '$alias' from $keystore" >&2
        echo "       set KEY_ALIAS and KEYSTORE_PASSWORD if the key uses other values" >&2
        return 2
    fi

    # awk, not sed with `\|` alternation: that is a GNU extension, and on BSD sed the pattern
    # silently matches nothing, which would read as "cannot tell" on a macOS maintainer's machine
    # while the key was perfectly fine. Both spellings of the fingerprint line are handled.
    printf '%s\n' "$listing" \
        | awk '
            /SHA-256:/ { sub(/^.*SHA-256:[[:space:]]*/, ""); print; exit }
            /SHA256:/  { sub(/^.*SHA256:[[:space:]]*/,  ""); print; exit }
        '
}

digest_of_apk() {
    local apk="$1"

    # Windows ships the tool as apksigner.bat and Git Bash's `command -v apksigner` does not
    # look at PATHEXT, so the bare name is not enough. APKSIGNER overrides everything, which is
    # also how CI points at a build-tools install that is not on PATH.
    local signer="${APKSIGNER:-}"
    if [[ -z "$signer" ]]; then
        local candidate
        for candidate in apksigner apksigner.bat apksigner.exe; do
            if command -v "$candidate" >/dev/null 2>&1; then
                signer="$candidate"
                break
            fi
        done
    fi

    if [[ -z "$signer" ]]; then
        echo "error: apksigner not found; point at the keystore instead, set APKSIGNER, or install Android build-tools" >&2
        return 2
    fi

    # `|| true` on purpose: this file runs under `set -o pipefail`, so a signer that exits
    # non-zero makes the whole pipeline fail and the caller exits 1, which means "different
    # certificate". A tool that could not read the APK has to land on exit 2, never on the one
    # code that tells a maintainer their key is wrong.
    local listing
    listing="$("$signer" verify --print-certs "$apk" 2>/dev/null || true)"

    printf '%s\n' "$listing" \
        | sed -n 's/^.*SHA-256 digest:[[:space:]]*//p' \
        | head -n 1
}

case "$target" in
    *.apk)      actual="$(digest_of_apk "$target")" || exit $? ;;
    # .p12/.pfx are PKCS12, which is what keytool -importkeystore writes and therefore what the
    # re-wrapped release key is. Rejecting them made the guard useless for exactly the file the
    # release procedure tells you to create.
    *.jks|*.keystore|*.p12|*.pfx) actual="$(digest_of_keystore "$target")" || exit $? ;;
    *)
        echo "error: $target is not a keystore (.jks/.keystore/.p12/.pfx) or an APK" >&2
        usage
        exit 2
        ;;
esac

actual_normalised="$(normalise "$actual")"

if [[ -z "$actual_normalised" ]]; then
    echo "error: could not read a certificate digest out of $target" >&2
    exit 2
fi

if [[ "$actual_normalised" != "$(normalise "$expected")" ]]; then
    echo "error: $target is not signed with the published release certificate" >&2
    echo "       expected: $(normalise "$expected")  ($properties)" >&2
    echo "       found:    $actual_normalised" >&2
    echo "       signing a release with this key would strand every existing install." >&2
    echo "       see the backup section of RELEASING.md before you build anything." >&2
    exit 1
fi

echo "ok: $target carries the published release certificate ($actual_normalised)"
