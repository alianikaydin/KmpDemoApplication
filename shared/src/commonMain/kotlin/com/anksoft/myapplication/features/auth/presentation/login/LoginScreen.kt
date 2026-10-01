package com.anksoft.myapplication.features.auth.presentation.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.anksoft.myapplication.core.presentation.UiText
import com.anksoft.myapplication.core.presentation.asString
import com.anksoft.myapplication.features.auth.presentation.signup.SignUpScreen
import com.anksoft.myapplication.features.home.presentation.HomeScreen
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.login_button
import myapplication.shared.generated.resources.login_email_label
import myapplication.shared.generated.resources.login_hide_password
import myapplication.shared.generated.resources.login_no_account_sign_up
import myapplication.shared.generated.resources.login_password_label
import myapplication.shared.generated.resources.login_show_password
import myapplication.shared.generated.resources.login_title
import org.jetbrains.compose.resources.stringResource

class LoginScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<LoginScreenModel>()
        val state by screenModel.state.collectAsState()

        LaunchedEffect(state.isLoggedIn) {
            if (state.isLoggedIn) {
                // replaceAll so Login leaves no back-stack entry (AC-1.3).
                navigator.replaceAll(HomeScreen())
            }
        }

        LoginContent(
            state = state,
            onEvent = screenModel::onEvent,
            onSignUpClick = { navigator.push(SignUpScreen()) }
        )
    }
}

@Composable
fun LoginContent(
    state: LoginState,
    onEvent: (LoginEvent) -> Unit,
    onSignUpClick: () -> Unit
) {
    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.login_title),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier
                        .padding(bottom = 32.dp)
                        .testTag(LoginTestTags.TITLE)
                )

                OutlinedTextField(
                    value = state.email,
                    onValueChange = { onEvent(LoginEvent.EmailChanged(it)) },
                    label = { Text(stringResource(Res.string.login_email_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(LoginTestTags.EMAIL_INPUT)
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) onEvent(LoginEvent.EmailFocusLost)
                        },
                    singleLine = true,
                    enabled = !state.isLoading,
                    isError = state.emailError != null,
                    supportingText = state.emailError?.let { error ->
                        { Text(error.asString()) }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                var passwordVisible by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = state.password,
                    onValueChange = { onEvent(LoginEvent.PasswordChanged(it)) },
                    label = { Text(stringResource(Res.string.login_password_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(LoginTestTags.PASSWORD_INPUT),
                    singleLine = true,
                    enabled = !state.isLoading,
                    isError = state.passwordError != null,
                    supportingText = state.passwordError?.let { error ->
                        { Text(error.asString()) }
                    },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onEvent(LoginEvent.LoginClicked) }
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Default.Visibility
                                } else {
                                    Icons.Default.VisibilityOff
                                },
                                contentDescription = stringResource(
                                    if (passwordVisible) {
                                        Res.string.login_hide_password
                                    } else {
                                        Res.string.login_show_password
                                    }
                                )
                            )
                        }
                    }
                )

                state.formError?.let { error ->
                    Text(
                        text = error.asString(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        // Announced by screen readers as soon as it appears.
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag(LoginTestTags.FORM_ERROR)
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onEvent(LoginEvent.LoginClicked) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(LoginTestTags.SUBMIT_BUTTON),
                    enabled = state.canSubmit
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(Res.string.login_button))
                    }
                }

                TextButton(
                    onClick = onSignUpClick,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(stringResource(Res.string.login_no_account_sign_up))
                }
            }
        }
    }
}

@Preview
@Composable
private fun LoginContentPreview() {
    LoginContent(state = LoginState(), onEvent = {}, onSignUpClick = {})
}

@Preview
@Composable
private fun LoginContentLoadingPreview() {
    LoginContent(
        state = LoginState(email = "user@example.com", password = "secret", isLoading = true),
        onEvent = {},
        onSignUpClick = {}
    )
}

@Preview
@Composable
private fun LoginContentErrorPreview() {
    LoginContent(
        state = LoginState(
            email = "not-an-email",
            emailError = UiText.Dynamic("Enter a valid email address"),
            formError = UiText.Dynamic("Email or password is incorrect")
        ),
        onEvent = {},
        onSignUpClick = {}
    )
}
