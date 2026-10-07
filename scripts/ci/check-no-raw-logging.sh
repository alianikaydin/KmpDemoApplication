#!/usr/bin/env bash
# Fails when code logs outside core/logging (AC-8): every log must go through AppLogger so
# redaction and the per-environment level apply. Writers in core/logging are exempt.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

pattern='(^|[^A-Za-z0-9_.])print(ln)?\(|Log\.[dviwe]\(|NSLog\(|console\.(log|info|warn|error|debug)\(|printStackTrace\('

# git grep exits 1 when nothing matches, which is the success case here.
if matches=$(git grep -nE "$pattern" -- shared androidApp webApp iosApp \
    ':(glob,exclude)**/core/logging/**' ':(glob,exclude)**/build/**'); then
  echo "Raw logging found; use AppLogger (see docs/logging.md):"
  echo "$matches"
  exit 1
fi
echo "check-no-raw-logging: OK"
