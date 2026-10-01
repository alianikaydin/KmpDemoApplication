#!/usr/bin/env bash
# Shared helpers for the UI test scripts. Source this file; do not execute it.
# Requires OUT_DIR (where results, the stage marker and logs are written).

: "${OUT_DIR:?OUT_DIR must be set}"
mkdir -p "$OUT_DIR"

# Records the current stage so the CI "Classify failure" step can tell
# infrastructure problems (build/boot/install) from UI test failures.
mark_stage() {
  echo "$1" > "$OUT_DIR/stage"
}

# Reports an infrastructure failure (not a UI assertion failure) and exits.
infra_fail() {
  echo "::error title=Infrastructure failure::$1"
  exit 2
}
