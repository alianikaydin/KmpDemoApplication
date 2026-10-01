#!/usr/bin/env bash
# Installs the simulator build and runs the Maestro flows on the booted simulator.
# Env: SIM_UDID (booted simulator), APP_PATH (path to MyApplication.app),
# OUT_DIR (default ui-results/ios).
set -euo pipefail

OUT_DIR="${OUT_DIR:-ui-results/ios}"
APP_ID="com.anksoft.myapplication.MyApplication"
export OUT_DIR

# shellcheck source=scripts/ui-tests/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

: "${SIM_UDID:?SIM_UDID must be set}"
: "${APP_PATH:?APP_PATH must be set}"

mkdir -p "$OUT_DIR/video" "$OUT_DIR/maestro"

mark_stage install
[ -d "$APP_PATH" ] || infra_fail "App bundle not found at $APP_PATH"
xcrun simctl install "$SIM_UDID" "$APP_PATH" || infra_fail "App could not be installed on the simulator"

xcrun simctl io "$SIM_UDID" recordVideo --codec h264 --force "$OUT_DIR/video/ios.mp4" &
video_pid=$!

cleanup() {
  # SIGINT lets simctl finalize the mp4 file.
  kill -INT "$video_pid" 2>/dev/null || true
  # Give simctl ~10s to finalize the mp4, then force it down.
  for _ in $(seq 1 10); do
    kill -0 "$video_pid" 2>/dev/null || break
    sleep 1
  done
  kill -TERM "$video_pid" 2>/dev/null || true
  wait "$video_pid" 2>/dev/null || true
}
trap cleanup EXIT

mark_stage test
status=0
maestro --udid "$SIM_UDID" test .maestro/ \
  -e APP_ID="$APP_ID" \
  --format junit \
  --output "$OUT_DIR/report.xml" \
  --test-output-dir "$OUT_DIR/maestro" || status=$?

# Screenshots land in the working directory when Maestro ignores the output dir.
mkdir -p "$OUT_DIR/screenshots"
find . -maxdepth 1 -name '*.png' -exec mv {} "$OUT_DIR/screenshots/" \;
if [ "$status" -ne 0 ]; then
  # Log what is on screen after the failure so it can be diagnosed without the artifact.
  echo "::group::Simulator view hierarchy after failure"
  maestro --udid "$SIM_UDID" hierarchy 2>/dev/null \
    | jq -c '.. | .attributes? // empty | {id: .["resource-id"], text, accessibilityText, value, enabled}
        | with_entries(select(.value != null and .value != ""))
        | select(has("id") or has("text") or has("accessibilityText") or has("value"))' \
    || echo "Could not read the view hierarchy"
  echo "::endgroup::"
fi
exit "$status"
