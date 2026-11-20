package com.dsrs.pokeclic.viewmodel

import com.dsrs.pokeclic.model.Direction
import com.dsrs.pokeclic.model.SpriteRepository
import com.dsrs.pokeclic.model.startFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Un segundo sin pulsar devuelve al entrenador al reposo. */
private const val FRAME_RESET_DELAY_MS = 1_000L

/**
 * Cuánto tarda el segundo fotograma de un paso lateral. Cae dentro de los ~100 ms
 * que dura el desplazamiento de ese clic, así que se ve el arranque (frame 1) y
 * luego la zancada (frame 2).
 */
private const val LATERAL_SECOND_FRAME_DELAY_MS = 60L

/**
 * Animación del entrenador: qué fotograma toca y cuándo vuelve al reposo.
 *
 * Extraído de [MainViewModel] para que la animación deje de compartir campos con
 * la economía. [animate] es puro (solo lee estado y sprites); los dos plazos
 * viven aquí como jobs propios.
 */
internal class TrainerAnimator(
    private val scope: CoroutineScope,
    private val sprites: SpriteRepository,
    private val commit: (persist: Boolean, block: GameUiState.() -> GameUiState) -> Unit
) {

    private var resetJob: Job? = null
    private var lateralJob: Job? = null

    /**
     * Fotograma siguiente tras un toque: si se estrena dirección se arranca por
     * su fotograma de entrada (los laterales empiezan en el segundo del ciclo);
     * si no, avanza el ciclo.
     */
    fun animate(
        state: GameUiState,
        direction: Direction,
        directionChanged: Boolean
    ): GameUiState {
        val total = sprites.frameCount(direction)
        val trainer = if (directionChanged) {
            val first = direction.startFrame().coerceIn(0, (total - 1).coerceAtLeast(0))
            state.trainer.copy(currentDirection = direction, currentFrame = first)
        } else {
            state.trainer.copy(currentFrame = (state.trainer.currentFrame + 1) % total)
        }
        return state.copy(
            trainer = trainer,
            trainerFrameRes = sprites.frame(trainer.currentDirection, trainer.currentFrame)
        )
    }

    /** Un clic nuevo manda: cancela el segundo fotograma de un lateral anterior. */
    fun cancelPendingStep() {
        lateralJob?.cancel()
        lateralJob = null
    }

    /** Programa el reposo tras [FRAME_RESET_DELAY_MS] sin pulsar. */
    fun scheduleReset() {
        resetJob?.cancel()
        resetJob = scope.launch {
            delay(FRAME_RESET_DELAY_MS)
            commit(false) {
                copy(
                    trainer = trainer.copy(currentDirection = Direction.FRONT, currentFrame = 0),
                    trainerFrameRes = sprites.frame(Direction.FRONT, 0)
                )
            }
        }
    }

    /**
     * Segundo fotograma del paso lateral: entra cuando el fondo ya está
     * deslizando, no en el mismo instante del toque.
     */
    fun scheduleLateralSecondFrame(direction: Direction) {
        lateralJob?.cancel()
        lateralJob = scope.launch {
            delay(LATERAL_SECOND_FRAME_DELAY_MS)
            commit(false) {
                if (trainer.currentDirection != direction) return@commit this
                val total = sprites.frameCount(direction)
                val frame = (direction.startFrame() + 1)
                    .coerceIn(0, (total - 1).coerceAtLeast(0))
                copy(
                    trainer = trainer.copy(currentFrame = frame),
                    trainerFrameRes = sprites.frame(direction, frame)
                )
            }
        }
    }

    /** Cancela los dos plazos (reset y paso lateral). */
    fun cancelAll() {
        resetJob?.cancel()
        resetJob = null
        cancelPendingStep()
    }
}
