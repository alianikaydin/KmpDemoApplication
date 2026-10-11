package com.anksoft.myapplication.core.network.mock

import com.anksoft.kmpdemo.contract.auth.AuthPaths
import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.kmpdemo.contract.auth.LoginRequestDto
import com.anksoft.kmpdemo.contract.auth.RefreshTokenRequestDto
import com.anksoft.kmpdemo.contract.auth.RegisterRequestDto
import com.anksoft.kmpdemo.contract.auth.UserDto
import com.anksoft.kmpdemo.contract.consent.AccountConsentDto
import com.anksoft.kmpdemo.contract.consent.ConsentDecisionDto
import com.anksoft.kmpdemo.contract.consent.ConsentLanguages
import com.anksoft.kmpdemo.contract.consent.ConsentPaths
import com.anksoft.kmpdemo.contract.consent.ConsentStatus
import com.anksoft.kmpdemo.contract.consent.ConsentTextsDto
import com.anksoft.kmpdemo.contract.error.ErrorCodes
import com.anksoft.kmpdemo.contract.error.ErrorResponseDto
import com.anksoft.myapplication.core.network.appJson
import com.anksoft.myapplication.features.auth.data.datasource.AuthRemoteDataSource
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/** A user the mock backend knows about before anyone registers. [consent] is the decision on record, if any. */
data class SeedUser(
    val email: String,
    val password: String,
    val name: String? = null,
    val consent: ConsentDecisionDto? = null
)

/**
 * In-memory stand-in for the auth backend, used in demo mode. Speaks the same
 * contract as AuthDtos.kt so the real client stack (safeCall, serialization,
 * error mapping) is exercised unchanged.
 *
 * Users live only in memory and are lost when the app process ends.
 * Routing is by path, so a new endpoint is one more branch.
 *
 * Tokens are plain strings that name their user, so a token can be validated after a process
 * restart. A refresh token works once; sending it again is answered with 401 like the real backend
 * does for a reused token. Every 401 carries `WWW-Authenticate: Bearer` like the real backend; the
 * client's single bearer provider does not depend on it, but a second auth provider would.
 *
 * The consent endpoints follow the consent API of the contract: `GET consent/texts` (public, one text
 * version in Turkish and English), and `GET`/`PUT account/consent` for the user the Bearer access
 * token names. Registering can carry a decision, and the response always reports it. The decision
 * time is a fixed demo value.
 */
class MockAuthServer(seedUsers: List<SeedUser> = listOf(DEMO_USER, CONSENT_PENDING_USER)) {

    private class Account(val password: String, val user: UserDto, var consent: ConsentDecisionDto?)

    private val mutex = Mutex()
    private val accounts = mutableMapOf<String, Account>()
    private var nextId = 1
    private var nextTokenSerial = 1
    private val usedRefreshTokens = mutableSetOf<String>()

    init {
        seedUsers.forEach { seed ->
            val email = seed.email.normalized()
            accounts[email] = Account(seed.password, UserDto(newUserId(), email, seed.name), seed.consent)
        }
    }

    suspend fun handle(scope: MockRequestHandleScope, request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath.trimStart('/')
        return when (request.method to path) {
            HttpMethod.Post to AuthRemoteDataSource.PATH_LOGIN -> login(scope, request)
            HttpMethod.Post to AuthRemoteDataSource.PATH_REGISTER -> register(scope, request)
            HttpMethod.Post to AuthPaths.REFRESH -> refresh(scope, request)
            HttpMethod.Get to ConsentPaths.TEXTS -> consentTexts(scope, request)
            HttpMethod.Get to ConsentPaths.ACCOUNT_CONSENT -> readConsent(scope, request)
            HttpMethod.Put to ConsentPaths.ACCOUNT_CONSENT -> writeConsent(scope, request)
            else -> scope.respondStatus(HttpStatusCode.NotFound)
        }
    }

    private suspend fun login(scope: MockRequestHandleScope, request: HttpRequestData): HttpResponseData {
        val body = request.decodeBody<LoginRequestDto>()
            ?: return scope.respondStatus(HttpStatusCode.BadRequest)
        val response = mutex.withLock {
            val account = accounts[body.email.normalized()]
            if (account == null || account.password != body.password) null else issueTokens(account.user)
        } ?: return scope.respondUnauthorized()
        return scope.respondJson(response)
    }

