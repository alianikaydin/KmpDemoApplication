package com.anksoft.myapplication.core.presentation

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

actual fun Modifier.exposeToggleState(checked: Boolean): Modifier = semantics { selected = checked }
