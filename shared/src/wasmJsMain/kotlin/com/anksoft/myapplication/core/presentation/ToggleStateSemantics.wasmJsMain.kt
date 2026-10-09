package com.anksoft.myapplication.core.presentation

import androidx.compose.ui.Modifier

// Android and the web already expose the toggle state of a toggleable element.
actual fun Modifier.exposeToggleState(checked: Boolean): Modifier = this
