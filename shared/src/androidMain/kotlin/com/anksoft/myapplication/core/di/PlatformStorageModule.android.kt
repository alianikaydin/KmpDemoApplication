package com.anksoft.myapplication.core.di

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformStorageModule: Module = module {
    single<Settings>(AppPreferencesSettings) {
        // A dedicated file, so preferences are not stored next to the session.
        SharedPreferencesSettings(
            get<Context>().getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        )
    }
    single<Settings>(CrashReportingSettings) {
        // Its own file so the backup rules can exclude exactly this one (see backup_rules.xml).
        SharedPreferencesSettings(
            get<Context>().getSharedPreferences("crash_reporting", Context.MODE_PRIVATE)
        )
    }
}
