package com.anksoft.myapplication.features.auth.data.repository

import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.domain.map
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.auth.data.datasource.AuthRemoteDataSource
import com.anksoft.myapplication.features.auth.data.dto.AuthResponseDto
import com.anksoft.myapplication.features.auth.data.mapper.toUser
import com.anksoft.myapplication.features.auth.domain.model.User
import com.anksoft.myapplication.features.auth.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val remoteDataSource: AuthRemoteDataSource,
    private val sessionManager: SessionManager
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<User, DataError> =
        remoteDataSource.login(email, password).map { it.persistSession() }

    override suspend fun register(email: String, password: String): Result<User, DataError> =
        remoteDataSource.register(email, password).map { it.persistSession() }

    override suspend fun logout() {
        sessionManager.clear()
    }

    override suspend fun getCurrentUser(): User? {
        val id = sessionManager.getUserId() ?: return null
        val email = sessionManager.getUserEmail() ?: return null
        return User(id = id, email = email, name = sessionManager.getUserName())
    }

    /**
     * Persists tokens and the identity needed to rehydrate getCurrentUser() on
     * next launch. The password is never stored (AC-1.8).
     */
    private fun AuthResponseDto.persistSession(): User {
        sessionManager.saveToken(accessToken)
        refreshToken?.let(sessionManager::saveRefreshToken)
        val domainUser = user.toUser()
        sessionManager.saveUserId(domainUser.id)
        sessionManager.saveUserEmail(domainUser.email)
        domainUser.name?.let(sessionManager::saveUserName)
        return domainUser
    }
}
