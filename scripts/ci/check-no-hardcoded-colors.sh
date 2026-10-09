#!/usr/bin/env bash
# Fails when code defines a color outside core/presentation/theme (AC-9): colors must come from
# MaterialTheme.colorScheme roles so light and dark stay consistent. Color.Transparent and
# Color.Unspecified are allowed. Tests are exempt.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

pattern='Color\((0x|[0-9])|Color\.(White|Black|Red|Green|Blue|Gray|LightGray|DarkGray|Yellow|Cyan|Magenta)\b'

# git grep exits 0 on a match (violation), 1 on no match (success) and 2+ on an error such as a
# bad pathspec or pattern; an error must not pass as "no match".
set +e
matches=$(git grep -nE "$pattern" -- '*.kt' shared androidApp webApp \
    ':(glob,exclude)**/core/presentation/theme/**' ':(glob,exclude)**/build/**' \
    ':(glob,exclude)**/*Test*/**')
rc=$?
set -e

case "$rc" in
  0)
    echo "Hard-coded colors found; use MaterialTheme.colorScheme roles (see docs/theming.md):"
    echo "$matches"
    exit 1
    ;;
  1)
    echo "check-no-hardcoded-colors: OK"
    ;;
  *)
    echo "check-no-hardcoded-colors: git grep failed with exit code $rc" >&2
    exit "$rc"
    ;;
esac
