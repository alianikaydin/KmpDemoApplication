package com.anksoft.myapplication.core.preferences

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.Test

class ThemeModeTest {

    // AC-1
    @Test
    fun nullOrBlankStoredValueIsSystem() {
        assertThat(ThemeMode.fromStored(null)).isEqualTo(ThemeMode.SYSTEM)
        assertThat(ThemeMode.fromStored("")).isEqualTo(ThemeMode.SYSTEM)
        assertThat(ThemeMode.fromStored("  ")).isEqualTo(ThemeMode.SYSTEM)
    }

    // AC-1
    @Test
    fun unknownStoredValueFallsBackToSystem() {
        assertThat(ThemeMode.fromStored("sepia")).isEqualTo(ThemeMode.SYSTEM)
        assertThat(ThemeMode.fromStored("DARK")).isEqualTo(ThemeMode.SYSTEM)
    }

    // AC-1
    @Test
    fun storedValuesRoundTrip() {
        ThemeMode.entries.forEach { mode ->
            assertThat(ThemeMode.fromStored(mode.storedValue)).isEqualTo(mode)
        }
    }

    // AC-1, AC-4
    @Test
    fun systemFollowsTheDevice() {
        assertThat(ThemeMode.SYSTEM.isDark(systemInDarkTheme = true)).isTrue()
        assertThat(ThemeMode.SYSTEM.isDark(systemInDarkTheme = false)).isFalse()
    }

    // AC-3, AC-5
    @Test
    fun lightAndDarkIgnoreTheDevice() {
        assertThat(ThemeMode.LIGHT.isDark(systemInDarkTheme = true)).isFalse()
        assertThat(ThemeMode.LIGHT.isDark(systemInDarkTheme = false)).isFalse()
        assertThat(ThemeMode.DARK.isDark(systemInDarkTheme = true)).isTrue()
        assertThat(ThemeMode.DARK.isDark(systemInDarkTheme = false)).isTrue()
    }

    // AC-15: pins the contract with the inline script in webApp/.../index.html.
    @Test
    fun storedFormatMatchesTheWebShim() {
        assertThat(ThemeMode.SYSTEM.storedValue).isNull()
        assertThat(ThemeMode.LIGHT.storedValue).isEqualTo("light")
        assertThat(ThemeMode.DARK.storedValue).isEqualTo("dark")
        assertThat(THEME_KEY).isEqualTo("app_prefs.theme")
    }
}
