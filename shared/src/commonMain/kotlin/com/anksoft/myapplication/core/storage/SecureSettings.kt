package com.anksoft.myapplication.core.storage

import com.russhwolf.settings.Settings

/**
 * Storage boundary for session tokens.
 *
 * This exists so the security NFR has a single seam to implement against
 * instead of the call sites needing to change later. Current per-platform status:
 *   - Android : plain SharedPreferences   -- TODO hardware-backed Keystore
 *   - iOS     : plain NSUserDefaults      -- TODO Keychain
 *   - js/wasm : localStorage              -- refresh tokens belong in an
 *               httpOnly cookie on web; do not ship web with tokens here.
 *
 * Tokens are therefore NOT yet encrypted at rest on any platform. Treated as a
 * known P0 follow-up rather than silently marked done.
 */
expect fun createSecureSettings(): Settings
