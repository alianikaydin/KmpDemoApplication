package com.anksoft.myapplication.features.profile

import com.anksoft.myapplication.features.profile.domain.model.AccountProfile
import com.anksoft.myapplication.features.profile.domain.model.ProfileState
import com.anksoft.myapplication.features.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [ProfileRepository] for screen and sign-in tests: the test sets [state] and reads the recorded calls. */
class FakeProfileRepository : ProfileRepository {

    override val state: MutableStateFlow<ProfileState> = MutableStateFlow(ProfileState.Loading)

    var refreshCalls: Int = 0
        private set

    val savedProfiles: MutableList<AccountProfile> = mutableListOf()

    override fun refresh() {
        refreshCalls++
    }

    override suspend fun saveSignedInProfile(profile: AccountProfile) {
        savedProfiles += profile
    }
}
