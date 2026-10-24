#!/usr/bin/env bash
#
# Regression tests for check-release-signing-identity.sh. Every case asserts the
# exact exit code, because a signing guard is only worth having if it fails
# closed: a case that exits 0 by accident tells the maintainer their key is fine
# while every user is about to be stranded.
#
# The fixture keystore is generated here, so the suite needs keytool but never
# the real release key. The expected digest is read out of keytool with awk,
# deliberately a different parser than the sed the guard uses: a parsing bug in
# one should not be invisible to the other.
#
# Usage: scripts/test-check-release-signing-identity.sh

set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
guard="$here/check-release-signing-identity.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

passphrase="testpass"
alias_name="testkey"
keystore="$tmp/fixture.jks"

if ! command -v keytool >/dev/null 2>&1; then
    printf 'skip: keytool not found, the signing guard cannot be exercised here\n' >&2
    exit 3
fi

keytool -genkeypair -keystore "$keystore" -storepass "$passphrase" -keypass "$passphrase" \
    -alias "$alias_name" -keyalg RSA -keysize 2048 -validity 1 \
    -dname "CN=Signing Identity Fixture" >/dev/null 2>&1 || {
        printf 'error: could not generate the fixture keystore\n' >&2
        exit 2
    }

digest="$(keytool -list -v -keystore "$keystore" -storepass "$passphrase" -alias "$alias_name" 2>/dev/null \
    | awk -F'SHA256: ' '/SHA256:/ {print $2; exit}' | tr -d '[:space:]')"

if [[ -z "$digest" ]]; then
    printf 'error: could not read the fixture digest\n' >&2
    exit 2
fi

# A digest that differs in one nibble: same shape, different identity.
mutated="${digest%?}$([[ "${digest: -1}" == "0" ]] && printf '1' || printf '0')"
# The same digest as keytool prints it, and as a human might paste it.
colons="$(printf '%s' "$digest" | sed 's/../&:/g; s/:$//')"
uppercase="$(printf '%s' "$colons" | tr '[:lower:]' '[:upper:]')"

printf 'releaseCertSha256=%s\n' "$digest" > "$tmp/match.properties"
printf 'releaseCertSha256=%s\n' "$mutated" > "$tmp/mismatch.properties"
printf 'releaseCertSha256=%s\n' "$uppercase" > "$tmp/uppercase.properties"
printf '# releaseCertSha256=%s\n' "$digest" > "$tmp/commented.properties"
printf 'somethingElse=%s\n' "$digest" > "$tmp/missing.properties"

failures=0
expect() {
    local label="$1" want="$2"
    shift 2
    "$@" >/dev/null 2>&1
    local got=$?
    if [[ "$got" == "$want" ]]; then
        printf 'ok   %s (exit %s)\n' "$label" "$got"
    else
        printf 'FAIL %s: want exit %s, got %s\n' "$label" "$want" "$got" >&2
        failures=$((failures + 1))
    fi
}

KEYSTORE_PASSWORD="$passphrase" KEY_ALIAS="$alias_name" \
    expect "keystore matches"           0 bash "$guard" "$keystore" "$tmp/match.properties"
KEYSTORE_PASSWORD="$passphrase" KEY_ALIAS="$alias_name" \
    expect "one nibble different"       1 bash "$guard" "$keystore" "$tmp/mismatch.properties"
KEYSTORE_PASSWORD="$passphrase" KEY_ALIAS="$alias_name" \
    expect "colons and uppercase"       0 bash "$guard" "$keystore" "$tmp/uppercase.properties"
KEYSTORE_PASSWORD="$passphrase" KEY_ALIAS="$alias_name" \
    expect "commented key is ignored"   2 bash "$guard" "$keystore" "$tmp/commented.properties"
KEYSTORE_PASSWORD="$passphrase" KEY_ALIAS="$alias_name" \
    expect "key absent"                 2 bash "$guard" "$keystore" "$tmp/missing.properties"
KEYSTORE_PASSWORD="$passphrase" KEY_ALIAS="$alias_name" \
    expect "missing keystore"           2 bash "$guard" "$tmp/nope.jks" "$tmp/match.properties"
KEYSTORE_PASSWORD="$passphrase" KEY_ALIAS="$alias_name" \
    expect "missing properties"         2 bash "$guard" "$keystore" "$tmp/nope.properties"
KEYSTORE_PASSWORD="$passphrase" KEY_ALIAS="$alias_name" \
    expect "no target argument"         2 bash "$guard" "" "$tmp/match.properties"
KEYSTORE_PASSWORD="$passphrase" KEY_ALIAS="$alias_name" \
    expect "unknown file type"          2 bash "$guard" "$tmp/match.properties" "$tmp/match.properties"
KEYSTORE_PASSWORD="$passphrase" KEY_ALIAS="notthealias" \
    expect "wrong alias"                2 bash "$guard" "$keystore" "$tmp/match.properties"
KEYSTORE_PASSWORD="wrongpass" KEY_ALIAS="$alias_name" \
    expect "wrong password"             2 bash "$guard" "$keystore" "$tmp/match.properties"

# The APK path parses a different tool's output, so it is exercised with a stand-in that prints
# what apksigner prints. A real APK would need build-tools and a signing step inside a shell
# test; what can actually break here is the parsing and the tool lookup, and this covers both.
cat > "$tmp/fake-apksigner" <<EOF
#!/usr/bin/env bash
case "\$*" in
  *"$tmp/good.apk"*)  printf 'Signer #1 certificate SHA-256 digest: %s\n' "$digest" ;;
  *"$tmp/other.apk"*) printf 'Signer #1 certificate SHA-256 digest: %s\n' "$mutated" ;;
  *) exit 1 ;;
esac
EOF
chmod +x "$tmp/fake-apksigner"
: > "$tmp/good.apk"
: > "$tmp/other.apk"
: > "$tmp/unreadable.apk"

expect "apk matches"                0 env APKSIGNER="$tmp/fake-apksigner" bash "$guard" "$tmp/good.apk" "$tmp/match.properties"
expect "apk signed by another key"  1 env APKSIGNER="$tmp/fake-apksigner" bash "$guard" "$tmp/other.apk" "$tmp/match.properties"
expect "apk that will not verify" 2 env APKSIGNER="$tmp/fake-apksigner" bash "$guard" "$tmp/unreadable.apk" "$tmp/match.properties"
# The release key is re-wrapped as PKCS12, so the extensions the guard accepts have to include
# it; rejecting the format the release procedure writes made the guard useless for that file.
cp "$keystore" "$tmp/release.p12"
KEYSTORE_PASSWORD="$passphrase" KEY_ALIAS="$alias_name"     expect "p12 keystore accepted"      0 bash "$guard" "$tmp/release.p12" "$tmp/match.properties"

expect "no apksigner at all"     2 env APKSIGNER= bash "$guard" "$tmp/good.apk" "$tmp/match.properties"

if [[ "$failures" -ne 0 ]]; then
    printf '\n%s case(s) failed\n' "$failures" >&2
    exit 1
fi
printf '\nall release-signing identity cases pass\n'
