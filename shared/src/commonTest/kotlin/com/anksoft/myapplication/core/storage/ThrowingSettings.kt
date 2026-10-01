package com.anksoft.myapplication.core.storage

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings

/** Settings whose string reads, writes and removals fail on demand, like a broken Keychain. */
class ThrowingSettings(
    private val delegate: Settings = MapSettings(),
    var failReads: Boolean = false,
    var failWrites: Boolean = false,
    var failRemoveFor: Set<String> = emptySet(),
) : Settings by delegate {

    override fun getStringOrNull(key: String): String? {
        if (failReads) error("Storage read failed")
        return delegate.getStringOrNull(key)
    }

    override fun putString(key: String, value: String) {
        if (failWrites) error("Storage write failed")
        delegate.putString(key, value)
    }

    override fun remove(key: String) {
        if (key in failRemoveFor) error("Storage remove failed")
        delegate.remove(key)
    }
}

/** Settings that accept string writes but silently drop them. */
class WriteDroppingSettings(
    private val delegate: Settings = MapSettings(),
) : Settings by delegate {

    override fun putString(key: String, value: String) = Unit
}
