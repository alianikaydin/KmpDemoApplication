package com.anksoft.myapplication.core.network

import com.anksoft.kmpdemo.contract.auth.AuthPaths
import com.anksoft.kmpdemo.contract.auth.RefreshTokenRequestDto
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.config.AppEnvironment
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.debug
import com.anksoft.myapplication.core.network.mock.MockAuthServer
import com.anksoft.myapplication.core.network.mock.createMockEngine
import com.anksoft.myapplication.core.session.SessionExpiry
import com.anksoft.myapplication.core.storage.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import org.koin.dsl.module
import io.ktor.client.plugins.logging.Logger as KtorLogger

/** Auth request timeout per the performance NFR. */
private const val REQUEST_TIMEOUT_MILLIS = 15_000L

/** A 401 from these endpoints is an answer (wrong password, rejected refresh), not an expired token. */
private val NO_REFRESH_PATHS = listOf(AuthPaths.LOGIN, AuthPaths.REGISTER, AuthPaths.REFRESH)

private const val BEARER_PREFIX = "Bearer "

val networkModule = module {
    single { MockAuthServer() }
    single {
        val config = get<AppConfig>()
        // Resolved on first use: SessionExpiry is not needed to build the client.
        val koin = getKoin()
        createHttpClient(
            engine = if (config.useMockBackend) createMockEngine(get()) else null,
            config = config,
            sessionManager = get(),
            logger = get(),
            onSessionExpired = { koin.get<SessionExpiry>().expire() }
        )
    }
    single<AuthTokenCache> { KtorAuthTokenCache(get()) }
}

/**
 * Builds the app's HttpClient. A null [engine] uses the platform default
 * (OkHttp / Darwin / Js); demo mode and tests pass a MockEngine instead.
 * HTTP logging goes through [logger] and its level depends on the environment.
 *
 * A 401 on a protected request triggers one token refresh. If the backend rejects the refresh
 * token, [onSessionExpired] is called.
 */
fun createHttpClient(
    engine: HttpClientEngine?,
    config: AppConfig,
    sessionManager: SessionManager,
    logger: AppLogger,
    onSessionExpired: () -> Unit = {}
): HttpClient {
    val httpLogger = KtorLoggerBridge(logger)
    val refresher = TokenRefresher(sessionManager, logger, onSessionExpired)
    val block: HttpClientConfig<*>.() -> Unit = {
        install(ContentNegotiation) {
            json(appJson)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        }
        install(Auth) {
            bearer {
                // Attaches the stored access token to outgoing requests.
                loadTokens {
                    sessionManager.getToken()?.let { accessToken ->
                        BearerTokens(
                            accessToken = accessToken,
                            refreshToken = sessionManager.getRefreshToken()
                        )
                    }
                }
                refreshTokens {
                    val path = response.request.url.encodedPath
                    if (NO_REFRESH_PATHS.any { path.endsWith(it) }) {
                        null
                    } else {
                        refresher.refresh(
                            staleRefreshToken = oldTokens?.refreshToken,
                            failedAccessToken = response.request.headers[HttpHeaders.Authorization]
                                ?.removePrefix(BEARER_PREFIX)
                        ) { refreshToken ->
                            client.post(AuthPaths.REFRESH) {
                                // Member of RefreshTokensParams: keeps this call out of the auth plugin.
                                markAsRefreshTokenRequest()
                                setBody(RefreshTokenRequestDto(refreshToken))
                            }
                        }
                    }
                }
            }
        }
        install(Logging) {
            // BODY/ALL would log request bodies -- i.e. plaintext passwords -- so they are never
            // used. Headers are logged in DEV only; STAGE and PROD log no HTTP traffic.
            this.logger = httpLogger
            level = config.environment.httpLogLevel()
            // Never log credentials or cookies.
            // Header names are matched case-insensitively: HTTP/2 servers send them lower-cased.
            sanitizeHeader { header -> SENSITIVE_HEADERS.any { it.equals(header, ignoreCase = true) } }
        }
        defaultRequest {
            url(config.baseUrl)
            contentType(ContentType.Application.Json)
        }
    }
    return if (engine == null) HttpClient(block) else HttpClient(engine, block)
}

/** Headers whose values must never reach a log line. */
private val SENSITIVE_HEADERS = listOf(HttpHeaders.Authorization, HttpHeaders.SetCookie, HttpHeaders.Cookie)

/** Forwards Ktor's HTTP log lines to the app logger. */
internal class KtorLoggerBridge(private val logger: AppLogger) : KtorLogger {
    override fun log(message: String) {
        logger.debug(LogTags.HTTP) { message }
    }
}

/** HTTP log level per environment: headers in DEV, nothing in STAGE and PROD. */
internal fun AppEnvironment.httpLogLevel(): LogLevel = when (this) {
    AppEnvironment.DEV -> LogLevel.HEADERS
    AppEnvironment.STAGE, AppEnvironment.PROD -> LogLevel.NONE
}
