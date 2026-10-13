# Theming

The app has three theme choices, picked in Settings: **System** (default), **Light** and
**Dark**. The choice is stored in `AppPreferences` (key `app_prefs.theme`) and survives restarts
and logout. `App()` turns it into one boolean, `themeMode.isDark(isSystemInDarkTheme())`, and
draws everything under `AppTheme(darkTheme)`.

## Rules

- **No hard-coded colors.** Take colors only from `MaterialTheme.colorScheme` roles (`primary`,
  `onSurface`, `error`, ...). `Color.Transparent` and `Color.Unspecified` are allowed.
  `scripts/ci/check-no-hardcoded-colors.sh` (part of `static-checks.sh`) fails the build on
  `Color(0x...)` or `Color.White`-style colors outside `core/presentation/theme/`.
- **One place defines the brand.** To change the app's colors, edit only
  `shared/src/commonMain/kotlin/com/anksoft/myapplication/core/presentation/theme/AppColorSchemes.kt`.
  Pass named arguments to the two calls, for example
  `lightColorScheme(primary = Color(0xFF...), onPrimary = ...)`, or paste the output of the
  Material Theme Builder there.
- **The contrast test must stay green.** `AppColorSchemesTest` checks text pairs (4.5:1) and the
  input outline (3:1) in both schemes, so a new brand color that is hard to read fails the test.
- Do not wrap screens in their own `MaterialTheme { }`; the root `AppTheme` is the only one.
  Changing the theme must only recompose: never use `key()` on the theme, so the back stack and
  typed input survive (see `App.kt`).

## Platform surfaces

`PlatformThemeEffect` (expect/actual) styles what Compose does not draw:

| Platform | What it does |
|----------|--------------|
| Android | Status/navigation bar icon contrast and the window background. `configChanges="uiMode"` keeps the Activity alive when the system dark mode changes. |
| iOS | `overrideUserInterfaceStyle` on the app windows (status bar, keyboard, system alerts). `System` removes the override. Nothing is written to `NSUserDefaults`. |
| Web | The page `color-scheme`. `index.html` has a small script that applies a stored Light/Dark choice before the app loads; it must stay in sync with `THEME_KEY` and `ThemeMode.storedValue` (a unit test pins them). The loading page and `styles.css` use CSS system colors (`Canvas`, `CanvasText`, `GrayText`), so no palette is repeated. |

Known limitation: the Android window theme (`themes.xml`, `values-night/themes.xml`) and the iOS
launch screen cannot read the app's choice. When Light or Dark is forced, the system splash can
show in the device's colors for a moment. With **System** selected there is no mismatch. The
first frame the app itself draws is always in the chosen theme.

## UI automation (Maestro)

- `AppTheme` tags its root surface `app_theme_light` or `app_theme_dark`. Flows assert that tag
  instead of colors. The tag has no text or content description, so screen readers skip it.
- `SYSTEM_APPEARANCE` (`light` or `dark`) is the device appearance. The run scripts set it on the
  emulator or simulator and pass it to Maestro as an env var. See `docs/ui-tests.md`.
- Settings options have the ids `settings_theme_system`, `settings_theme_light` and
  `settings_theme_dark`.
