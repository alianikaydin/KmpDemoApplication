package com.anksoft.myapplication.core.network.mock

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import com.anksoft.kmpdemo.contract.auth.AuthPaths
import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.kmpdemo.contract.auth.LoginRequestDto
import com.anksoft.kmpdemo.contract.auth.RefreshTokenRequestDto
import com.anksoft.kmpdemo.contract.consent.AccountConsentDto
import com.anksoft.kmpdemo.contract.consent.ConsentDecisionDto
import com.anksoft.kmpdemo.contract.consent.ConsentPaths
import com.anksoft.kmpdemo.contract.consent.ConsentTextsDto
import com.anksoft.myapplication.core.config.TestAppConfigs
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.network.KtorAuthTokenCache
import com.anksoft.myapplication.core.network.createHttpClient
import com.anksoft.myapplication.core.network.safeCall
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.auth.data.datasource.AuthRemoteDataSource
import com.anksoft.myapplication.features.auth.data.repository.AuthRepositoryImpl
import com.anksoft.myapplication.features.auth.domain.model.User
import com.anksoft.myapplication.features.consent.data.datasource.ConsentLocalDataSource
import com.anksoft.myapplication.features.consent.data.datasource.ConsentRemoteDataSource
import com.anksoft.myapplication.features.consent.domain.model.ConsentChoice
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.fail
import kotlinx.coroutines.test.runTest

class MockAuthServerTest {

    private val sessionManager = SessionManager(MapSettings())
    private val httpClient = createHttpClient(
        engine = createMockEngine(MockAuthServer()),
        config = TestAppConfigs.demo(),
        sessionManager = sessionManager,
        logger = NoOpLogger
    )
    private val remoteDataSource = AuthRemoteDataSource(httpClient, NoOpLogger)
    private val repository = AuthRepositoryImpl(
        remoteDataSource,
        sessionManager,
        KtorAuthTokenCache(httpClient),
        ConsentLocalDataSource(sessionManager),
        emptyList(),
        NoOpLogger
    )

    private val consentSource = ConsentRemoteDataSource(httpClient, NoOpLogger)

    private val demoUser = MockAuthServer.DEMO_USER
    private val consentUser = MockAuthServer.CONSENT_PENDING_USER

    @AfterTest
    fun tearDown() {
        httpClient.close()
    }

    @Test
    fun demoUserLoginSucceedsAndPersistsSession() = runTest {
        val result = repository.login(demoUser.email, demoUser.password)

        assertThat(result).isEqualTo(
            Result.Success(User(id = "demo-1", email = demoUser.email, name = demoUser.name))
        )
        assertThat(sessionManager.getToken()).isNotNull()
        assertThat(sessionManager.getRefreshToken()).isNotNull()
    }

