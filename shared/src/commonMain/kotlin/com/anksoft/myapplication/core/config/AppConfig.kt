package com.anksoft.myapplication.core.config

/**
 * Build-time app configuration, supplied by each platform entry point to initKoin.
 *
 * Platforms pass raw inputs (environment, optional backend URL, whether demo mode is allowed,
 * version); the rules that turn them into a config live in [create] so they are tested once.
 *
 * @property environment Environment this build targets.
 * @property baseUrl Root URL for every API request.
 * @property useMockBackend When true, requests are served by the in-app MockAuthServer
 * instead of the network. Only [AppEnvironment.DEV] may enable it.
 * @property versionName User-visible version, from the single version source.
 * @property versionCode Monotonic build number, from the single version source.
 */
data class AppConfig(
    val environment: AppEnvironment,
    val baseUrl: String,
    val useMockBackend: Boolean,
    val versionName: String,
    val versionCode: Int
) {
    init {
        require(!useMockBackend || environment == AppEnvironment.DEV) {
            "Mock backend is only allowed in DEV"
        }
        require(versionName.isNotBlank()) { "versionName must not be blank" }
        require(versionCode > 0) { "versionCode must be positive" }
    }

    companion object {
        const val MOCK_BASE_URL = "https://mock.local/"

        const val STAGE_BASE_URL = "https://api-stage.example.com/" // PLACEHOLDER: replace with the real stage URL
        const val PROD_BASE_URL = "https://api.example.com/" // PLACEHOLDER: replace with the real prod URL

        /**
         * Applies the environment rules:
         * - [AppEnvironment.STAGE] and [AppEnvironment.PROD] always use their fixed URL, never the
         *   mock backend; [backendUrl] and [demoAllowed] are ignored.
         * - [AppEnvironment.DEV] uses [backendUrl] when given, else demo mode if [demoAllowed].
         *
         * @throws IllegalStateException if DEV has neither a backend URL nor demo mode.
         * @throws IllegalArgumentException if [backendUrl] is not an http(s) URL.
         */
        fun create(
            environment: AppEnvironment,
            backendUrl: String?,
            demoAllowed: Boolean,
            versionName: String,
            versionCode: Int
        ): AppConfig = when (environment) {
            AppEnvironment.STAGE -> AppConfig(
                environment, STAGE_BASE_URL, useMockBackend = false, versionName, versionCode
            )
            AppEnvironment.PROD -> AppConfig(
                environment, PROD_BASE_URL, useMockBackend = false, versionName, versionCode
            )
            AppEnvironment.DEV -> {
                val url = backendUrl?.trim().orEmpty()
                when {
                    url.isNotEmpty() -> AppConfig(
                        environment, normalizeUrl(url), useMockBackend = false, versionName, versionCode
                    )
                    demoAllowed -> AppConfig(
                        environment, MOCK_BASE_URL, useMockBackend = true, versionName, versionCode
                    )
                    else -> throw IllegalStateException("DEV needs a backend URL or demo mode")
                }
            }
        }

        /**
         * Same as [create] for platforms that only pass strings (iOS Info.plist).
         *
         * @throws IllegalArgumentException if [environment] or [versionCode] cannot be parsed.
         */
        fun fromRaw(
            environment: String,
            backendUrl: String?,
            demoAllowed: Boolean,
            versionName: String,
            versionCode: String
        ): AppConfig = create(
            environment = AppEnvironment.parse(environment),
            backendUrl = backendUrl,
            demoAllowed = demoAllowed,
            versionName = versionName,
            versionCode = versionCode.trim().toIntOrNull()
                ?: throw IllegalArgumentException("versionCode '$versionCode' is not a number")
        )

        private fun normalizeUrl(url: String): String {
            require(url.startsWith("http://") || url.startsWith("https://")) {
                "Backend URL must start with http:// or https://"
            }
            return if (url.endsWith("/")) url else "$url/"
        }
    }
}
