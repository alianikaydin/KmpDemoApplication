package com.anksoft.myapplication.features.update.domain.model

/**
 * An app version as numeric components. Compared component by component, never as text, so
 * `1.10.0` is newer than `1.9.0` and `1.2` equals `1.2.0` (AC-16).
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int = when {
        major != other.major -> major.compareTo(other.major)
        minor != other.minor -> minor.compareTo(other.minor)
        else -> patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        // At most nine digits per component keeps every value inside Int.
        private val FORMAT = Regex("[0-9]{1,9}(\\.[0-9]{1,9}){0,2}")

        /** 1 to 3 dot-separated components of 1..9 decimal digits; missing ones are 0. Anything else is null. */
        fun parse(raw: String): AppVersion? {
            if (!FORMAT.matches(raw)) return null
            val parts = raw.split('.').map { it.toInt() }
            return AppVersion(
                major = parts[0],
                minor = parts.getOrElse(1) { 0 },
                patch = parts.getOrElse(2) { 0 },
            )
        }
    }
}
