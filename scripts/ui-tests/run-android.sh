#!/usr/bin/env bash
# Installs the debug APK on the running emulator and runs the Maestro flows.
# Called from android-emulator-runner (the emulator is already booted) or run
# locally with an emulator/device attached.
#
# Env: APK (path to the debug APK), OUT_DIR (default ui-results/android),
# FLOWS (Maestro workspace/flow path, default .maestro/),
# MAESTRO_EXTRA_ARGS (extra `maestro test` arguments, e.g. "-e KEY=value"),
# SYSTEM_APPEARANCE (light or dark, default light): the emulator's night mode for the run.
set -euo pipefail

OUT_DIR="${OUT_DIR:-ui-results/android}"
APK="${APK:-androidApp/build/outputs/apk/dev/debug/androidApp-dev-debug.apk}"
APP_ID="com.anksoft.myapplication.dev"
FLOWS="${FLOWS:-.maestro/}"
SYSTEM_APPEARANCE="${SYSTEM_APPEARANCE:-light}"
export OUT_DIR

# shellcheck source=scripts/ui-tests/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

mkdir -p "$OUT_DIR/video" "$OUT_DIR/maestro"

mark_stage install
adb wait-for-device
booted=""
for _ in $(seq 1 60); do
  booted="$(adb shell getprop sys.boot_completed | tr -d '\r' || true)"
  [ "$booted" = "1" ] && break
  sleep 2
done
[ "$booted" = "1" ] || infra_fail "Emulator did not finish booting within 120s"
[ -f "$APK" ] || infra_fail "APK not found at $APK"
adb install -r "$APK" || infra_fail "APK could not be installed"

case "$SYSTEM_APPEARANCE" in
  light) night_mode=no ;;
  dark) night_mode=yes ;;
  *) infra_fail "SYSTEM_APPEARANCE must be light or dark, got: $SYSTEM_APPEARANCE" ;;
esac
adb shell cmd uimode night "$night_mode" || infra_fail "Could not set the emulator appearance"

adb logcat -c
adb logcat > "$OUT_DIR/logcat.txt" &
logcat_pid=$!

# screenrecord stops after 170s per file, so record in a loop of chunks.
(
  i=0
  while :; do
    adb shell screenrecord --time-limit 170 "/sdcard/ui-$i.mp4" || true
    i=$((i + 1))
  done
) &
video_pid=$!

# Prints what the device was doing into the job log (artifacts are not readable
# from the log): focused window, crashes/ANRs and graphics errors from logcat.
diagnose() {
  echo "::group::Emulator diagnostics"
  adb devices -l || true
  adb shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' || true
  adb shell pidof "$APP_ID" || echo "app process not running"
  adb shell screencap -p /sdcard/diag.png 2>/dev/null && adb pull /sdcard/diag.png "$OUT_DIR/diag.png" || true
  echo "--- logcat: crashes, ANRs, graphics errors, app tag"
  adb logcat -d 2>/dev/null | grep -E "FATAL EXCEPTION|AndroidRuntime|ANR in|Application Not Responding|EGL|GLES|am_crash|am_anr|Displayed|$APP_ID" | tail -n 80 || true
  echo "--- app process log (last 80 lines, all tags)"
  adb logcat -d --pid="$(adb shell pidof "$APP_ID" | tr -d '\r' | awk '{print $1}')" 2>/dev/null | tail -n 80 || true
  echo "--- visible UI elements (uiautomator): resource-id / text"
  adb shell uiautomator dump /sdcard/hierarchy.xml >/dev/null 2>&1 \
    && adb shell cat /sdcard/hierarchy.xml 2>/dev/null \
      | grep -oE '(resource-id|text|package)="[^"]+"' | head -n 60 || true
  echo "--- maestro: commands and their status"
  find "$OUT_DIR/maestro" "$HOME/.maestro/tests" -name 'commands-*.json' 2>/dev/null | tail -n 3 | while read -r f; do
    echo "# $f"
    grep -oE '"(tapOnElement|inputTextCommand|assertConditionCommand|extendedWaitUntil|launchAppCommand|runFlowCommand|scrollUntilVisible|waitForAnimationToEndCommand|takeScreenshotCommand|hideKeyboardCommand|[A-Za-z]+Command)"|"status" *: *"[A-Z]+"|"id" *: *"[a-z_]+"' "$f" | tr '\n' ' ' | fold -w 200
    echo
  done
  find "$HOME/.maestro/tests" -name maestro.log 2>/dev/null | tail -n 1 | xargs -r tail -n 60 || true
  echo "::endgroup::"
}

cleanup() {
  [ "${status:-0}" -ne 0 ] && diagnose
  kill "$video_pid" 2>/dev/null || true
  adb shell pkill -INT screenrecord 2>/dev/null || true
  sleep 2
  # Video is best effort: some emulator images record empty files.
  for f in $(adb shell ls /sdcard/ui-*.mp4 2>/dev/null | tr -d '\r'); do
    adb pull "$f" "$OUT_DIR/video/" || true
  done
  kill "$logcat_pid" 2>/dev/null || true
}
trap cleanup EXIT

mark_stage test
status=0
# shellcheck disable=SC2086  # MAESTRO_EXTRA_ARGS is intentionally word-split
maestro test "$FLOWS" \
  -e APP_ID="$APP_ID" \
  -e SYSTEM_APPEARANCE="$SYSTEM_APPEARANCE" \
  ${MAESTRO_EXTRA_ARGS:-} \
  --format junit \
  --output "$OUT_DIR/report.xml" \
  --test-output-dir "$OUT_DIR/maestro" || status=$?

# Screenshots land in the working directory when Maestro ignores the output dir.
mkdir -p "$OUT_DIR/screenshots"
find . -maxdepth 1 -name '*.png' -exec mv {} "$OUT_DIR/screenshots/" \;
exit "$status"
