package com.anksoft.myapplication.core.config

/** Deployment environment a build targets. Each one has its own backend and app identity. */
enum class AppEnvironment {
    DEV,
    STAGE,
    PROD;

    companion object {
        /**
         * Case-insensitive lookup of an environment by name.
         *
         * @throws IllegalArgumentException if [value] is blank or not a known environment.
         */
        fun parse(value: String): AppEnvironment =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Unknown environment '$value'; expected one of ${entries.joinToString { it.name }}"
                )
    }
}
