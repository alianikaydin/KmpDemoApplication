package com.anksoft.myapplication.features.auth.data.datasource

import com.anksoft.kmpdemo.contract.auth.AuthPaths
import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.kmpdemo.contract.auth.LoginRequestDto
import com.anksoft.kmpdemo.contract.auth.RegisterRequestDto
import com.anksoft.kmpdemo.contract.consent.ConsentDecisionDto
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.network.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Owns the HttpClient and the auth endpoint paths. Previously AuthRepositoryImpl
 * took an HttpClient and never used it; the client lives here now.
 */
class AuthRemoteDataSource(
    private val httpClient: HttpClient,
    private val logger: AppLogger
) {

    suspend fun login(email: String, password: String): Result<AuthResponseDto, DataError.Remote> =
        safeCall(logger, PATH_LOGIN) {
            httpClient.post(PATH_LOGIN) {
                setBody(LoginRequestDto(email = email, password = password))
            }
        }

    /** A null [consent] is not sent at all (the JSON encoder omits defaults), like an older client. */
    suspend fun register(
        email: String,
        password: String,
        consent: ConsentDecisionDto?
    ): Result<AuthResponseDto, DataError.Remote> =
        safeCall(logger, PATH_REGISTER) {
            httpClient.post(PATH_REGISTER) {
                setBody(RegisterRequestDto(email = email, password = password, consent = consent))
            }
        }

    companion object {
        const val PATH_LOGIN = AuthPaths.LOGIN
        const val PATH_REGISTER = AuthPaths.REGISTER
    }
}
