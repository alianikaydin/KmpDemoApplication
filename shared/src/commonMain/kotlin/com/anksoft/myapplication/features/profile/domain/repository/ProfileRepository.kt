package com.anksoft.myapplication.features.profile.domain.repository

import com.anksoft.myapplication.features.profile.domain.model.AccountProfile
import com.anksoft.myapplication.features.profile.domain.model.ProfileState
import kotlinx.coroutines.flow.StateFlow

interface ProfileRepository {
    /** The profile area of the signed-in account (AC-1..AC-9). */
    val state: StateFlow<ProfileState>

    /**
     * Loads the cache if it is not loaded yet, then fetches the profile in the application scope.
     * Joins a refresh that is already running; does nothing without a session. Never throws.
     */
    fun refresh()

    /**
     * Shows and stores the profile from a sign-in or registration response (AC-2). Dropped when
     * the session changed meanwhile (AC-13). Never throws.
     */
    suspend fun saveSignedInProfile(profile: AccountProfile)
}
