package com.anksoft.myapplication.features.consent.presentation.prompt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.presentation.UiText
import com.anksoft.myapplication.core.presentation.asString
import com.anksoft.myapplication.features.consent.domain.model.ConsentTexts
import com.anksoft.myapplication.features.consent.domain.usecase.ConsentTextsLoad
import com.anksoft.myapplication.features.consent.presentation.ConsentDescription
import com.anksoft.myapplication.features.consent.presentation.ConsentTextsError
import com.anksoft.myapplication.features.consent.presentation.PrivacyPolicyLink
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.consent_accept
import myapplication.shared.generated.resources.consent_error_text_updated
import myapplication.shared.generated.resources.consent_prompt_title
import myapplication.shared.generated.resources.consent_reject
import org.jetbrains.compose.resources.stringResource

/** Pushed by the home screen once per session while the account has no current decision (AC-7). */
class ConsentPromptScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<ConsentPromptScreenModel>()
        val state by screenModel.state.collectAsState()

        LaunchedEffect(state.isDone) {
            if (state.isDone) navigator.pop()
        }

        ConsentPromptContent(state = state, onEvent = screenModel::onEvent)
    }
}

@Composable
fun ConsentPromptContent(
    state: ConsentPromptState,
    onEvent: (ConsentPromptEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.testTag(ConsentPromptTestTags.SCREEN)) { padding ->
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(Res.string.consent_prompt_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() }
                )

                when (val load = state.texts) {
                    is ConsentTextsLoad.Loaded -> {
                        ConsentDescription(texts = load.texts)
                        PrivacyPolicyLink(
                            url = load.texts.safePolicyUrl,
                            testTag = ConsentPromptTestTags.PRIVACY_POLICY
                        )
                    }

                    is ConsentTextsLoad.Failed -> ConsentTextsError(
                        onRetryClick = { onEvent(ConsentPromptEvent.RetryTexts) },
                        retryTestTag = ConsentPromptTestTags.RETRY
                    )

                    ConsentTextsLoad.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }

                state.error?.let { error ->
                    Text(
                        text = error.asString(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag(ConsentPromptTestTags.ERROR)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Same component, same size: accepting must not be easier to reach than rejecting (AC-7).
                OutlinedButton(
                    onClick = { onEvent(ConsentPromptEvent.Accept) },
                    enabled = state.canDecide,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(ConsentPromptTestTags.ACCEPT)
                ) {
                    Text(stringResource(Res.string.consent_accept))
                }
                OutlinedButton(
                    onClick = { onEvent(ConsentPromptEvent.Reject) },
                    enabled = state.canDecide,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(ConsentPromptTestTags.REJECT)
                ) {
                    Text(stringResource(Res.string.consent_reject))
                }
            }
        }
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
private fun ConsentPromptReadyPreview() {
    MaterialTheme {
        ConsentPromptContent(
            state = ConsentPromptState(texts = ConsentTextsLoad.Loaded(previewTexts)),
            onEvent = {}
        )
    }
}

@Preview
@Composable
private fun ConsentPromptErrorPreview() {
    MaterialTheme {
        ConsentPromptContent(
            state = ConsentPromptState(
                texts = ConsentTextsLoad.Loaded(previewTexts),
                error = UiText.Resource(Res.string.consent_error_text_updated)
            ),
            onEvent = {}
        )
    }
}

@Preview
@Composable
private fun ConsentPromptTextFailedPreview() {
    MaterialTheme {
        ConsentPromptContent(
            state = ConsentPromptState(texts = ConsentTextsLoad.Failed(DataError.Remote.NO_INTERNET)),
            onEvent = {}
        )
    }
}
