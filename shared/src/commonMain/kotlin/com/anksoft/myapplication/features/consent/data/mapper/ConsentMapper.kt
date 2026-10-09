package com.anksoft.myapplication.features.consent.data.mapper

import com.anksoft.kmpdemo.contract.consent.AccountConsentDto
import com.anksoft.kmpdemo.contract.consent.ConsentDecisionDto
import com.anksoft.kmpdemo.contract.consent.ConsentTextsDto
import com.anksoft.myapplication.features.consent.domain.model.AccountConsent
import com.anksoft.myapplication.features.consent.domain.model.ConsentChoice
import com.anksoft.myapplication.features.consent.domain.model.ConsentDecision
import com.anksoft.myapplication.features.consent.domain.model.ConsentTexts
import com.anksoft.kmpdemo.contract.consent.ConsentStatus as WireConsentStatus

fun ConsentTextsDto.toDomain(): ConsentTexts = ConsentTexts(
    version = version,
    language = language,
    label = label,
    description = description,
    policyUrl = policyUrl
)

/** A status this app does not know is read as "no decision", which switches collection off (AC-25). */
fun AccountConsentDto.toDomain(): AccountConsent = AccountConsent(
    decision = when (status) {
        WireConsentStatus.GRANTED -> ConsentDecision.GRANTED
        WireConsentStatus.DENIED -> ConsentDecision.DENIED
        else -> ConsentDecision.NONE
    },
    textVersion = textVersion,
    reconsentRequired = reconsentRequired
)

fun ConsentChoice.toDto(): ConsentDecisionDto = ConsentDecisionDto(
    status = if (granted) WireConsentStatus.GRANTED else WireConsentStatus.DENIED,
    textVersion = textVersion,
    textLanguage = textLanguage
)
