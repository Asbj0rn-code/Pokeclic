package com.dsrs.pokeclic.viewmodel

import com.dsrs.pokeclic.model.CatchingDefaults
import com.dsrs.pokeclic.model.CatchingGame
import com.dsrs.pokeclic.model.Encounter
import com.dsrs.pokeclic.model.SpeciesCatalog
import com.dsrs.pokeclic.model.Zone
import com.dsrs.pokeclic.model.isFreePlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Tick del temporizador de caminar. */
private const val WALK_TICK_MS = 100L

/** Ventana de actividad: sin tocar durante este tiempo, no aparece nada. */
private const val WALK_ACTIVITY_WINDOW_MS = 1_000L

/** Segundos de caminata continuada que llenan la barra de encuentro. */
private const val ENCOUNTER_SECONDS = 3f

/**
 * Motor de caminata y encuentros salvajes.
 *
 * Extraído de [MainViewModel]: el bucle solo avanza si el jugador ha tocado hace
 * poco, la zona tiene fauna y no hay encuentro ni gimnasio en curso. Las reglas de
 * la captura viven en [CatchingGame]; este motor decide **cuándo** aparece un
 * salvaje y delega el arranque del minijuego en [startCatching].
 *
 * @param zoneFor resuelve la zona actual sin que el motor conozca el catálogo.
 * @param startCatching arranca el minijuego de captura al abrir un encuentro.
 */
internal class EncounterEngine(
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
    private val random: Random,
    private val state: () -> GameUiState,
    private val commit: (persist: Boolean, block: GameUiState.() -> GameUiState) -> Unit,
    private val zoneFor: (Int) -> Zone,
    private val startCatching: () -> Unit
) {

    private var lastTapMs: Long = 0L

    /** Registra actividad del jugador en [atMs]; sin ella no hay encuentros. */
    fun noteActivity(atMs: Long) {
        lastTapMs = atMs
    }

    /** Olvida la actividad: usado al resetear, borrar o saltar de zona. */
    fun resetActivity() {
        lastTapMs = 0L
    }

    /** Lanza el temporizador de caminar. Vive mientras viva el ViewModel. */
    fun start() {
        scope.launch {
            while (isActive) {
                delay(WALK_TICK_MS)
                val current = state()
                if ((!current.hasWilds) ||
                    current.isPokedexOpen || current.encounter != null || current.gym != null ||
                    // Guard del tutorial: fuera del juego libre no
                    // hay encuentros (ni experiencia ni capturas).
                    !current.tutorialPhase.isFreePlay
                ) {
                    continue
                }
                if (nowMs() - lastTapMs > WALK_ACTIVITY_WINDOW_MS) continue

                val next = current.walkProgress + WALK_TICK_MS / 1000f / ENCOUNTER_SECONDS
                if (next >= 1f) spawnEncounter() else {
                    commit(false) { copy(walkProgress = next) }
                }
            }
        }
    }

    /**
     * Abre un encuentro salvaje de la zona actual.
     *
     * Zonas sin fauna (`wildSpeciesIds` vacía, las ciudades) nunca abren
     * encuentros: caminar ahí no genera spawns ni toca el catálogo.
     */
    private fun spawnEncounter() {
        val current = state()
        val zone = zoneFor(current.zoneIndex)
        if (zone.wildSpeciesIds.isEmpty()) return
        val species = SpeciesCatalog.require(zone.wildSpeciesIds.random(random))
        val config = CatchingDefaults.configFor(species.captureDifficulty)
        commit(true) {
            copy(
                encounter = Encounter(
                    speciesId = species.id,
                    speciesName = species.name,
                    speciesType = species.type,
                    speciesDexNumber = species.dexNumber
                ),
                catching = CatchingGame.initialState(config),
                walkProgress = 0f,
                seenSpecies = seenSpecies + species.id
            )
        }
        startCatching()
    }
}
