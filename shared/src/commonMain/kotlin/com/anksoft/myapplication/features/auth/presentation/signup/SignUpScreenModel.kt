package com.anksoft.myapplication.features.auth.presentation.signup

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.onFailure
import com.anksoft.myapplication.core.domain.onSuccess
import com.anksoft.myapplication.core.presentation.UiText
import com.anksoft.myapplication.core.presentation.toUiText
import com.anksoft.myapplication.features.auth.domain.PasswordError
import com.anksoft.myapplication.features.auth.domain.UserDataValidator
import com.anksoft.myapplication.features.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.error_email_already_registered
import myapplication.shared.generated.resources.error_email_invalid
import myapplication.shared.generated.resources.error_email_required
import myapplication.shared.generated.resources.error_password_no_digit
import myapplication.shared.generated.resources.error_password_no_uppercase
import myapplication.shared.generated.resources.error_password_required
import myapplication.shared.generated.resources.error_password_too_short
import myapplication.shared.generated.resources.error_passwords_do_not_match

data class SignUpState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val emailError: UiText? = null,
    val passwordError: UiText? = null,
    val confirmPasswordError: UiText? = null,
    val formError: UiText? = null,
    /** Unmet password rules, so the UI can show a live checklist (AC-2.2). */
    val unmetPasswordRules: List<PasswordError> = emptyList(),
    val isLoading: Boolean = false,
    val isSignedUp: Boolean = false
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() &&
            password.isNotBlank() &&
            confirmPassword.isNotBlank() &&
            !isLoading
}

sealed interface SignUpEvent {
    data class EmailChanged(val email: String) : SignUpEvent
    data class PasswordChanged(val password: String) : SignUpEvent
    data class ConfirmPasswordChanged(val confirmPassword: String) : SignUpEvent
    data object EmailFocusLost : SignUpEvent
    data object SignUpClicked : SignUpEvent
}

class SignUpScreenModel(
    private val repository: AuthRepository,
    private val validator: UserDataValidator
) : StateScreenModel<SignUpState>(SignUpState()) {

    fun onEvent(event: SignUpEvent) {
        when (event) {
            is SignUpEvent.EmailChanged -> mutableState.update {
                it.copy(email = event.email, emailError = null, formError = null)
            }

            // Password rules are recomputed on every keystroke to drive the checklist.
            is SignUpEvent.PasswordChanged -> mutableState.update {
                it.copy(
                    password = event.password,
                    unmetPasswordRules = validator.validatePassword(event.password),
                    passwordError = null,
                    formError = null
                )
            }

            is SignUpEvent.ConfirmPasswordChanged -> mutableState.update {
                it.copy(
                    confirmPassword = event.confirmPassword,
                    confirmPasswordError = null,
                    formError = null
                )
            }

            SignUpEvent.EmailFocusLost -> mutableState.update {
                if (it.email.isNotBlank() && !validator.isValidEmail(it.email)) {
                    it.copy(emailError = UiText.Resource(Res.string.error_email_invalid))
                } else {
                    it
                }
            }

            SignUpEvent.SignUpClicked -> submit()
        }
    }

    private fun submit() {
        if (state.value.isLoading) return

        val current = state.value
        val email = current.email.trim()

        val emailError = when {
            email.isBlank() -> UiText.Resource(Res.string.error_email_required)
            !validator.isValidEmail(email) -> UiText.Resource(Res.string.error_email_invalid)
            else -> null
        }
        val passwordError = when {
            current.password.isBlank() -> UiText.Resource(Res.string.error_password_required)
            else -> validator.validatePassword(current.password).firstOrNull()?.toUiText()
        }
        val confirmPasswordError = if (current.password != current.confirmPassword) {
            UiText.Resource(Res.string.error_passwords_do_not_match)
        } else {
            null
        }

        if (emailError != null || passwordError != null || confirmPasswordError != null) {
            mutableState.update {
                it.copy(
                    emailError = emailError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmPasswordError,
                    formError = null
                )
            }
            return
        }

        screenModelScope.launch {
            mutableState.update { it.copy(isLoading = true, formError = null) }
            repository.register(email.lowercase(), current.password)
                .onSuccess {
                    // AC-2.5: registration authenticates, no second login needed.
                    mutableState.update { it.copy(isLoading = false, isSignedUp = true) }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(isLoading = false, formError = error.toSignUpUiText())
                    }
                }
        }
    }

    /** CONFLICT means the email is already taken (AC-2.4). */
    private fun DataError.toSignUpUiText(): UiText =
        if (this == DataError.Remote.CONFLICT) {
            UiText.Resource(Res.string.error_email_already_registered)
        } else {
            toUiText()
        }
}

fun PasswordError.toUiText(): UiText = when (this) {
    PasswordError.TooShort -> UiText.Resource(Res.string.error_password_too_short)
    PasswordError.NoDigit -> UiText.Resource(Res.string.error_password_no_digit)
    PasswordError.NoUppercase -> UiText.Resource(Res.string.error_password_no_uppercase)
}
