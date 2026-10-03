package com.anksoft.myapplication.features.auth.domain

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class UserDataValidatorTest {

    private val validator = UserDataValidator()

    @Test
    fun acceptsWellFormedEmails() {
        listOf(
            "user@example.com",
            "first.last@example.co.uk",
            "user+tag@example.io",
            "u@sub.domain.example.com"
        ).forEach { email ->
            assertThat(validator.isValidEmail(email), name = email).isTrue()
        }
    }

    @Test
    fun rejectsMalformedEmails() {
        listOf(
            "",
            "   ",
            "plainstring",
            "no-at-sign.com",
            "@example.com",
            "user@",
            "user@example",
            "user@.com",
            "user name@example.com",
            "user@exam ple.com",
            "two@@example.com"
        ).forEach { email ->
            assertThat(validator.isValidEmail(email), name = email).isFalse()
        }
    }

    @Test
    fun trimsSurroundingWhitespaceBeforeValidating() {
        assertThat(validator.isValidEmail("  user@example.com  ")).isTrue()
    }

    @Test
    fun passwordMeetingEveryRuleHasNoUnmetRules() {
        assertThat(validator.validatePassword("Password1")).isEmpty()
    }

    @Test
    fun passwordReportsEveryUnmetRuleAtOnce() {
        assertThat(validator.validatePassword("abc")).containsExactlyInAnyOrder(
            PasswordError.TooShort,
            PasswordError.NoDigit,
            PasswordError.NoUppercase
        )
    }

    @Test
    fun passwordOfExactlyMinimumLengthIsAccepted() {
        assertThat(validator.validatePassword("Passwo1d")).isEmpty()
    }

    @Test
    fun passwordMissingOnlyADigitReportsOnlyThat() {
        assertThat(validator.validatePassword("PasswordX"))
            .containsExactlyInAnyOrder(PasswordError.NoDigit)
    }

    @Test
    fun passwordMissingOnlyUppercaseReportsOnlyThat() {
        assertThat(validator.validatePassword("password1"))
            .containsExactlyInAnyOrder(PasswordError.NoUppercase)
    }

    @Test
    fun unicodeUppercaseLetterCountsAsUppercase() {
        // Delegated to CredentialRules; a non-ASCII uppercase letter satisfies the rule.
        assertThat(validator.validatePassword("Ürünler1")).isEmpty()
    }

    @Test
    fun overlongPasswordIsNotReportedAsAChecklistError() {
        assertThat(validator.validatePassword("Aa1" + "x".repeat(200))).isEmpty()
    }
}
