# Crash reporting

Crashes and caught errors go to Firebase Crashlytics on Android and iOS, only with the account's
consent (`docs/privacy-consent.md`) and only in stage and prod. **Web is not supported**: the web
build has no vendor, crash reporting is a no-op there and the browser makes no Firebase request.

## Rule for feature code

- Errors are recorded with `AppLogger.error(tag, throwable) { "..." }`. That is the only way.
- Never write personal data (e-mail, user id, token, password) into a log message or breadcrumb.
  `Redactor` masks the usual patterns, but it is a safety net.
- Never call `CrashReporter` or a vendor SDK directly from feature code.
- A throwable's message never reaches a report. Only its type name, its causes' type names and
  its stack frames do. Put the context you need into the log message.

## Architecture

```
AppLogger.error(tag, throwable) { "msg" }
   |
DispatchingLogger --(RemoteLogWriter, INFO and above, gets the throwable)--> CrashLogWriter
                                                                                |
                         INFO+  -> breadcrumb          ERROR + throwable -> non-fatal
                                                                                |
                                                          GatedCrashReporter (gate + Redactor)
                                                                                |
                                  platform CrashReporter  <--- Koin: named(PLATFORM_CRASH_REPORTER)
                                  Android: FirebaseCrashReporter     iOS: IosCrashReporter -> Swift bridge
                                  web / no config: none -> NoOpCrashReporter

ConsentManager.optionalDataConsent + AppEnvironment --> CrashReportingController
                      opens or closes CrashGate, switches the SDK collection flag, manages the install id
```

- `core/crash` (common code) knows no vendor type: `CrashReporter`, `NoOpCrashReporter`,
  `NonFatalReport`, `CrashGate`, `GatedCrashReporter`, `CrashLogWriter`, `InstallIdStore`,
  `CrashReportingController`, `TestCrashTrigger`. `scripts/ci/check-no-vendor-in-shared.sh`
  fails the build when `shared/src` imports a Firebase type.
- Android: the Firebase SDK is a dependency of `androidApp` only (`crash/FirebaseCrashReporter`).
  `ReportedException` and `SanitizingExceptionHandler` (pure JVM, in `shared/androidMain`) strip
  throwable messages from caught and fatal errors.
- iOS: the Firebase SDK is a Swift package of the Xcode project only. `FirebaseCrashBridge.swift`
  implements the Kotlin `NativeCrashBridge`; `IosCrashReporter` and a Kotlin/Native unhandled
  exception hook (`KotlinCrashHook.kt`) send the Kotlin stack addresses so a Kotlin crash shows a
  readable Kotlin stack.
- A build without a vendor (web, or no Firebase config file) binds nothing under
  `PLATFORM_CRASH_REPORTER`. The controller stays idle, no install id is created and no storage
  is touched.

## Consent and environment

| Consent | dev | stage / prod |
|---------|-----|--------------|
| `GRANTED` | closed | environment key and install id set, SDK collection on, then the gate opens |
| `UNKNOWN` (signed out, loading, not decided, text update pending) | closed | gate closes, then SDK collection goes off; the install id is kept |
| `DENIED` (refused or withdrawn) | closed | as `UNKNOWN`, plus unsent reports are deleted, the install id is forgotten and the vendor's own installation id (Firebase FID) is deleted |

- The rule follows the current state, not transitions, so a withdrawal made on another device is
  handled like one made here. The same state is applied only once per launch.
- At every start the controller first deletes unsent reports, before turning anything on.
  Whatever is still unsent then was recorded without consent (AC-6). A crash that happened while
  collection was on is sent by the SDK itself at its start.
- The manifest (`firebase_crashlytics_collection_enabled`, `firebase_data_collection_default_enabled`)
  and `Info.plist` (`FirebaseCrashlyticsCollectionEnabled`, `FirebaseDataCollectionDefaultEnabled`)
  start the SDK with collection off.
- The anonymous install id is a random UUID in its own store (`CrashReportingSettings`): the
  `crash_reporting` SharedPreferences file on Android (excluded from backup and device transfer,
  see `data_extraction_rules.xml` and `backup_rules.xml`), `NSUserDefaults` on iOS. It has no
  relation to the account, so signing out and in keeps it; a reinstall or a withdrawal replaces it.

## Firebase configuration

One Firebase project with one app registration per environment and platform (3 x 2). The config
files are **not committed** (`.gitignore`); only `*.example` files are.

| Platform | Environment | File | Application / bundle id |
|----------|-------------|------|-------------------------|
| Android | dev | `androidApp/src/dev/google-services.json` | `com.anksoft.myapplication.dev` |
| Android | stage | `androidApp/src/stage/google-services.json` | `com.anksoft.myapplication.stage` |
| Android | prod | `androidApp/src/prod/google-services.json` | `com.anksoft.myapplication` |
| iOS | dev (Debug) | `iosApp/Firebase/dev/GoogleService-Info.plist` | `com.anksoft.myapplication.MyApplication.dev` |
| iOS | stage | `iosApp/Firebase/stage/GoogleService-Info.plist` | `com.anksoft.myapplication.MyApplication.stage` |
| iOS | prod (Release) | `iosApp/Firebase/prod/GoogleService-Info.plist` | `com.anksoft.myapplication.MyApplication` |

