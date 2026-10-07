package com.anksoft.myapplication.core.di

import org.koin.core.module.Module
import org.koin.core.qualifier.named

/**
 * Qualifier of the [com.russhwolf.settings.Settings] that holds [com.anksoft.myapplication.core.preferences.AppPreferences].
 * The unqualified `Settings` is the session store and must never be mixed with this one.
 */
internal val AppPreferencesSettings = named("appPreferencesSettings")

/**
 * Per-platform storage for non-sensitive preferences, bound as `Settings` under
 * [AppPreferencesSettings]. Android uses its own SharedPreferences file, iOS NSUserDefaults and
 * web localStorage (separated from the session by the `app_prefs.` key prefix).
 */
expect val platformStorageModule: Module
