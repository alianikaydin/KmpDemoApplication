package com.anksoft.myapplication.core.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Keeps the session key in the Android Keystore under [alias]. The key never leaves the Keystore
 * and cannot be exported. No user authentication, unlocked-device or StrongBox requirement is
 * set: the session must stay readable in the background and on devices without those features.
 */
internal class AndroidKeystoreKeyProvider(
    private val alias: String = DEFAULT_ALIAS,
) : SecretKeyProvider {

    @Synchronized
    override fun existing(): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    @Synchronized
    override fun getOrCreate(): SecretKey {
        existing()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build()
        )
        return generator.generateKey()
    }

    @Synchronized
    override fun delete() {
        val store = keyStore()
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        const val DEFAULT_ALIAS = "session_aes_gcm_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_BITS = 256
    }
}
