package com.anksoft.myapplication.core.storage

import android.content.Context
import com.anksoft.myapplication.core.logging.AppLogger
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.core.scope.Scope

/**
 * Dedicated file for the encrypted session. `backup_rules.xml` and `data_extraction_rules.xml`
 * exclude it by this name, so change all three together.
 */
internal const val SESSION_PREFS_NAME = "session_secure"

actual fun Scope.createSecureSettings(): Settings {
    val context = get<Context>()
    return createEncryptedSessionStore(
        // The file the no-arg Settings() used before: where older builds kept the plain session.
        legacy = SharedPreferencesSettings(
            context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        ),
        secureStore = SharedPreferencesSettings(
            context.getSharedPreferences(SESSION_PREFS_NAME, Context.MODE_PRIVATE)
        ),
        cipher = AesGcmSessionCipher(AndroidKeystoreKeyProvider()),
        logger = get<AppLogger>(),
    )
}

/**
 * Builds the encrypted session store and deletes the session keys an older build left in the plain
 * [legacy] file. Nothing is read from [legacy] or copied: users sign in again after the update, so
 * a plain-text token never stays on the device.
 */
internal fun createEncryptedSessionStore(
    legacy: Settings,
    secureStore: Settings,
    cipher: SessionCipher,
    logger: AppLogger,
): Settings {
    // Must never block app start; a failed removal is retried on the next launch.
    SessionManager.SESSION_KEYS.forEach { key ->
        runCatching { if (legacy.hasKey(key)) legacy.remove(key) }
    }
    return EncryptedSessionSettings(secureStore, cipher, logger)
}