(The iOS bundle id carries `$(TEAM_ID)` after `MyApplication` when a team id is set.)

### Local setup

1. Copy the `*.example` file next to where the real one goes, drop the `.example` suffix and fill
   in the values from the Firebase console (or download the file from the console).
2. Android: `androidApp` always applies the Google Services and Crashlytics Gradle plugins, with
   `missingGoogleServicesStrategy = WARN`. A flavor without its own file only gets a warning and the
   app runs with the no-op reporter.
3. iOS: a build phase copies `iosApp/Firebase/<env>/GoogleService-Info.plist` into the app. A
   missing file prints `warning: Firebase config missing ...`; the app starts without crash
   reporting.
4. Dev builds never send reports, even with a file and a granted consent.

### CI secrets (names fixed, base64)

`FIREBASE_ANDROID_STAGE_JSON_B64`, `FIREBASE_ANDROID_PROD_JSON_B64`,
`FIREBASE_IOS_STAGE_PLIST_B64`, `FIREBASE_IOS_PROD_PLIST_B64`. A release job writes them before
the build, for example:

```
echo "$FIREBASE_ANDROID_STAGE_JSON_B64" | base64 --decode > androidApp/src/stage/google-services.json
echo "$FIREBASE_IOS_STAGE_PLIST_B64" | base64 --decode > iosApp/Firebase/stage/GoogleService-Info.plist
```

There is no release CI job yet (separate story). The current CI runs without the files, which is
the supported no-op path.

### Adding an environment or a Firebase project

1. Register the app in the Firebase console with the application / bundle id from the table.
2. Download the config file to the path in the table and keep it out of git.
3. For a new environment, add the flavor (`androidApp/build.gradle.kts`) and the xcconfig, and
   extend this table. The Gradle plugins pick the new flavor's `google-services.json` up on their own.
4. To use another Firebase project, replace the files; nothing in the code names a project.

## Test crash and end-to-end check

Settings shows "Trigger test crash" (`settings_test_crash`) below the logout button in dev and stage
builds. It throws a `TestCrashException` from the click handler. Prod has no such action: the Android
prod flavor binds no `TestCrashTrigger` and the iOS Release configuration does not define
`TEST_CRASH_ENABLED`.

To check a stage build end to end: sign in, accept optional data collection, tap the button,
reopen the app and look for `TestCrashException` in the Crashlytics dashboard. In dev the same
action crashes the app but nothing is sent.

## Known limits

- Crashes before sign-in are not reported, because consent is not known yet (accepted with the
  consent feature).
- Reports are sent at the next launch. A fatal crash in the session in which consent was just
  granted, or a withdrawal in the same session as a crash, can be lost; this errs on the side of
  privacy.
- Unsent reports are deleted at every start, so non-fatal reports that were queued while collection
  was on are lost if the user signed out in the same session.
- On iOS the Firebase SDK has no on-demand fatal API, so a fatal Kotlin crash can appear as two
  records (the Kotlin error and the process abort).
- Android R8 is off (`docs/environments.md`), so there is no mapping file to upload yet. When R8 is
  turned on, check the mapping upload per flavor; the Crashlytics plugin is applied to every flavor,
  so flavors without a config file must disable it.
- Fatal crashes on Android show a sanitized throwable in Logcat as well (type name and stack, no
  message) when a Firebase config is present. Use a build without the config file to see messages.

## Android SDK versions

Firebase Android libraries get their versions from the Firebase BoM (`firebase-bom` in
`gradle/libs.versions.toml`); `firebase-crashlytics` and `firebase-installations` are declared
without versions. Raise the BoM to move all Firebase libraries together.

## iOS package version exception

The Firebase iOS SDK version is not in `gradle/libs.versions.toml` (Swift Package Manager cannot read
it). It is pinned by `minimumVersion` of the package reference in `iosApp.xcodeproj/project.pbxproj`
(up to next major) and by the committed
`iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved`. Change both together.

## Input for the store privacy forms

Filling the forms is out of scope; the facts for them:

- Data collected: crash logs and diagnostics (stack traces, device model, OS version, app version,
  environment name), breadcrumbs made of our own log lines, a random anonymous install id and the
  Firebase installation id.
- Not collected: user id, e-mail, name, tokens, request or response bodies, throwable messages.
- Only after the account's consent to optional data collection; withdrawing it stops collection,
  deletes unsent reports and resets both identifiers. Not used for tracking or advertising.
