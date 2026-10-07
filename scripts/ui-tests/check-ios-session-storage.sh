#!/usr/bin/env bash
# Checks on the iOS simulator that the session is kept out of NSUserDefaults
# (ios-keychain AC-1) and that an update from the NSUserDefaults build moves a
# logged-in session to the Keychain (ios-keychain AC-4).
# Env: SIM_UDID (booted simulator), APP_PATH (current build), LEGACY_APP_PATH
# (same app built with the old NSUserDefaults session storage), OUT_DIR
# (default ui-results/ios-storage).
set -euo pipefail

OUT_DIR="${OUT_DIR:-ui-results/ios-storage}"
APP_ID="com.anksoft.myapplication.MyApplication.dev"
SESSION_KEYS=(auth_token refresh_token user_id user_email user_name)
export OUT_DIR

# shellcheck source=scripts/ui-tests/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

: "${SIM_UDID:?SIM_UDID must be set}"
: "${APP_PATH:?APP_PATH must be set}"
: "${LEGACY_APP_PATH:?LEGACY_APP_PATH must be set}"

mkdir -p "$OUT_DIR/maestro"

run_flow() {
  local flow="$1"
  maestro --udid "$SIM_UDID" test "$flow" \
    -e APP_ID="$APP_ID" \
    --format junit \
    --output "$OUT_DIR/$(basename "$flow" .yaml).xml" \
    --test-output-dir "$OUT_DIR/maestro"
}

install_app() {
  xcrun simctl install "$SIM_UDID" "$1" || infra_fail "App at $1 could not be installed"
}

stop_app() {
  xcrun simctl terminate "$SIM_UDID" "$APP_ID" 2>/dev/null || true
}

# Prints the session keys present in the app's NSUserDefaults plist, one per line.
session_keys_in_prefs() {
  local container plist
  container="$(xcrun simctl get_app_container "$SIM_UDID" "$APP_ID" data)"
  plist="$container/Library/Preferences/$APP_ID.plist"
  [ -f "$plist" ] || return 0
  plutil -convert json -o - "$plist" | jq -r 'keys[]' | grep -Fx "${SESSION_KEYS[@]/#/-e}" || true
}

# cfprefsd writes the plist asynchronously, so poll for up to 15 seconds.
# Usage: expect_prefs present|absent <label>
expect_prefs() {
  local want="$1" label="$2" found=""
  for _ in $(seq 1 15); do
    found="$(session_keys_in_prefs)"
    if [ "$want" = present ] && [ -n "$found" ]; then break; fi
    if [ "$want" = absent ] && [ -z "$found" ]; then break; fi
    sleep 1
  done
  echo "Session keys in NSUserDefaults ($label): ${found:-none}" | tr '\n' ' '
  echo
  if [ "$want" = present ] && [ -z "$found" ]; then
    echo "::error::$label: expected the session in NSUserDefaults, found none"
    return 1
  fi
  if [ "$want" = absent ] && [ -n "$found" ]; then
    echo "::error::$label: session keys still in NSUserDefaults: $(echo "$found" | tr '\n' ' ')"
    return 1
  fi
}

mark_stage test
xcrun simctl uninstall "$SIM_UDID" "$APP_ID" 2>/dev/null || true

echo "::group::AC-4: update from the NSUserDefaults build keeps the session"
install_app "$LEGACY_APP_PATH"
run_flow .maestro/storage/login_fresh.yaml
stop_app
expect_prefs present "old build after login"
# Installing over the old build keeps the app's data container, like an App Store update.
install_app "$APP_PATH"
run_flow .maestro/storage/relaunch_shows_home.yaml
stop_app
expect_prefs absent "new build after first launch"
echo "::endgroup::"

echo "::group::AC-1: login on the new build writes nothing to NSUserDefaults"
run_flow .maestro/storage/login_fresh.yaml
stop_app
expect_prefs absent "new build after login"
# The session must still be there, so it was stored in the Keychain.
run_flow .maestro/storage/relaunch_shows_home.yaml
stop_app
echo "::endgroup::"

echo "AC-1 and AC-4 passed on the simulator."
