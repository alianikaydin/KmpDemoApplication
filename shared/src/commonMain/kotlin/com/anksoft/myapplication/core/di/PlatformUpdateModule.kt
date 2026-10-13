package com.anksoft.myapplication.core.di

import org.koin.core.module.Module

/**
 * Binds the platform's [com.anksoft.myapplication.features.update.domain.AppStore]. The web binds
 * nothing, so no update check runs there (AC-17).
 */
expect val platformUpdateModule: Module
