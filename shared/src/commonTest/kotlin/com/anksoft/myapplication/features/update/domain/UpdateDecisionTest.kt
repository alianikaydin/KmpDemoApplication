package com.anksoft.myapplication.features.update.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.anksoft.myapplication.features.update.domain.model.AppVersion
import com.anksoft.myapplication.features.update.domain.model.UpdateRequirement
import com.anksoft.myapplication.features.update.domain.model.VersionPolicy
import kotlin.test.Test

class UpdateDecisionTest {

    private fun v(raw: String): AppVersion = requireNotNull(AppVersion.parse(raw)) { "bad test version $raw" }

    private fun decide(installed: String, min: String, latest: String, dismissed: String? = null) =
        decideUpdate(v(installed), VersionPolicy(v(min), v(latest)), dismissed?.let(::v))

    // AC-1
    @Test
    fun installedBelowMinimumRequiresAnUpdate() {
        assertThat(decide("1.0.0", min = "1.1.0", latest = "1.2.0")).isEqualTo(UpdateRequirement.Required)
    }

    // AC-5
    @Test
    fun installedEqualToMinimumIsNotBlocked() {
        assertThat(decide("1.1.0", min = "1.1.0", latest = "1.1.0")).isEqualTo(UpdateRequirement.None)
    }

    // AC-7
    @Test
    fun installedBetweenMinimumAndLatestGetsAnOptionalUpdate() {
        assertThat(decide("1.1.0", min = "1.1.0", latest = "1.2.0"))
            .isEqualTo(UpdateRequirement.Optional(v("1.2.0")))
    }

    // AC-11
    @Test
    fun installedEqualToOrNewerThanLatestNeedsNothing() {
        assertThat(decide("1.2.0", min = "1.0.0", latest = "1.2.0")).isEqualTo(UpdateRequirement.None)
        assertThat(decide("1.3.0", min = "1.0.0", latest = "1.2.0")).isEqualTo(UpdateRequirement.None)
    }

    // AC-12
    @Test
    fun requiredWinsWhenBothConditionsHold() {
        assertThat(decide("1.0.0", min = "1.1.0", latest = "1.2.0")).isEqualTo(UpdateRequirement.Required)
    }

    // AC-12
    @Test
    fun requiredIsNotSkippedByAnEarlierLater() {
        assertThat(decide("1.0.0", min = "1.1.0", latest = "1.2.0", dismissed = "1.2.0"))
            .isEqualTo(UpdateRequirement.Required)
    }

    // AC-8
    @Test
    fun dismissedLatestIsNotAskedAgain() {
        assertThat(decide("1.1.0", min = "1.0.0", latest = "1.4.0", dismissed = "1.4.0"))
            .isEqualTo(UpdateRequirement.None)
    }

    // AC-9
    @Test
    fun aNewerLatestThanTheDismissedOneAsksOnceMore() {
        assertThat(decide("1.1.0", min = "1.0.0", latest = "1.5.0", dismissed = "1.4.0"))
            .isEqualTo(UpdateRequirement.Optional(v("1.5.0")))
    }

    // AC-8
    @Test
    fun aDismissedVersionAboveLatestIsNotAskedAgain() {
        assertThat(decide("1.1.0", min = "1.0.0", latest = "1.4.0", dismissed = "1.9.0"))
            .isEqualTo(UpdateRequirement.None)
    }

    // AC-16
    @Test
    fun versionsAreComparedNumerically() {
        assertThat(decide("1.9.0", min = "1.10.0", latest = "1.10.0")).isEqualTo(UpdateRequirement.Required)
        assertThat(decide("1.2", min = "1.2.0", latest = "1.2.0")).isEqualTo(UpdateRequirement.None)
    }

    // AC-5
    @Test
    fun defaultPolicyOfZerosNeverAsksForAnything() {
        assertThat(decide("1.0.0", min = "0.0.0", latest = "0.0.0")).isEqualTo(UpdateRequirement.None)
    }
}
