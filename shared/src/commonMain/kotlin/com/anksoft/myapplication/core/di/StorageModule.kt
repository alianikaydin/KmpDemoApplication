package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.core.preferences.AppPreferences
import com.anksoft.myapplication.core.preferences.ContentLanguage
import com.anksoft.myapplication.core.preferences.SettingsAppPreferences
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.core.storage.createSecureSettings
import org.koin.dsl.module

val storageModule = module {
    includes(platformStorageModule)

    // Session store (unqualified Settings). Preferences use the qualified one below.
    single { createSecureSettings() }
    single { SessionManager(get()) }
    single<AppPreferences> { SettingsAppPreferences(get(AppPreferencesSettings), get()) }
    single { ContentLanguage(get()) }
}
