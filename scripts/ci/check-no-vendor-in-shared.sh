#!/usr/bin/env bash
# Fails when shared code imports a crash reporting vendor SDK (crash reporting AC-1). Common and
# platform code under shared/ only knows the CrashReporter interface; the Firebase SDKs live in
# androidApp and in the Xcode project (docs/crash-reporting.md).
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

pattern='^import (com\.google\.firebase|cocoapods\.Firebase|platform\.Firebase|Firebase[A-Za-z]*)\b'

# git grep exits 0 on a match (violation), 1 on no match (success) and 2+ on an error such as a
# bad pathspec or pattern; an error must not pass as "no match".
set +e
matches=$(git grep -nE "$pattern" -- shared/src)
rc=$?
set -e

case "$rc" in
  0)
    echo "Vendor SDK import in shared/; use CrashReporter (see docs/crash-reporting.md):"
    echo "$matches"
    exit 1
    ;;
  1)
    echo "check-no-vendor-in-shared: OK"
    ;;
  *)
    echo "check-no-vendor-in-shared: git grep failed with exit code $rc" >&2
    exit "$rc"
    ;;
esac
