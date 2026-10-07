#!/usr/bin/env bash
# Runs every scripts/ci/check-*.sh in order and fails if any of them fails.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

status=0
for check in scripts/ci/check-*.sh; do
  echo "==> $check"
  bash "$check" || status=1
done
exit "$status"
