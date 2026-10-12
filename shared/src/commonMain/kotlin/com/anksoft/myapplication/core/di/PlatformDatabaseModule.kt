package com.anksoft.myapplication.core.di

import org.koin.core.module.Module

/** Binds the platform's [com.anksoft.myapplication.core.database.DatabaseDriverFactory]. The web binds nothing: no database (AC-23). */
expect val platformDatabaseModule: Module
