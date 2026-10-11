package com.anksoft.myapplication.features.auth.presentation.signup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.presentation.asString
import com.anksoft.myapplication.features.consent.domain.model.ConsentTexts
import com.anksoft.myapplication.features.consent.domain.usecase.ConsentTextsLoad
import com.anksoft.myapplication.features.consent.presentation.ConsentDescription
import com.anksoft.myapplication.features.consent.presentation.ConsentStateMarker
import com.anksoft.myapplication.features.consent.presentation.ConsentTextsError
import com.anksoft.myapplication.features.consent.presentation.PrivacyPolicyLink
import com.anksoft.myapplication.features.home.presentation.HomeScreen
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.common_back
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.common_back))
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
                    .verticalScroll(rememberScrollState())
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

                Spacer(modifier = Modifier.height(16.dp))

                ConsentSection(state = state, onEvent = onEvent)

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

/**
 * The optional consent box (AC-1, AC-2): unchecked by default, never required, and usable only while
 * the backend text is on screen (AC-4). One toggleable row, so the whole label is the touch target
 * and a screen reader announces the box together with its text.
 */
@Composable
private fun ConsentSection(state: SignUpState, onEvent: (SignUpEvent) -> Unit) {
    when (val load = state.consentTexts) {
        is ConsentTextsLoad.Loaded -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = state.consentChecked,
                        enabled = !state.isLoading,
                        role = Role.Checkbox,
                        onValueChange = { onEvent(SignUpEvent.ConsentCheckedChange(it)) }
                    )
                    .testTag(SignUpTestTags.CONSENT_CHECKBOX),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The row owns the interaction; the box itself only draws the state.
                Checkbox(checked = state.consentChecked, onCheckedChange = null)
                Spacer(modifier = Modifier.width(12.dp))
                ConsentDescription(texts = load.texts, modifier = Modifier.weight(1f))
            }
            ConsentStateMarker(
                checked = state.consentChecked,
                onTag = SignUpTestTags.CONSENT_STATE_ON,
                offTag = SignUpTestTags.CONSENT_STATE_OFF
            )
            PrivacyPolicyLink(url = load.texts.safePolicyUrl, testTag = SignUpTestTags.PRIVACY_POLICY)
        }

        is ConsentTextsLoad.Failed -> ConsentTextsError(
            onRetryClick = { onEvent(SignUpEvent.RetryConsentTexts) },
            retryTestTag = SignUpTestTags.CONSENT_RETRY
        )

        ConsentTextsLoad.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

private val previewTexts = ConsentTexts(
    version = 1,
    language = "en",
    label = "Share optional usage data",
    description = "Helps us improve the app. You can change this later in Settings.",
    policyUrl = "https://example.com/privacy"
)

@Preview
@Composable
private fun SignUpContentLoadedPreview() {
    MaterialTheme {
        SignUpContent(
            state = SignUpState(consentTexts = ConsentTextsLoad.Loaded(previewTexts)),
            onEvent = {},
            onBackClick = {}
        )
    }
}

@Preview
@Composable
private fun SignUpContentFailedPreview() {
    MaterialTheme {
        SignUpContent(
            state = SignUpState(consentTexts = ConsentTextsLoad.Failed(DataError.Remote.NO_INTERNET)),
            onEvent = {},
            onBackClick = {}
        )
    }
}
