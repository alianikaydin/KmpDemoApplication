package com.anksoft.myapplication.features.consent.presentation.prompt

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.onFailure
import com.anksoft.myapplication.core.domain.onSuccess
import com.anksoft.myapplication.core.presentation.UiText
import com.anksoft.myapplication.core.presentation.toUiText
import com.anksoft.myapplication.features.consent.domain.AccountConsentManager
import com.anksoft.myapplication.features.consent.domain.model.ConsentChoice
import com.anksoft.myapplication.features.consent.domain.usecase.ConsentTextsLoad
import com.anksoft.myapplication.features.consent.domain.usecase.ObserveConsentTextsUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.consent_error_text_updated

data class ConsentPromptState(
    val texts: ConsentTextsLoad = ConsentTextsLoad.Loading,
    val isSaving: Boolean = false,
    val error: UiText? = null,
    /** The decision was recorded; the screen closes itself. */
    val isDone: Boolean = false
) {
    /** A decision is possible only for a text the user can see, and one at a time (AC-7). */
    val canDecide: Boolean
        get() = texts is ConsentTextsLoad.Loaded && !isSaving && !isDone
}

sealed interface ConsentPromptEvent {
    data object Accept : ConsentPromptEvent
    data object Reject : ConsentPromptEvent
    data object RetryTexts : ConsentPromptEvent
}

/** The one-time consent prompt (AC-7..AC-11). Both answers go through the same path. */
class ConsentPromptScreenModel(
    private val consentManager: AccountConsentManager,
    observeConsentTexts: ObserveConsentTextsUseCase
) : StateScreenModel<ConsentPromptState>(ConsentPromptState()) {

    private val retryTexts = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        screenModelScope.launch {
            observeConsentTexts(retryTexts).collect { load ->
                mutableState.update { it.copy(texts = load) }
            }
        }
    }

    fun onEvent(event: ConsentPromptEvent) {
        when (event) {
            ConsentPromptEvent.Accept -> decide(granted = true)
            ConsentPromptEvent.Reject -> decide(granted = false)
            ConsentPromptEvent.RetryTexts -> retryTexts.tryEmit(Unit)
        }
    }

    private fun decide(granted: Boolean) {
        val current = state.value
        val loaded = current.texts as? ConsentTextsLoad.Loaded
        if (loaded == null || !current.canDecide) return

        screenModelScope.launch {
            mutableState.update { it.copy(isSaving = true, error = null) }
            consentManager.decide(ConsentChoice.of(granted, loaded.texts))
                .onSuccess { mutableState.update { it.copy(isSaving = false, isDone = true) } }
                .onFailure { error -> showFailure(error) }
        }
    }

    private fun showFailure(error: DataError) {
        if (error == DataError.Remote.UNPROCESSABLE) {
            // The text version on screen is gone: show the current text and ask again (AC-22).
            retryTexts.tryEmit(Unit)
            mutableState.update {
                it.copy(isSaving = false, error = UiText.Resource(Res.string.consent_error_text_updated))
            }
        } else {
            // The prompt stays open so the user can try again (AC-11).
            mutableState.update { it.copy(isSaving = false, error = error.toUiText()) }
        }
    }
}
