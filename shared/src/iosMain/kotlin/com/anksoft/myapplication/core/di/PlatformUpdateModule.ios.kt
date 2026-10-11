package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.features.update.data.AppleAppStore
import com.anksoft.myapplication.features.update.domain.AppStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformUpdateModule: Module = module {
    single<AppStore> { AppleAppStore() }
}
