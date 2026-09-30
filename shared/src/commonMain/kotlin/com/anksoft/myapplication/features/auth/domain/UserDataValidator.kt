package com.anksoft.myapplication.features.auth.domain

import com.anksoft.myapplication.core.domain.Error

sealed interface PasswordError : Error {
    data object TooShort : PasswordError
    data object NoDigit : PasswordError
    data object NoUppercase : PasswordError
}

/**
 * Pure Kotlin so it is directly unit-testable in commonTest with no platform deps.
 * Rules per the validation table: >= 8 chars, >= 1 digit, >= 1 uppercase.
 */
class UserDataValidator {

    fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

    /** Returns every unmet rule, so the UI can render a live checklist rather than one error at a time. */
    fun validatePassword(password: String): List<PasswordError> = buildList {
        if (password.length < MIN_PASSWORD_LENGTH) add(PasswordError.TooShort)
        if (password.none { it.isDigit() }) add(PasswordError.NoDigit)
        if (password.none { it.isUpperCase() }) add(PasswordError.NoUppercase)
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8

        // Pragmatic rather than RFC-complete: one @, no whitespace, a dotted TLD of >= 2 chars.
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9!#\$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#\$%&'*+/=?^_`{|}~-]+)*@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*\.[A-Za-z]{2,}\$")
    }
}
