package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.info
import com.anksoft.myapplication.core.logging.loggingModule
import com.anksoft.myapplication.core.network.networkModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Starts Koin. [config] has no default on purpose: a forgotten call must not compile instead of
 * silently falling back to the mock or prod backend. [platformModules] are loaded last so a
 * platform can add or override bindings.
 */
fun initKoin(
    config: AppConfig,
    platformModules: List<Module> = emptyList(),
    appDeclaration: KoinAppDeclaration = {}
) = startKoin {
    appDeclaration()
    modules(
        module { single { config } },
        loggingModule,
        networkModule,
        storageModule,
        repositoryModule,
        useCaseModule,
        viewModelModule
    )
    modules(platformModules)
}.also { app ->
    // Single startup line for every platform, including iOS.
    app.koin.get<AppLogger>().info(LogTags.APP) {
        "Started env=${config.environment} version=${config.versionName}"
    }
}
