package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.features.profile.FakeProfileLocalDataSource
import com.anksoft.myapplication.features.profile.data.datasource.ProfileLocalDataSource
import org.koin.dsl.module

/**
 * Replaces the profile store in every `initKoin` of a host test. The profile repository is created at
 * start and the real store would need the Android context for the database file, which host tests
 * do not have. A test that forgets this module fails to start Koin, on purpose.
 */
val fakeProfileStoreModule = module { single<ProfileLocalDataSource> { FakeProfileLocalDataSource() } }
