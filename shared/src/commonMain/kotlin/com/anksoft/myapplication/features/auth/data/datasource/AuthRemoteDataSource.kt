package com.anksoft.myapplication.features.auth.data.datasource

import com.anksoft.kmpdemo.contract.auth.AuthPaths
import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.kmpdemo.contract.auth.LoginRequestDto
import com.anksoft.kmpdemo.contract.auth.RegisterRequestDto
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.network.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Owns the HttpClient and the auth endpoint paths. Previously AuthRepositoryImpl
 * took an HttpClient and never used it; the client lives here now.
 */
class AuthRemoteDataSource(private val httpClient: HttpClient) {

    suspend fun login(email: String, password: String): Result<AuthResponseDto, DataError.Remote> =
        safeCall {
            httpClient.post(PATH_LOGIN) {
                setBody(LoginRequestDto(email = email, password = password))
            }
        }

    suspend fun register(email: String, password: String): Result<AuthResponseDto, DataError.Remote> =
        safeCall {
            httpClient.post(PATH_REGISTER) {
                setBody(RegisterRequestDto(email = email, password = password))
            }
        }

    companion object {
        const val PATH_LOGIN = AuthPaths.LOGIN
        const val PATH_REGISTER = AuthPaths.REGISTER
    }
}