    private suspend fun refresh(scope: MockRequestHandleScope, request: HttpRequestData): HttpResponseData {
        val body = request.decodeBody<RefreshTokenRequestDto>()
            ?: return scope.respondStatus(HttpStatusCode.BadRequest)
        val response = mutex.withLock {
            val user = userForRefreshToken(body.refreshToken)
            // add() is false for a token that was already used.
            if (user == null || !usedRefreshTokens.add(body.refreshToken)) null else issueTokens(user)
        } ?: return scope.respondUnauthorized()
        return scope.respondJson(response)
    }

    /** Refresh tokens look like `mock-refresh-<userId>-<serial>`; unknown users and formats are rejected. */
    private fun userForRefreshToken(token: String): UserDto? {
        if (!token.startsWith(REFRESH_TOKEN_PREFIX)) return null
        val userId = token.removePrefix(REFRESH_TOKEN_PREFIX).substringBeforeLast('-', missingDelimiterValue = "")
        return accounts.values.firstOrNull { it.user.id == userId }?.user
    }

    private suspend fun register(scope: MockRequestHandleScope, request: HttpRequestData): HttpResponseData {
        val body = request.decodeBody<RegisterRequestDto>()
            ?: return scope.respondStatus(HttpStatusCode.BadRequest)
        // An invalid decision refuses the whole registration: no account without its consent record.
        body.consent?.let { decision ->
            when (validate(decision)) {
                DecisionCheck.INVALID -> return scope.respondStatus(HttpStatusCode.BadRequest)
                DecisionCheck.UNKNOWN_VERSION -> return scope.respondUnknownConsentVersion()
                DecisionCheck.OK -> Unit
            }
        }
        val email = body.email.normalized()
        val response = mutex.withLock {
            if (email in accounts) {
                null
            } else {
                val user = UserDto(id = newUserId(), email = email)
                accounts[email] = Account(body.password, user, body.consent)
                issueTokens(user).copy(consent = body.consent.toAccountConsent())
            }
        } ?: return scope.respondStatus(HttpStatusCode.Conflict)
        return scope.respondJson(response)
    }

    private fun consentTexts(scope: MockRequestHandleScope, request: HttpRequestData): HttpResponseData {
        val requested = request.url.parameters[ConsentPaths.LANG_PARAM]
            ?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_')
        // A language without a text falls back to English, like the real backend.
        val language = requested?.takeIf { it in CONSENT_TEXTS } ?: ConsentLanguages.FALLBACK
        return scope.respondJson(ConsentTextsDto.serializer(), CONSENT_TEXTS.getValue(language))
    }

    private suspend fun readConsent(scope: MockRequestHandleScope, request: HttpRequestData): HttpResponseData {
        val consent = mutex.withLock { accountFor(request)?.let { it.consent.toAccountConsent() } }
            ?: return scope.respondUnauthorized()
        return scope.respondJson(AccountConsentDto.serializer(), consent)
    }

    private suspend fun writeConsent(scope: MockRequestHandleScope, request: HttpRequestData): HttpResponseData {
        val decision = request.decodeBody<ConsentDecisionDto>()
        val response = mutex.withLock {
            val account = accountFor(request) ?: return@withLock null
            when (if (decision == null) DecisionCheck.INVALID else validate(decision)) {
                DecisionCheck.INVALID -> scope.respondStatus(HttpStatusCode.BadRequest)
                DecisionCheck.UNKNOWN_VERSION -> scope.respondUnknownConsentVersion()
                DecisionCheck.OK -> {
                    account.consent = decision
                    scope.respondJson(AccountConsentDto.serializer(), decision.toAccountConsent())
                }
            }
        }
        return response ?: scope.respondUnauthorized()
    }

    /** The account the request's Bearer access token names, or null for a missing or unknown token. */
    private fun accountFor(request: HttpRequestData): Account? {
        val header = request.headers[HttpHeaders.Authorization] ?: return null
        if (!header.startsWith(BEARER_PREFIX)) return null
        val token = header.removePrefix(BEARER_PREFIX)
        if (!token.startsWith(ACCESS_TOKEN_PREFIX)) return null
        val userId = token.removePrefix(ACCESS_TOKEN_PREFIX).substringBeforeLast('-', missingDelimiterValue = "")
        return accounts.values.firstOrNull { it.user.id == userId }
    }

