package com.anksoft.myapplication.core.storage

/** Reversible, readable-by-tests encoding with switchable failures. Provides no security. */
internal class FakeSessionCipher : SessionCipher {
    var failEncrypt = false
    var failDecrypt = false
    var deleteKeyCalls = 0
        private set

    override fun encrypt(plaintext: String, associatedData: String): String {
        if (failEncrypt) throw SessionCipherException()
        return "enc[$associatedData]:${plaintext.reversed()}"
    }

    override fun decrypt(encoded: String, associatedData: String): String {
        if (failDecrypt) throw SessionCipherException()
        val prefix = "enc[$associatedData]:"
        if (!encoded.startsWith(prefix)) throw SessionCipherException()
        return encoded.removePrefix(prefix).reversed()
    }

    override fun deleteKey() {
        deleteKeyCalls++
    }
}
