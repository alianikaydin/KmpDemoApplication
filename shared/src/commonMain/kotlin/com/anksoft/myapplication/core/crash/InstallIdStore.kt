package com.anksoft.myapplication.core.crash

import com.russhwolf.settings.Settings
import kotlin.uuid.Uuid

/** Key of the anonymous install id inside the crash settings store. */
internal const val INSTALL_ID_KEY = "crash.install_id"

/**
 * Random install id that has no relation to the account (so signing out and in does not change
 * it), stored apart from the session and the preferences. Reads and writes never throw: when the
 * storage is broken, an id held in memory for the process lifetime is used instead.
 *
 * [settingsProvider] is lazy on purpose: the store is only touched once consent is granted, so a
 * build without a crash vendor never writes an identifier.
 */
class InstallIdStore(
    private val settingsProvider: () -> Settings,
    private val newId: () -> String = { Uuid.random().toString() },
) {
    // Only used when the storage fails; guarded by the single-writer use from the controller.
    private var memoryId: String? = null

    fun getOrCreate(): String {
        memoryId?.let { return it }
        val stored = runCatching { settingsProvider().getStringOrNull(INSTALL_ID_KEY) }.getOrNull()
        if (!stored.isNullOrBlank()) return stored
        val created = newId()
        val persisted = runCatching { settingsProvider().putString(INSTALL_ID_KEY, created) }.isSuccess
        // Remember the id in memory only when it could not be stored, so the next call matches.
        if (!persisted) memoryId = created
        return created
    }

    /** Forgets the id; the next [getOrCreate] returns a new one. */
    fun reset() {
        memoryId = null
        runCatching { settingsProvider().remove(INSTALL_ID_KEY) }
    }
}
