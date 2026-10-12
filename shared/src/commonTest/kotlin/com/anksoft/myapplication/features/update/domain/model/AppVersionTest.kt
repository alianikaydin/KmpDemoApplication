package com.anksoft.myapplication.features.update.domain.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNull
import kotlin.test.Test

class AppVersionTest {

    // AC-16
    @Test
    fun parseFillsMissingComponentsWithZero() {
        assertThat(AppVersion.parse("1")).isEqualTo(AppVersion(1, 0, 0))
        assertThat(AppVersion.parse("1.2")).isEqualTo(AppVersion(1, 2, 0))
        assertThat(AppVersion.parse("1.2.3")).isEqualTo(AppVersion(1, 2, 3))
    }

    // AC-15
    @Test
    fun parseAcceptsZeroAndTheLargestNineDigitComponent() {
        assertThat(AppVersion.parse("0.0.0")).isEqualTo(AppVersion(0, 0, 0))
        assertThat(AppVersion.parse("999999999.0.0")).isEqualTo(AppVersion(999_999_999, 0, 0))
    }

    // AC-15
    @Test
    fun parseRejectsEverythingThatIsNotOneToThreeNumericComponents() {
        listOf(
            "", "abc", "1.2.3.4", "v1.2", "1.2-beta", "1..2", "1.", ".1", "-1", " 1.2", "1.2 ",
            "1000000000.0.0", "1.2.x", "١.٢",
        ).forEach { raw ->
            assertThat(AppVersion.parse(raw), name = "parse(\"$raw\")").isNull()
        }
    }

    // AC-16
    @Test
    fun componentsAreComparedAsNumbersNotAsText() {
        assertThat(AppVersion(1, 10, 0)).isGreaterThan(AppVersion(1, 9, 0))
        assertThat(AppVersion(2, 0, 0)).isGreaterThan(AppVersion(1, 99, 99))
        assertThat(AppVersion(1, 2, 3)).isGreaterThan(AppVersion(1, 2, 2))
        assertThat(AppVersion(1, 0, 9)).isLessThan(AppVersion(1, 1, 0))
    }

    // AC-16
    @Test
    fun aShortVersionEqualsItsPaddedForm() {
        assertThat(AppVersion.parse("1.2")).isEqualTo(AppVersion.parse("1.2.0"))
        assertThat(AppVersion.parse("01.2")).isEqualTo(AppVersion.parse("1.2.0"))
    }

    @Test
    fun toStringPrintsAllThreeComponents() {
        assertThat(AppVersion.parse("1.2").toString()).isEqualTo("1.2.0")
    }
}
