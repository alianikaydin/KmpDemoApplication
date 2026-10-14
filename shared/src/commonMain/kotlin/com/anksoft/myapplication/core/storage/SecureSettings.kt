package com.anksoft.myapplication.core.storage

import com.russhwolf.settings.Settings
import org.koin.core.scope.Scope

/**
 * Storage boundary for session tokens, bound by Koin so the Android actual can read the application
 * Context and the logger from the scope. Per platform:
 *   - Android : AES-256-GCM with a non-exportable Android Keystore key, in its own preferences file
 *               (`session_secure`) that backups exclude. A value that cannot be decrypted clears the
 *               session; one that cannot be encrypted is not written. There is no plain fallback.
 *   - iOS     : Keychain (this device only, after first unlock); legacy NSUserDefaults entries are
 *               migrated on first launch.
 *   - js/wasm : localStorage, NOT protected.
 *
 * Web limits (docs/secrets.md):
 *   (a) localStorage is readable by any script on the page, so XSS steals the tokens.
 *   (b) Encrypting in the browser gives no real protection, because the key is available to the
 *       same JavaScript.
 *   (c) The correct solution is an httpOnly + Secure + SameSite cookie for the refresh token, which
 *       needs a backend change.
 *   (d) Until that is done, web must not ship to stage or prod with real users.
 */
expect fun Scope.createSecureSettings(): Settings
