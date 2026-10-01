package com.anksoft.myapplication.core.storage

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSUserDefaults
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrService

/** Fixed rather than the bundle id, so a bundle id change does not drop the session. */
private const val KEYCHAIN_SERVICE = "com.anksoft.myapplication.session"

@OptIn(
    ExperimentalSettingsImplementation::class,
    ExperimentalSettingsApi::class,
    ExperimentalForeignApi::class,
)
actual fun createSecureSettings(): Settings {
    // The retained service string is intentionally never released: this is
    // created once per process as a Koin single.
    val keychain = KeychainSettings(
        kSecAttrService to CFBridgingRetain(KEYCHAIN_SERVICE),
        // ThisDeviceOnly keeps tokens out of backups restored to another device.
        kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
    )
    val userDefaults = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
    // A failed migration must not crash app launch; it is retried next launch.
    runCatching {
        SessionStorageMigrator(
            legacy = userDefaults,
            secure = keychain,
            installMarker = userDefaults,
        ).migrate()
    }
    return keychain
}
