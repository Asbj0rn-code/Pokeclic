/**
 * Física del minijuego de captura a nivel modelo, sin ViewModel ni relojes:
 * garantiza las invariantes que los tests del ViewModel dan por sentadas
 * cuando dirigen el sprite con un `Random` inyectado.
 */
package com.dsrs.pokeclic.model

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchingGameTest {

    private val config = CatchingDefaults.configFor(0f)

    /** Sin dardos: `step` nunca dispara un salto brusco del sprite. */
    private object NoDartRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextFloat(): Float = 0.99f
    }

    @Test
    fun initialState_isSymmetricAndPlaying() {
        val state = CatchingGame.initialState(config)

        assertEquals(0.5f, state.barPos)
        assertEquals(0.5f, state.spritePos)
        assertEquals(0f, state.progress)
        assertEquals(CatchingPhase.PLAYING, state.phase)
    }

    @Test
    fun step_keepsBarSpriteAndProgressInsideTheirRanges() {
        val minBar = config.barSize / 2f
        val maxBar = 1f - config.barSize / 2f
        var state = CatchingGame.initialState(config)

        repeat(200) { tick ->
            state = CatchingGame.step(
                state = state,
                held = tick % 2 == 0,
                dt = 1f / 60f,
                random = Random(tick)
            )
        }

        assertTrue(state.barPos in minBar..maxBar)
        assertTrue(state.spritePos in config.spriteMargin..(1f - config.spriteMargin))
        assertTrue(state.progress in 0f..1f)
    }

    @Test
    fun heldMovesTheBarUpAndReleasedMovesItDown() {
        val initial = CatchingGame.initialState(config)

        val held = CatchingGame.step(initial, held = true, dt = 0.2f, random = Random(1))
        val released = CatchingGame.step(initial, held = false, dt = 0.2f, random = Random(1))

        assertTrue(held.barPos > initial.barPos)
        assertTrue(released.barPos < initial.barPos)
    }

    @Test
    fun escapeOnlyHappensAfterTheInitialGrace() {
        // Barra abajo y sprite arriba (sin solape); sin dardos, la barra queda
        // clavada en el borde y el sprite converge al centro sin alcanzarla.
        val apart = CatchingGame.initialState(config).copy(
            barPos = config.barSize / 2f,
            spritePos = 1f - config.spriteMargin
        )

        val duringGrace = CatchingGame.step(apart, held = false, dt = 0.5f, random = NoDartRandom)
        assertEquals(CatchingPhase.PLAYING, duringGrace.phase)

        val afterGrace = CatchingGame.step(duringGrace, held = false, dt = 0.6f, random = NoDartRandom)
        assertEquals(CatchingPhase.ESCAPED, afterGrace.phase)
    }

    @Test
    fun progressFillsOnOverlapAndCapturesWhenFull() {
        // Centrados por construcción: hay solape desde el primer tick.
        val almostFull = CatchingGame.initialState(config).copy(progress = 0.98f)

        val next = CatchingGame.step(almostFull, held = true, dt = 0.1f, random = Random(3))

        assertEquals(CatchingPhase.CAPTURED, next.phase)
    }
}
