package com.anksoft.myapplication.features.home.presentation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.anksoft.myapplication.features.consent.domain.AccountConsentManager
import com.anksoft.myapplication.features.consent.domain.model.needsPrompt
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    /** The one-time consent prompt is due and has not been opened yet. */
    val showConsentPrompt: Boolean = false
)

sealed interface HomeEvent {
    data object ConsentPromptOpened : HomeEvent
}

class HomeScreenModel(
    private val consentManager: AccountConsentManager
) : StateScreenModel<HomeState>(HomeState()) {

    init {
        screenModelScope.launch {
            // Only a known "no current decision" asks (AC-7); Loading and Unavailable never do (AC-12).
            // claimPrompt() hands the prompt out once per session, so a second emission shows nothing (AC-10).
            consentManager.status.collect { status ->
                if (status.needsPrompt && consentManager.claimPrompt()) {
                    mutableState.update { it.copy(showConsentPrompt = true) }
                }
            }
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.ConsentPromptOpened -> mutableState.update { it.copy(showConsentPrompt = false) }
        }
    }
}
