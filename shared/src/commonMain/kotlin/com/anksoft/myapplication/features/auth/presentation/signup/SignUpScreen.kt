package com.anksoft.myapplication.features.auth.presentation.signup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.anksoft.myapplication.core.presentation.asString
import com.anksoft.myapplication.features.home.presentation.HomeScreen
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.login_email_label
import myapplication.shared.generated.resources.login_password_label
import myapplication.shared.generated.resources.signup_button
import myapplication.shared.generated.resources.signup_confirm_password_label
import myapplication.shared.generated.resources.signup_title
import org.jetbrains.compose.resources.stringResource

class SignUpScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<SignUpScreenModel>()
        val state by screenModel.state.collectAsState()

        LaunchedEffect(state.isSignedUp) {
            if (state.isSignedUp) {
                navigator.replaceAll(HomeScreen())
            }
        }

        SignUpContent(
            state = state,
            onEvent = screenModel::onEvent,
            onBackClick = { navigator.pop() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpContent(
    state: SignUpState,
    onEvent: (SignUpEvent) -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.signup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                OutlinedTextField(
                    value = state.email,
                    onValueChange = { onEvent(SignUpEvent.EmailChanged(it)) },
                    label = { Text(stringResource(Res.string.login_email_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SignUpTestTags.EMAIL_INPUT)
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) onEvent(SignUpEvent.EmailFocusLost)
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

                OutlinedTextField(
                    value = state.password,
                    onValueChange = { onEvent(SignUpEvent.PasswordChanged(it)) },
                    label = { Text(stringResource(Res.string.login_password_label)) },
                    modifier = Modifier.fillMaxWidth().testTag(SignUpTestTags.PASSWORD_INPUT),
                    singleLine = true,
                    enabled = !state.isLoading,
                    isError = state.passwordError != null,
                    supportingText = state.passwordError?.let { error ->
                        { Text(error.asString()) }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    )
                )

                // AC-2.2: live checklist rather than one error at a time.
                if (state.password.isNotEmpty() && state.unmetPasswordRules.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        state.unmetPasswordRules.forEach { rule ->
                            Text(
                                text = rule.toUiText().asString(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.confirmPassword,
                    onValueChange = { onEvent(SignUpEvent.ConfirmPasswordChanged(it)) },
                    label = { Text(stringResource(Res.string.signup_confirm_password_label)) },
                    modifier = Modifier.fillMaxWidth().testTag(SignUpTestTags.CONFIRM_PASSWORD_INPUT),
                    singleLine = true,
                    enabled = !state.isLoading,
                    isError = state.confirmPasswordError != null,
                    supportingText = state.confirmPasswordError?.let { error ->
                        { Text(error.asString()) }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onEvent(SignUpEvent.SignUpClicked) }
                    )
                )

                state.formError?.let { error ->
                    Text(
                        text = error.asString(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onEvent(SignUpEvent.SignUpClicked) },
                    modifier = Modifier.fillMaxWidth().testTag(SignUpTestTags.SUBMIT_BUTTON),
                    enabled = state.canSubmit
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Text(stringResource(Res.string.signup_button))
                    }
                }
            }
        }
    }
}
