package com.anksoft.myapplication.features.update.domain

import com.anksoft.myapplication.features.update.domain.model.AppVersion
import com.anksoft.myapplication.features.update.domain.model.UpdateRequirement
import com.anksoft.myapplication.features.update.domain.model.VersionPolicy

/**
 * Chooses what to show for the [installed] version. A required update wins over an optional one
 * (AC-12). An optional update is skipped when the user already said "later" for this [VersionPolicy.latest]
 * or a newer one, so a newer latest asks once more (AC-8, AC-9).
 */
fun decideUpdate(
    installed: AppVersion,
    policy: VersionPolicy,
    dismissedLatest: AppVersion?,
): UpdateRequirement = when {
    installed < policy.minSupported -> UpdateRequirement.Required
    installed < policy.latest && (dismissedLatest == null || dismissedLatest < policy.latest) ->
        UpdateRequirement.Optional(policy.latest)
    else -> UpdateRequirement.None
}
