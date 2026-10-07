# Logging

All layers log through one interface, `AppLogger` (`core/logging`). It is bound once in
Koin (`loggingModule`) and injected like any other dependency. It is called `AppLogger`
because Ktor and Kermit already have their own `Logger` types.

```kotlin
class LoginScreenModel(/* ... */, private val logger: AppLogger) {
    fun submit() {
        logger.debug(LogTags.AUTH) { "login blocked by validation" }
    }
}
```

The message is a lambda: it is only built when the level passes, so debug strings cost
nothing in prod. Use `debug`, `info`, `warn(tag, throwable)` and `error(tag, throwable)`.
Only the simple class name of a throwable is recorded, never its message or stack trace.

## Levels per environment

The minimum level comes from `AppConfig.environment` (see `docs/environments.md`).

| Environment | Minimum level | HTTP log (Ktor) |
|-------------|---------------|-----------------|
| dev | DEBUG | headers |
| stage | INFO | off |
| prod | WARN | off |

`LogLevel.BODY` and `ALL` are never used, because request bodies contain passwords.
`Authorization`, `Cookie` and `Set-Cookie` header values are masked.

## Tags

Fixed constants in `LogTags`: `App`, `Network`, `Http`, `Auth`. Do not derive tags from
class names (minified JS would mangle them).

## What must never be logged

Never pass a user id, e-mail, token or password as a log parameter. `Redactor` masks
e-mail addresses, `Bearer` values, JWTs and `token`/`password`/`secret`-style key-value
pairs, but it is pattern based and only a safety net. Network failures are logged in one
place (`safeCall`) with the error type, path and status code only.

## Writers

`DispatchingLogger` sends each entry to every `LogWriter` registered in Koin. The default
writer prints to the platform console (Logcat, Xcode console, browser console) through
Kermit. To add a destination, such as crash reporting, register another writer:

```kotlin
single<LogWriter>(named("crash")) { CrashReportingWriter() }
```

The writer list is captured once, when `AppLogger` is created (at the start of `initKoin`).
A writer must therefore be registered in the modules passed to `initKoin` (including
`platformModules`); one added later with `loadKoinModules` is never called. A writer that
throws is skipped and does not affect the caller or the other writers.

Writers get an already redacted `LogEntry`. To turn logging off, override the logger:
`single<AppLogger> { NoOpLogger }`.

Unit tests must not resolve the real console writer (`android.util.Log` is not available
on the host). Use `recordingLogger()` from the test sources, or override
`named(CONSOLE_LOG_WRITER)` in `platformModules` when calling `initKoin`.

## CI check

`scripts/ci/check-no-raw-logging.sh` fails the build when `println`, `Log.*` (including
`Log.wtf`), `System.out`/`System.err` printing, `NSLog`, `os_log`, `debugPrint`, `console.*`,
`printStackTrace` or a direct Kermit import appear outside `core/logging`. A `git grep` error
also fails the check. It runs from
`scripts/ci/static-checks.sh`, which runs every `scripts/ci/check-*.sh`.
