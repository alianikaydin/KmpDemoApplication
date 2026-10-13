package com.anksoft.myapplication.core.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.fail

class AppColorSchemesTest {

    // AC-8
    @Test
    fun lightSchemeTextPairsMeetTheTextContrast() {
        assertPairs("light", LightColorScheme)
    }

    // AC-3, AC-8
    @Test
    fun darkSchemeTextPairsMeetTheTextContrast() {
        assertPairs("dark", DarkColorScheme)
    }

    // AC-3, AC-9
    @Test
    fun darkSchemeIsDarkAndLightSchemeIsLight() {
        assertThat(luminance(DarkColorScheme.background)).isLessThan(0.2)
        assertThat(luminance(LightColorScheme.background)).isGreaterThan(0.8)
    }

    private fun assertPairs(name: String, scheme: ColorScheme) {
        val textPairs = with(scheme) {
            mapOf(
                "primary/onPrimary" to (primary to onPrimary),
                "primaryContainer/onPrimaryContainer" to (primaryContainer to onPrimaryContainer),
                "secondary/onSecondary" to (secondary to onSecondary),
                "secondaryContainer/onSecondaryContainer" to (secondaryContainer to onSecondaryContainer),
                "tertiary/onTertiary" to (tertiary to onTertiary),
                "tertiaryContainer/onTertiaryContainer" to (tertiaryContainer to onTertiaryContainer),
                "error/onError" to (error to onError),
                "errorContainer/onErrorContainer" to (errorContainer to onErrorContainer),
                "background/onBackground" to (background to onBackground),
                "surface/onSurface" to (surface to onSurface),
                "surface/onSurfaceVariant" to (surface to onSurfaceVariant),
                "surfaceVariant/onSurfaceVariant" to (surfaceVariant to onSurfaceVariant),
                "inverseSurface/inverseOnSurface" to (inverseSurface to inverseOnSurface),
                "surface/primary" to (surface to primary),
                "surface/error" to (surface to error),
                "background/error" to (background to error)
            )
        }
        textPairs.forEach { (pair, colors) -> assertRatio(name, pair, colors, minimum = 4.5) }
        // Non-text: the outline of an input field must stand out from the surface.
        assertRatio(name, "surface/outline", scheme.surface to scheme.outline, minimum = 3.0)
    }

    private fun assertRatio(scheme: String, pair: String, colors: Pair<Color, Color>, minimum: Double) {
        val ratio = contrastRatio(colors.first, colors.second)
        if (ratio < minimum) fail("$scheme scheme: $pair has contrast $ratio, needs at least $minimum")
    }

    /** WCAG contrast ratio; computed by hand so the host test needs no platform color code. */
    private fun contrastRatio(a: Color, b: Color): Double {
        val l1 = luminance(a)
        val l2 = luminance(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun luminance(color: Color): Double =
        0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

    private fun linear(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
}
