package com.anksoft.myapplication.core.network.mock

/**
 * Version policies the mock backend can answer with. `NONE` is the default, so a demo build never
 * shows an update screen unless a launch argument asks for one (AC-19, AC-27).
 */
enum class DemoVersionPolicy(val minSupported: String, val latest: String) {
    NONE(minSupported = "0.0.0", latest = "0.0.0"),
    OPTIONAL(minSupported = "0.0.0", latest = "999.0.0"),
    REQUIRED(minSupported = "999.0.0", latest = "999.0.0");

    companion object {
        /** Case-insensitive; null and unknown names mean [NONE]. */
        fun parse(raw: String?): DemoVersionPolicy =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NONE
    }
}
