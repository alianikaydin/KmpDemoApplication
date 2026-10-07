package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.core.config.AppConfig
import org.koin.core.KoinApplication

/**
 * iOS entry point. Swift cannot see default arguments, so every parameter is explicit and
 * everything is passed as a string or boolean (see [AppConfig.fromRaw]).
 * Swift sees this as `KoinIosKt.doInitKoin(...)`.
 */
fun doInitKoin(
    environment: String,
    backendUrl: String?,
    demoAllowed: Boolean,
    versionName: String,
    versionCode: String
): KoinApplication = initKoin(
    config = AppConfig.fromRaw(
        environment = environment,
        backendUrl = backendUrl,
        demoAllowed = demoAllowed,
        versionName = versionName,
        versionCode = versionCode
    )
)
