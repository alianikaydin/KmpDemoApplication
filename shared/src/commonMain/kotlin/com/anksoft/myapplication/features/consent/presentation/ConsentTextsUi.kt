package com.anksoft.myapplication.features.consent.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anksoft.myapplication.features.consent.domain.model.ConsentTexts
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.common_retry
import myapplication.shared.generated.resources.consent_privacy_policy_link
import myapplication.shared.generated.resources.consent_texts_load_failed
import org.jetbrains.compose.resources.stringResource

/**
 * An invisible marker next to a consent control that says whether it is on, for UI automation only.
 * Compose on iOS does not expose the state of a checkbox or switch to XCUITest (see docs/ui-tests.md),
 * so Maestro reads this tag instead. It has no text and no description, so a screen reader finds
 * nothing to announce; it must stay outside the control's merged semantics.
 */
@Composable
fun ConsentStateMarker(checked: Boolean, onTag: String, offTag: String) {
    Box(modifier = Modifier.size(1.dp).testTag(if (checked) onTag else offTag))
}

/** The label and the short description of the consent text, exactly as the backend sent them (AC-21). */
@Composable
fun ConsentDescription(texts: ConsentTexts, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = texts.label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = texts.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Opens the privacy policy. Pass [ConsentTexts.safePolicyUrl]: a null link (not https, or no text
 * yet) shows nothing (AC-1).
 */
@Composable
fun PrivacyPolicyLink(url: String?, testTag: String, modifier: Modifier = Modifier) {
    if (url == null) return
    val uriHandler = LocalUriHandler.current
    TextButton(
        // Some platforms have no handler for a link; that must not crash the screen.
        onClick = { runCatching { uriHandler.openUri(url) } },
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(testTag)
    ) {
        Text(stringResource(Res.string.consent_privacy_policy_link))
    }
}

/** "The text could not be loaded" with a retry button (AC-4, AC-13). */
@Composable
fun ConsentTextsError(onRetryClick: () -> Unit, retryTestTag: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.consent_texts_load_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        TextButton(
            onClick = onRetryClick,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag(retryTestTag)
        ) {
            Text(stringResource(Res.string.common_retry))
        }
    }
}
