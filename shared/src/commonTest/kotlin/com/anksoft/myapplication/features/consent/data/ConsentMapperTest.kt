package com.anksoft.myapplication.features.consent.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.anksoft.kmpdemo.contract.consent.AccountConsentDto
import com.anksoft.kmpdemo.contract.consent.ConsentDecisionDto
import com.anksoft.kmpdemo.contract.consent.ConsentTextsDto
import com.anksoft.myapplication.features.consent.data.mapper.toDomain
import com.anksoft.myapplication.features.consent.data.mapper.toDto
import com.anksoft.myapplication.features.consent.domain.model.AccountConsent
import com.anksoft.myapplication.features.consent.domain.model.ConsentChoice
import com.anksoft.myapplication.features.consent.domain.model.ConsentDecision
import com.anksoft.myapplication.features.consent.domain.model.ConsentTexts
import kotlin.test.Test

class ConsentMapperTest {

    private fun account(status: String, reconsentRequired: Boolean = false) = AccountConsentDto(
        status = status,
        textVersion = 3,
        textLanguage = "tr",
        decidedAt = "2026-10-09T10:00:00Z",
        reconsentRequired = reconsentRequired
    )

    // AC-21
    @Test
    fun consentTextsKeepEveryFieldTheBackendSent() {
        val dto = ConsentTextsDto(
            version = 2,
            language = "tr",
            label = "Etiket",
            description = "Açıklama",
            policyUrl = "https://example.com/privacy"
        )

        assertThat(dto.toDomain()).isEqualTo(
            ConsentTexts(2, "tr", "Etiket", "Açıklama", "https://example.com/privacy")
        )
    }

    // AC-25
    @Test
    fun knownStatusValuesMapToTheirDecision() {
        assertThat(account("granted").toDomain().decision).isEqualTo(ConsentDecision.GRANTED)
        assertThat(account("denied").toDomain().decision).isEqualTo(ConsentDecision.DENIED)
        assertThat(account("none").toDomain().decision).isEqualTo(ConsentDecision.NONE)
    }

    // AC-25: a value from a newer backend must switch collection off, not crash or grant.
    @Test
    fun anUnknownStatusIsReadAsNoDecision() {
        assertThat(account("withdrawn").toDomain().decision).isEqualTo(ConsentDecision.NONE)
        assertThat(account("").toDomain().decision).isEqualTo(ConsentDecision.NONE)
        assertThat(account("GRANTED").toDomain().decision).isEqualTo(ConsentDecision.NONE)
    }

    // AC-23
    @Test
    fun theVersionAndTheReconsentFlagAreCarriedOver() {
        assertThat(account("granted", reconsentRequired = true).toDomain())
            .isEqualTo(AccountConsent(ConsentDecision.GRANTED, textVersion = 3, reconsentRequired = true))
    }

    // AC-22
    @Test
    fun aChoiceBecomesARequestWithTheShownTextsVersionAndLanguage() {
        assertThat(ConsentChoice(granted = true, textVersion = 3, textLanguage = "tr").toDto())
            .isEqualTo(ConsentDecisionDto(status = "granted", textVersion = 3, textLanguage = "tr"))
        assertThat(ConsentChoice(granted = false, textVersion = 1, textLanguage = "en").toDto())
            .isEqualTo(ConsentDecisionDto(status = "denied", textVersion = 1, textLanguage = "en"))
    }
}
