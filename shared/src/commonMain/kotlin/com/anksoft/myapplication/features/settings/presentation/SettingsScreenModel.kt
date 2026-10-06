package com.anksoft.myapplication.features.settings.presentation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.anksoft.myapplication.core.config.AppConfig
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
    val versionCode: Int
)

sealed interface SettingsEvent {
    data object Logout : SettingsEvent
}

class SettingsScreenModel(
    private val authRepository: AuthRepository,
    appConfig: AppConfig
) : StateScreenModel<SettingsState>(
    SettingsState(
        environmentName = appConfig.environment.name,
        versionName = appConfig.versionName,
        versionCode = appConfig.versionCode
    )
) {

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.Logout -> logout()
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
