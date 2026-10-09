package com.anksoft.myapplication.features.consent.data.datasource

import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.consent.domain.model.AccountConsent
import com.anksoft.myapplication.features.consent.domain.model.ConsentDecision
import com.anksoft.kmpdemo.contract.consent.ConsentStatus as WireConsentStatus

/**
 * The cached decision of the signed-in account, kept in the session store so that logging out
 * (and a reinstall on iOS) removes it with the rest of the session. Only the status and the text
 * version are stored (AC-27); never the user, the language or the time.
 */
class ConsentLocalDataSource(private val sessionManager: SessionManager) {

    /** Null when nothing is cached, or when the cached value is not one this app wrote. */
    fun read(): AccountConsent? {
        val decision = when (sessionManager.getConsentStatus()) {
            WireConsentStatus.GRANTED -> ConsentDecision.GRANTED
            WireConsentStatus.DENIED -> ConsentDecision.DENIED
            WireConsentStatus.NONE -> ConsentDecision.NONE
            else -> return null
        }
        return AccountConsent(decision = decision, textVersion = sessionManager.getConsentTextVersion())
    }

    /** Caches [consent] for the session that is stored right now (the sign-in just stored it). */
    fun write(consent: AccountConsent) {
        val stored = consent.toStored()
        sessionManager.saveConsent(stored.status, stored.textVersion)
    }

    /** Caches [consent] only if the session of [userId] is still the stored one. */
    fun writeForUser(userId: String, consent: AccountConsent): Boolean {
        val stored = consent.toStored()
        return sessionManager.saveConsentForUser(userId, stored.status, stored.textVersion)
    }

    fun clear() = sessionManager.clearConsent()

    private class Stored(val status: String, val textVersion: Int?)

    /**
     * A grant that a newer text wants renewed is cached as "no decision", so the prompt comes
     * back after a restart instead of the old grant being trusted (AC-23).
     */
    private fun AccountConsent.toStored(): Stored = when {
        decision == ConsentDecision.GRANTED && !reconsentRequired ->
            Stored(WireConsentStatus.GRANTED, textVersion)

        decision == ConsentDecision.DENIED -> Stored(WireConsentStatus.DENIED, textVersion)
        else -> Stored(WireConsentStatus.NONE, null)
    }
}
