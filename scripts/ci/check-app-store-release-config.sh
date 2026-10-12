#!/usr/bin/env bash
# Proves that :shared:verifyAppStoreReleaseConfig stops a prod App Store archive without a numeric
# App Store ID (AC-20) and lets every other build through. It needs Gradle, so the CI build job runs
# it; static-checks.sh skips it on purpose and must stay Gradle-free.
#
# The variables are set per command with `env`, so nothing leaks into the calling shell or later steps.
set -uo pipefail

cd "$(git rev-parse --show-toplevel)"

status=0
output="$(mktemp)"
trap 'rm -f "$output"' EXIT

# Runs the task with the given Xcode-style variables. --rerun-tasks keeps Gradle from skipping it as up to date.
run_task() {
  env "$@" ./gradlew :shared:verifyAppStoreReleaseConfig --rerun-tasks --console=plain >"$output" 2>&1
}

expect_pass() {
  local label="$1"
  shift
  if run_task "$@"; then
    echo "OK:   $label passed as expected"
  else
    echo "FAIL: $label should have passed"
    cat "$output"
    status=1
  fi
}

expect_failure_naming() {
  local label="$1" name="$2"
  shift 2
  if run_task "$@"; then
    echo "FAIL: $label should have failed"
    cat "$output"
    status=1
  elif grep -q "$name" "$output"; then
    echo "OK:   $label failed as expected and names $name"
    grep -m1 "$name" "$output"
  else
    echo "FAIL: $label failed, but the output does not mention $name"
    cat "$output"
    status=1
  fi
}

expect_failure_naming "(a) prod archive without an App Store ID" KMP_APP_STORE_ID \
  ACTION=install KMP_ENVIRONMENT=PROD KMP_APP_STORE_ID=
expect_pass "(b) prod archive with an App Store ID" \
  ACTION=install KMP_ENVIRONMENT=PROD KMP_APP_STORE_ID=123456789
expect_pass "(c) prod build without an App Store ID" \
  ACTION=build KMP_ENVIRONMENT=PROD KMP_APP_STORE_ID=

exit "$status"
