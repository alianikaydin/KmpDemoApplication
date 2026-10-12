package com.anksoft.myapplication.features.profile.domain.model

/** What the profile area shows. Never holds another account's data (AC-12). */
sealed interface ProfileState {
    /** No session: nothing to show. */
    data object SignedOut : ProfileState

    /** Nothing cached yet and the profile is being fetched. */
    data object Loading : ProfileState

    /** A profile is shown. [isStale] is true when the latest refresh failed, so it may be out of date. */
    data class Available(val profile: AccountProfile, val isStale: Boolean) : ProfileState

    /** Nothing cached and the fetch failed. */
    data object Unavailable : ProfileState
}
