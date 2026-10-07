package com.anksoft.myapplication.features.auth.presentation.login

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.RecordingLogWriter
import com.anksoft.myapplication.core.logging.recordingLogger
import com.anksoft.myapplication.core.presentation.UiText
import com.anksoft.myapplication.features.auth.FakeAuthRepository
import com.anksoft.myapplication.features.auth.domain.UserDataValidator
import com.anksoft.myapplication.features.auth.domain.usecase.LoginUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.error_email_invalid
import myapplication.shared.generated.resources.error_email_required
import myapplication.shared.generated.resources.error_invalid_credentials
import myapplication.shared.generated.resources.error_no_internet
import myapplication.shared.generated.resources.error_password_required
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class LoginScreenModelTest {

    private lateinit var repository: FakeAuthRepository
    private lateinit var model: LoginScreenModel
    private lateinit var logWriter: RecordingLogWriter

    @BeforeTest
    fun setUp() {
        // screenModelScope is Main-dispatched, so Main must be replaced.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeAuthRepository()
        val (logger, writer) = recordingLogger()
        logWriter = writer
        model = LoginScreenModel(LoginUseCase(repository), UserDataValidator(), logger)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun submitButtonIsDisabledUntilBothFieldsAreFilled() = runTest {
        assertThat(model.state.value.canSubmit).isFalse()

        model.onEvent(LoginEvent.EmailChanged("user@example.com"))
        assertThat(model.state.value.canSubmit).isFalse()

        model.onEvent(LoginEvent.PasswordChanged("Password1"))
        assertThat(model.state.value.canSubmit).isTrue()
    }

    @Test
    fun blankSubmitShowsFieldErrorsAndDoesNotCallRepository() = runTest {
        model.onEvent(LoginEvent.LoginClicked)

        val state = model.state.value
        assertThat(state.emailError).isEqualTo(UiText.Resource(Res.string.error_email_required))
        assertThat(state.passwordError).isEqualTo(UiText.Resource(Res.string.error_password_required))
        assertThat(repository.loginCalls).isEmpty()
    }

    @Test
    fun invalidEmailOnSubmitShowsFieldErrorAndDoesNotCallRepository() = runTest {
        model.onEvent(LoginEvent.EmailChanged("not-an-email"))
        model.onEvent(LoginEvent.PasswordChanged("Password1"))
        model.onEvent(LoginEvent.LoginClicked)

        assertThat(model.state.value.emailError)
            .isEqualTo(UiText.Resource(Res.string.error_email_invalid))
        assertThat(repository.loginCalls).isEmpty()
    }

    @Test
    fun invalidEmailIsFlaggedOnFocusLossButNotWhileTyping() = runTest {
        model.onEvent(LoginEvent.EmailChanged("not-an-email"))
        assertThat(model.state.value.emailError).isNull()

        model.onEvent(LoginEvent.EmailFocusLost)
        assertThat(model.state.value.emailError)
            .isEqualTo(UiText.Resource(Res.string.error_email_invalid))
    }

    @Test
    fun blankEmailIsNotFlaggedOnFocusLoss() = runTest {
        model.onEvent(LoginEvent.EmailFocusLost)
        assertThat(model.state.value.emailError).isNull()
    }

    @Test
    fun successfulLoginSetsIsLoggedInAndClearsLoading() = runTest {
        model.onEvent(LoginEvent.EmailChanged("user@example.com"))
        model.onEvent(LoginEvent.PasswordChanged("Password1"))
        model.onEvent(LoginEvent.LoginClicked)

        val state = model.state.value
        assertThat(state.isLoggedIn).isTrue()
        assertThat(state.isLoading).isFalse()
        assertThat(state.formError).isNull()
    }

    @Test
    fun emailIsTrimmedAndLowercasedBeforeReachingRepository() = runTest {
        model.onEvent(LoginEvent.EmailChanged("  User@Example.COM  "))
        model.onEvent(LoginEvent.PasswordChanged("Password1"))
        model.onEvent(LoginEvent.LoginClicked)

        assertThat(repository.loginCalls).hasSize(1)
        assertThat(repository.loginCalls.first().first).isEqualTo("user@example.com")
    }

    @Test
    fun unauthorizedShowsInvalidCredentialsClearsPasswordKeepsEmail() = runTest {
        repository.loginResult = Result.Failure(DataError.Remote.UNAUTHORIZED)

        model.onEvent(LoginEvent.EmailChanged("user@example.com"))
        model.onEvent(LoginEvent.PasswordChanged("wrong-password"))
        model.onEvent(LoginEvent.LoginClicked)

        val state = model.state.value
        assertThat(state.formError)
            .isEqualTo(UiText.Resource(Res.string.error_invalid_credentials))
        assertThat(state.password).isEqualTo("")
        assertThat(state.email).isEqualTo("user@example.com")
        assertThat(state.isLoggedIn).isFalse()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun noInternetShowsOfflineMessageAndPreservesBothFields() = runTest {
        repository.loginResult = Result.Failure(DataError.Remote.NO_INTERNET)

        model.onEvent(LoginEvent.EmailChanged("user@example.com"))
        model.onEvent(LoginEvent.PasswordChanged("Password1"))
        model.onEvent(LoginEvent.LoginClicked)

        val state = model.state.value
        assertThat(state.formError).isEqualTo(UiText.Resource(Res.string.error_no_internet))
        assertThat(state.password).isEqualTo("Password1")
        assertThat(state.email).isEqualTo("user@example.com")
    }

    @Test
    fun serverErrorSurfacesGenericMessageNeverRawThrowable() = runTest {
        repository.loginResult = Result.Failure(DataError.Remote.SERVER_ERROR)

        model.onEvent(LoginEvent.EmailChanged("user@example.com"))
        model.onEvent(LoginEvent.PasswordChanged("Password1"))
        model.onEvent(LoginEvent.LoginClicked)

        assertThat(model.state.value.formError).isNotNull()
    }

    @Test
    fun repeatedTapsWhileRequestInFlightTriggerOnlyOneCall() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.loginGate = gate

        model.onEvent(LoginEvent.EmailChanged("user@example.com"))
        model.onEvent(LoginEvent.PasswordChanged("Password1"))

        model.onEvent(LoginEvent.LoginClicked)
        assertThat(model.state.value.isLoading).isTrue()

        model.onEvent(LoginEvent.LoginClicked)
        model.onEvent(LoginEvent.LoginClicked)

        assertThat(repository.loginCalls).hasSize(1)

        gate.complete(Unit)
    }

    @Test
    fun typingClearsPreviousFormError() = runTest {
        repository.loginResult = Result.Failure(DataError.Remote.UNAUTHORIZED)
        model.onEvent(LoginEvent.EmailChanged("user@example.com"))
        model.onEvent(LoginEvent.PasswordChanged("wrong"))
        model.onEvent(LoginEvent.LoginClicked)
        assertThat(model.state.value.formError).isNotNull()

        model.onEvent(LoginEvent.PasswordChanged("Password1"))
        assertThat(model.state.value.formError).isNull()
    }

    // AC-1
    @Test
    fun validationRejectionIsLoggedWithoutFieldValues() = runTest {
        model.onEvent(LoginEvent.EmailChanged("not-an-email"))
        model.onEvent(LoginEvent.PasswordChanged("Password1"))

        model.onEvent(LoginEvent.LoginClicked)

        val entry = logWriter.entries.single()
        assertThat(entry.tag).isEqualTo(LogTags.AUTH)
        assertThat(entry.severity).isEqualTo(LogSeverity.DEBUG)
        assertThat(entry.message).isEqualTo("login blocked by validation")
    }
}
