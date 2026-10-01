package com.anksoft.myapplication.core.di

import assertk.assertThat
import assertk.assertions.isInstanceOf
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.network.mock.MockAuthServer
import com.anksoft.myapplication.features.auth.domain.repository.AuthRepository
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.test.runTest
import org.koin.core.context.loadKoinModules
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test

class InitKoinTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun demoConfigWiresMockBackendEndToEnd() = runTest {
        val koin = initKoin(config = AppConfig.Demo).koin
        // Platform settings need an Android context; swap in memory-backed settings.
        loadKoinModules(module { single<Settings> { MapSettings() } })

        val demoUser = MockAuthServer.DEMO_USER
        val result = koin.get<AuthRepository>().login(demoUser.email, demoUser.password)

        assertThat(result).isInstanceOf(Result.Success::class)
    }
}
