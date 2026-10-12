# Environments and versions

The app has three environments: **dev**, **stage** and **prod**. Dev is the default
for local work. The environment is fixed when the app is built; there is no runtime
switch and no debug-only control in Settings. Settings shows the environment name and
the version (`Version 1.0 (1)`) in every environment, prod included. The one exception to "no runtime
switch" is demo mode only: the `kmpDemoVersionPolicy` launch argument picks the mock backend's version
policy (see `docs/app-updates.md`); builds with a real backend ignore it.

## Commands

| Environment | Android | iOS (scheme / configuration) | Web |
|-------------|---------|------------------------------|-----|
| dev | `:androidApp:assembleDevDebug`, `:androidApp:installDevDebug` | `iosApp` / Debug | `-PkmpEnv=dev` (default) |
| stage | `:androidApp:assembleStageDebug`, `...StageRelease` | `iosApp-Stage` / Stage | `-PkmpEnv=stage` |
| prod | `:androidApp:assembleProdDebug`, `...ProdRelease` | `iosApp-Prod` / Release | `-PkmpEnv=prod` |

`:androidApp:assembleDebug` builds all three flavors. Android application ids:
`com.anksoft.myapplication.dev`, `.stage` and no suffix for prod (the existing id).
iOS bundle ids end the same way (`.dev`, `.stage`, none) after
`com.anksoft.myapplication.MyApplication$(TEAM_ID)`. Display names are
"MyApplication Dev", "MyApplication Stage" and "MyApplication", so all three can be
installed side by side.

## Backend and demo mode

The rule lives in `AppConfig.create` (shared) and is unit tested per environment.

- **stage / prod:** fixed URL (`AppConfig.STAGE_BASE_URL`, `AppConfig.PROD_BASE_URL`),
  mock backend never on. A backend URL or demo flag passed to the build is ignored on
  mobile and rejected on web.
- **dev, mobile:** the backend URL if given (`-PkmpBackendUrl=...` on Android,
  `Local.xcconfig` on iOS), otherwise demo mode (in-app mock server).
- **dev, web:** differs on purpose. The build fails unless you pass `-PkmpDemo=true`
  or `-PkmpBackendUrl=<url>`. Stage/prod web builds fail if either is passed. The
  check runs in the `generateWebBuildEnv` task, so unit tests and other Gradle tasks
  are not affected. An invalid `-PkmpEnv` value also fails the build.

The stage and prod URLs are placeholders (`api-stage.example.com`, `api.example.com`).
Replace the two constants in `AppConfig.kt` when the real backends exist.

## Version

`version.properties` in the repo root is the only place for the version:

```
VERSION_NAME=1.0
VERSION_CODE=1
```

`VERSION_NAME` has one to three numeric components (`1.0`, `1.2.3`), because the update check compares it with
the backend's policy. `scripts/ci/check-version-format.sh` (part of `static-checks.sh`) fails the build for
anything else, such as `1.0-beta`.

Android, iOS (`MARKETING_VERSION`, `CURRENT_PROJECT_VERSION` via an xcconfig include)
and web read it. Do not add comment lines: `#` is a directive in xcconfig and breaks
the iOS build. `-PkmpVersionCode=<n>` overrides the code for Android and web. The
code is not raised automatically in CI.

## App store links

The update screen sends the user to the store. Android uses the app's own package name. iOS needs the numeric
App Store ID in `KMP_APP_STORE_ID`: empty in dev and stage (the update screen then shows "Couldn't open the
store."), set in `iosApp/Configuration/Prod.xcconfig`. A prod App Store archive (`ACTION=install`,
`KMP_ENVIRONMENT=PROD`) fails when the ID is missing or not numeric (`verifyAppStoreReleaseConfig`). Details:
`docs/app-updates.md`.

## Notes

- Web builds of different environments served from the same origin share
  `localStorage`, hence the login session. Host each environment on its own origin.
- iOS Keychain service name is the same in all environments. The sessions stay
  separate because the default keychain access group follows the bundle id.
- `Local.xcconfig` only affects the dev (Debug) configuration.
- R8/minify is not enabled.
