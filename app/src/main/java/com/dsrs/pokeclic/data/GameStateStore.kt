package com.dsrs.pokeclic.data

import android.content.Context
import android.content.SharedPreferences
import com.dsrs.pokeclic.model.GameSnapshot
import com.dsrs.pokeclic.model.TutorialPhase
import com.dsrs.pokeclic.model.tutorialPhaseFromName

/**
 * Persistencia del estado de juego.
 *
 * La interfaz no depende de Android para que el ViewModel se pueda probar en la
 * JVM con un doble. La implementación usa `SharedPreferences`: el volumen es
 * mínimo y `apply()` no bloquea el hilo de UI. La única excepción es
 * [clear()], que usa `commit()` por ser destructiva.
 */
interface GameStateStore {
    /** Estado guardado, o `null` si es la primera partida. */
    fun load(): GameSnapshot?

    fun save(snapshot: GameSnapshot)

    /**
     * Borra todo lo persistido (partida nueva desde cero).
     *
     * Al ser una operación destructiva se escribe de forma **síncrona**
     * (`commit()`): devuelve `true` solo si el borrado quedó en disco, de modo
     * que un cierre inmediato a continuación no conserve el guardado antiguo.
     */
    fun clear(): Boolean
}

/**
 * Implementación de [GameStateStore] sobre `SharedPreferences`.
 *
 * Los conjuntos se serializan como cadenas delimitadas porque los ids del
 * contenido (especies, medallas) no contienen comas.
 *
 * El sistema de mejoras se retiró del modelo por no tener UI; cuando vuelva,
 * habrá que añadir aquí su mapa persistido (`upgrades`).
 */
class SharedPreferencesGameStateStore internal constructor(
    private val prefs: SharedPreferences
) : GameStateStore {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    override fun load(): GameSnapshot? {
        if (!prefs.contains(KEY_COINS)) return null
        return GameSnapshot(
            playerName = decodeNullable(prefs.getString(KEY_PLAYER_NAME, null)),
            coins = prefs.getLong(KEY_COINS, 0L),
            seenSpecies = decodeSet(prefs.getString(KEY_SEEN, null)),
            rewardedSpecies = decodeSet(prefs.getString(KEY_REWARDED, null)),
            caughtSpecies = decodeSet(prefs.getString(KEY_CAUGHT, null)),
            medals = decodeSet(prefs.getString(KEY_MEDALS, null)),
            starterSpeciesId = decodeNullable(prefs.getString(KEY_STARTER, null)),
            starterBond = prefs.getInt(KEY_BOND, 0),
            ownedSpecies = decodeSet(prefs.getString(KEY_OWNED, null)),
            leadSpeciesId = decodeNullable(prefs.getString(KEY_LEAD, null)),
            gymClearedRun = decodeSet(prefs.getString(KEY_GYM_CLEARED_RUN, null)),
            zoneIndex = prefs.getInt(KEY_ZONE_INDEX, 0),
            zoneClicks = prefs.getLong(KEY_ZONE_CLICKS, 0L),
            tutorialPhase = loadTutorialPhase(),
            tutorialMessageClosed = prefs.getBoolean(KEY_TUTORIAL_MESSAGE_CLOSED, false)
        )
    }

    override fun save(snapshot: GameSnapshot) {
        prefs.edit()
            .putString(KEY_PLAYER_NAME, encodeNullable(snapshot.playerName))
            .putLong(KEY_COINS, snapshot.coins)
            .putString(KEY_SEEN, encodeSet(snapshot.seenSpecies))
            .putString(KEY_REWARDED, encodeSet(snapshot.rewardedSpecies))
            .putString(KEY_CAUGHT, encodeSet(snapshot.caughtSpecies))
            .putString(KEY_MEDALS, encodeSet(snapshot.medals))
            .putString(KEY_STARTER, encodeNullable(snapshot.starterSpeciesId))
            .putInt(KEY_BOND, snapshot.starterBond)
            .putString(KEY_OWNED, encodeSet(snapshot.ownedSpecies))
            .putString(KEY_LEAD, encodeNullable(snapshot.leadSpeciesId))
            .putString(KEY_GYM_CLEARED_RUN, encodeSet(snapshot.gymClearedRun))
            .putInt(KEY_ZONE_INDEX, snapshot.zoneIndex)
            .putLong(KEY_ZONE_CLICKS, snapshot.zoneClicks)
            .putString(KEY_TUTORIAL_PHASE, snapshot.tutorialPhase.name)
            .putBoolean(KEY_TUTORIAL_MESSAGE_CLOSED, snapshot.tutorialMessageClosed)
            .apply()
    }

    override fun clear(): Boolean = prefs.edit().clear().commit()

    /**
     * Progreso del tutorial guardado, con la regla de compatibilidad de los
     * **guardados anteriores a esta característica**:
     *
     * - Clave presente y valor conocido → la fase exacta que se guardó.
     * - Clave presente con valor desconocido (datos corruptos o de una versión
     *   futura) → `COMPLETED`: nunca se re-ofrece un tutorial por datos ilegibles.
     * - Clave ausente → se deduce de la partida: `COMPLETED` si ya hay starter
     *   (jugador veterano al que no se le ofrece el tutorial) y `PENDING` si no
     *   (aún en la introducción de Oak, o partida recién creada).
     */
    private fun loadTutorialPhase(): TutorialPhase {
        val raw = prefs.getString(KEY_TUTORIAL_PHASE, null) ?: return legacyTutorialPhase()
        return tutorialPhaseFromName(raw, TutorialPhase.COMPLETED)
    }

    /** Fase del tutorial deducida para un guardado sin la clave nueva. */
    private fun legacyTutorialPhase(): TutorialPhase =
        if (decodeNullable(prefs.getString(KEY_STARTER, null)) != null) {
            TutorialPhase.COMPLETED
        } else {
            TutorialPhase.PENDING
        }

    private fun encodeNullable(value: String?): String = value.orEmpty()

    private fun decodeNullable(value: String?): String? = value?.takeIf { it.isNotEmpty() }

    private fun encodeSet(set: Set<String>): String =
        set.joinToString(ITEM_SEPARATOR)

    private fun decodeSet(value: String?): Set<String> =
        value?.takeIf { it.isNotEmpty() }
            ?.split(ITEM_SEPARATOR)
            ?.toSet()
            .orEmpty()

    private companion object {
        const val PREFS_NAME = "kanto_clicker"
        const val KEY_PLAYER_NAME = "player_name"
        const val KEY_COINS = "coins"
        const val KEY_SEEN = "seen"
        const val KEY_REWARDED = "rewarded"
        const val KEY_CAUGHT = "caught"
        const val KEY_MEDALS = "medals"
        const val KEY_STARTER = "starter"
        const val KEY_BOND = "starter_bond"
        const val KEY_OWNED = "owned"
        const val KEY_LEAD = "lead"
        const val KEY_GYM_CLEARED_RUN = "gym_cleared_run"
        const val KEY_ZONE_INDEX = "zone_index"
        const val KEY_ZONE_CLICKS = "zone_clicks"
        const val KEY_TUTORIAL_PHASE = "tutorial_phase"
        const val KEY_TUTORIAL_MESSAGE_CLOSED = "tutorial_message_closed"
        const val ITEM_SEPARATOR = ","
    }
}
