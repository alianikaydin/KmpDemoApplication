package com.anksoft.myapplication.features.update.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.force_update_message
import myapplication.shared.generated.resources.force_update_title
import myapplication.shared.generated.resources.update_open_store
import myapplication.shared.generated.resources.update_store_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * The full-screen update screen shown instead of the navigator for an unsupported version (AC-1).
 * It has no close action and no back handling: nothing else is composed while it is shown (AC-2).
 */
@Composable
fun ForceUpdateContent(
    state: AppUpdateState.Required,
    onEvent: (AppUpdateEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.testTag(UpdateTestTags.FORCE_UPDATE_SCREEN)) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.force_update_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(Res.string.force_update_message),
                    style = MaterialTheme.typography.bodyLarge,
                )
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
                Button(
                    onClick = { onEvent(AppUpdateEvent.OpenStore) },
                    enabled = !state.isOpeningStore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(UpdateTestTags.FORCE_UPDATE_OPEN_STORE),
                ) {
                    Text(stringResource(Res.string.update_open_store))
                }
            }
        }
    }
}

@Preview
@Composable
private fun ForceUpdatePreview() {
    MaterialTheme {
        ForceUpdateContent(state = AppUpdateState.Required(), onEvent = {})
    }
}

@Preview
@Composable
private fun ForceUpdateStoreErrorPreview() {
    MaterialTheme {
        ForceUpdateContent(
            state = AppUpdateState.Required(storeError = UiText.Resource(Res.string.update_store_unavailable)),
            onEvent = {},
        )
    }
}
