#!/usr/bin/env bash
# Fails when code logs outside core/logging (AC-8): every log must go through AppLogger so
# redaction and the per-environment level apply. Writers in core/logging are exempt.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

pattern='(^|[^A-Za-z0-9_.])print(ln)?\(|Log\.(v|d|i|w|e|wtf)\(|NSLog\(|os_log\(|debugPrint\(|System\.(out|err)\.print|console\.(log|info|warn|error|debug)\(|printStackTrace\(|import co\.touchlab\.kermit'

# git grep exits 0 on a match (violation), 1 on no match (success) and 2+ on an error such as a
# bad pathspec or pattern; an error must not pass as "no match".
set +e
matches=$(git grep -nE "$pattern" -- shared androidApp webApp iosApp \
    ':(glob,exclude)**/core/logging/**' ':(glob,exclude)**/build/**')
rc=$?
set -e

case "$rc" in
  0)
    echo "Raw logging found; use AppLogger (see docs/logging.md):"
    echo "$matches"
    exit 1
    ;;
  1)
    echo "check-no-raw-logging: OK"
    ;;
  *)
    echo "check-no-raw-logging: git grep failed with exit code $rc" >&2
    exit "$rc"
    ;;
esac
