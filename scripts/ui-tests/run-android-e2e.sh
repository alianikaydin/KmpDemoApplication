#!/usr/bin/env bash
# Runs the end-to-end flows in .maestro-e2e/ against the real backend.
# Generates a unique user per run and delegates to run-android.sh.
set -euo pipefail

export E2E_EMAIL="e2e-${GITHUB_RUN_ID:-local}-$(date +%s)-${RANDOM}@example.com"
export E2E_PASSWORD="E2ePass1234"
export FLOWS="${FLOWS:-.maestro-e2e/}"
export MAESTRO_EXTRA_ARGS="-e E2E_EMAIL=$E2E_EMAIL -e E2E_PASSWORD=$E2E_PASSWORD"

exec bash "$(dirname "${BASH_SOURCE[0]}")/run-android.sh"
