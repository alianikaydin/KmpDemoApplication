package com.anksoft.myapplication.features.auth

import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.features.auth.domain.model.User
import com.anksoft.myapplication.features.auth.domain.repository.AuthRepository
import kotlinx.coroutines.CompletableDeferred

class FakeAuthRepository : AuthRepository {

    var loginResult: Result<User, DataError> =
        Result.Success(User(id = "1", email = "user@example.com", name = "Test User"))

    var registerResult: Result<User, DataError> =
        Result.Success(User(id = "1", email = "user@example.com", name = "Test User"))

    /** Set to suspend login indefinitely so in-flight behaviour can be asserted. */
    var loginGate: CompletableDeferred<Unit>? = null

    val loginCalls = mutableListOf<Pair<String, String>>()
    val registerCalls = mutableListOf<Pair<String, String>>()
    var logoutCallCount = 0

    override suspend fun login(email: String, password: String): Result<User, DataError> {
        loginCalls += email to password
        loginGate?.await()
        return loginResult
    }

    override suspend fun register(email: String, password: String): Result<User, DataError> {
        registerCalls += email to password
        return registerResult
    }

    override suspend fun logout() {
        logoutCallCount++
    }

    override suspend fun getCurrentUser(): User? = null
}
