package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.core.config.AppConfig
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
        networkModule,
        storageModule,
        repositoryModule,
        useCaseModule,
        viewModelModule
    )
    modules(platformModules)
}
