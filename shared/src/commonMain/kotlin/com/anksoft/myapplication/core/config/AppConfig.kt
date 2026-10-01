package com.anksoft.myapplication.core.config

/**
 * Build-time app configuration, supplied by each platform entry point to initKoin.
 *
 * @property baseUrl Root URL for every API request.
 * @property useMockBackend When true, requests are served by the in-app MockAuthServer
 * instead of the network. Must stay false for release builds.
 */
data class AppConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val useMockBackend: Boolean = false
) {
    companion object {
        // TODO replace with the real backend URL once one exists.
        const val DEFAULT_BASE_URL = "https://api.example.com/"
        const val MOCK_BASE_URL = "https://mock.local/"

        /** Demo mode: no backend needed, auth is served in-process. */
        val Demo = AppConfig(baseUrl = MOCK_BASE_URL, useMockBackend = true)
    }
}
