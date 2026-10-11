package com.anksoft.myapplication.features.auth.domain.repository

import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.features.auth.domain.model.User
import com.anksoft.myapplication.features.consent.domain.model.ConsentChoice

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<User, DataError>

    /**
     * Creates the account and signs in. [consent] is the decision the user made on the form, or
     * null when the consent text could not be shown: then no decision is sent and the account
     * starts without one (AC-4).
     */
    suspend fun register(email: String, password: String, consent: ConsentChoice?): Result<User, DataError>

    suspend fun logout()
    suspend fun getCurrentUser(): User?
}
