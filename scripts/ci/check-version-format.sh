#!/usr/bin/env bash
# Fails when VERSION_NAME in version.properties is not 1 to 3 dot-separated numeric components
# (e.g. 1, 1.2, 1.2.3). The app compares its version numerically against the backend's version
# policy (docs/app-updates.md); anything else would silently disable the update check.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

version=$(sed -n 's/^VERSION_NAME=//p' version.properties | head -n 1)

if [[ ! "$version" =~ ^[0-9]{1,9}(\.[0-9]{1,9}){0,2}$ ]]; then
  echo "check-version-format: VERSION_NAME '$version' in version.properties must be 1 to 3 numeric components (e.g. 1.2.3)" >&2
  exit 1
fi
echo "check-version-format: OK ($version)"
