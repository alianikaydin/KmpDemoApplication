package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.error
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Qualifier of the process-wide [CoroutineScope] for work that belongs to no screen (the consent
 * state machine, later crash and analytics hooks). It runs on [Dispatchers.Default], so everything it
 * touches must be safe to use from any thread; session state is guarded by the session lock.
 */
val ApplicationScope: Qualifier = named("applicationScope")

val applicationScopeModule = module {
    single<CoroutineScope>(ApplicationScope) {
        val logger = get<AppLogger>()
        // One failing background job must neither cancel its siblings nor crash the app.
        val handler = CoroutineExceptionHandler { _, throwable ->
            logger.error(LogTags.APP, throwable) { "uncaught error in the application scope" }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
    }
}
