#!/usr/bin/env bash
# Prints the next release version, like 0.2.1, for a bump of patch, minor or major.
#
# Usage: git tag --list | next-version.sh <patch|minor|major>
#
# Reads tag names, one per line, from standard input. Only tags that are exactly vX.Y.Z, with no leading zeros, count;
# others are ignored. The highest of them is bumped, so the new version is always above every release tag. Fails if
# the bump isn't one of the three or if there is no such tag.
set -euo pipefail

bump=${1-}
case "$bump" in
  patch | minor | major) ;;
  *)
    echo "The bump '$bump' isn't patch, minor or major." >&2
    exit 1
    ;;
esac

# Up to 9 digits, so the numbers fit in Bash's arithmetic.
number='(0|[1-9][0-9]{0,8})'
release_tag="^v$number\.$number\.$number$"

found=false
major=0 minor=0 patch=0
while IFS= read -r tag || [[ -n "$tag" ]]; do
  [[ "$tag" =~ $release_tag ]] || continue
  x=${BASH_REMATCH[1]} y=${BASH_REMATCH[2]} z=${BASH_REMATCH[3]}
  if ! $found || ((x > major || (x == major && (y > minor || (y == minor && z > patch))))); then
    major=$x minor=$y patch=$z
    found=true
  fi
done

if ! $found; then
  echo "There is no tag like v1.2.3, so there is no version to bump." >&2
  exit 1
fi

case "$bump" in
  patch) patch=$((patch + 1)) ;;
  minor) minor=$((minor + 1)) patch=0 ;;
  major) major=$((major + 1)) minor=0 patch=0 ;;
esac
echo "$major.$minor.$patch"
