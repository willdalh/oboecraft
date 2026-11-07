#!/usr/bin/env bash
# Tests next-version.sh against example tag lists. Run by the Build workflow.
set -uo pipefail

script="$(dirname "$0")/next-version.sh"
failures=0

# expect <bump> <tags, one per line> <expected version, or "fail">
expect() {
  local bump=$1 tags=$2 expected=$3 actual
  if actual=$(printf '%s' "$tags" | "$script" "$bump" 2>/dev/null); then :; else actual=fail; fi
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL: bump '$bump' of tags [${tags//$'\n'/ }] gave '$actual', expected '$expected'"
    failures=$((failures + 1))
  fi
}

released=$'v0.1.0\nv0.2.0'
expect patch "$released" 0.2.1
expect minor "$released" 0.3.0
expect major "$released" 1.0.0

# The highest tag counts, compared as numbers and not as text, whatever the order.
expect patch $'v0.10.0\nv0.9.5\nv0.2.0' 0.10.1
expect minor $'v1.0.0\nv0.99.99' 1.1.0
expect major $'v2.3.4\nv10.0.0\nv9.9.9' 11.0.0
expect patch $'v1.2.9\nv1.2.10' 1.2.11
expect patch 'v1.2.3' 1.2.4
expect patch $'v1.2.3\n' 1.2.4

# Tags that aren't exactly vX.Y.Z are ignored.
expect patch $'v0.2.0\nv9.0.0-rc.1\nv9.0\n9.0.0\nv9.0.0.0\nrelease-9\nv09.0.0\n v9.0.0\nv9.0.0 \nV9.0.0' 0.2.1

# No tags, or none of the form vX.Y.Z, fail instead of guessing a first version.
expect patch '' fail
expect minor $'v1.0\nlatest\nv1.0.0-beta' fail

# Only patch, minor and major are bumps.
expect '' "$released" fail
expect Patch "$released" fail
expect 'patch; echo 9.9.9' "$released" fail
# shellcheck disable=SC2016 # The command substitution is meant to stay unexpanded.
expect '$(echo 9.9.9)' "$released" fail

if ((failures > 0)); then
  echo "$failures failed."
  exit 1
fi
echo "All passed."
