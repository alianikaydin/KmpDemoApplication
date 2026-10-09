package com.anksoft.myapplication.features.settings.presentation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.preferences.AppLanguage
import com.anksoft.myapplication.core.preferences.AppPreferences
import com.anksoft.myapplication.core.preferences.ThemeMode
import com.anksoft.myapplication.features.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val isLoading: Boolean = false,
    val isLoggedOut: Boolean = false,
    val userName: String = "Mock User",
    val userEmail: String = "mock@example.com",
    val environmentName: String,
    val versionName: String,
    val versionCode: Int,
    val selectedLanguage: AppLanguage = AppLanguage.SYSTEM,
    val selectedThemeMode: ThemeMode = ThemeMode.SYSTEM
)

sealed interface SettingsEvent {
    data object Logout : SettingsEvent
    data class LanguageSelect(val language: AppLanguage) : SettingsEvent
    data class ThemeModeSelect(val themeMode: ThemeMode) : SettingsEvent
}

class SettingsScreenModel(
    private val authRepository: AuthRepository,
    appConfig: AppConfig,
    private val appPreferences: AppPreferences
) : StateScreenModel<SettingsState>(
    SettingsState(
        environmentName = appConfig.environment.name,
        versionName = appConfig.versionName,
        versionCode = appConfig.versionCode,
        selectedLanguage = appPreferences.language.value,
        selectedThemeMode = appPreferences.themeMode.value
    )
) {

    init {
        // The preferences are the source of truth; the state only mirrors them.
        screenModelScope.launch {
            appPreferences.language.collect { language ->
                mutableState.update { it.copy(selectedLanguage = language) }
            }
        }
        screenModelScope.launch {
            appPreferences.themeMode.collect { themeMode ->
                mutableState.update { it.copy(selectedThemeMode = themeMode) }
            }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.Logout -> logout()
            is SettingsEvent.LanguageSelect -> appPreferences.setLanguage(event.language)
            is SettingsEvent.ThemeModeSelect -> appPreferences.setThemeMode(event.themeMode)
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
