package com.anksoft.myapplication.core.crash

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.matches
import com.anksoft.myapplication.core.storage.ThrowingSettings
import com.russhwolf.settings.MapSettings
import kotlin.test.Test

class InstallIdStoreTest {

    private val storage = MapSettings()

    // AC-17
    @Test
    fun createsOnceAndReturnsTheSameIdAcrossInstances() {
        val first = InstallIdStore({ storage }).getOrCreate()
        val second = InstallIdStore({ storage }).getOrCreate()

        assertThat(second).isEqualTo(first)
        assertThat(storage.getStringOrNull(INSTALL_ID_KEY)).isEqualTo(first)
    }

    // AC-17
    @Test
    fun resetMakesTheNextIdDifferent() {
        val store = InstallIdStore({ storage })
        val before = store.getOrCreate()

        store.reset()

        assertThat(storage.getStringOrNull(INSTALL_ID_KEY)).isEqualTo(null)
        assertThat(store.getOrCreate()).isNotEqualTo(before)
    }

    @Test
    fun brokenStorageFallsBackToInMemoryIdWithoutThrowing() {
        val broken = ThrowingSettings(failReads = true, failWrites = true, failRemoveFor = setOf(INSTALL_ID_KEY))
        val store = InstallIdStore({ broken })

        val first = store.getOrCreate()

        assertThat(store.getOrCreate()).isEqualTo(first)
        store.reset()
        assertThat(store.getOrCreate()).isNotEqualTo(first)
    }

    @Test
    fun storageThatCannotBeResolvedDoesNotThrow() {
        val store = InstallIdStore({ error("no storage") })

        assertThat(store.getOrCreate().isNotBlank()).isEqualTo(true)
    }

    // AC-16: the id is random, not derived from a user, e-mail or session.
    @Test
    fun idIsARandomUuidUnrelatedToSession() {
        val ids = List(5) { InstallIdStore({ MapSettings() }).getOrCreate() }

        ids.forEach { assertThat(it).matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) }
        assertThat(ids.toSet().size).isEqualTo(ids.size)
    }

    @Test
    fun theStoreNeverTouchesStorageUntilAnIdIsAsked() {
        var resolved = false
        InstallIdStore({ resolved = true; storage })

        assertThat(resolved).isEqualTo(false)
    }
}
