#!/usr/bin/env bash
# Installs the debug APK on the running emulator and runs the Maestro flows.
# Called from android-emulator-runner (the emulator is already booted) or run
# locally with an emulator/device attached.
#
# Env: APK (path to the debug APK), OUT_DIR (default ui-results/android).
set -euo pipefail

OUT_DIR="${OUT_DIR:-ui-results/android}"
APK="${APK:-androidApp/build/outputs/apk/debug/androidApp-debug.apk}"
APP_ID="com.anksoft.myapplication"
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

cleanup() {
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
maestro test .maestro/ \
  -e APP_ID="$APP_ID" \
  --format junit \
  --output "$OUT_DIR/report.xml" \
  --test-output-dir "$OUT_DIR/maestro" || status=$?

# Screenshots land in the working directory when Maestro ignores the output dir.
mkdir -p "$OUT_DIR/screenshots"
find . -maxdepth 1 -name '*.png' -exec mv {} "$OUT_DIR/screenshots/" \;
exit "$status"
