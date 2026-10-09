#!/usr/bin/env bash
# Validates the hand-edited Xcode project and the committed SPM lock file.
# Needs Xcode on PATH. Usage: check-ios-project.sh <cloned-source-packages-dir>
set -euo pipefail

spm_dir="${1:?usage: check-ios-project.sh <cloned-source-packages-dir>}"
project="iosApp/iosApp.xcodeproj"

plutil -lint "$project/project.pbxproj"
xcodebuild -list -project "$project"

xcodebuild -resolvePackageDependencies -project "$project" -scheme iosApp \
  -clonedSourcePackagesDirPath "$spm_dir"

# Package.resolved must be exactly what Xcode produces. On a diff, print the regenerated
# file so it can be committed from the log.
if ! git diff --exit-code -- '**/Package.resolved'; then
  echo "::error title=Package.resolved is out of date::Xcode regenerated it; the new content is below"
  git ls-files -m -- '**/Package.resolved' | while read -r f; do
    echo "===== $f ====="
    cat "$f"
  done
  exit 1
fi
