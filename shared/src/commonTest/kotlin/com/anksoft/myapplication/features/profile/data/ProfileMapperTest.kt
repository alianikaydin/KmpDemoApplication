package com.anksoft.myapplication.features.profile.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.anksoft.kmpdemo.contract.auth.UserDto
import com.anksoft.myapplication.features.profile.data.mapper.toAccountProfile
import kotlin.test.Test

class ProfileMapperTest {

    // AC-1
    @Test
    fun mapsIdEmailAndNameFromTheUserDto() {
        val profile = UserDto(id = "u1", email = "user@example.com", name = "Test User").toAccountProfile()

        assertThat(profile.userId).isEqualTo("u1")
        assertThat(profile.email).isEqualTo("user@example.com")
        assertThat(profile.name).isEqualTo("Test User")
    }

    // AC-1
    @Test
    fun aMissingNameStaysMissing() {
        val profile = UserDto(id = "u1", email = "user@example.com", name = null).toAccountProfile()

        assertThat(profile.name).isNull()
    }

    // AC-1
    @Test
    fun anEmptyNameBecomesNoName() {
        val profile = UserDto(id = "u1", email = "user@example.com", name = "").toAccountProfile()

        assertThat(profile.name).isNull()
    }

    // AC-1
    @Test
    fun aBlankNameBecomesNoName() {
        val profile = UserDto(id = "u1", email = "user@example.com", name = "   ").toAccountProfile()

        assertThat(profile.name).isNull()
    }
}
