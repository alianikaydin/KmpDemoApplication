# UI smoke tests (Maestro)

Every pull request to `master` runs a small set of UI flows on an Android
emulator and an iOS simulator. The flows are written with
[Maestro](https://maestro.mobile.dev) (YAML) so they read like manual test
steps. The workflow is `.github/workflows/ui-tests.yml` (`UI Tests`); it is
independent from `ci.yml`.

## Where things live

| Path | Purpose |
|------|---------|
| `.maestro/flows/` | One file per flow, named `NN_what_it_checks.yaml` |
| `.maestro/subflows/` | Reusable steps (`launch_clean`, `launch_with_version_policy`, `login_with`, `submit_signup`, `dismiss_consent_prompt`, `assert_selected`, `assert_not_selected`, `logout_from_settings`) |
| `.maestro/config.yaml` | Flow selection and execution order |
| `scripts/ui-tests/` | Install, run, boot and classify scripts used by CI (and locally) |

Current flows: `01_app_launch` (Login screen is shown), `02_login_success`
(demo user reaches Home), `03_login_wrong_password` (error is shown, user stays
on Login), `04_session_persists_until_logout` (session survives a restart and
ends on logout), `05_reinstall_drops_session` (a session left from a previous
install is dropped), `06_settings_shows_environment` (Settings shows the
environment and version), `07_language_switch` (in-app language change applies
at once and the back stack is kept, AC-6), `08_signup_consent_unchecked` and
`09_signup_consent_checked` (the optional consent box at sign-up and the Settings switch that follows),
`10_consent_prompt_reject` and `11_consent_prompt_accept` (the one-time prompt, the Settings switch and
no second prompt at the next login), `12_theme_switch` (the theme choice applies at once, survives a restart and a logout, and the back stack is kept), `13_force_update` (the required-update screen, the store error and, on Android, Back not getting
past the screen and the same running app showing it again when brought back without a restart, AC-1, AC-2, AC-4) and `14_optional_update` (the update prompt, "Later" and no prompt after a restart,
AC-7, AC-8, AC-10) and `15_settings_profile` (Settings shows the signed-in account's own e-mail and name,
and a second account on the same device never sees the first one's details). The app runs in demo mode (in-app mock backend), so no network is needed.

Seed users of the mock backend:

| User | Password | Consent decision |
|------|----------|------------------|
| `demo@example.com` | `Demo1234` | granted, so the prompt never shows in the other flows |
| `consent@example.com` | `Demo1234` | none, so the prompt shows after login (flows 10 and 11) |

The consent controls expose their state to accessibility services through `toggleable(role = ...)` plus a
localized `stateDescription` ("Checked" / "Not checked" for the sign-up checkbox, "On" / "Off" for the
Settings switch; Turkish: "İşaretli" / "İşaretli değil", "Açık" / "Kapalı"). On Android Maestro reads the
toggleable state as `checked:` (the `stateDescription` is what TalkBack speaks), and flows 08 to 11 assert it
there. In flows 10 and 11 the wait after tapping the switch also requires `enabled: true`, because the switch
is disabled while the decision is being saved.

On iOS Compose does not expose the checked state to XCUITest: with a plain `toggleable` the hierarchy shows
only `id`, `accessibilityText` and `enabled`, and `checked: true` never matches (see the `ios-ui` run of
PR #21, commit `0c2b681`, where marking the row as selected changed nothing). Whether Compose Multiplatform
maps `stateDescription` to `accessibilityValue` is not verified. There is deliberately no test-only marker in
the UI. On iOS Maestro checks navigation and that the one-time prompt does not come back; it does not check
the decision value. The decision logic behind the controls (initial value, what is sent on sign-up, prompt and
switch changes, the disabled state while saving) is covered by `SignUpScreenModelTest`,
`SettingsScreenModelTest` and `ConsentPromptScreenModelTest`, and the rendered state (on/off and the
`stateDescription` text for both states, and toggling) by the Compose UI tests `SignUpConsentUiTest` and
`SettingsConsentUiTest`. The ScreenModel tests run on Android host tests and on iOS (`ios-test`); the Compose
UI tests live in `iosTest` and run only on iOS, since Android already checks the rendered state with Maestro
(`checked:`). The sign-up flows
submit with the keyboard's Done key on iOS (`submit_signup`), because the keyboard covers the submit button.

`dismiss_consent_prompt` is a safety step after each login: if the prompt is on screen it rejects it,
otherwise it does nothing. The mock keeps decisions in memory only, so they are lost when the app
process ends; flows 10 and 11 therefore do not restart the app between steps. Every flow starts with
`launch_clean`, which makes them independent of each other.

## Update flows (13 and 14)

The two flows start through `launch_with_version_policy`, which launches the app with the argument
`kmpDemoVersionPolicy` (`required` or `optional`; Maestro passes it as an intent extra on Android and as a
launch argument on iOS) and waits for the screen it expects. The mock backend reads the argument only in
demo mode (`docs/app-updates.md`); the other flows start without it and see "no update".

While the update prompt (a dialog, so its own window) is open, Maestro on Android sees only the dialog's
elements, not the screen behind it. Flow 14 therefore checks that the app runs behind the prompt by asserting
the Login screen right after "Later" has closed it.

The store button is deterministic in CI because there is nothing to open: the emulator image
(`target: default`) has no Play Store, so the intent fails, and the iOS dev build has an empty
`KMP_APP_STORE_ID`. Both flows therefore assert the "Couldn't open the store." message. On a local emulator
with the Play Store, the store button of these flows opens the Play Store instead and the error step fails;
use an image without it, or skip the store step. The success path (the real store page) is checked by hand
with an Internal Testing or TestFlight build.

## Offline profile check (manual)

Settings keeps showing the stored profile without a network. There is no automated flow for it: in demo mode
requests go to the in-app mock, so airplane mode changes nothing there, and the one case that needs no
network and no stored profile (a first start offline) cannot be produced without a test hook in the app.
Unit tests cover it (`ProfileRepositoryImplTest`, `SettingsProfileUiTest`) and `SqlProfileLocalDataSourceTest`
proves the stored profile survives a restart on a real database. QA or the developer checks the rest once on
a device and notes the result in the QA report:

- **Android** (emulator, dev build, local backend with `-PkmpBackendUrl=http://10.0.2.2:8081/api/v1/`):
  1. Sign in and open Settings: the e-mail is shown.
  2. Close the app. `adb shell cmd connectivity airplane-mode enable`. Open the app and Settings: the e-mail
     and the note "Couldn't get the latest details; showing the last saved ones." are shown, no error dialog.
  3. "Try again": the note stays.
  4. `adb shell cmd connectivity airplane-mode disable`, "Try again": the note disappears.
- **iOS** (simulator): the same steps; cut the network on the Mac (for example turn Wi-Fi off).
- **Screen readers:** TalkBack and VoiceOver read the e-mail, the name, the note, the loading state and
  "Try again"; check light and dark theme.
- **Separate apps:** with dev and stage installed on one device, signing in to one shows no profile in the
  other.

## End-to-end check against the real backend (Android)

The `android-e2e` job in the same workflow checks register -> logout -> login ->
Home on the emulator against the real backend. It checks out
`alianikaydin/KmpDemoBackend` into `backend/`, starts it with
`scripts/server-up.sh`, builds the dev-flavor APK (`assembleDevDebug`) with
`-PkmpBackendUrl=http://10.0.2.2:8081/api/v1/` and runs
`.maestro-e2e/` through `scripts/ui-tests/run-android-e2e.sh` (a unique user
per run). It also checks that Settings shows the new account's e-mail (the real `auth/me` and the profile
stored at sign-up). It is separate from `.maestro/`, so the demo-mode suite is unchanged.
Results and the backend container log are in the `android-e2e-results` artifact.

## iOS session storage check

`.github/workflows/ios-session-storage.yml` runs only when session storage or
the iOS app changes (or by hand). It builds the iOS app twice: as it is, and
with the old NSUserDefaults `createSecureSettings()` restored. Then
`scripts/ui-tests/check-ios-session-storage.sh` runs the flows in
`.maestro/storage/` and reads the app's NSUserDefaults plist on the simulator:

- Update from the old build: log in on the old build, install the new build
  over it, and Home must open with no session keys left in NSUserDefaults.
- Fresh login on the new build: no session keys in NSUserDefaults, and Home
  opens again after a restart (the session is in the Keychain).

## Adding a flow

1. Add `.maestro/flows/NN_name.yaml`, starting with
   `runFlow: ../subflows/launch_clean.yaml` so every flow begins from a clean state.
2. Target elements with `id:` (a Compose `testTag`). Add new tag values to the
   `*TestTags` objects (`LoginTestTags`, `HomeTestTags`, `SettingsTestTags`) and use them in the
   screen with `Modifier.testTag(...)`. Do not hard-code tag strings in Kotlin.
3. Do not match or assert user-facing text; it changes with the app language. Use
   `id:` for taps and assertions. The exceptions are a flow that tests the
   language itself (`07_language_switch`), which asserts the expected translation, and the
   account's e-mail and name, which do not change with the language and are asserted with
   `id` and `text` together (`15_settings_profile`, the e2e flow).
4. Take a `takeScreenshot` at the end of the flow.
5. To check which theme is drawn, assert the marker `app_theme_light` or `app_theme_dark`
   (see `docs/theming.md`), never colors. To check a radio option in Settings, use
   `runFlow: ../subflows/assert_selected.yaml` with `env: ID: <option id>`; it asserts
   `checked` on Android and `selected` on iOS; `assert_not_selected.yaml` is the opposite check.

## Running locally

Install Maestro (`curl -fsSL https://get.maestro.mobile.dev | bash`), then:

- Android: start an emulator, run `./gradlew :androidApp:assembleDevDebug`, then
  `bash scripts/ui-tests/run-android.sh` (override `APK` and `OUT_DIR` if needed).
- iOS: build the app with Xcode for a simulator, boot it, then
  `SIM_UDID=<udid> APP_PATH=<path to MyApplication.app> bash scripts/ui-tests/run-ios.sh`.
- Quick loop without the scripts:
  `maestro test .maestro/ -e APP_ID=com.anksoft.myapplication.dev` (Android) or
  `-e APP_ID=com.anksoft.myapplication.MyApplication.dev` (iOS).

## Appearance (light and dark)

The device appearance is set by `SYSTEM_APPEARANCE` (`light` default, or `dark`). The run scripts
apply it (`adb shell cmd uimode night yes|no` on Android, `xcrun simctl ui <udid> appearance` on
iOS) and pass it to Maestro as `-e SYSTEM_APPEARANCE=...`; flows use it to know which theme marker
to expect while the app follows the system. Pull requests run the suite once in light mode. To run
the whole suite in dark mode, start the `UI Tests` workflow by hand (Actions, Run workflow) with
`appearance: dark`, or locally: `SYSTEM_APPEARANCE=dark bash scripts/ui-tests/run-android.sh`.
The artifact names end with the appearance (`android-ui-results-dark`).

## Results and artifacts

Open the run in GitHub: Actions, `UI Tests`, the run, then **Artifacts** at the
bottom. Download `android-ui-results-<appearance>` and `ios-ui-results-<appearance>`. They are kept for
14 days. Layout:

- `report.xml`: JUnit result per flow
- `screenshots/` and `maestro/`: screenshots and Maestro debug output
- `video/*.mp4`: screen recording (best effort)
- `logcat.txt` (Android) or `xcodebuild.log` (iOS)
- `stage`: last stage the job reached (`build`, `boot`, `install`, `test`)

## Failure types

A failed job shows one of two annotations (and a line in the run summary):

- **UI test failure**: a Maestro flow failed in the `test` stage. Check
  `report.xml` and the screenshots.
- **Infrastructure failure**: the build, emulator/simulator boot or app
  install failed before the flows could run. This is not a UI assertion.
