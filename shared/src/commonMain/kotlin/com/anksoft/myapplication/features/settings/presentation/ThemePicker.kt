package com.anksoft.myapplication.features.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.anksoft.myapplication.core.preferences.ThemeMode
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.settings_theme
import myapplication.shared.generated.resources.theme_dark
import myapplication.shared.generated.resources.theme_light
import myapplication.shared.generated.resources.theme_system
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ThemePicker(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = ThemeMode.entries.map { themeMode ->
        RadioOption(
            value = themeMode,
            label = when (themeMode) {
                ThemeMode.SYSTEM -> stringResource(Res.string.theme_system)
                ThemeMode.LIGHT -> stringResource(Res.string.theme_light)
                ThemeMode.DARK -> stringResource(Res.string.theme_dark)
            },
            testTag = themeMode.testTag()
        )
    }
    SettingsRadioGroup(
        title = stringResource(Res.string.settings_theme),
        options = options,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier
    )
}

private fun ThemeMode.testTag(): String = when (this) {
    ThemeMode.SYSTEM -> SettingsTestTags.THEME_SYSTEM
    ThemeMode.LIGHT -> SettingsTestTags.THEME_LIGHT
    ThemeMode.DARK -> SettingsTestTags.THEME_DARK
}
