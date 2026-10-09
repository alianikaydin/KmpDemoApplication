package com.anksoft.myapplication.core.di

import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformStorageModule: Module = module {
    // localStorage has no namespaces; SettingsAppPreferences separates its key with a prefix.
    single<Settings>(AppPreferencesSettings) { Settings() }
    // No crash vendor on the web, so this is never resolved; it exists for expect/actual completeness.
    single<Settings>(CrashReportingSettings) { Settings() }
}
