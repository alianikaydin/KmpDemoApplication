package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.core.consent.ConsentManager
import com.anksoft.myapplication.core.session.SessionExpiry
import com.anksoft.myapplication.core.session.SessionObserver
import com.anksoft.myapplication.features.consent.domain.AccountConsentManager
import org.koin.dsl.binds
import org.koin.dsl.module

val sessionModule = module {
    // Observers are resolved on first use so the network layer can call SessionExpiry
    // without constructing them (and what they depend on) while the HttpClient is built.
    single { SessionExpiry(get(), lazy { getAll<SessionObserver>() }) }

    // One instance, three faces: the facade other features query, the observer of the session and
    // the state the consent screens render. Created at start so the first value is ready, from the
    // cache, before anything asks (E5 needs that to decide on a pending crash report).
    single(createdAtStart = true) { AccountConsentManager(get(), get(ApplicationScope), logger = get()) }
        .binds(arrayOf(ConsentManager::class, SessionObserver::class))
}
