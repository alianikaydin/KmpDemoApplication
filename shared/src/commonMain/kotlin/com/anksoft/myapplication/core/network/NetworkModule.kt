package com.anksoft.myapplication.core.network

import com.anksoft.myapplication.core.storage.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/** Auth request timeout per the performance NFR. */
private const val REQUEST_TIMEOUT_MILLIS = 15_000L

val networkModule = module {
    single {
        val sessionManager = get<SessionManager>()
        HttpClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    }
                )
            }
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            }
            install(Auth) {
                bearer {
                    // Attaches the stored access token to outgoing requests.
                    // Previously the token was saved but never sent.
                    loadTokens {
                        sessionManager.getToken()?.let { accessToken ->
                            BearerTokens(
                                accessToken = accessToken,
                                refreshToken = sessionManager.getRefreshToken()
                            )
                        }
                    }
                    // Silent refresh (AC-5.2) needs the backend refresh contract,
                    // which does not exist yet -- deliberately left to P1 rather
                    // than guessed at. Until then a 401 surfaces to the caller.
                }
            }
            install(Logging) {
                // LogLevel.ALL would print request bodies -- i.e. plaintext
                // passwords -- to logcat. HEADERS only, per AC-1.8.
                level = LogLevel.HEADERS
            }
            defaultRequest {
                url(BASE_URL)
                contentType(ContentType.Application.Json)
            }
        }
    }
}

// TODO move to a build-config field per flavour once a real backend exists.
private const val BASE_URL = "https://api.example.com/"
