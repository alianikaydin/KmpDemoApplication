package com.anksoft.myapplication.core.storage

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import java.security.KeyStoreException
import javax.crypto.SecretKey
import kotlin.io.encoding.Base64
import kotlin.test.Test

class AesGcmSessionCipherTest {

    private val keys = SoftwareKeyProvider()
    private val cipher = AesGcmSessionCipher(keys)

    // AC-1
    @Test
    fun decryptReturnsTheEncryptedValue() {
        val encoded = cipher.encrypt("access-token-123", "auth_token")

        assertThat(cipher.decrypt(encoded, "auth_token")).isEqualTo("access-token-123")
    }

    // AC-1
    @Test
    fun encodedValueDoesNotContainThePlaintext() {
        val encoded = cipher.encrypt("access-token-123", "auth_token")
        val raw = Base64.Default.decode(encoded).decodeToString()

        assertThat(encoded).doesNotContain("access-token-123")
        assertThat(raw).doesNotContain("access-token-123")
    }

    @Test
    fun sameValueEncryptedTwiceUsesDifferentIvs() {
        val first = cipher.encrypt("same", "auth_token")
        val second = cipher.encrypt("same", "auth_token")

        assertThat(first).isNotEqualTo(second)
    }

    // AC-6
    @Test
    fun differentAssociatedDataFailsToDecrypt() {
        val encoded = cipher.encrypt("access-token-123", "auth_token")

        assertFailure { cipher.decrypt(encoded, "refresh_token") }
            .isInstanceOf(SessionCipherException::class)
    }

    // AC-6
    @Test
    fun tamperedCiphertextFailsToDecrypt() {
        val bytes = Base64.Default.decode(cipher.encrypt("access-token-123", "auth_token"))
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()

        assertFailure { cipher.decrypt(Base64.Default.encode(bytes), "auth_token") }
            .isInstanceOf(SessionCipherException::class)
    }

    // AC-6
    @Test
    fun tooShortValueFailsToDecrypt() {
        assertFailure { cipher.decrypt(Base64.Default.encode(ByteArray(10)), "auth_token") }
            .isInstanceOf(SessionCipherException::class)
    }

    // AC-6
    @Test
    fun valueThatIsNotBase64FailsToDecrypt() {
        assertFailure { cipher.decrypt("not base64 !!!", "auth_token") }
            .isInstanceOf(SessionCipherException::class)
    }

    // AC-6
    @Test
    fun decryptWithAReplacedKeyFails() {
        val encoded = cipher.encrypt("access-token-123", "auth_token")
        val other = AesGcmSessionCipher(SoftwareKeyProvider())

        assertFailure { other.decrypt(encoded, "auth_token") }
            .isInstanceOf(SessionCipherException::class)
    }

    // AC-6
    @Test
    fun decryptWithoutAKeyFailsAndDoesNotCreateOne() {
        val provider = SoftwareKeyProvider(initial = null)
        val noKeyCipher = AesGcmSessionCipher(provider)

        assertFailure { noKeyCipher.decrypt(cipher.encrypt("x", "auth_token"), "auth_token") }
            .isInstanceOf(SessionCipherException::class)
        assertThat(provider.existing()).isNull()
        assertThat(provider.createCalls).isEqualTo(0)
    }

    @Test
    fun encryptCreatesTheKeyWhenMissing() {
        val provider = SoftwareKeyProvider(initial = null)
        val freshCipher = AesGcmSessionCipher(provider)

        val encoded = freshCipher.encrypt("access-token-123", "auth_token")

        assertThat(provider.createCalls).isEqualTo(1)
        assertThat(freshCipher.decrypt(encoded, "auth_token")).isEqualTo("access-token-123")
    }

    // AC-7
    @Test
    fun keyProviderFailureOnEncryptSurfacesAsCipherException() {
        val failing = object : SecretKeyProvider {
            override fun existing() = null
            override fun getOrCreate(): SecretKey = throw KeyStoreException("boom")
            override fun delete() = Unit
        }

        assertFailure { AesGcmSessionCipher(failing).encrypt("secret-value", "auth_token") }
            .isInstanceOf(SessionCipherException::class)
            .hasMessage("Session cipher operation failed")
    }

    @Test
    fun deleteKeyRemovesTheKey() {
        cipher.deleteKey()

        assertThat(keys.existing()).isNull()
        assertThat(keys.deleteCalls).isEqualTo(1)
    }
}
