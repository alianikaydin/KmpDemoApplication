package com.anksoft.myapplication.features.update.data.repository

import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.warn
import com.anksoft.myapplication.features.update.data.datasource.VersionPolicyRemoteDataSource
import com.anksoft.myapplication.features.update.data.mapper.toContractValue
import com.anksoft.myapplication.features.update.data.mapper.toVersionPolicyOrNull
import com.anksoft.myapplication.features.update.domain.model.UpdatePlatform
import com.anksoft.myapplication.features.update.domain.model.VersionPolicy
import com.anksoft.myapplication.features.update.domain.repository.VersionPolicyRepository

class VersionPolicyRepositoryImpl(
    private val remote: VersionPolicyRemoteDataSource,
    private val logger: AppLogger,
) : VersionPolicyRepository {

    override suspend fun getVersionPolicy(platform: UpdatePlatform): Result<VersionPolicy, DataError.Remote> =
        when (val result = remote.getVersionPolicy(platform.toContractValue())) {
            is Result.Failure -> result
            is Result.Success -> {
                val policy = result.data.toVersionPolicyOrNull()
                if (policy == null) {
                    // Neither the values nor the body are logged (AC-15).
                    logger.warn(LogTags.UPDATE) { "Version policy ignored: invalid version value" }
                    Result.Failure(DataError.Remote.SERIALIZATION)
                } else {
                    Result.Success(policy)
                }
            }
        }
}
