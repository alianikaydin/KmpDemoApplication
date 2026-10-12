package com.anksoft.myapplication.features.profile.data.datasource

import com.anksoft.kmpdemo.contract.auth.AuthPaths
import com.anksoft.kmpdemo.contract.auth.UserDto
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.network.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.request.get

/** The account profile endpoint. The path comes from the contract and is relative to the base URL. */
class ProfileRemoteDataSource(
    private val httpClient: HttpClient,
    private val logger: AppLogger,
) {

    /** Protected endpoint: the bearer token is attached and a 401 starts the usual refresh. */
    suspend fun me(): Result<UserDto, DataError.Remote> =
        safeCall(logger, AuthPaths.ME) { httpClient.get(AuthPaths.ME) }
}
