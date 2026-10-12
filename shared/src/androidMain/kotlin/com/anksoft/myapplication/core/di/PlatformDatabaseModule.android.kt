package com.anksoft.myapplication.core.di

import android.content.Context
import com.anksoft.myapplication.core.database.AndroidDatabaseDriverFactory
import com.anksoft.myapplication.core.database.DatabaseDriverFactory
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformDatabaseModule: Module = module {
    single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(get<Context>()) }
}
