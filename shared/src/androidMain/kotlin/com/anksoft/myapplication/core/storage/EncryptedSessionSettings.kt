package com.anksoft.myapplication.core.storage

import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.error
import com.anksoft.myapplication.core.logging.warn
import com.russhwolf.settings.Settings
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException

/**
 * A [Settings] that keeps only encrypted strings in [store], a file used for nothing else.
 *
 * Failure behaviour is fail-closed (AC-6, AC-7); there is no path that writes a plain value:
 *   - A value that cannot be decrypted (corrupt, tampered with, key gone) wipes the whole store and
 *     the key and reads as missing, so a half-valid session is never left behind.
 *   - A value that cannot be encrypted is not written, the key's previous value is removed, and the
 *     exception propagates ([SessionManager] swallows it, leaving the user logged out).
 *
 * Only strings are supported; the session holds nothing else. Decrypted values are cached in
 * memory so the Keystore is touched once per key and process.
 */
internal class EncryptedSessionSettings(
    private val store: Settings,
    private val cipher: SessionCipher,
    private val logger: AppLogger,
) : Settings {

    private val cache = mutableMapOf<String, String>()

    override val keys: Set<String> @Synchronized get() = store.keys

    override val size: Int @Synchronized get() = store.size

    @Synchronized
    override fun clear() {
        cache.clear()
        store.clear()
    }

    @Synchronized
    override fun remove(key: String) {
        cache.remove(key)
        store.remove(key)
    }

    @Synchronized
    override fun hasKey(key: String): Boolean = store.hasKey(key)

    @Synchronized
    override fun getStringOrNull(key: String): String? {
        cache[key]?.let { return it }
        val encoded = store.getStringOrNull(key) ?: return null
        return try {
            cipher.decrypt(encoded, associatedData = key).also { cache[key] = it }
        } catch (e: Exception) {
            if (!e.isCryptoFailure()) throw e
            logger.warn(LogTags.SESSION, e) { "Stored session could not be decrypted; clearing it" }
            wipe()
            null
        }
    }

    override fun getString(key: String, defaultValue: String): String = getStringOrNull(key) ?: defaultValue

    @Synchronized
    override fun putString(key: String, value: String) {
        try {
            store.putString(key, cipher.encrypt(value, associatedData = key))
            cache[key] = value
        } catch (e: Exception) {
            if (!e.isCryptoFailure()) throw e
            cache.remove(key)
            runCatching { store.remove(key) }
            logger.error(LogTags.SESSION, e) { "Session value could not be encrypted; nothing was stored" }
            throw e
        }
    }

    private fun wipe() {
        cache.clear()
        runCatching { store.clear() }
        runCatching { cipher.deleteKey() }
    }

    private fun Exception.isCryptoFailure(): Boolean =
        this is GeneralSecurityException ||
            this is ProviderException ||
            this is IllegalArgumentException ||
            this is IOException

    override fun putInt(key: String, value: Int) = unsupported()
    override fun getInt(key: String, defaultValue: Int): Int = unsupported()
    override fun getIntOrNull(key: String): Int? = unsupported()
    override fun putLong(key: String, value: Long) = unsupported()
    override fun getLong(key: String, defaultValue: Long): Long = unsupported()
    override fun getLongOrNull(key: String): Long? = unsupported()
    override fun putFloat(key: String, value: Float) = unsupported()
    override fun getFloat(key: String, defaultValue: Float): Float = unsupported()
    override fun getFloatOrNull(key: String): Float? = unsupported()
    override fun putDouble(key: String, value: Double) = unsupported()
    override fun getDouble(key: String, defaultValue: Double): Double = unsupported()
    override fun getDoubleOrNull(key: String): Double? = unsupported()
    override fun putBoolean(key: String, value: Boolean) = unsupported()
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = unsupported()
    override fun getBooleanOrNull(key: String): Boolean? = unsupported()

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException("The session store only holds strings")
}
