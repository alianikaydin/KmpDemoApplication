package com.anksoft.myapplication.features.auth.presentation.login

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.onFailure
import com.anksoft.myapplication.core.domain.onSuccess
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.debug
import com.anksoft.myapplication.core.presentation.UiText
import com.anksoft.myapplication.core.presentation.toUiText
import com.anksoft.myapplication.core.session.SessionExpiry
import com.anksoft.myapplication.features.auth.domain.UserDataValidator
import com.anksoft.myapplication.features.auth.domain.usecase.LoginUseCase
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.error_email_invalid
import myapplication.shared.generated.resources.error_email_required
import myapplication.shared.generated.resources.error_invalid_credentials
import myapplication.shared.generated.resources.error_password_required
import myapplication.shared.generated.resources.error_session_expired

data class LoginState(
    val email: String = "",
    val password: String = "",
    val emailError: UiText? = null,
    val passwordError: UiText? = null,
    /** Form-level error (bad credentials, offline, server). Field errors sit on the fields. */
    val formError: UiText? = null,
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false
) {
    /** AC-1.1: the button stays disabled until both fields have content. */
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isLoading
}

sealed interface LoginEvent {
    data class EmailChanged(val email: String) : LoginEvent
    data class PasswordChanged(val password: String) : LoginEvent
    data object EmailFocusLost : LoginEvent
    data object LoginClicked : LoginEvent
}

class LoginScreenModel(
    private val login: LoginUseCase,
    private val validator: UserDataValidator,
    private val logger: AppLogger,
    sessionExpiry: SessionExpiry
) : StateScreenModel<LoginState>(LoginState()) {

    init {
        // The session ended on its own (refresh token rejected): explain why Login is showing.
        if (sessionExpiry.consumeExpiredNotice()) {
            mutableState.update { it.copy(formError = UiText.Resource(Res.string.error_session_expired)) }
        }
    }

    fun onEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.EmailChanged -> mutableState.update {
                // Clear errors while typing so the user is not scolded mid-keystroke.
                it.copy(email = event.email, emailError = null, formError = null)
            }

            is LoginEvent.PasswordChanged -> mutableState.update {
                it.copy(password = event.password, passwordError = null, formError = null)
            }

            // AC-1.2: email format is validated on focus loss, not on every keystroke.
            LoginEvent.EmailFocusLost -> mutableState.update {
                val email = it.email
                if (email.isNotBlank() && !validator.isValidEmail(email)) {
                    it.copy(emailError = UiText.Resource(Res.string.error_email_invalid))
                } else {
                    it
                }
            }

            LoginEvent.LoginClicked -> submit()
        }
    }

    private fun submit() {
        // AC-1.7: ignore repeat taps while a request is in flight.
        if (state.value.isLoading) return

        val email = state.value.email.trim()
        val password = state.value.password

        val emailError = when {
            email.isBlank() -> UiText.Resource(Res.string.error_email_required)
            !validator.isValidEmail(email) -> UiText.Resource(Res.string.error_email_invalid)
            else -> null
        }
        val passwordError = if (password.isBlank()) {
            UiText.Resource(Res.string.error_password_required)
        } else {
            null
        }

        if (emailError != null || passwordError != null) {
            // Never log the field values.
            logger.debug(LogTags.AUTH) { "login blocked by validation" }
            mutableState.update {
                it.copy(emailError = emailError, passwordError = passwordError, formError = null)
            }
            return
        }

        screenModelScope.launch {
            mutableState.update { it.copy(isLoading = true, formError = null) }
            login(email, password)
                .onSuccess {
                    mutableState.update { it.copy(isLoading = false, isLoggedIn = true) }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            // AC-1.4: wrong credentials clears the password but keeps the email.
                            password = if (error == DataError.Remote.UNAUTHORIZED) "" else it.password,
                            formError = error.toLoginUiText()
                        )
                    }
                }
        }
    }

    /** UNAUTHORIZED means "bad credentials" here, so it does not use the generic mapping. */
    private fun DataError.toLoginUiText(): UiText =
        if (this == DataError.Remote.UNAUTHORIZED) {
            UiText.Resource(Res.string.error_invalid_credentials)
        } else {
            toUiText()
        }
}
