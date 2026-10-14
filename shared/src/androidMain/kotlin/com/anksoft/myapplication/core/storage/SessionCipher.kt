package com.anksoft.myapplication.core.storage

import java.security.GeneralSecurityException
import javax.crypto.SecretKey

/**
 * Encrypts and decrypts the session strings kept by [EncryptedSessionSettings].
 *
 * The encoded form is opaque to callers. [associatedData] binds a ciphertext to the preference key
 * it is stored under, so a value copied to another key fails to decrypt.
 */
internal interface SessionCipher {
    /** Never returns the plaintext. Throws [SessionCipherException] on any failure. */
    fun encrypt(plaintext: String, associatedData: String): String

    /** Throws [SessionCipherException] when the value is corrupt, tampered with or the key is gone. */
    fun decrypt(encoded: String, associatedData: String): String

    /** Destroys the key; everything encrypted so far becomes undecryptable. */
    fun deleteKey()
}

/** Source of the AES key; the Android Keystore in production, a software key in tests. */
internal interface SecretKeyProvider {
    /** The current key, or null when none exists. Never creates one. */
    fun existing(): SecretKey?

    fun getOrCreate(): SecretKey

    fun delete()
}

/** Fixed message on purpose: it must never carry a session value or key material. */
internal class SessionCipherException(cause: Throwable? = null) :
    GeneralSecurityException("Session cipher operation failed", cause)
