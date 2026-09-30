package com.anksoft.myapplication.core.presentation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Lets screen models describe text without resolving it, so they stay free of
 * Compose/platform context and remain unit-testable. The composable resolves it.
 */
sealed interface UiText {
    data class Dynamic(val value: String) : UiText
    data class Resource(val id: StringResource) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Dynamic -> value
    is UiText.Resource -> stringResource(id)
}
