package com.anksoft.myapplication.features.profile.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.anksoft.myapplication.features.profile.domain.model.AccountProfile
import com.anksoft.myapplication.features.profile.domain.model.ProfileState
import kotlin.test.Test

class ProfileStateRuleTest {

    private val profile = AccountProfile(userId = "u1", email = "user@example.com", name = "User")

    // AC-9, AC-10
    @Test
    fun withoutSessionNothingIsShownEvenIfSomethingIsCached() {
        val state = profileStateOf(
            signedIn = false,
            cached = profile,
            isRefreshing = true,
            lastRefreshFailed = true,
        )
        assertThat(state).isEqualTo(ProfileState.SignedOut)
    }

    // AC-3, AC-6
    @Test
    fun cachedProfileIsShownAsCurrentWhileNoRefreshHasFailed() {
        val state = profileStateOf(
            signedIn = true,
            cached = profile,
            isRefreshing = false,
            lastRefreshFailed = false,
        )
        assertThat(state).isEqualTo(ProfileState.Available(profile, isStale = false))
    }

    // AC-3
    @Test
    fun cachedProfileStaysCurrentWhileARefreshRuns() {
        val state = profileStateOf(
            signedIn = true,
            cached = profile,
            isRefreshing = true,
            lastRefreshFailed = false,
        )
        assertThat(state).isEqualTo(ProfileState.Available(profile, isStale = false))
    }

    // AC-5
    @Test
    fun cachedProfileIsStaleAfterAFailedRefresh() {
        val state = profileStateOf(
            signedIn = true,
            cached = profile,
            isRefreshing = false,
            lastRefreshFailed = true,
        )
        assertThat(state).isEqualTo(ProfileState.Available(profile, isStale = true))
    }

    // AC-5, AC-8
    @Test
    fun cachedProfileStaysStaleWhileARetryRuns() {
        val state = profileStateOf(
            signedIn = true,
            cached = profile,
            isRefreshing = true,
            lastRefreshFailed = true,
        )
        assertThat(state).isEqualTo(ProfileState.Available(profile, isStale = true))
    }

    // AC-7
    @Test
    fun withoutCachedProfileAFailedRefreshIsUnavailable() {
        val state = profileStateOf(
            signedIn = true,
            cached = null,
            isRefreshing = false,
            lastRefreshFailed = true,
        )
        assertThat(state).isEqualTo(ProfileState.Unavailable)
    }

    // AC-8
    @Test
    fun retryFromAnEmptyCacheShowsLoadingAgain() {
        val state = profileStateOf(
            signedIn = true,
            cached = null,
            isRefreshing = true,
            lastRefreshFailed = true,
        )
        assertThat(state).isEqualTo(ProfileState.Loading)
    }

    // AC-4
    @Test
    fun withoutCachedProfileAndWithoutFailureTheProfileIsLoading() {
        val state = profileStateOf(
            signedIn = true,
            cached = null,
            isRefreshing = false,
            lastRefreshFailed = false,
        )
        assertThat(state).isEqualTo(ProfileState.Loading)
    }
}
