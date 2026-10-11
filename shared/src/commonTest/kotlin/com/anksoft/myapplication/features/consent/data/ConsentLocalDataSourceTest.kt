package com.anksoft.myapplication.features.consent.data

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.consent.data.datasource.ConsentLocalDataSource
import com.anksoft.myapplication.features.consent.domain.model.AccountConsent
import com.anksoft.myapplication.features.consent.domain.model.ConsentDecision
import com.russhwolf.settings.MapSettings
import kotlin.test.Test

class ConsentLocalDataSourceTest {

    private val settings = MapSettings()
    private val session = SessionManager(settings)
    private val local = ConsentLocalDataSource(session)

    // AC-27
    @Test
    fun nothingIsCachedBeforeTheFirstWrite() {
        assertThat(local.read()).isNull()
    }

    // AC-6
    @Test
    fun everyDecisionIsReadBackAsItWasWritten() {
        listOf(
            AccountConsent(ConsentDecision.GRANTED, textVersion = 2),
            AccountConsent(ConsentDecision.DENIED, textVersion = 1),
            AccountConsent(ConsentDecision.NONE, textVersion = null)
        ).forEach { consent ->
            local.write(consent)

            assertThat(local.read()).isEqualTo(consent)
        }
    }

    // AC-23
    @Test
    fun aGrantThatNeedsReconsentIsCachedAsNoDecisionSoThePromptReturnsAfterARestart() {
        local.write(AccountConsent(ConsentDecision.GRANTED, textVersion = 1, reconsentRequired = true))

        assertThat(local.read()).isEqualTo(AccountConsent(ConsentDecision.NONE, textVersion = null))
    }

    // AC-27
    @Test
    fun onlyTheStatusAndTheVersionAreWritten() {
        local.write(AccountConsent(ConsentDecision.GRANTED, textVersion = 2))

        assertThat(settings.keys).isEqualTo(
            setOf(SessionManager.KEY_CONSENT_STATUS, SessionManager.KEY_CONSENT_TEXT_VERSION)
        )
    }

    // AC-25
    @Test
    fun aCachedValueThisAppDidNotWriteIsIgnored() {
        session.saveConsent("withdrawn", 1)

        assertThat(local.read()).isNull()
    }

    // AC-18
    @Test
    fun clearRemovesTheCachedDecision() {
        local.write(AccountConsent(ConsentDecision.GRANTED, textVersion = 2))

        local.clear()

        assertThat(local.read()).isNull()
        assertThat(settings.keys).isEmpty()
    }

    // AC-18
    @Test
    fun anAnswerForAnotherUsersSessionIsNotCached() {
        session.saveToken("access")
        session.saveUserId("u1")

        val stored = local.writeForUser("u2", AccountConsent(ConsentDecision.GRANTED, textVersion = 1))

        assertThat(stored).isFalse()
        assertThat(local.read()).isNull()
    }

    @Test
    fun anAnswerForTheStoredSessionIsCached() {
        session.saveToken("access")
        session.saveUserId("u1")

        val stored = local.writeForUser("u1", AccountConsent(ConsentDecision.DENIED, textVersion = 1))

        assertThat(stored).isTrue()
        assertThat(local.read()).isEqualTo(AccountConsent(ConsentDecision.DENIED, textVersion = 1))
    }
}
