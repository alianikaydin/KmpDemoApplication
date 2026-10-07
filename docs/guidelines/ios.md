# iOS / Swift / KMP interop checklist

Condensed from the Swift API Design Guidelines, SwiftUI conventions and
Apple's Human Interface Guidelines. The UI is Compose Multiplatform, so most
iOS work is the `iosApp/` host and the Kotlin API that Swift sees.

## Swift (`iosApp/`)
- [ ] 4-space indent; `UpperCamelCase` types/protocols, `lowerCamelCase` everything else.
- [ ] Names read as English phrases at the call site; omit needless words; label arguments for clarity.
- [ ] Prefer `struct` and `let`; no force unwrap (`!`) or `try!` outside previews.
- [ ] Mark types `final`/`private` where possible; one main type per file.
- [ ] SwiftUI views small and composable; state with `@State`/`@StateObject`/`@Observable`, not globals.
- [ ] UI work on the main actor (`@MainActor`).
- [ ] Keep `iOSApp.swift` (Koin init via `doInitKoin`) and `ContentView.swift` (hosts `MainViewController`) minimal.

## Kotlin API exposed to Swift (`shared/src/iosMain`, public `commonMain`)
- [ ] Keep the surface small: mark helpers `internal`.
- [ ] Functions that throw and are called from Swift get `@Throws(...)`; otherwise exceptions crash the app.
- [ ] Do not expose `suspend` functions, `Flow` or generic sealed types directly; wrap them in a callback/plain-type helper.
- [ ] Avoid name clashes and default arguments Swift cannot see; use `@ObjCName` when the Swift name would be awkward.
- [ ] Top-level functions surface as `<File>Kt.<name>`: keep entry points in clearly named files (`MainViewController.kt`, `AppModule.kt`).

## Platform code (`expect`/`actual`)
- [ ] Every `expect` has an `actual` for android, ios, js and wasmJs.
- [ ] Secrets (tokens) belong in the Keychain, never plain `NSUserDefaults`. Known gap: `SecureSettings.iosMain.kt` currently returns plain `Settings()` (NSUserDefaults); flag it rather than adding new secrets on top of it.

## Language
- [ ] The app language is chosen in the Compose Settings screen and written to the `AppleLanguages` user default on every launch. Do not add a second language setting in Swift. See `docs/localization.md`.

## UX (HIG)
- [ ] Respect safe areas, Dynamic Type and dark mode.
- [ ] Use platform-expected back/swipe behaviour; Voyager navigation must not break the iOS swipe-back gesture.
- [ ] Touch targets ≥ 44pt; VoiceOver labels on meaningful controls.
