package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.core.network.networkModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(
            networkModule,
            storageModule,
            repositoryModule,
            useCaseModule,
            viewModelModule
        )
    }

// Helper for iOS
fun doInitKoin() = initKoin {}
