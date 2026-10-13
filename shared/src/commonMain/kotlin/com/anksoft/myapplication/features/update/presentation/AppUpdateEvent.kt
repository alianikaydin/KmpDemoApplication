package com.anksoft.myapplication.features.update.presentation

sealed interface AppUpdateEvent {
    /** "Update": open this app's store page. */
    data object OpenStore : AppUpdateEvent

    /** "Later" (or closing the prompt): do not ask again for this latest version. */
    data object DismissPrompt : AppUpdateEvent
}
