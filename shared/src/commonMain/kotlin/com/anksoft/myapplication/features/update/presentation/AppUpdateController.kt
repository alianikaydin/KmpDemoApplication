package com.anksoft.myapplication.features.update.presentation

import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.warn
import com.anksoft.myapplication.core.presentation.UiText
import com.anksoft.myapplication.features.update.domain.AppStore
import com.anksoft.myapplication.features.update.domain.DismissedUpdateStore
import com.anksoft.myapplication.features.update.domain.model.UpdateRequirement
import com.anksoft.myapplication.features.update.domain.usecase.CheckForUpdateUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.update_store_unavailable

/**
 * Holds the update state for the whole process. It is not a screen model on purpose: the state must
 * outlive every screen and Activity, because the required screen replaces the navigator (AC-2, AC-13).
 *
 * It never touches the session, consent or navigation (AC-6, AC-22).
 */
class AppUpdateController(
    private val checkForUpdate: CheckForUpdateUseCase,
    private val dismissedUpdates: DismissedUpdateStore,
    /** Null on the web, where no update check runs (AC-17). */
    private val appStore: AppStore?,
    private val scope: CoroutineScope,
    private val logger: AppLogger,
) {

    private val started = MutableStateFlow(false)
    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.None)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    /** Runs the check once per process; later calls do nothing (AC-13). Without an [AppStore] nothing runs at all (AC-17). */
    fun start() {
        if (!started.compareAndSet(expect = false, update = true)) return
        val store = appStore ?: return
        scope.launch {
            _state.value = checkForUpdate(store.platform).toState()
        }
    }

    fun onEvent(event: AppUpdateEvent) {
        when (event) {
            AppUpdateEvent.OpenStore -> openStore()
            AppUpdateEvent.DismissPrompt -> dismissPrompt()
        }
    }

    private fun openStore() {
        var accepted = false
        _state.update { current ->
            // The lambda may run more than once; the flag always reflects the last run.
            val opening = current.startOpeningStore()
            accepted = opening != null
            opening ?: current
        }
        if (!accepted) return
        scope.launch {
            val opened = appStore?.openAppPage() ?: false
            if (!opened) logger.warn(LogTags.UPDATE) { "Store page could not be opened" }
            _state.update { current -> current.afterStoreAttempt(opened) }
        }
    }

    private fun dismissPrompt() {
        val current = _state.value as? AppUpdateState.Optional ?: return
        dismissedUpdates.setDismissedLatestVersion(current.latest)
        _state.update { if (it is AppUpdateState.Optional) AppUpdateState.None else it }
    }
}

private fun UpdateRequirement.toState(): AppUpdateState = when (this) {
    UpdateRequirement.None -> AppUpdateState.None
    is UpdateRequirement.Optional -> AppUpdateState.Optional(latest)
    UpdateRequirement.Required -> AppUpdateState.Required()
}

/** The state while the store opens, or null when there is nothing to open or an attempt is already running. */
private fun AppUpdateState.startOpeningStore(): AppUpdateState? = when (this) {
    AppUpdateState.None -> null
    is AppUpdateState.Optional -> if (isOpeningStore) null else copy(isOpeningStore = true, storeError = null)
    is AppUpdateState.Required -> if (isOpeningStore) null else copy(isOpeningStore = true, storeError = null)
}

/**
 * The prompt closes when the store opened, without being remembered as "later" (AC-10); the required
 * screen stays. When it could not be opened both stay and show the error (AC-4).
 */
private fun AppUpdateState.afterStoreAttempt(opened: Boolean): AppUpdateState {
    val error = if (opened) null else UiText.Resource(Res.string.update_store_unavailable)
    return when (this) {
        AppUpdateState.None -> this
        is AppUpdateState.Optional ->
            if (opened) AppUpdateState.None else copy(isOpeningStore = false, storeError = error)
        is AppUpdateState.Required -> copy(isOpeningStore = false, storeError = error)
    }
}
