package com.anksoft.myapplication.core.di

import org.koin.core.module.Module
import org.koin.dsl.module

// Intentionally empty: no persistent database on the web (AC-23, docs/local-database.md).
actual val platformDatabaseModule: Module = module { }
