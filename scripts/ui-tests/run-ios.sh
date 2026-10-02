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
run_marker="$(mktemp)"
run_start="$(date '+%Y-%m-%d %H:%M:%S')"
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
  # `maestro hierarchy` prints log lines before the JSON document; skip them.
  maestro --udid "$SIM_UDID" hierarchy 2>/dev/null | sed -n '/^{/,$p' \
    | jq -c '.. | .attributes? // empty | {id: .["resource-id"], text, accessibilityText, value, enabled}
        | with_entries(select(.value != null and .value != ""))
        | select(has("id") or has("text") or has("accessibilityText") or has("value"))' \
    || echo "Could not read the view hierarchy"
  echo "::endgroup::"

  # Print the command-by-command status of each failed flow, so the failing step is visible.
  for commands in $(find "$OUT_DIR/maestro" -name 'commands-*.json' 2>/dev/null); do
    if jq -e 'any(.[]; .metadata.status == "FAILED")' "$commands" >/dev/null 2>&1; then
      echo "::group::Steps of $(basename "$commands")"
      jq -r '.[] | "\(.metadata.status // "?")\t\(.command | keys[0])\t\(.command | tostring | .[0:160])"' "$commands" \
        || head -c 8000 "$commands"
      echo "::endgroup::"
    fi
  done

  # The app's own log shows why it stopped when no crash report is written.
  # XCTest/automation chatter from Maestro's driver is filtered out so the whole
  # run fits; the whole filtered log is kept in the artifact as app.log.
  xcrun simctl spawn "$SIM_UDID" log show --start "$run_start" --style compact \
    --predicate '(process == "MyApplication" AND NOT (subsystem BEGINSWITH "com.apple.dt" OR subsystem == "com.apple.xpc" OR subsystem == "com.apple.CoreAnalytics")) OR ((process == "SpringBoard" OR process == "runningboardd" OR process == "launchd_sim") AND eventMessage CONTAINS[c] "anksoft" AND NOT eventMessage CONTAINS "SBHLibrary")' \
    2>/dev/null | grep -v '^Filtering' > "$OUT_DIR/app.log" || echo "Could not read the simulator log"
  echo "::group::App launches and terminations during the run"
  grep -iE 'launch|terminat|exit|crash|kill|watchdog|jetsam|signal|fault' "$OUT_DIR/app.log" | cut -c1-400 | tail -n 200 || true
  echo "::endgroup::"
  echo "::group::App log (last 200 lines)"
  cut -c1-400 "$OUT_DIR/app.log" | tail -n 200
  echo "::endgroup::"
  echo "::group::Maestro log lines about the app stopping"
  grep -rihE 'crash|not running|terminat|stopped' "$OUT_DIR/maestro" --include='*.log' | cut -c1-400 | tail -n 80 || true
  echo "::endgroup::"

  # Crash reports are written asynchronously; give ReportCrash a moment.
  sleep 10
  echo "Diagnostic reports written during this run:"
  find "$HOME/Library/Logs/DiagnosticReports" -newer "$run_marker" -type f 2>/dev/null || true
  # Print crash reports of the app produced during this run.
  for report in $(find "$HOME/Library/Logs/DiagnosticReports" -name '*.ips' -newer "$run_marker" 2>/dev/null | grep -i 'MyApplication\|iosApp'); do
    echo "::group::Crash report $(basename "$report")"
    tail -n +2 "$report" | jq '{
        exception, termination, asi,
        frames: [.threads[.faultingThread].frames[]? | "\(.imageIndex) \(.symbol // "?")"][:60]
      }' 2>/dev/null || head -c 8000 "$report"
    echo "::endgroup::"
  done
fi
exit "$status"
