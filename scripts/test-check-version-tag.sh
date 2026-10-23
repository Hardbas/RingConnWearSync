#!/usr/bin/env bash
#
# Regression tests for check-version-tag.sh. Every case asserts the exact exit
# code, because the guard is only worth having if it fails closed.
#
# Usage: scripts/test-check-version-tag.sh

set -uo pipefail
unset GITHUB_REF_NAME

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
guard="$here/check-version-tag.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

failures=0
expect() {
    local label="$1" want="$2"
    shift 2
    "$@" >/dev/null 2>&1
    local got=$?
    if [[ "$got" == "$want" ]]; then
        printf 'ok   %s (exit %s)\n' "$label" "$got"
    else
        printf 'FAIL %s: want exit %s, got %s\n' "$label" "$want" "$got"
        failures=$((failures + 1))
    fi
}

printf 'versionName=1.9.25\nversionCode=60\n' > "$tmp/plain"
printf '  versionName=1.9.25\n' > "$tmp/indented"
printf 'versionName=1.9.24\nversionName=1.9.25\n' > "$tmp/duplicate"
printf '# versionName=0.0.1\nversionName=1.9.25\n' > "$tmp/commented"
printf 'versionCode=60\n' > "$tmp/missing"

expect "tag equals versionName"        0 bash "$guard" v1.9.25 "$tmp/plain"
expect "older tag"                     1 bash "$guard" v1.9.24 "$tmp/plain"
expect "prefix near miss"              1 bash "$guard" v1.9.2 "$tmp/plain"
expect "iteration suffix"              1 bash "$guard" v1.9.25-warm1 "$tmp/plain"
expect "trailing space in tag"         1 bash "$guard" "v1.9.25 " "$tmp/plain"
expect "no tag argument"               2 bash "$guard" "" "$tmp/plain"
expect "missing properties file"       2 bash "$guard" v1.9.25 "$tmp/nope"
expect "versionName absent"            2 bash "$guard" v1.9.25 "$tmp/missing"
expect "value padded with spaces"      0 bash "$guard" v1.9.25 "$tmp/indented"
expect "commented line is ignored"     0 bash "$guard" v1.9.25 "$tmp/commented"
expect "duplicate keys fail closed"    1 bash "$guard" v1.9.25 "$tmp/duplicate"

if [[ "$failures" -ne 0 ]]; then
    printf '\n%s case(s) failed\n' "$failures" >&2
    exit 1
fi
printf '\nall version-tag guard cases pass\n'
