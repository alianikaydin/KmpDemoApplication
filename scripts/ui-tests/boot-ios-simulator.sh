#!/usr/bin/env bash
# Boots the first available iPhone simulator whose iOS runtime is >= 18.2 (the
# app's deployment target) and exports its UDID as SIM_UDID.
set -euo pipefail

OUT_DIR="${OUT_DIR:-ui-results/ios}"
export OUT_DIR
# shellcheck source=scripts/ui-tests/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

mark_stage boot

udid="$(xcrun simctl list devices available -j | jq -r '
  def ver: capture("iOS-(?<a>[0-9]+)-(?<b>[0-9]+)") | [(.a | tonumber), (.b | tonumber)];
  [ .devices | to_entries[]
    | select(.key | test("SimRuntime\\.iOS-"))
    | select((.key | ver) >= [18, 2])
    | .value[] | select(.name | startswith("iPhone")) | .udid ]
  | first // empty')"

[ -n "$udid" ] || infra_fail "No available iPhone simulator with iOS >= 18.2"

xcrun simctl boot "$udid" || infra_fail "Simulator $udid could not be booted"
timeout 300 xcrun simctl bootstatus "$udid" -b || infra_fail "Simulator $udid did not finish booting within 300s"

echo "Simulator booted: $udid"
[ -z "${GITHUB_ENV:-}" ] || echo "SIM_UDID=$udid" >> "$GITHUB_ENV"
[ -z "${GITHUB_OUTPUT:-}" ] || echo "udid=$udid" >> "$GITHUB_OUTPUT"
