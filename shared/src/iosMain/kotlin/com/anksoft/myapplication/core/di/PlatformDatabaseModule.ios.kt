package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.core.database.DatabaseDriverFactory
import com.anksoft.myapplication.core.database.IosDatabaseDriverFactory
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformDatabaseModule: Module = module {
    single<DatabaseDriverFactory> { IosDatabaseDriverFactory() }
}
