package com.anksoft.myapplication.features.update

import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.features.update.domain.model.UpdatePlatform
import com.anksoft.myapplication.features.update.domain.model.VersionPolicy
import com.anksoft.myapplication.features.update.domain.repository.VersionPolicyRepository

/** Answers with [response] and records every call. */
class FakeVersionPolicyRepository(
    var response: Result<VersionPolicy, DataError.Remote> = Result.Failure(DataError.Remote.UNKNOWN),
) : VersionPolicyRepository {

    val requestedPlatforms = mutableListOf<UpdatePlatform>()
    val callCount: Int get() = requestedPlatforms.size

    override suspend fun getVersionPolicy(platform: UpdatePlatform): Result<VersionPolicy, DataError.Remote> {
        requestedPlatforms += platform
        return response
    }
}
