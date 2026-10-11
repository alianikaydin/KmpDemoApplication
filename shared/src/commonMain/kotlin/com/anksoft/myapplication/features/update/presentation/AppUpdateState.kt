package com.anksoft.myapplication.features.update.presentation

import com.anksoft.myapplication.core.presentation.UiText
import com.anksoft.myapplication.features.update.domain.model.AppVersion

/** What the app shows about updates. [None] is also the state while the check runs and after a failed one. */
sealed interface AppUpdateState {
    data object None : AppUpdateState

    /** A dismissible suggestion to move to [latest]. */
    data class Optional(
        val latest: AppVersion,
        val isOpeningStore: Boolean = false,
        val storeError: UiText? = null,
    ) : AppUpdateState

    /** The full-screen, non-dismissible update screen. */
    data class Required(
        val isOpeningStore: Boolean = false,
        val storeError: UiText? = null,
    ) : AppUpdateState
}
