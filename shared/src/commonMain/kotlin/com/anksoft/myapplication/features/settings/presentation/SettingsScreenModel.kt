package com.anksoft.myapplication.features.settings.presentation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.anksoft.myapplication.features.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val isLoading: Boolean = false,
    val isLoggedOut: Boolean = false,
    val userName: String = "Mock User",
    val userEmail: String = "mock@example.com"
)

class SettingsScreenModel(
    private val authRepository: AuthRepository
) : StateScreenModel<SettingsState>(SettingsState()) {

    fun logout() {
        screenModelScope.launch {
            mutableState.update { it.copy(isLoading = true) }
            authRepository.logout()
            mutableState.update { it.copy(isLoading = false, isLoggedOut = true) }
        }
    }
}
