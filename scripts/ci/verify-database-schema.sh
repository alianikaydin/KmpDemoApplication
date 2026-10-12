#!/usr/bin/env bash
# Guards the local database schema (AC-19). Needs Gradle, so it is not a check-*.sh script and
# static-checks.sh does not run it; the build-and-test job runs it as its own step.
#
# Order matters: the migrations are verified against the committed snapshots first. Generating the
# snapshot of the current schema comes second, otherwise it would overwrite the file the check reads.
set -uo pipefail

cd "$(git rev-parse --show-toplevel)"

dir=shared/src/commonMain/sqldelight/databases
status=0

echo "==> verifySqlDelightMigration"
./gradlew :shared:verifySqlDelightMigration --console=plain || status=1

echo "==> generateCommonMainAppDatabaseSchema"
./gradlew :shared:generateCommonMainAppDatabaseSchema --console=plain || status=1

# A new snapshot that is not committed means the schema changed (or 1.db is missing).
# Existing snapshots that differ are not compared here: verifySqlDelightMigration did that.
while IFS= read -r line; do
  echo "::error::Commit the schema snapshot ${line#?? } (artifact database-schema)"
  status=1
done < <(git status --porcelain --untracked-files=all -- "$dir" | grep '^??' || true)

# TEMP: print the Gradle-generated snapshot so it can be committed byte for byte.
base64 -w0 "$dir/1.db" | sed 's/^/SNAPSHOT_1_DB_BASE64:/'; echo

# Leave the tree as it was found.
git checkout -- "$dir" 2>/dev/null || true

exit "$status"
