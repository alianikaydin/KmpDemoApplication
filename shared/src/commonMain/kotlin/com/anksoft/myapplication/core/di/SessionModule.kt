package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.core.session.SessionExpiry
import com.anksoft.myapplication.core.session.SessionObserver
import org.koin.dsl.module

val sessionModule = module {
    // Observers are resolved on first use so the network layer can call SessionExpiry
    // without constructing them (and what they depend on) while the HttpClient is built.
    single { SessionExpiry(get(), lazy { getAll<SessionObserver>() }) }
}
