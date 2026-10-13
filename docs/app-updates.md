# App updates (force update)

On Android and iOS the app can ask the user to update. There are two levels, both decided by the backend:

- **Required:** the installed version is below the minimum the backend still supports. A full-screen
  "Update required" page replaces the app until the user updates. Back and a restart do not get past it.
- **Optional:** a newer version exists. A dialog offers "Update" or "Later" over the app.

## How it works

- The check runs **once per cold start**, in the background. The app opens at once; the page or the dialog
  appears when the answer arrives. It is not repeated when the app comes back to the foreground.
- It calls `GET app/version-policy?platform=android|ios` (see the backend README, "Version policy (force
  update)"). The endpoint is public: the app sends no token and a `401` from it never touches the session.
- **Fail-open:** any problem (no network, timeout, server error, an unreadable answer, an installed version
  that is not numeric) means no update screen. A broken backend must not lock users out.
- The check needs no session and no consent: it is a functional check, not optional data collection.
- Every build talks to its own backend (dev, stage and prod each have their own policy values). The mock
  backend of the demo mode answers "no update" unless a launch argument says otherwise (below).

## Version format

Policy values from the backend are exactly `x.y.z`. The app's own `VERSION_NAME` (`version.properties`)
may have one to three numeric components (`1.0`, `1.2.3`); missing components count as 0. A value like
`1.0-beta` is rejected by `scripts/ci/check-version-format.sh`, which `static-checks.sh` runs. Versions
compare component by component, so `1.10.0` is newer than `1.9.0`.

## Rules for the people who change the policy

- Never raise `LATEST` before the new version is live in the store.
- Never raise `MIN_SUPPORTED` above the version that is live in the store; users would be sent to an update
  they cannot install.
- For an emergency forced update, follow the steps in the backend README ("Version policy (force update)").
  The change reaches a user at the next cold start of the app.

## Store links

- **Android:** the Play Store page of the app's own package name (the id differs per flavor: `.dev`,
  `.stage`, none for prod). The Play Store app is opened directly; if it is not installed the user sees
  "Couldn't open the store."
- **iOS:** `https://apps.apple.com/app/id<KMP_APP_STORE_ID>`. The numeric ID comes from
  `iosApp/Configuration/Prod.xcconfig` (App Store Connect > App Information > Apple ID) and is empty in
  dev and stage, so those builds show "Couldn't open the store." A prod App Store archive
  (`ACTION=install` and `KMP_ENVIRONMENT=PROD`) fails in Xcode when the ID is missing or not numeric
  (`verifyAppStoreReleaseConfig`).

## "Later"

"Later", Back and a tap outside the dialog close the optional dialog and remember the **latest version that
was offered**. The dialog stays away until the backend offers a newer one. Tapping "Update" and reaching the
store does not remember anything. The required page cannot be dismissed.

## Demo mode

In the demo mode (in-app mock backend) the launch argument `kmpDemoVersionPolicy` picks the answer of the mock:
`none` (default), `optional` or `required`. Android passes it as an intent extra
(`adb shell am start -n com.anksoft.myapplication.dev/com.anksoft.myapplication.MainActivity --es kmpDemoVersionPolicy required`),
iOS as a launch argument (`-kmpDemoVersionPolicy required`). Builds that use a real backend ignore it. The
Maestro flows 13 and 14 use it (`docs/ui-tests.md`).

## Web

Not applicable. The web app makes no request and shows no screen: a page load always brings the current
version, and there is no store to send the user to.
