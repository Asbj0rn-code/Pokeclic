package com.dsrs.pokeclic.viewmodel

import com.dsrs.pokeclic.model.CatchingDefaults
import com.dsrs.pokeclic.model.CatchingGame
import com.dsrs.pokeclic.model.CatchingPhase
import com.dsrs.pokeclic.model.CatchingState
import com.dsrs.pokeclic.model.SpeciesCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Tick del minijuego de captura: barra y sprite a 60 por segundo. */
private const val CATCHING_TICK_MS = 16L

/** Tope del `dt` para que una pausa larga no mueva la barra de golpe. */
private const val MAX_CATCHING_DT_SECONDS = 0.1f

/**
 * Motor del minijuego de captura: el bucle de la barra y el cierre del encuentro.
 *
 * Extraído de [MainViewModel] como el gimnasio: el motor orquesta (bucle,
 * pulsación, resultado) y las reglas del juego viven en [CatchingGame]. Se
 * prueba a través del ViewModel, que es lo que ya hacen los tests.
 *
 * @param state lectura del estado actual; el ViewModel es su único dueño.
 * @param commit escritura con la misma semántica que el resto: deriva y publica.
 *   El resultado del encuentro persiste (capturas y monedas); la barra no.
 */
internal class CatchingEngine(
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
    private val random: Random,
    private val state: () -> GameUiState,
    private val commit: (persist: Boolean, block: GameUiState.() -> GameUiState) -> Unit
) {

    private var job: Job? = null
    private var held: Boolean = false

    /** Registra la pulsación de la partida en curso (pulsar = barra sube). */
    fun onHold(value: Boolean) {
        held = value
    }

    /**
     * Cierra el panel de resultado y aplica sus consecuencias: **capturado** suma
     * la especie (y las monedas, solo la primera vez); **escapado** no cambia
     * nada. En ambos casos se vuelve al juego, lista para el siguiente
     * encuentro.
     */
    fun onResultContinue() {
        val catching = state().catching ?: return
        if (catching.phase == CatchingPhase.PLAYING) return
        cancel()

        val captured = catching.phase == CatchingPhase.CAPTURED
        commit(true) {
            val current = encounter ?: return@commit withoutEncounter()
            if (!captured) return@commit withoutEncounter()

            val firstTime = current.speciesId !in rewardedSpecies
            val reward = if (firstTime) {
                SpeciesCatalog.require(current.speciesId).coinReward
            } else {
                0L
            }
            copy(
                ownedSpecies = ownedSpecies + current.speciesId,
                rewardedSpecies = rewardedSpecies + current.speciesId,
                coins = coins + reward,
                caughtSpecies = caughtSpecies + current.speciesId,
                seenSpecies = seenSpecies + current.speciesId
            ).withoutEncounter()
        }
    }

    /**
     * Bucle del minijuego: avanza barra y sprite cada [CATCHING_TICK_MS] con la
     * pulsación registrada. Se detiene al terminar la partida (captura o huida);
     * el panel de resultado espera al jugador.
     */
    fun start() {
        cancel()
        job = scope.launch {
            // La Pokébola se lanza y queda clavada en el centro antes de que el
            // riel empiece a moverse: el estado se queda centrado durante el
            // vuelo y la pausa de reacción.
            delay(CatchingDefaults.THROW_MS + CatchingDefaults.REACTION_MS)
            // `dt` sale del reloj real y no del tick nominal: así el movimiento
            // no se ralentiza si un frame tarda más de lo previsto.
            var lastTickMs = nowMs()
            while (isActive) {
                delay(CATCHING_TICK_MS)
                val now = nowMs()
                val dt = ((now - lastTickMs) / 1000f).coerceIn(0f, MAX_CATCHING_DT_SECONDS)
                lastTickMs = now
                if (dt <= 0f) continue
                var finished = false
                updateCatching { current ->
                    val next = CatchingGame.step(
                        state = current,
                        held = held,
                        dt = dt,
                        random = random
                    )
                    finished = next.phase != CatchingPhase.PLAYING
                    next
                }
                if (finished) break
            }
        }
    }

    /** Cancela el bucle de captura y suelta la pulsación que quedara a medias. */
    fun cancel() {
        job?.cancel()
        job = null
        held = false
    }

    /** Actualiza el minijuego (estado transitorio, no se persiste). */
    private fun updateCatching(block: (CatchingState) -> CatchingState) {
        commit(false) {
            val catching = catching ?: return@commit this
            copy(catching = block(catching))
        }
    }
}
