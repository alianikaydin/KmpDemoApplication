package com.anksoft.myapplication.features.settings.presentation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.preferences.AppLanguage
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.onFailure
import com.anksoft.myapplication.core.preferences.AppPreferences
import com.anksoft.myapplication.core.presentation.UiText
import com.anksoft.myapplication.core.presentation.toUiText
import com.anksoft.myapplication.features.auth.domain.repository.AuthRepository
import com.anksoft.myapplication.features.consent.domain.AccountConsentManager
import com.anksoft.myapplication.features.consent.domain.model.ConsentChoice
import com.anksoft.myapplication.features.consent.domain.model.ConsentStatus
import com.anksoft.myapplication.features.consent.domain.model.ConsentTexts
import com.anksoft.myapplication.features.consent.domain.usecase.ConsentTextsLoad
import com.anksoft.myapplication.features.consent.domain.usecase.ObserveConsentTextsUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.consent_error_text_updated

/** What the privacy section shows (AC-13..AC-16). */
data class PrivacyState(
    val isChecked: Boolean = false,
    val isToggleEnabled: Boolean = false,
    val isSaving: Boolean = false,
    /** The decision or the text could not be loaded: offer "try again" (AC-13). */
    val showRetry: Boolean = false,
    /** The backend's label for the switch; null until the text is loaded. */
    val label: String? = null,
    val policyUrl: String? = null,
    val error: UiText? = null
)

data class SettingsState(
    val isLoading: Boolean = false,
    val isLoggedOut: Boolean = false,
    val userName: String = "Mock User",
    val userEmail: String = "mock@example.com",
    val environmentName: String,
    val versionName: String,
    val versionCode: Int,
    val selectedLanguage: AppLanguage = AppLanguage.SYSTEM,
    val privacy: PrivacyState = PrivacyState()
)

sealed interface SettingsEvent {
    data object Logout : SettingsEvent
    data class LanguageSelect(val language: AppLanguage) : SettingsEvent
    data class ConsentToggle(val enabled: Boolean) : SettingsEvent
    data object RetryConsent : SettingsEvent
}

class SettingsScreenModel(
    private val authRepository: AuthRepository,
    appConfig: AppConfig,
    private val appPreferences: AppPreferences,
    private val consentManager: AccountConsentManager,
    observeConsentTexts: ObserveConsentTextsUseCase
) : StateScreenModel<SettingsState>(
    SettingsState(
        environmentName = appConfig.environment.name,
        versionName = appConfig.versionName,
        versionCode = appConfig.versionCode,
        selectedLanguage = appPreferences.language.value
    )
) {

    private val retryTexts = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // The value the user asked for while it is being saved; the switch shows it meanwhile.
    private val pendingChoice = MutableStateFlow<Boolean?>(null)
    private val consentError = MutableStateFlow<UiText?>(null)

    // The text on screen, so a decision is recorded for exactly what the user saw (AC-22).
    private var latestTexts: ConsentTexts? = null

    init {
        // Opening Settings looks at the account's decision again (AC-12).
        consentManager.refresh()
        screenModelScope.launch {
            combine(
                consentManager.status,
                observeConsentTexts(retryTexts).onEach { latestTexts = (it as? ConsentTextsLoad.Loaded)?.texts },
                pendingChoice,
                consentError
            ) { status, texts, pending, error -> toPrivacyState(status, texts, pending, error) }
                .collect { privacy -> mutableState.update { it.copy(privacy = privacy) } }
        }
        // The preferences are the source of truth; the state only mirrors them.
        screenModelScope.launch {
            appPreferences.language.collect { language ->
                mutableState.update { it.copy(selectedLanguage = language) }
            }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.Logout -> logout()
            is SettingsEvent.LanguageSelect -> appPreferences.setLanguage(event.language)
            is SettingsEvent.ConsentToggle -> toggleConsent(event.enabled)
            SettingsEvent.RetryConsent -> {
                consentError.value = null
                consentManager.refresh()
                retryTexts.tryEmit(Unit)
            }
        }
    }

    private fun toPrivacyState(
        status: ConsentStatus,
        texts: ConsentTextsLoad,
        pending: Boolean?,
        error: UiText?
    ): PrivacyState {
        val loaded = texts as? ConsentTextsLoad.Loaded
        val decided = when (status) {
            ConsentStatus.Undecided, ConsentStatus.Denied, is ConsentStatus.Granted -> true
            ConsentStatus.SignedOut, ConsentStatus.Loading, ConsentStatus.Unavailable -> false
        }
        return PrivacyState(
            // A switched-off grant that the backend has not confirmed still shows as on (the confirmed state).
            isChecked = pending ?: (status is ConsentStatus.Granted && !status.reconsentRequired),
            isToggleEnabled = loaded != null && decided && pending == null,
            isSaving = pending != null,
            showRetry = status == ConsentStatus.Unavailable || texts is ConsentTextsLoad.Failed,
            label = loaded?.texts?.label,
            policyUrl = loaded?.texts?.safePolicyUrl,
            error = error
        )
    }

    private fun toggleConsent(enabled: Boolean) {
        if (!state.value.privacy.isToggleEnabled) return
        val texts = latestTexts ?: return
        pendingChoice.value = enabled
        consentError.value = null
        screenModelScope.launch {
            consentManager.decide(ConsentChoice.of(enabled, texts))
                .onFailure { error -> showConsentFailure(error) }
            pendingChoice.value = null
        }
    }

    private fun showConsentFailure(error: DataError) {
        if (error == DataError.Remote.UNPROCESSABLE) {
            // The text changed under the user: show the current one and let them decide again (AC-22).
            retryTexts.tryEmit(Unit)
            consentError.value = UiText.Resource(Res.string.consent_error_text_updated)
        } else {
            consentError.value = error.toUiText()
        }
    }

    private fun logout() {
        screenModelScope.launch {
            mutableState.update { it.copy(isLoading = true) }
            authRepository.logout()
            mutableState.update { it.copy(isLoading = false, isLoggedOut = true) }
        }
    }
}
