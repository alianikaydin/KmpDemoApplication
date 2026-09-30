package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.core.storage.createSecureSettings
import org.koin.dsl.module

val storageModule = module {
    single { createSecureSettings() }
    single { SessionManager(get()) }
}
