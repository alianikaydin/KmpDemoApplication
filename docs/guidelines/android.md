# Android / Kotlin / Compose checklist

Condensed from the Kotlin coding conventions, the Android Kotlin style guide
and the Compose API guidelines. Only the rules that matter for this repo.

## Kotlin
- [ ] 4-space indent, max ~120 chars per line, trailing commas in multi-line lists.
- [ ] `PascalCase` types, `camelCase` functions/properties, `UPPER_SNAKE_CASE` constants and enum entries.
- [ ] One top-level class per file, file named after it; extension-only files named by purpose (`DataErrorToUiText.kt`).
- [ ] Prefer `val`, immutable collections and `data class` for state.
- [ ] Expression bodies for one-liners; `when` over `if` chains on sealed types (no `else` branch on sealed `when`).
- [ ] No `!!`; use `?.`, `?:`, `requireNotNull` with a message.
- [ ] Visibility as narrow as possible (`private`/`internal`); no unused imports.
- [ ] KDoc on public APIs only when the name does not say it all; comments explain *why*.

## Coroutines and Flow
- [ ] Launch in `screenModelScope`; no `GlobalScope`, no `runBlocking` outside tests.
- [ ] Never swallow `CancellationException` (use `ensureActive()` like `safeCall`).
- [ ] Expose `StateFlow` read-only; mutate via `update { }`.
- [ ] Inject dispatchers when a class does blocking work; tests use `UnconfinedTestDispatcher`/`StandardTestDispatcher`.

## Compose
- [ ] Screen = Voyager `Screen` that collects state and forwards events; the UI body is a stateless `XxxContent(state, onEvent, modifier)`.
- [ ] Every public composable that emits UI takes `modifier: Modifier = Modifier` as the first optional parameter and applies it to its root.
- [ ] State hoisting: composables receive values and lambdas, never a ScreenModel.
- [ ] `remember`/`rememberSaveable` for UI-only state; `derivedStateOf` for values derived from changing state.
- [ ] Lambdas named `onXxx` in present tense (`onLoginClick`), not `onLoginClicked`.
- [ ] Material 3 components and `MaterialTheme` colors/typography/shapes; no hard-coded colors or text sizes.
- [ ] All user-facing text from `composeResources/values/strings.xml` (`stringResource`, `UiText`); keys in `snake_case` English. Add the Turkish text to `values-tr/strings.xml` in the same change (CI fails on missing keys); see `docs/localization.md`.
- [ ] Accessibility: `contentDescription` on meaningful icons/images (null for decorative), touch targets ≥ 48dp.
- [ ] `@Preview` for new screens' `Content` composable with sample state.
- [ ] No side effects in composition; use `LaunchedEffect` keyed on what triggers it.

## Architecture
- [ ] Unidirectional data flow: Event → ScreenModel → State.
- [ ] `domain` is pure Kotlin; errors via `Result`/`DataError`, network via `safeCall`, DI in `core/di`.
- [ ] No blocking I/O on the main thread.

## Tests
- [ ] Name tests by behaviour (`wrongPasswordClearsPasswordAndKeepsEmail`), reference `AC-n` in a comment.
- [ ] Use fakes over mocks; assertk for assertions, turbine for Flows.
