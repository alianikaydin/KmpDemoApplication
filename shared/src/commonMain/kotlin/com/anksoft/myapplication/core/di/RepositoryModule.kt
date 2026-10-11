package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.features.auth.data.datasource.AuthRemoteDataSource
import com.anksoft.myapplication.features.auth.data.repository.AuthRepositoryImpl
import com.anksoft.myapplication.features.auth.domain.repository.AuthRepository
import com.anksoft.myapplication.features.consent.data.datasource.ConsentLocalDataSource
import com.anksoft.myapplication.features.consent.data.datasource.ConsentRemoteDataSource
import com.anksoft.myapplication.features.consent.data.repository.ConsentRepositoryImpl
import com.anksoft.myapplication.features.consent.domain.repository.ConsentRepository
import org.koin.dsl.module

val repositoryModule = module {
    single { AuthRemoteDataSource(get(), get()) }
    single { ConsentRemoteDataSource(get(), get()) }
    single { ConsentLocalDataSource(get()) }
    single<ConsentRepository> { ConsentRepositoryImpl(get(), get(), get()) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get(), get(), getAll(), get()) }
}
