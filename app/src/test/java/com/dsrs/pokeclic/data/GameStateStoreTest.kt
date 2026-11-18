/**
 * Round-trip del snapshot contra un `SharedPreferences` en memoria: es la
 * única forma de cubrir el cableado de claves (un typo en `KEY_CAUGHT`
 * perdería el historial de la Pokédex sin que ningún test lo note).
 */
package com.dsrs.pokeclic.data

import android.content.SharedPreferences
import com.dsrs.pokeclic.model.GameSnapshot
import com.dsrs.pokeclic.model.TutorialPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ───────────────── Versión de esquema y validación ─────────────────

    @Test
    fun save_writesTheSchemaVersionContract() {
        val prefs = FakePrefs()
        SharedPreferencesGameStateStore(prefs).save(GameSnapshot(coins = 1L))

        // La versión en disco es parte del contrato: cambiarla exige migración.
        assertEquals(2, prefs.values["schema_version"])
    }

    @Test
    fun load_saveFromAFutureSchema_returnsNull() {
        // Un formato que esta versión no conoce no se reinterpreta a ciegas.
        val prefs = FakePrefs()
        prefs.values["coins"] = 10L
        prefs.values["schema_version"] = 99

        assertNull(SharedPreferencesGameStateStore(prefs).load())
    }

    @Test
    fun load_legacySaveWithoutVersion_stillLoads() {
        val prefs = FakePrefs()
        prefs.values["coins"] = 7L
        prefs.values["zone_index"] = 1

        val loaded = SharedPreferencesGameStateStore(prefs).load()!!

        assertEquals(7L, loaded.coins)
        assertEquals(1, loaded.zoneIndex)
    }

    @Test
    fun load_negativeProgress_normalizesToZero() {
        val prefs = FakePrefs()
        prefs.values["coins"] = 10L
        prefs.values["zone_index"] = -4
        prefs.values["zone_clicks"] = -9L
        prefs.values["starter_bond"] = -3

        val loaded = SharedPreferencesGameStateStore(prefs).load()!!

        assertEquals(0, loaded.zoneIndex)
        assertEquals(0L, loaded.zoneClicks)
        assertEquals(0, loaded.starterBond)
    }

    // ───────────────── Persistencia del tutorial ─────────────────

    @Test
    fun tutorialPhase_roundTripsThroughSharedPreferences() {
        val prefs = FakePrefs()
        val store = SharedPreferencesGameStateStore(prefs)
        val snapshot = GameSnapshot(
            coins = 5L,
            starterSpeciesId = "charmander",
            tutorialPhase = TutorialPhase.RETURN_POKEDEX
        )

        store.save(snapshot)

        // La clave nueva es parte del contrato de guardado.
        assertTrue(prefs.contains("tutorial_phase"))
        assertEquals(TutorialPhase.RETURN_POKEDEX, store.load()!!.tutorialPhase)
    }

    @Test
    fun tutorialPhase_everyPhaseRoundTripsExactly() {
        val store = SharedPreferencesGameStateStore(FakePrefs())
        TutorialPhase.entries.forEach { phase ->
            store.save(GameSnapshot(coins = 1L, tutorialPhase = phase))
            assertEquals(
                "La reapertura debe conservar exactamente $phase",
                phase,
                store.load()!!.tutorialPhase
            )
        }
    }

    @Test
    fun oldSaveWithoutTutorialKey_withStarter_loadsCompleted() {
        val prefs = FakePrefs()
        prefs.values["coins"] = 10L
        prefs.values["starter"] = "charmander"

        val loaded = SharedPreferencesGameStateStore(prefs).load()!!

        assertEquals(TutorialPhase.COMPLETED, loaded.tutorialPhase)
        // El resto de la partida no se altera por la clave nueva.
        assertEquals("charmander", loaded.starterSpeciesId)
    }

    @Test
    fun oldSaveWithoutTutorialKey_withoutStarter_loadsPending() {
        val prefs = FakePrefs()
        prefs.values["coins"] = 10L

        val loaded = SharedPreferencesGameStateStore(prefs).load()!!

        assertEquals(TutorialPhase.PENDING, loaded.tutorialPhase)
    }

    @Test
    fun corruptTutorialValue_loadsCompleted() {
        val prefs = FakePrefs()
        prefs.values["coins"] = 10L
        prefs.values["starter"] = "bulbasaur"
        prefs.values["tutorial_phase"] = "valor_de_una_version_futura"

        val loaded = SharedPreferencesGameStateStore(prefs).load()!!

        assertEquals(TutorialPhase.COMPLETED, loaded.tutorialPhase)
    }

    @Test
    fun tutorialMessageClosed_roundTripsThroughSharedPreferences() {
        val prefs = FakePrefs()
        val store = SharedPreferencesGameStateStore(prefs)
        val snapshot = GameSnapshot(
            coins = 5L,
            starterSpeciesId = "charmander",
            tutorialPhase = TutorialPhase.DEPLOY_POKEBALL,
            tutorialMessageClosed = true
        )

        store.save(snapshot)

        assertTrue(prefs.contains("tutorial_message_closed"))
        assertEquals(true, store.load()!!.tutorialMessageClosed)
    }

    @Test
    fun oldSaveWithoutMessageClosedKey_defaultsToVisibleMessage() {
        // Guardado antiguo (o nuevo paso): sin la clave, el mensaje está visible.
        val prefs = FakePrefs()
        prefs.values["coins"] = 10L
        prefs.values["starter"] = "charmander"
        prefs.values["tutorial_phase"] = "DEPLOY_POKEBALL"

        val loaded = SharedPreferencesGameStateStore(prefs).load()!!

        assertEquals(false, loaded.tutorialMessageClosed)
    }

    @Test
    fun clear_alsoRemovesTheTutorialKeys() {
        val prefs = FakePrefs()
        val store = SharedPreferencesGameStateStore(prefs)
        store.save(GameSnapshot(coins = 1L, tutorialPhase = TutorialPhase.EXP_NOTICE))

        assertTrue(store.clear())
        assertNull(store.load())
        assertFalse(prefs.contains("tutorial_phase"))
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
