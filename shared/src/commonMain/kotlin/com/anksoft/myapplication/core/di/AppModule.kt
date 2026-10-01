package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.network.networkModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

fun initKoin(
    config: AppConfig = AppConfig(),
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
}

/** iOS entry point; Swift passes true for DEBUG builds. */
fun doInitKoin(useMockBackend: Boolean) =
    initKoin(if (useMockBackend) AppConfig.Demo else AppConfig())
