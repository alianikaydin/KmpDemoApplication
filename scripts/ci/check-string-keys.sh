#!/usr/bin/env bash
# Fails when a translated strings.xml does not have exactly the keys of the default
# values/strings.xml (localization AC-11). Pure Python, so the Gradle-less static step can run it.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

python3 scripts/ci/check_string_keys.py \
  shared/src/commonMain/composeResources \
  androidApp/src/main/res
