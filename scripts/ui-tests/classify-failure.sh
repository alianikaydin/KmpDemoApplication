#!/usr/bin/env bash
# Labels a failed UI test job as a UI test failure or an infrastructure failure,
# based on the stage marker written by the other scripts. Usage: classify-failure.sh <platform>
set -euo pipefail

platform="${1:?usage: classify-failure.sh <android|ios>}"
stage="$(cat "ui-results/$platform/stage" 2>/dev/null || echo setup)"

case "$stage" in
  test)
    title="UI test failure"
    msg="Maestro flow failed on $platform, see report.xml and screenshots in the ${platform}-ui-results artifact"
    ;;
  build)
    title="Infrastructure failure"
    msg="Build failed on $platform (or the emulator/simulator did not start), not a UI assertion"
    ;;
  *)
    title="Infrastructure failure"
    msg="Job failed on $platform during '$stage' (build/emulator/install), not a UI assertion"
    ;;
esac

echo "::error title=$title::$msg"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  echo "**$title:** $msg" >> "$GITHUB_STEP_SUMMARY"
fi
