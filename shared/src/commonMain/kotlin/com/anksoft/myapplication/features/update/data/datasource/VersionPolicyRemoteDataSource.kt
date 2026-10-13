package com.anksoft.myapplication.features.update.data.datasource

import com.anksoft.kmpdemo.contract.app.VersionPolicyDto
import com.anksoft.kmpdemo.contract.app.VersionPolicyPaths
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.network.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/** The version policy endpoint. The path comes from the contract and is relative to the base URL. */
class VersionPolicyRemoteDataSource(
    private val httpClient: HttpClient,
    private val logger: AppLogger,
) {

    /** Public endpoint: the HTTP client sends no Authorization header to it (AC-21). */
    suspend fun getVersionPolicy(platform: String): Result<VersionPolicyDto, DataError.Remote> =
        safeCall(logger, VersionPolicyPaths.VERSION_POLICY) {
            httpClient.get(VersionPolicyPaths.VERSION_POLICY) {
                parameter(VersionPolicyPaths.PLATFORM_PARAM, platform)
            }
        }
}
