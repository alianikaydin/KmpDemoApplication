package com.anksoft.myapplication.core.di

import org.koin.core.module.Module
import org.koin.dsl.module

// Intentionally empty: the web always loads the current version, so no AppStore is bound and no
// update check runs (AC-17).
actual val platformUpdateModule: Module = module { }
