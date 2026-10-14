package com.anksoft.myapplication.core.storage

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** A plain in-memory AES key standing in for the Android Keystore, which host tests do not have. */
internal class SoftwareKeyProvider(initial: SecretKey? = newKey()) : SecretKeyProvider {
    private var key: SecretKey? = initial
    var deleteCalls = 0
        private set
    var createCalls = 0
        private set

    override fun existing(): SecretKey? = key

    override fun getOrCreate(): SecretKey = key ?: newKey().also {
        key = it
        createCalls++
    }

    override fun delete() {
        deleteCalls++
        key = null
    }

    companion object {
        fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }
}
