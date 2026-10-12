# Local database

The app keeps a small SQLite database (SQLDelight 2.x, `core/database`) on Android and iOS. Its first user
is the account profile that Settings shows (`features/profile`). This page is the contract for everyone who
adds to it.

## It is a cache, nothing else

- The database holds **re-downloadable data only**. It is never the source of truth: the backend is, and a
  file can be deleted and rebuilt at any time (a file that cannot be opened is deleted by the app itself and
  created again, `AppDatabaseProvider`).
- **No secrets.** No access or refresh token, no password, no consent decision. Those stay in the session
  store (`SessionManager`, Keychain on iOS). The schema today is one table, `account_profile`, with three
  columns: `user_id`, `email`, `name`. `AppDatabaseSchemaTest` fails when a table or column is added, so the
  decision is made on purpose (AC-14).
- **No encryption** (product decision, backlog item 4). E-mail and name are stored in the clear in the app's
  private storage. On iOS this is a lower protection level than the Keychain; it is accepted and limited by
  the delete rules below.

## Where the file lives

| Platform | Location | Backup |
|----------|----------|--------|
| Android | `databases/app.db` in the app's private storage | excluded: `res/xml/data_extraction_rules.xml` (Android 12+) and `res/xml/backup_rules.xml` (Android 11 and older) list `app.db` and its `-journal`, `-wal`, `-shm` files |
| iOS | `Library/Caches/app.db` | Caches are not backed up, and the system may purge them; the profile is then fetched again |
| Web | none | no persistent database: the profile is kept in memory while the app is open (AC-23) |

Every app (dev, stage, prod) has its own sandbox and therefore its own file (AC-20). The web binds no
`DatabaseDriverFactory` (`platformDatabaseModule` is empty on js and wasmJs), so `AppDatabaseProvider`
reports no database and no log line is written for it.

## Delete at the end of a session

A feature that stores data of the signed-in account must remove it when the session ends (AC-10, AC-11):

1. Add the table in a `.sq` file.
2. The repository that owns the data is a `SessionObserver`. In `onSignedOut` it clears its in-memory copy at
   once and starts the deletion in the application scope (no disk I/O in the callback: observers run under the
   session lock, often on the main thread). Delete everything that does not belong to the session that is
   current when the deletion runs, like `ProfileRepositoryImpl` does with `retainOnly(currentUserId())`.
3. Reads are filtered by user id, so a row that stayed behind (a failed deletion) is never shown to another
   account (AC-12). The app also cleans up at start.
4. Update the list in `AppDatabaseSchemaTest` and this page.

## Local first, then the network

`ProfileRepositoryImpl` and `profileStateOf` are the pattern:

- Show what is stored at once, refresh from the network in the application scope (one refresh at a time).
- A failed refresh never hides stored data. It marks it stale and the screen shows a note and "Try again"
  (AC-5, AC-6). Only when nothing is stored does the screen show an empty state (AC-7).
- A result carries the session generation it was started in and is dropped when the session changed in the
  meantime (a late answer after logout is never stored or shown, AC-13).
- If the database is missing or broken, the in-memory copy alone works (AC-17, AC-23).

### Lock order

`ProfileRepositoryImpl` has two locks. Keep this order in any new code of the same kind:

- `stateLock` (plain lock) guards the fields. Never hold it across a suspension, a database call or a call
  into `SessionManager`.
- `dbMutex` orders every call into the data source. Never make a network call under it.
- Allowed nesting: session lock -> `stateLock`, `dbMutex` -> session lock, `dbMutex` -> `stateLock`. Do not
  nest `stateLock` -> session lock or `stateLock` -> `dbMutex`; that is how a cycle (deadlock) starts.

## Schema changes and migrations

The schema is versioned with SQLDelight's own mechanism: `.sq` files hold the current schema, `.sqm` files
hold the steps, and `shared/src/commonMain/sqldelight/databases/<version>.db` holds a snapshot of each
released version. `verifyMigrations` is on. CI runs `scripts/ci/verify-database-schema.sh` (step "Verify
database schema"): it first checks the committed snapshots against the migrations, then generates the snapshot
of the current schema and fails if that file is not committed. The generated snapshot is attached to a failed
run as the `database-schema` artifact, so it can be committed without running Gradle locally.

To change the schema:

1. Check that the snapshot of the current version is committed (`1.db` today).
2. Change the `.sq` file.
3. Add `<current version>.sqm` with the `ALTER TABLE` steps.
4. Push. The step "Verify database schema" fails and lists the new `<next version>.db`; download it from the
   `database-schema` artifact and commit it. Never edit an existing `.db` file by hand.
5. **The first real migration needs a test** in `androidHostTest`: copy a database created by the old schema
   (from the old `.db` snapshot), open it with `JdbcSqliteDriver`, apply `AppDatabase.Schema.migrate`, and show
   that the stored data survived (AC-19). Then update the version expectation in `AppDatabaseSchemaTest`.

## SQL dialect

Android 7 (`minSdk 24`) ships SQLite 3.9. Use only `SELECT`, `INSERT OR REPLACE`, `UPDATE` and `DELETE`.
No UPSERT (`ON CONFLICT ... DO UPDATE`), no `RETURNING`, no window functions.

## Logging

Log tags are `Database` (opening, recovery) and `Profile` (data source, repository). A line carries the action
and the error type only: never a user id, e-mail, name, SQL or file path (AC-15). `LogSqliteDriver` is not
used, because it logs every statement.

## Tests

- `commonTest`: repository logic against a fake store (`FakeProfileLocalDataSource`), also run on iOS.
- `androidHostTest`: the real queries and the file on real SQLite through JDBC (`JdbcDatabaseDriverFactory`),
  run by `./gradlew :shared:testAndroidHostTest`.
- `initKoin` in host tests needs `fakeProfileStoreModule` (the real store needs an Android context).
