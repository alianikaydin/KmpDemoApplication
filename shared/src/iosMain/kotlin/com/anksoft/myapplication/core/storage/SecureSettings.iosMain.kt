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

/**
 * Fixed rather than the bundle id, so renaming the app does not drop the session. Dev, stage and
 * prod installs share this name but have different bundle ids, and the default keychain access
 * group is derived from the bundle id, so their sessions stay separate.
 */
private const val KEYCHAIN_SERVICE = "com.anksoft.myapplication.session"

@OptIn(
    ExperimentalSettingsImplementation::class,
    ExperimentalSettingsApi::class,
    ExperimentalForeignApi::class,
)
internal fun createSessionKeychain(): Settings = KeychainSettings(
    // The retained service string is intentionally never released: the app
    // creates this once per process as a Koin single.
    kSecAttrService to CFBridgingRetain(KEYCHAIN_SERVICE),
    // ThisDeviceOnly keeps tokens out of backups restored to another device.
    kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
)

actual fun createSecureSettings(): Settings {
    val keychain = createSessionKeychain()
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
