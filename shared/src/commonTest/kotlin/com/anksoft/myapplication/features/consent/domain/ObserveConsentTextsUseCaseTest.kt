package com.anksoft.myapplication.features.consent.domain

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.preferences.AppLanguage
import com.anksoft.myapplication.core.preferences.ContentLanguage
import com.anksoft.myapplication.core.preferences.FakeAppPreferences
import com.anksoft.myapplication.features.consent.FakeConsentRepository
import com.anksoft.myapplication.features.consent.domain.usecase.ConsentTextsLoad
import com.anksoft.myapplication.features.consent.domain.usecase.ObserveConsentTextsUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ObserveConsentTextsUseCaseTest {

    private val repository = FakeConsentRepository()
    private val preferences = FakeAppPreferences()
    private val retry = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val useCase = ObserveConsentTextsUseCase(
        repository = repository,
        contentLanguage = ContentLanguage(preferences, deviceLanguage = { "en" })
    )

    private val englishTexts = FakeConsentRepository.texts(language = "en")
    private val turkishTexts = FakeConsentRepository.texts(language = "tr")

    // AC-4, AC-21
    @Test
    fun startsLoadingAndThenDeliversTheTextForTheCurrentLanguage() = runTest {
        useCase(retry).test {
            assertThat(awaitItem()).isEqualTo(ConsentTextsLoad.Loading)
            assertThat(awaitItem()).isEqualTo(ConsentTextsLoad.Loaded(englishTexts))
        }
        assertThat(repository.textRequests).isEqualTo(listOf("en"))
    }

    // AC-4
    @Test
    fun aFailureIsReportedWithItsError() = runTest {
        repository.textsProvider = { Result.Failure(DataError.Remote.NO_INTERNET) }

        useCase(retry).test {
            assertThat(awaitItem()).isEqualTo(ConsentTextsLoad.Loading)
            assertThat(awaitItem()).isEqualTo(ConsentTextsLoad.Failed(DataError.Remote.NO_INTERNET))
        }
    }

    // AC-4
    @Test
    fun retryLoadsTheTextAgainAndCanSucceed() = runTest {
        repository.textsProvider = { Result.Failure(DataError.Remote.SERVER_ERROR) }

        useCase(retry).test {
            awaitItem()
            assertThat(awaitItem()).isEqualTo(ConsentTextsLoad.Failed(DataError.Remote.SERVER_ERROR))

            repository.textsProvider = { tag -> Result.Success(FakeConsentRepository.texts(language = tag)) }
            retry.tryEmit(Unit)

            assertThat(awaitItem()).isEqualTo(ConsentTextsLoad.Loading)
            assertThat(awaitItem()).isEqualTo(ConsentTextsLoad.Loaded(englishTexts))
        }
        assertThat(repository.textRequests).isEqualTo(listOf("en", "en"))
    }

    // AC-24
    @Test
    fun changingTheLanguageLoadsTheTextAgainInTheNewLanguage() = runTest {
        useCase(retry).test {
            awaitItem()
            assertThat(awaitItem()).isEqualTo(ConsentTextsLoad.Loaded(englishTexts))

            preferences.setLanguage(AppLanguage.TURKISH)

            assertThat(awaitItem()).isEqualTo(ConsentTextsLoad.Loading)
            assertThat(awaitItem()).isEqualTo(ConsentTextsLoad.Loaded(turkishTexts))
        }
        assertThat(repository.textRequests).isEqualTo(listOf("en", "tr"))
    }

    // AC-24
    @Test
    fun aLateAnswerInThePreviousLanguageNeverReplacesTheCurrentOne() = runTest {
        val englishGate = CompletableDeferred<Unit>()
        repository.textsGate = englishGate

        useCase(retry).test {
            assertThat(awaitItem()).isEqualTo(ConsentTextsLoad.Loading)

            // The English request is still open when the user switches to Turkish.
            repository.textsGate = null
            preferences.setLanguage(AppLanguage.TURKISH)
            assertThat(awaitItem()).isEqualTo(ConsentTextsLoad.Loading)
            assertThat(awaitItem()).isEqualTo(ConsentTextsLoad.Loaded(turkishTexts))

            englishGate.complete(Unit)
            expectNoEvents()
        }
    }
}
