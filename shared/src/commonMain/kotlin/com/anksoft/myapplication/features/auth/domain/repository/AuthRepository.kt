package com.anksoft.myapplication.features.auth.domain.repository

import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.features.auth.domain.model.User

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<User, DataError>
    suspend fun register(email: String, password: String): Result<User, DataError>
    suspend fun logout()
    suspend fun getCurrentUser(): User?
}
