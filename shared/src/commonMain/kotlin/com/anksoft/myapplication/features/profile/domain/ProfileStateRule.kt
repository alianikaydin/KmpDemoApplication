package com.anksoft.myapplication.features.profile.domain

import com.anksoft.myapplication.features.profile.domain.model.AccountProfile
import com.anksoft.myapplication.features.profile.domain.model.ProfileState

/**
 * The one rule that turns what is known into what the profile area shows: local first, the
 * network only refreshes it, and a failed refresh never hides a cached profile.
 */
fun profileStateOf(
    signedIn: Boolean,
    cached: AccountProfile?,
    isRefreshing: Boolean,
    lastRefreshFailed: Boolean,
): ProfileState = when {
    !signedIn -> ProfileState.SignedOut // AC-9, AC-10
    cached != null -> ProfileState.Available(cached, isStale = lastRefreshFailed) // AC-3, AC-5, AC-6
    lastRefreshFailed && !isRefreshing -> ProfileState.Unavailable // AC-7
    else -> ProfileState.Loading // AC-4, AC-8 (retry from empty)
}
