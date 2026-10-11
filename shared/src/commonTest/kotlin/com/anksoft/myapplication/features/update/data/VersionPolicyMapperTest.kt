package com.anksoft.myapplication.features.update.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.anksoft.kmpdemo.contract.app.VersionPolicyDto
import com.anksoft.myapplication.features.update.data.mapper.toContractValue
import com.anksoft.myapplication.features.update.data.mapper.toVersionPolicyOrNull
import com.anksoft.myapplication.features.update.domain.model.AppVersion
import com.anksoft.myapplication.features.update.domain.model.UpdatePlatform
import com.anksoft.myapplication.features.update.domain.model.VersionPolicy
import kotlin.test.Test

class VersionPolicyMapperTest {

    // AC-15
    @Test
    fun aValidDtoBecomesAPolicy() {
        val policy = VersionPolicyDto(minSupportedVersion = "1.2.0", latestVersion = "2.0.1").toVersionPolicyOrNull()

        assertThat(policy).isEqualTo(VersionPolicy(AppVersion(1, 2, 0), AppVersion(2, 0, 1)))
    }

    // AC-15
    @Test
    fun anUnreadableMinimumMakesTheWholePolicyUnusable() {
        listOf("abc", "", "1.2.3.4").forEach { bad ->
            assertThat(VersionPolicyDto(bad, "1.0.0").toVersionPolicyOrNull()).isNull()
        }
    }

    // AC-15
    @Test
    fun anUnreadableLatestMakesTheWholePolicyUnusable() {
        listOf("abc", "", "1.2.3.4").forEach { bad ->
            assertThat(VersionPolicyDto("1.0.0", bad).toVersionPolicyOrNull()).isNull()
        }
    }

    // AC-18
    @Test
    fun platformsMapToTheLowerCaseContractValues() {
        assertThat(UpdatePlatform.ANDROID.toContractValue()).isEqualTo("android")
        assertThat(UpdatePlatform.IOS.toContractValue()).isEqualTo("ios")
    }
}
