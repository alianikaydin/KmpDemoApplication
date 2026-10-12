package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.features.auth.data.datasource.AuthRemoteDataSource
import com.anksoft.myapplication.features.auth.data.repository.AuthRepositoryImpl
import com.anksoft.myapplication.features.auth.domain.repository.AuthRepository
import com.anksoft.myapplication.features.consent.data.datasource.ConsentLocalDataSource
import com.anksoft.myapplication.features.consent.data.datasource.ConsentRemoteDataSource
import com.anksoft.myapplication.features.consent.data.repository.ConsentRepositoryImpl
import com.anksoft.myapplication.features.consent.domain.repository.ConsentRepository
import com.anksoft.myapplication.features.update.data.SettingsDismissedUpdateStore
import com.anksoft.myapplication.features.update.data.datasource.VersionPolicyRemoteDataSource
import com.anksoft.myapplication.features.update.data.repository.VersionPolicyRepositoryImpl
import com.anksoft.myapplication.features.update.domain.DismissedUpdateStore
import com.anksoft.myapplication.features.update.domain.repository.VersionPolicyRepository
import org.koin.dsl.module

val repositoryModule = module {
    includes(platformUpdateModule)

    single { AuthRemoteDataSource(get(), get()) }
    single { ConsentRemoteDataSource(get(), get()) }
    single { ConsentLocalDataSource(get()) }
    single<ConsentRepository> { ConsentRepositoryImpl(get(), get(), get()) }
    single { VersionPolicyRemoteDataSource(get(), get()) }
    single<VersionPolicyRepository> { VersionPolicyRepositoryImpl(get(), get()) }
    single<DismissedUpdateStore> { SettingsDismissedUpdateStore(get(AppPreferencesSettings), get()) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get(), get(), getAll(), get()) }
}
