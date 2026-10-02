package com.anksoft.myapplication.core.config

/**
 * Build-time app configuration, supplied by each platform entry point to initKoin.
 *
 * @property baseUrl Root URL for every API request.
 * Each platform passes one optional backend URL (Gradle property or xcconfig); the decision
 * between demo mode and a real backend lives here, in [fromBackendUrl] and [forBuild].
 *
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

        /**
         * Blank or null [backendUrl] selects [Demo]; otherwise requests go to that URL.
         * A trailing slash is added when missing.
         *
         * @throws IllegalArgumentException if the URL does not start with http:// or https://.
         */
        fun fromBackendUrl(backendUrl: String?): AppConfig {
            val url = backendUrl?.trim().orEmpty()
            if (url.isEmpty()) return Demo
            require(url.startsWith("http://") || url.startsWith("https://")) {
                "Backend URL must start with http:// or https://"
            }
            return AppConfig(baseUrl = if (url.endsWith("/")) url else "$url/")
        }

        /** Debug builds honour [backendUrl]; release builds always use the default config. */
        fun forBuild(isDebug: Boolean, backendUrl: String?): AppConfig =
            if (isDebug) fromBackendUrl(backendUrl) else AppConfig()
    }
}
