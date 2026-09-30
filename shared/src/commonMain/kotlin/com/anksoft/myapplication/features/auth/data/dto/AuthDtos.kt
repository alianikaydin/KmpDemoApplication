package com.anksoft.myapplication.features.auth.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format, deliberately separate from the domain User so a backend contract
 * change does not ripple into domain/presentation. Shapes are a PROPOSAL --
 * no auth backend contract exists yet (see plan section 1.7). When the real
 * contract lands, only this file, AuthMapper and AuthRemoteDataSource change.
 */

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String
)

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: UserDto
)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val name: String? = null
)
