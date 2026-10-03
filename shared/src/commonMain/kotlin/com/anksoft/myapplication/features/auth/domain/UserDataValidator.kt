package com.anksoft.myapplication.features.auth.domain

import com.anksoft.kmpdemo.contract.auth.CredentialRules
import com.anksoft.kmpdemo.contract.auth.PasswordRule
import com.anksoft.myapplication.core.domain.Error

sealed interface PasswordError : Error {
    data object TooShort : PasswordError
    data object NoDigit : PasswordError
    data object NoUppercase : PasswordError
}

/**
 * Delegates to [CredentialRules] from the contract library so the app and the
 * backend always agree on what a valid e-mail and password are. The library is
 * pure Kotlin, so this stays unit-testable in commonTest with no platform deps.
 */
class UserDataValidator {

    fun isValidEmail(email: String): Boolean = CredentialRules.isValidEmail(email.trim())

    /**
     * Returns every unmet rule, so the UI can render a live checklist rather than one error at a time.
     * The server-only maximum length (TOO_LONG) is not surfaced as a checklist item.
     */
    fun validatePassword(password: String): List<PasswordError> {
        val violations = CredentialRules.passwordViolations(password)
        return buildList {
            if (PasswordRule.TOO_SHORT in violations) add(PasswordError.TooShort)
            if (PasswordRule.NO_DIGIT in violations) add(PasswordError.NoDigit)
            if (PasswordRule.NO_UPPERCASE in violations) add(PasswordError.NoUppercase)
        }
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = CredentialRules.MIN_PASSWORD_LENGTH
    }
}
