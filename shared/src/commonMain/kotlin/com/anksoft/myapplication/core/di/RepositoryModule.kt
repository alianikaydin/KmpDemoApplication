package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.core.session.SessionObserver
import com.anksoft.myapplication.features.auth.data.datasource.AuthRemoteDataSource
import com.anksoft.myapplication.features.auth.data.repository.AuthRepositoryImpl
import com.anksoft.myapplication.features.auth.domain.repository.AuthRepository
import com.anksoft.myapplication.features.consent.data.datasource.ConsentLocalDataSource
import com.anksoft.myapplication.features.consent.data.datasource.ConsentRemoteDataSource
import com.anksoft.myapplication.features.consent.data.repository.ConsentRepositoryImpl
import com.anksoft.myapplication.features.consent.domain.repository.ConsentRepository
import com.anksoft.myapplication.features.profile.data.datasource.ProfileLocalDataSource
import com.anksoft.myapplication.features.profile.data.datasource.ProfileRemoteDataSource
import com.anksoft.myapplication.features.profile.data.datasource.SqlProfileLocalDataSource
import com.anksoft.myapplication.features.profile.data.repository.ProfileRepositoryImpl
import com.anksoft.myapplication.features.profile.domain.repository.ProfileRepository
import com.anksoft.myapplication.features.update.data.SettingsDismissedUpdateStore
import com.anksoft.myapplication.features.update.data.datasource.VersionPolicyRemoteDataSource
import com.anksoft.myapplication.features.update.data.repository.VersionPolicyRepositoryImpl
import com.anksoft.myapplication.features.update.domain.DismissedUpdateStore
import com.anksoft.myapplication.features.update.domain.repository.VersionPolicyRepository
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.binds
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
    single { ProfileRemoteDataSource(get(), get()) }
    single<ProfileLocalDataSource> { SqlProfileLocalDataSource(get(), Dispatchers.Default, get()) }
    // Created at start: drops the rows of sessions that ended while the app was not running and loads
    // the cache before Settings opens (AC-3, AC-6). One instance, two faces: the repository Settings
    // reads and the observer of the session.
    single(createdAtStart = true) {
        ProfileRepositoryImpl(get(), get(), get(), get(ApplicationScope), get())
    }.binds(arrayOf(ProfileRepository::class, SessionObserver::class))
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get(), get(), get(), getAll(), get()) }
}
