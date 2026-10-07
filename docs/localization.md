# Localization (Turkish and English)

User-facing text exists in two languages. English is the default and the fallback; a device
language the app does not support (for example German) shows English.

## The rule

Text is written **only** in `shared/src/commonMain/composeResources/values/strings.xml`, and the
Turkish text goes into `values-tr/strings.xml` **in the same change**. Never write
`Text("...")` or `contentDescription = "..."` with a literal. Keys are `snake_case` English.

- Screens call `stringResource(Res.string.key)`.
- Screen models describe text with `UiText.Resource(Res.string.key)` and the screen resolves it
  with `asString()`. Do not call `getString(...)` in non-composable code: it does not see the
  in-app language.
- Escape `&` as `&amp;` and `'` as `\'` in the XML.
- Turkish uses the formal "siz" form ("Giriş yapın", "Parolanızı mı unuttunuz?").
- The app name is the same in both languages, so Android has no `values-tr` `app_name`
  (it comes from the flavor's `resValue`) and iOS has no `InfoPlist.strings`.

CI runs `scripts/ci/check-string-keys.sh` in the "Static checks" step. It fails when a
`values-xx/strings.xml` misses a key, has an extra key, has an empty value or uses different
format arguments (`%1$s`, `%2$d`) than `values/strings.xml`. Run it locally with
`bash scripts/ci/check-string-keys.sh`. It also covers `androidApp/src/main/res` if that ever
gets a `values/strings.xml`.

### The one exception: language names

A language is always listed in itself ("Türkçe", "English"), whatever the current language is,
so people can find their own language. These names live in `AppLanguage.nativeName` and are not
string resources. "System default" is a normal string (`language_system_default`).

### Mind the default locale

The Android build sets the process default `Locale` to the chosen language, and Turkish has a
dotted and a dotless i: Java's `"I".toLowerCase()` becomes `"ı"` under Turkish. So:

- Do not use `toLowerCase()`, `toUpperCase()` or `String.format(...)` without an explicit
  `Locale`; they follow the default.
- Kotlin's `lowercase()` and `uppercase()` are locale-independent. Use them for identifiers
  (for example e-mail comparison). For text shown to the user, pass the locale explicitly.

## How the language is chosen

`AppPreferences.language` (`core/preferences`) is the only source of truth. It holds
`AppLanguage.SYSTEM`, `TURKISH` or `ENGLISH`, is stored in its own non-secure store (Android
SharedPreferences file `app_preferences`, iOS `NSUserDefaults`, web `localStorage`, key
`app_prefs.language`) and is not touched by `SessionManager.clear()`. Never store session or
identity data there.

`App()` passes the language to `ProvideAppLocale`, which calls the platform actual of
`appLocaleProvides`:

| Platform | What it does |
|----------|--------------|
| Android | Replaces the locale in `LocalConfiguration` and sets `Locale.setDefault`. "System default" re-reads the system configuration every time, so it follows the device language. |
| iOS | Writes `AppleLanguages` (removes it for "System default"). |
| Web | Calls `window.__setAppLocale`, defined by the shim in `webApp/.../index.html`, which overrides `navigator.languages`. |

Behaviour to know:

- Platforms that do not recompose on a language change (`appLocaleNeedsContentKey = true`) re-create
  the **content of the screen** with `key(language)`. The Navigator is never keyed, so the back
  stack and the screen models survive; only `remember`ed UI state of the open screen resets.
- iOS: the in-app choice wins. When the app is on "System default", the `AppleLanguages` key is
  removed at launch, so a language picked in the iOS Settings app for this app is not kept.
- Android 13+: a language picked in the system's per-app language settings reaches the app as the
  system configuration, so it applies while the app is on "System default". An explicit in-app
  choice wins. The app does not call `LocaleManager`.
- Web: the browser language is read when the page loads. Changing the browser language is only
  noticed after a reload. The selected language is kept in `localStorage`.
- Material and system strings (for example accessibility labels from the framework) may stay in
  the device language on Android if they do not read `LocalConfiguration`.

## Adding a language

1. Add an entry to `AppLanguage` (`tag`, `nativeName`) and a test line in `AppLanguageTest`.
2. Add `shared/src/commonMain/composeResources/values-xx/strings.xml` with every key.
3. Add `<locale android:name="xx" />` to `androidApp/src/main/res/xml/locales_config.xml`.
4. Add the code to `CFBundleLocalizations` in `iosApp/iosApp/Info.plist` and to `knownRegions`
   in `iosApp/iosApp.xcodeproj/project.pbxproj`.
5. Add a tag in `SettingsTestTags` and a branch in `SettingsScreen` (`AppLanguage.testTag()`).
6. Right-to-left languages need extra work (layout direction) and are not covered here.

## UI tests

Maestro flows match elements by `id:` (testTag), never by text, so they pass in any language.
`07_language_switch` is the exception: it checks the translation on purpose.
