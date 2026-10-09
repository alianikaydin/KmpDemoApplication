package com.anksoft.myapplication.core.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

actual val platformStorageModule: Module = module {
    // Not the Keychain: preferences are not secret. The session lives in the Keychain.
    single<Settings>(AppPreferencesSettings) { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
    // Not the Keychain either: the Keychain survives a reinstall, and the install id must not.
    single<Settings>(CrashReportingSettings) { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
}
