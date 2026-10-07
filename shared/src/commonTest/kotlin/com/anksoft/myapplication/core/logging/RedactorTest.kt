package com.anksoft.myapplication.core.logging

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import kotlin.test.Test

class RedactorTest {

    // AC-4
    @Test
    fun emailAddressIsMasked() {
        val result = Redactor.redact("login for jane.doe+x@example.co.uk failed")

        assertThat(result).isEqualTo("login for *** failed")
    }

    // AC-4
    @Test
    fun bearerTokenIsMaskedCaseInsensitively() {
        val result = Redactor.redact("Authorization: bearer abc.DEF-123_x")

        assertThat(result).isEqualTo("Authorization: Bearer ***")
    }

    // AC-4
    @Test
    fun tokenKeyValuePairsAreMaskedButKeyStaysReadable() {
        val result = Redactor.redact("token=abc123 refresh_token: r-456 access_token=a1")

        assertThat(result).isEqualTo("token=*** refresh_token: *** access_token=***")
    }

    // AC-4
    @Test
    fun jsonPasswordFieldIsMasked() {
        val result = Redactor.redact("""{"password":"Secret123","other":"ok"}""")

        assertThat(result).doesNotContain("Secret123")
        assertThat(result).contains(""""password":"***"""")
        assertThat(result).contains(""""other":"ok"""")
    }

    // AC-4
    @Test
    fun turkishPasswordKeyIsMasked() {
        assertThat(Redactor.redact("parola=Gizli1")).isEqualTo("parola=***")
    }

    // AC-4
    @Test
    fun jwtIsMasked() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJl"

        val result = Redactor.redact("session $jwt created")

        assertThat(result).isEqualTo("session *** created")
    }

    // AC-4
    @Test
    fun queryStringTokenIsMasked() {
        val result = Redactor.redact("GET /auth/me?token=s3cret&x=1")

        assertThat(result).isEqualTo("GET /auth/me?token=***&x=1")
    }

    @Test
    fun ordinaryTextIsLeftUntouched() {
        val text = "Started env=DEV version=1.0 path=auth/login status=500"

        assertThat(Redactor.redact(text)).isEqualTo(text)
    }
}
