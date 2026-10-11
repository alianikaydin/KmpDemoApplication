# Product analytics

Anonymous usage events, sent only for an account that granted optional data collection
(see [privacy-consent.md](privacy-consent.md)). The vendor is hidden behind the `Analytics` port
(`core/analytics`); common code never touches a vendor SDK. Platform adapters (Firebase on Android
and iOS) are added separately; until then, and on every platform without a vendor, analytics is a
no-op (`NoOpAnalytics`).

## Rule for feature code

Send events through `AnalyticsTracker.track(AnalyticsEvent)` only. Never call `Analytics` or a
vendor SDK from a feature. The consent and environment gate is inside the tracker, so a call site
needs no check of its own.

- Screen models report a screen from `init`. A model lives as long as its screen is on the back
  stack, so an event is sent once per opening, not on back navigation or a language change.
- Composables never send events.

## Catalog

| Event | Parameters | Sent when |
|-------|------------|-----------|
| `screen_view` | `screen_name`, `screen_class` (both `home` or `settings`), `language` (`tr`, `en`) | The Home or Settings screen model is created: once per screen opening. |

`language` is added by the tracker to every event. It is the language the UI is shown in: the
in-app choice, or on "System default" the device language when the app supports it, otherwise
`en` (the default strings). It is read when the event is sent, so a language switch applies at
once. The values come from `AppLanguage`; there is no second list to keep in step.

`screen_class` repeats the screen name on purpose. Left out, the vendor fills in a platform class
name that differs between Android and iOS.

## Naming and limits

- `snake_case`, starting with a letter, letters/digits/`_` only.
- Firebase limits: event and parameter names up to 40 characters, string values up to 100, up to
  25 parameters per event. Reserved prefixes: `firebase_`, `google_`, `ga_`. Reserved event names
  are listed in the Firebase documentation; `screen_view` is a standard event name.
- `AnalyticsCatalogTest` checks names, prefixes, value sets and lengths for every catalog event.

## No personal data

Parameter values come only from enums and constants. No user id, e-mail, name or token is ever a
parameter, and `setUserId` does not exist on the port. The debug log line carries the same
catalog names and values and nothing else.

## Adding an event

1. Add a subtype to `AnalyticsEvent` with enum-typed fields (no free text).
2. Add its branches to `vendorName` and `vendorParams()` in `AnalyticsEvent.kt`. Both are
   `when` expressions without `else`, so the compiler points at what is missing.
3. Add the event to the list in `AnalyticsCatalogTest` and a row to the catalog table above.
4. Test it: a tracker test for the parameters and a test at the use site.
5. If a new parameter must appear in reports, list it for a custom dimension in the vendor
   console (needs the project owner).

## Consent and environment

One rule, in `AnalyticsTracker`: events are forwarded only when the consent is `GRANTED` and the
environment is not dev.

- **Nothing is queued.** An event without consent is dropped. It is not sent later when consent
  arrives. The consent is read when `track` is called.
- **Dev never sends.** It writes a debug line instead: `Analytics` tag, for example
  `event=screen_view screen_name=home screen_class=home language=en forwarded=false`.
- **Stage and prod** are separated by the vendor app registration; events carry no environment
  parameter.
- **Collection flag.** `AnalyticsTracker.start()` runs once from `initKoin`. It applies the
  current consent before it returns, so collection is off before the first screen exists, and then
  follows every change.
- **Reset.** In every state where collection is off (`DENIED`, `UNKNOWN`, dev) the tracker turns
  the flag off and then calls `resetAnalyticsData()`. The rule keeps no memory, so a restart or a
  missed intermediate value still ends clean, and resetting while already off is harmless.
  Sign-out gives `UNKNOWN`, so the next account on the device starts without the previous one's
  analytics identity.
- **Difference from crash reporting.** Crash reporting keeps its identity on `UNKNOWN` and clears
  it only on `DENIED`. Analytics resets on everything but `GRANTED`, because a sign-out must clear
  the analytics identity.
- A vendor that throws never crashes the app: the failure is logged as a warning and the next step
  still runs (a failing disable does not skip the reset).

## Consequences

- No events before sign-in (login and sign-up screens, failed attempts) and none before the
  decision is known. Login and sign-up conversion cannot be measured.
- Right after a sign-in whose decision is not cached, the first Home view is dropped, because the
  decision is still loading when the Home model is created. Check with a cold start or Settings.
- A consent withdrawn on another device applies here once the app has refreshed the decision
  (cached decision until then).

## Web

Not supported. No vendor is bound, analytics is a no-op and no network request is made.

## Vendor automatic events

Whatever automatic events the vendor SDK collects stay on while collection is on; the app does not
filter them. Automatic screen reporting must be switched off in the platform configuration of each
adapter, because screens are reported from the catalog.
