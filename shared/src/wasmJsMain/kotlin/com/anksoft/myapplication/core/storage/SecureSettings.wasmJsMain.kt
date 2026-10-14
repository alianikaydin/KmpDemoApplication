package com.anksoft.myapplication.core.storage

import com.russhwolf.settings.Settings
import org.koin.core.scope.Scope

actual fun Scope.createSecureSettings(): Settings = Settings()
