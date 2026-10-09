package com.anksoft.myapplication.core.presentation

import androidx.compose.ui.Modifier

/**
 * Makes the on/off state of a toggleable row readable by iOS accessibility and UI automation.
 *
 * Compose on iOS does not expose the toggle state of a checkbox or switch (no value, no selected
 * trait), so VoiceOver-based tools such as Maestro cannot tell "on" from "off". On iOS this marks the
 * row as selected while it is on; the other platforms already expose the state and are unchanged.
 * Use it next to `toggleable(...)` on the same element.
 */
expect fun Modifier.exposeToggleState(checked: Boolean): Modifier
