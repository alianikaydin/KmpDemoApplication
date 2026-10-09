package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.features.auth.data.datasource.AuthRemoteDataSource
import com.anksoft.myapplication.features.auth.data.repository.AuthRepositoryImpl
import com.anksoft.myapplication.features.auth.domain.repository.AuthRepository
import org.koin.dsl.module

val repositoryModule = module {
    single { AuthRemoteDataSource(get(), get()) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get(), getAll(), get()) }
}
