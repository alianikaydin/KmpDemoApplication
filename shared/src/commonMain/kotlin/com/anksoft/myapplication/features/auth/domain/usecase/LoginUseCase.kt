package com.anksoft.myapplication.features.auth.domain.usecase

import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.features.auth.domain.model.User
import com.anksoft.myapplication.features.auth.domain.repository.AuthRepository

/**
 * Normalises the email before it reaches the network so "  Bob@Example.COM "
 * and "bob@example.com" authenticate as the same account.
 */
class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): Result<User, DataError> =
        repository.login(
            email = email.trim().lowercase(),
            password = password
        )
}
