#!/usr/bin/env bash
#
# Fails when a release tag does not name the version in version.properties.
#
# Prevents the tag history from drifting away from what the build actually
# stamps into the APKs: without this guard a repository can publish
# `v1.9.17-warm3` while the manifests still say 1.9.25, and nobody can tell
# which APK a user installed.
#
# Usage:  scripts/check-version-tag.sh <tag> [version.properties]
#         scripts/check-version-tag.sh          # reads GITHUB_REF_NAME in CI
#
# Exit codes: 0 match, 1 mismatch, 2 usage or missing input.

set -euo pipefail

tag="${1:-${GITHUB_REF_NAME:-}}"
properties="${2:-version.properties}"

if [[ -z "$tag" ]]; then
    echo "error: no tag given and GITHUB_REF_NAME is empty" >&2
    echo "usage: $0 <tag> [version.properties]" >&2
    exit 2
fi

if [[ ! -f "$properties" ]]; then
    echo "error: $properties not found" >&2
    exit 2
fi

version_name="$(sed -n 's/^versionName[[:space:]]*=[[:space:]]*//p' "$properties" | tr -d '[:space:]')"

if [[ -z "$version_name" ]]; then
    echo "error: versionName is missing from $properties" >&2
    exit 2
fi

if [[ "$tag" != "v$version_name" ]]; then
    echo "error: tag '$tag' does not match versionName '$version_name' in $properties" >&2
    echo "       expected tag: v$version_name" >&2
    echo "       bump version.properties in the same commit, or retag correctly" >&2
    exit 1
fi

echo "ok: tag '$tag' matches versionName '$version_name'"
