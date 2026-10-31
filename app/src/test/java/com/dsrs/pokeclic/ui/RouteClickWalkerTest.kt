package com.dsrs.pokeclic.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El paso de un clic es lo que define la sensación: debe avanzar una cantidad
 * fija, terminar en un plazo corto y **no dejar deslizamiento** después.
 */
class RouteClickWalkerTest {

    private val step = 0.04
    private val maxStep = 100_000_000L // 100 ms
    private val minStep = 30_000_000L

    private fun walker() = RouteClickWalker(
        stepTiles = step,
        maxStepNanos = maxStep,
        minStepNanos = minStep
    )

    @Test
    fun withoutClicks_theBackgroundNeverMoves() {
        val walker = walker()
        assertEquals(0.0, walker.positionAt(0, 0), 0.0)
        assertEquals(0.0, walker.positionAt(0, 1_000_000_000L), 0.0)
    }

    @Test
    fun oneClick_movesExactlyOneStep() {
        val walker = walker()
        assertEquals(0.0, walker.positionAt(1, 0), 1e-9)
        assertEquals(step, walker.positionAt(1, maxStep), 1e-9)
    }

    @Test
    fun afterTheStep_thereIsNoCoasting() {
        val walker = walker()
        walker.positionAt(1, 0)
        assertEquals(step, walker.positionAt(1, maxStep), 1e-9)
        // Diez veces el plazo después, la posición sigue clavada.
        assertEquals(step, walker.positionAt(1, maxStep * 10), 0.0)
        assertEquals(step, walker.positionAt(1, maxStep * 1_000), 0.0)
    }

    @Test
    fun theStepIsLinearAndNeverOvershoots() {
        val walker = walker()
        walker.positionAt(1, 0)
        assertEquals(step / 2, walker.positionAt(1, maxStep / 2), 1e-6)
        assertEquals(step * 3 / 4, walker.positionAt(1, maxStep * 3 / 4), 1e-6)
        assertEquals(step, walker.positionAt(1, maxStep * 3), 1e-9)
    }

    @Test
    fun fastClicks_keepUpWithTheClickRate() {
        val walker = walker()
        // 10 clics cada 30 ms: cuando llega el último, el fondo no puede quedarse
        // más de un paso por detrás del destino.
        var now = 0L
        for (click in 1..10L) {
            walker.positionAt(click, now)
            now += minStep
        }
        val position = walker.positionAt(10, now)
        assertEquals(step * 10, position, step)
    }

    @Test
    fun aBurstOfClicksBecomesThemAll() {
        val walker = walker()
        // Varios clics entre dos frames: se aplican todos.
        walker.positionAt(4, 0)
        assertEquals(step * 4, walker.positionAt(4, maxStep), 1e-9)
    }

    @Test
    fun slowClicks_useTheFullStepTime() {
        val walker = walker()
        walker.positionAt(1, 0)
        // Un segundo después del clic, el paso ya terminó hace mucho.
        assertTrue(walker.positionAt(1, 1_000_000_000L) == step)
    }

    @Test
    fun remounting_startsWhereItLeftOff() {
        // Al volver a componer (otra pestaña, otra zona) el contador ya lleva 10
        // clics: no se repiten, se arranca directamente en su posición.
        val walker = RouteClickWalker(
            stepTiles = step,
            maxStepNanos = maxStep,
            minStepNanos = minStep,
            initialSteps = 10L
        )

        assertEquals(step * 10, walker.positionAt(10, 0), 1e-9)
        assertEquals(step * 10, walker.positionAt(10, maxStep), 1e-9)
        // El clic nuevo arranca su paso en la posición en la que estaba.
        assertEquals(step * 10, walker.positionAt(11, maxStep * 2), 1e-9)
        assertEquals(step * 11, walker.positionAt(11, maxStep * 3), 1e-9)
    }

    @Test
    fun aResetOfTheCounter_jumpsBackToTheOrigin() {
        // Cambio de zona: el recorrido vuelve a empezar sin animar el rebobinado.
        val walker = RouteClickWalker(
            stepTiles = step,
            maxStepNanos = maxStep,
            minStepNanos = minStep,
            initialSteps = 10L
        )

        assertEquals(0.0, walker.positionAt(0, 0), 1e-9)
    }
}
