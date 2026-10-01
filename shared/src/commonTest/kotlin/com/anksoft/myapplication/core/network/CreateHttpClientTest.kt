package com.anksoft.myapplication.core.network

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.storage.SessionManager
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class CreateHttpClientTest {

    @Test
    fun defaultConfigHasMockBackendDisabled() {
        assertThat(AppConfig().useMockBackend).isFalse()
    }

    @Test
    fun requestsUseBaseUrlFromConfig() = runTest {
        var requestedUrl: Url? = null
        val engine = MockEngine { request ->
            requestedUrl = request.url
            respondOk()
        }
        val client = createHttpClient(
            engine = engine,
            config = AppConfig(baseUrl = "https://configured.test/api/"),
            sessionManager = SessionManager(MapSettings())
        )

        client.get("auth/ping")

        assertThat(requestedUrl.toString()).isEqualTo("https://configured.test/api/auth/ping")
    }
}
