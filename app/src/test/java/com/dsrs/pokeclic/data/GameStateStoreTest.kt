/**
 * Round-trip del snapshot contra un `SharedPreferences` en memoria: es la
 * única forma de cubrir el cableado de claves (un typo en `KEY_CAUGHT`
 * perdería el historial de la Pokédex sin que ningún test lo note).
 */
package com.dsrs.pokeclic.data

import android.content.SharedPreferences
import com.dsrs.pokeclic.model.GameSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateStoreTest {

    @Test
    fun snapshot_roundTripsThroughSharedPreferences() {
        val prefs = FakePrefs()
        val store = SharedPreferencesGameStateStore(prefs)
        val snapshot = GameSnapshot(
            playerName = "Ash",
            coins = 42L,
            seenSpecies = setOf("bulbasaur", "pidgey"),
            rewardedSpecies = setOf("pidgey"),
            caughtSpecies = setOf("bulbasaur", "pidgey", "rattata"),
            medals = setOf("medal_brock"),
            starterSpeciesId = "bulbasaur",
            starterBond = 3,
            ownedSpecies = setOf("pidgey"),
            leadSpeciesId = "pidgey",
            gymClearedRun = setOf("brock"),
            zoneIndex = 2,
            zoneClicks = 7L
        )

        store.save(snapshot)

        // La clave persistida es parte del contrato con los guardados previos.
        assertTrue(prefs.contains("caught"))
        assertEquals(snapshot, store.load())
    }

    @Test
    fun oldSaveWithoutCaughtKey_loadsEmptyHistory() {
        val prefs = FakePrefs()
        prefs.values["coins"] = 10L
        prefs.values["seen"] = "bulbasaur"

        val loaded = SharedPreferencesGameStateStore(prefs).load()!!

        assertEquals(emptySet<String>(), loaded.caughtSpecies)
        assertEquals(setOf("bulbasaur"), loaded.seenSpecies)
    }

    @Test
    fun clear_removesEveryPersistedValue() {
        val prefs = FakePrefs()
        val store = SharedPreferencesGameStateStore(prefs)
        store.save(GameSnapshot(coins = 1L))

        assertTrue(store.clear())
        assertNull(store.load())
        assertTrue(prefs.values.isEmpty())
    }

    /** `SharedPreferences` en memoria: valores y editor viven en el mismo mapa. */
    private class FakePrefs : SharedPreferences {
        val values = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getString(key: String?, defValue: String?): String? =
            values[key] as? String ?: defValue

        override fun getStringSet(
            key: String?,
            defValues: MutableSet<String>?
        ): MutableSet<String>? = @Suppress("UNCHECKED_CAST") (values[key] as? MutableSet<String>) ?: defValues

        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            values[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor(values)

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        private class Editor(
            private val values: MutableMap<String, Any?>
        ) : SharedPreferences.Editor {

            override fun putString(key: String?, value: String?) = apply { values[key!!] = value }

            override fun putStringSet(key: String?, values: MutableSet<String>?) =
                apply { this@Editor.values[key!!] = values }

            override fun putInt(key: String?, value: Int) = apply { values[key!!] = value }

            override fun putLong(key: String?, value: Long) = apply { values[key!!] = value }

            override fun putFloat(key: String?, value: Float) = apply { values[key!!] = value }

            override fun putBoolean(key: String?, value: Boolean) = apply { values[key!!] = value }

            override fun remove(key: String?) = apply { values.remove(key) }

            override fun clear() = apply { values.clear() }

            override fun commit(): Boolean = true

            override fun apply() = Unit
        }
    }
}
