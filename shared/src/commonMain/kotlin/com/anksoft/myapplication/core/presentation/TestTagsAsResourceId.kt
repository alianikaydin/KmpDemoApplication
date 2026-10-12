package com.anksoft.myapplication.core.presentation

import androidx.compose.ui.Modifier

/**
 * Exposes test tags below this node as accessibility resource ids, so UI automation (Maestro) finds
 * them on Android. A dialog is its own window, so the Activity's setting does not reach it; apply this
 * to the dialog content (analysis R8). Other platforms already expose test tags: no-op there.
 */
expect fun Modifier.testTagsAsResourceId(): Modifier
