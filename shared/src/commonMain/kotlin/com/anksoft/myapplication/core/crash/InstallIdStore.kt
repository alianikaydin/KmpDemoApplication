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
        val read = runCatching { settingsProvider().getStringOrNull(INSTALL_ID_KEY) }
        val stored = read.getOrNull()
        if (!stored.isNullOrBlank()) return stored
        val created = newId()
        // A failed read says nothing about what is stored, so do not overwrite it: keep the new id
        // in memory for this process. A failed write is handled the same way.
        val persisted = read.isSuccess &&
            runCatching { settingsProvider().putString(INSTALL_ID_KEY, created) }.isSuccess
        if (!persisted) memoryId = created
        return created
    }

    /** Forgets the id; the next [getOrCreate] returns a new one. */
    fun reset() {
        memoryId = null
        runCatching { settingsProvider().remove(INSTALL_ID_KEY) }
    }
}