    private enum class DecisionCheck { OK, INVALID, UNKNOWN_VERSION }

    private fun validate(decision: ConsentDecisionDto): DecisionCheck = when {
        decision.status != ConsentStatus.GRANTED && decision.status != ConsentStatus.DENIED -> DecisionCheck.INVALID
        CONSENT_TEXTS[decision.textLanguage]?.version != decision.textVersion -> DecisionCheck.UNKNOWN_VERSION
        else -> DecisionCheck.OK
    }

    private fun ConsentDecisionDto?.toAccountConsent(): AccountConsentDto =
        if (this == null) {
            AccountConsentDto(status = ConsentStatus.NONE, reconsentRequired = false)
        } else {
            AccountConsentDto(
                status = status,
                textVersion = textVersion,
                textLanguage = textLanguage,
                decidedAt = DECIDED_AT,
                reconsentRequired = false
            )
        }

    private fun issueTokens(user: UserDto): AuthResponseDto {
        val serial = nextTokenSerial++
        return AuthResponseDto(
            accessToken = "mock-access-${user.id}-$serial",
            refreshToken = "$REFRESH_TOKEN_PREFIX${user.id}-$serial",
            user = user
        )
    }

    private fun newUserId(): String = "demo-${nextId++}"

    private fun String.normalized(): String = trim().lowercase()

    private suspend inline fun <reified T> HttpRequestData.decodeBody(): T? = try {
        appJson.decodeFromString<T>(body.toByteArray().decodeToString())
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun MockRequestHandleScope.respondJson(response: AuthResponseDto): HttpResponseData =
        respondJson(AuthResponseDto.serializer(), response)

    private fun <T> MockRequestHandleScope.respondJson(serializer: KSerializer<T>, value: T): HttpResponseData =
        respond(
            content = appJson.encodeToString(serializer, value),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )

    private fun MockRequestHandleScope.respondUnknownConsentVersion(): HttpResponseData =
        respond(
            content = appJson.encodeToString(
                ErrorResponseDto.serializer(),
                ErrorResponseDto(ErrorCodes.UNKNOWN_CONSENT_VERSION, "Unknown consent text version.")
            ),
            status = HttpStatusCode.UnprocessableEntity,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )

    private fun MockRequestHandleScope.respondUnauthorized(): HttpResponseData =
        respond(
            content = "",
            status = HttpStatusCode.Unauthorized,
            headers = headersOf(HttpHeaders.WWWAuthenticate, "Bearer")
        )

    private fun MockRequestHandleScope.respondStatus(status: HttpStatusCode): HttpResponseData =
        respond(content = "", status = status)

    companion object {
        private const val ACCESS_TOKEN_PREFIX = "mock-access-"
        private const val REFRESH_TOKEN_PREFIX = "mock-refresh-"
        private const val BEARER_PREFIX = "Bearer "
        private const val DECIDED_AT = "2026-01-01T00:00:00Z"

        /** The one consent text version the mock knows, in the languages the app has. */
        private val CONSENT_TEXTS = mapOf(
            "en" to ConsentTextsDto(
                version = 1,
                language = "en",
                label = "Allow optional data collection",
                description = "Help us improve the app: we may collect crash reports and usage statistics.",
                policyUrl = "https://example.com/privacy"
            ),
            "tr" to ConsentTextsDto(
                version = 1,
                language = "tr",
                label = "İsteğe bağlı veri toplamaya izin ver",
                description = "Uygulamayı geliştirmemize yardım edin: " +
                    "çökme raporları ve kullanım istatistikleri toplayabiliriz.",
                policyUrl = "https://example.com/privacy"
            )
        )

        /** Has already granted consent (text version 1, English), so no consent prompt appears for it. */
        val DEMO_USER = SeedUser(
            email = "demo@example.com",
            password = "Demo1234",
            name = "Demo User",
            consent = ConsentDecisionDto(ConsentStatus.GRANTED, textVersion = 1, textLanguage = "en")
        )

        /** Has no consent decision on record, so signing in shows the one-time consent prompt. */
        val CONSENT_PENDING_USER = SeedUser(
            email = "consent@example.com",
            password = "Demo1234",
            name = "Consent User"
        )
    }
}
