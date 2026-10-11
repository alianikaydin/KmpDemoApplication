package com.anksoft.myapplication.features.update.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.anksoft.myapplication.core.presentation.UiText
import com.anksoft.myapplication.core.presentation.asString
import com.anksoft.myapplication.core.presentation.testTagsAsResourceId
import com.anksoft.myapplication.features.update.domain.model.AppVersion
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.update_open_store
import myapplication.shared.generated.resources.update_prompt_later
import myapplication.shared.generated.resources.update_prompt_message
import myapplication.shared.generated.resources.update_prompt_title
import myapplication.shared.generated.resources.update_store_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * The dismissible update suggestion (AC-7). Back and a tap outside close it like "Later" (AC-8),
 * which is the standard dialog behaviour.
 */
@Composable
fun UpdatePromptDialog(
    state: AppUpdateState.Optional,
    onEvent: (AppUpdateEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = { onEvent(AppUpdateEvent.DismissPrompt) },
        modifier = modifier
            .testTagsAsResourceId()
            .testTag(UpdateTestTags.UPDATE_PROMPT),
        title = {
            Text(
                text = stringResource(Res.string.update_prompt_title),
                modifier = Modifier.semantics { heading() },
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = stringResource(Res.string.update_prompt_message))
                state.storeError?.let { error ->
                    Text(
                        text = error.asString(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag(UpdateTestTags.UPDATE_STORE_ERROR),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(AppUpdateEvent.OpenStore) },
                enabled = !state.isOpeningStore,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(UpdateTestTags.UPDATE_PROMPT_OPEN_STORE),
            ) {
                Text(stringResource(Res.string.update_open_store))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(AppUpdateEvent.DismissPrompt) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(UpdateTestTags.UPDATE_PROMPT_LATER),
            ) {
                Text(stringResource(Res.string.update_prompt_later))
            }
        },
    )
}

private val previewLatest = AppVersion(1, 2, 0)

@Preview
@Composable
private fun UpdatePromptPreview() {
    MaterialTheme {
        UpdatePromptDialog(state = AppUpdateState.Optional(previewLatest), onEvent = {})
    }
}

@Preview
@Composable
private fun UpdatePromptStoreErrorPreview() {
    MaterialTheme {
        UpdatePromptDialog(
            state = AppUpdateState.Optional(
                previewLatest,
                storeError = UiText.Resource(Res.string.update_store_unavailable),
            ),
            onEvent = {},
        )
    }
}
