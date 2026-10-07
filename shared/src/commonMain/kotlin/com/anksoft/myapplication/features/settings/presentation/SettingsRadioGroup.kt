package com.anksoft.myapplication.features.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** One row of a [SettingsRadioGroup]. [testTag] is the stable id UI automation uses. */
internal data class RadioOption<T>(val value: T, val label: String, val testTag: String)

/**
 * A titled single-choice list. Reusable for any setting with a few fixed options (the theme
 * picker will use it too). Each row is one touch target of at least 48dp.
 */
@Composable
internal fun <T> SettingsRadioGroup(
    title: String,
    options: List<RadioOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.selectableGroup()) {
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = option.value == selected,
                            onClick = { onSelect(option.value) },
                            role = Role.RadioButton
                        )
                        .testTag(option.testTag)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // The row handles the click, so the button itself is not clickable.
                    RadioButton(selected = option.value == selected, onClick = null)
                    Text(text = option.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
