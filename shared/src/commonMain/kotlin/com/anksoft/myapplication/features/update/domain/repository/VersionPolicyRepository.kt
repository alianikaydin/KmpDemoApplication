package com.anksoft.myapplication.features.update.domain.repository

import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.features.update.domain.model.UpdatePlatform
import com.anksoft.myapplication.features.update.domain.model.VersionPolicy

interface VersionPolicyRepository {
    /** Fetches the policy for [platform]. Needs no session; an unusable answer is a failure. */
    suspend fun getVersionPolicy(platform: UpdatePlatform): Result<VersionPolicy, DataError.Remote>
}