    @Test
    fun wrongPasswordReturnsUnauthorized() = runTest {
        val result = repository.login(demoUser.email, "Wrong1234")

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNAUTHORIZED))
    }

    @Test
    fun unknownEmailReturnsUnauthorized() = runTest {
        val result = repository.login("nobody@example.com", demoUser.password)

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNAUTHORIZED))
    }

    @Test
    fun loginIgnoresEmailCase() = runTest {
        val result = repository.login(demoUser.email.uppercase(), demoUser.password)

        assertThat(result).isInstanceOf(Result.Success::class)
    }

    @Test
    fun registeredUserCanLogIn() = runTest {
        val register = repository.register("new@example.com", "Secret123", consent = null)
        val login = repository.login("new@example.com", "Secret123")

        assertThat(register).isInstanceOf(Result.Success::class)
        assertThat(login).isInstanceOf(Result.Success::class)
    }

    @Test
    fun registeringExistingEmailReturnsConflict() = runTest {
        val result = repository.register(demoUser.email, "Another123", consent = null)

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.CONFLICT))
    }

    @Test
    fun loginResponseMatchesAuthContract() = runTest {
        val result = remoteDataSource.login(demoUser.email, demoUser.password)

        val response = (result as Result.Success<AuthResponseDto>).data
        assertThat(response.accessToken).isNotNull()
        assertThat(response.refreshToken).isNotNull()
        assertThat(response.user.name).isEqualTo(demoUser.name)
    }

    @Test
    fun unknownPathReturnsUnknownError() = runTest {
        val result = safeCall<AuthResponseDto>(NoOpLogger, "auth/unknown") { httpClient.post("auth/unknown") }

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNKNOWN))
    }

    private suspend fun refresh(refreshToken: String, client: HttpClient = httpClient): HttpResponse =
        client.post(AuthPaths.REFRESH) { setBody(RefreshTokenRequestDto(refreshToken)) }

    private suspend fun demoLogin(): AuthResponseDto =
        (remoteDataSource.login(demoUser.email, demoUser.password) as Result.Success<AuthResponseDto>).data

    // AC-28
    @Test
    fun refreshIssuesANewTokenPairForTheSameUser() = runTest {
        val login = demoLogin()

        val response = refresh(requireNotNull(login.refreshToken))

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val refreshed = response.body<AuthResponseDto>()
        assertThat(refreshed.user).isEqualTo(login.user)
        assertThat(refreshed.accessToken).isNotEqualTo(login.accessToken)
        assertThat(refreshed.refreshToken).isNotEqualTo(login.refreshToken)
    }

    // AC-28
    @Test
    fun aRefreshTokenWorksOnlyOnce() = runTest {
        val login = demoLogin()
        refresh(requireNotNull(login.refreshToken))

        val reuse = refresh(requireNotNull(login.refreshToken))

        assertThat(reuse.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(reuse.headers[HttpHeaders.WWWAuthenticate]).isEqualTo("Bearer")
    }

    // AC-28
    @Test
    fun theRotatedRefreshTokenCanBeRefreshedAgain() = runTest {
        val first = refresh(requireNotNull(demoLogin().refreshToken)).body<AuthResponseDto>()

        val second = refresh(requireNotNull(first.refreshToken))

        assertThat(second.status).isEqualTo(HttpStatusCode.OK)
    }

    // AC-28
    @Test
    fun unknownRefreshTokensAreRejected() = runTest {
        listOf("garbage", "mock-refresh-", "mock-refresh-demo-99-1", "mock-refresh-nodash").forEach { token ->
            val response = refresh(token)

            assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
            assertThat(response.headers[HttpHeaders.WWWAuthenticate]).isEqualTo("Bearer")
        }
    }

    // AC-28
    @Test
    fun refreshTokenFromBeforeAProcessRestartStillWorksForSeededUsers() = runTest {
        val login = demoLogin()
        val restartedClient = createHttpClient(
            engine = createMockEngine(MockAuthServer()),
            config = TestAppConfigs.demo(),
            sessionManager = SessionManager(MapSettings()),
            logger = NoOpLogger
        )

        try {
            val response = refresh(requireNotNull(login.refreshToken), restartedClient)

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        } finally {
            restartedClient.close()
        }
    }

    // AC-28
    @Test
    fun everyUnauthorizedAnswerAsksForBearerAuth() = runTest {
        val response = httpClient.post(AuthPaths.LOGIN) {
            setBody(LoginRequestDto(demoUser.email, "Wrong1234"))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(response.headers[HttpHeaders.WWWAuthenticate]).isEqualTo("Bearer")
    }

    private fun <T> Result<T, DataError.Remote>.dataOrFail(): T = when (this) {
        is Result.Success -> data
        is Result.Failure -> fail("Expected success but got $error")
    }

    private suspend fun signIn(user: SeedUser) {
        assertThat(repository.login(user.email, user.password)).isInstanceOf(Result.Success::class)
    }

    private fun decision(status: String, version: Int = 1, language: String = "en") =
        ConsentDecisionDto(status = status, textVersion = version, textLanguage = language)

    // region consent texts

    // AC-21, AC-24
    @Test
    fun consentTextsAreServedInTurkishAndEnglishWithAVersion() = runTest {
        val turkish = consentSource.getTexts("tr").dataOrFail()
        val english = consentSource.getTexts("en").dataOrFail()

        assertThat(turkish.language).isEqualTo("tr")
        assertThat(english.language).isEqualTo("en")
        assertThat(turkish.version).isEqualTo(english.version)
        assertThat(turkish.label).isNotEqualTo(english.label)
        assertThat(english.policyUrl).isEqualTo("https://example.com/privacy")
    }

    // AC-24
    @Test
    fun theLanguageParameterIsNormalisedLikeTheRealBackend() = runTest {
        listOf("TR", " tr-TR ", "tr_TR").forEach { tag ->
            assertThat(consentSource.getTexts(tag).dataOrFail().language).isEqualTo("tr")
        }
    }

    // AC-24
    @Test
    fun aLanguageWithoutATextFallsBackToEnglish() = runTest {
        listOf("de", "", "x-unknown").forEach { tag ->
            assertThat(consentSource.getTexts(tag).dataOrFail().language).isEqualTo("en")
        }
    }

    // AC-1, AC-21
    @Test
    fun consentTextsNeedNoSession() = runTest {
        val response = httpClient.get(ConsentPaths.TEXTS)

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.body<ConsentTextsDto>().language).isEqualTo("en")
    }

    // endregion

    // region account consent

    // AC-6, AC-31
    @Test
    fun theDemoUserHasAlreadyGrantedConsentSoNoPromptAppears() = runTest {
        signIn(demoUser)

        val consent = consentSource.getAccountConsent().dataOrFail()

        assertThat(consent.status).isEqualTo("granted")
        assertThat(consent.textVersion).isEqualTo(1)
        assertThat(consent.reconsentRequired).isEqualTo(false)
    }

    // AC-7, AC-31
    @Test
    fun theConsentPendingUserHasNoDecisionOnRecord() = runTest {
        signIn(consentUser)

        assertThat(consentSource.getAccountConsent().dataOrFail().status).isEqualTo("none")
    }

    // AC-15, AC-17
    @Test
    fun aSavedDecisionIsReturnedByTheNextRead() = runTest {
        signIn(consentUser)

        val denied = consentSource.putAccountConsent(decision("denied")).dataOrFail()
        assertThat(denied.status).isEqualTo("denied")
        assertThat(consentSource.getAccountConsent().dataOrFail().status).isEqualTo("denied")

        consentSource.putAccountConsent(decision("granted", language = "tr")).dataOrFail()
        val granted = consentSource.getAccountConsent().dataOrFail()
        assertThat(granted.status).isEqualTo("granted")
        assertThat(granted.textLanguage).isEqualTo("tr")
    }

    // AC-22
    @Test
    fun theSavedDecisionKeepsTheVersionLanguageAndATime() = runTest {
        signIn(consentUser)

        val saved = consentSource.putAccountConsent(decision("granted", language = "tr")).dataOrFail()

        assertThat(saved.textVersion).isEqualTo(1)
        assertThat(saved.textLanguage).isEqualTo("tr")
        assertThat(saved.decidedAt).isNotNull()
    }

    @Test
    fun savingTheSameDecisionTwiceIsHarmless() = runTest {
        signIn(consentUser)

        val first = consentSource.putAccountConsent(decision("denied")).dataOrFail()
        val second = consentSource.putAccountConsent(decision("denied")).dataOrFail()

        assertThat(second).isEqualTo(first)
    }

    // AC-11
    @Test
    fun aTextVersionTheServerDoesNotKnowIsRefusedAndTheDecisionStaysAsItWas() = runTest {
        signIn(consentUser)

        val result = consentSource.putAccountConsent(decision("granted", version = 9))

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNPROCESSABLE))
        assertThat(consentSource.getAccountConsent().dataOrFail().status).isEqualTo("none")
    }

    @Test
    fun aLanguageWithoutATextIsRefusedAsUnknownVersion() = runTest {
        signIn(consentUser)

        val result = consentSource.putAccountConsent(decision("granted", language = "de"))

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNPROCESSABLE))
    }

    @Test
    fun aStatusOtherThanGrantedOrDeniedIsABadRequest() = runTest {
        signIn(consentUser)

        listOf("none", "withdrawn", "").forEach { status ->
            val response = httpClient.put(ConsentPaths.ACCOUNT_CONSENT) { setBody(decision(status)) }

            assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        }
    }

    // AC-28
    @Test
    fun theProtectedConsentEndpointsRefuseARequestWithoutAToken() = runTest {
        val read = httpClient.get(ConsentPaths.ACCOUNT_CONSENT)
        val write = httpClient.put(ConsentPaths.ACCOUNT_CONSENT) { setBody(decision("granted")) }

        assertThat(read.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(read.headers[HttpHeaders.WWWAuthenticate]).isEqualTo("Bearer")
        assertThat(write.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(write.headers[HttpHeaders.WWWAuthenticate]).isEqualTo("Bearer")
    }

    // AC-28
    @Test
    fun aTokenThatNamesNoKnownUserIsRefused() = runTest {
        sessionManager.saveToken("mock-access-demo-99-1")
        sessionManager.saveUserId("demo-99")

        val result = consentSource.getAccountConsent()

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNAUTHORIZED))
    }

    // AC-28
    @Test
    fun aTokenFromBeforeAProcessRestartStillNamesItsSeededUser() = runTest {
        signIn(demoUser)
        val restartedClient = createHttpClient(
            engine = createMockEngine(MockAuthServer()),
            config = TestAppConfigs.demo(),
            sessionManager = sessionManager,
            logger = NoOpLogger
        )

        try {
            val consent = ConsentRemoteDataSource(restartedClient, NoOpLogger).getAccountConsent().dataOrFail()

            assertThat(consent.status).isEqualTo("granted")
        } finally {
            restartedClient.close()
        }
    }

    // endregion

    // region registration with consent

    // AC-3, AC-22
    @Test
    fun registeringWithADecisionReturnsItAndKeepsItOnTheAccount() = runTest {
        val result = repository.register("fresh@example.com", "Secret123", ConsentChoice(true, 1, "tr"))
        assertThat(result).isInstanceOf(Result.Success::class)

        val consent = consentSource.getAccountConsent().dataOrFail()

        assertThat(consent.status).isEqualTo("granted")
        assertThat(consent.textLanguage).isEqualTo("tr")
        assertThat(sessionManager.getConsentStatus()).isEqualTo("granted")
    }

    // AC-2
    @Test
    fun registeringWithTheBoxUncheckedRecordsDenied() = runTest {
        repository.register("fresh@example.com", "Secret123", ConsentChoice(false, 1, "en"))

        assertThat(consentSource.getAccountConsent().dataOrFail().status).isEqualTo("denied")
        assertThat(sessionManager.getConsentStatus()).isEqualTo("denied")
    }

    // AC-4
    @Test
    fun registeringWithoutADecisionLeavesTheAccountUndecided() = runTest {
        val response = remoteDataSource.register("fresh@example.com", "Secret123", consent = null)

        val consent: AccountConsentDto? = response.dataOrFail().consent
        assertThat(consent?.status).isEqualTo("none")
    }

    // AC-5
    @Test
    fun aRegistrationWithAnUnknownTextVersionCreatesNoAccount() = runTest {
        val result = repository.register("fresh@example.com", "Secret123", ConsentChoice(true, 9, "en"))

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNPROCESSABLE))
        assertThat(repository.login("fresh@example.com", "Secret123"))
            .isEqualTo(Result.Failure(DataError.Remote.UNAUTHORIZED))
    }
}
