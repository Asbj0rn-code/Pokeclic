package com.dsrs.pokeclic.ui

import com.dsrs.pokeclic.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * La trayectoria es la única parte del fondo que se puede comprobar sin emulador:
 * decide cuánto avanza el fondo en cada eje y hacia dónde camina el entrenador.
 *
 * El modelo es simétrico: **un clic recorre lo mismo en cualquier dirección**, y
 * los dos ejes son scrolls sin tope. Lo que se comprueba aquí es esa ley, que
 * cada tramo mueva un solo eje y que la dirección salga del tramo, no aparte.
 *
 * La trayectoria se escribe en clics, así que los tests convierten a baldosas con
 * [tiles], la misma ley que usa la física.
 */
class RouteBackgroundMotionTest {

    private fun tiles(clicks: Float): Double =
        (clicks * RouteScrollDefaults.TILES_PER_CLICK).toDouble()

    /** 10 recto · 5 derecha · 5 recto · 5 izquierda: 25 clics, cierra el lateral. */
    private val motion = RouteBackgroundMotion(
        legs = listOf(
            RouteLeg(clicks = 10f),
            RouteLeg(clicks = 5f, direction = Direction.RIGHT),
            RouteLeg(clicks = 5f),
            RouteLeg(clicks = 5f, direction = Direction.LEFT)
        )
    )

    @Test
    fun cycleDistance_addsEveryLeg() {
        assertEquals(tiles(25f), motion.cycleDistance.toDouble(), 0.000001)
    }

    @Test
    fun lateralDistance_comesFromTheClicks() {
        // La ley del modelo: un tramo lateral de 5 clics recorre exactamente lo
        // mismo que cinco clics rectos. No hay magnitud aparte que ajustar.
        assertEquals(
            5f * RouteScrollDefaults.TILES_PER_CLICK,
            motion.legs[1].distance,
            1e-6f
        )
    }

    @Test
    fun straightLeg_keepsTheCharacterOnTheCentreLine() {
        assertEquals(0f, motion.lateralDistanceAt(tiles(8f)), 1e-6f)
    }

    @Test
    fun lateralLeg_doesNotScrollVertically() {
        // Del clic 10 al 15 el fondo se corre de lado: la distancia vertical no
        // puede moverse ni un píxel (nada de diagonales).
        val frozen = motion.verticalDistanceAt(tiles(10f))
        assertEquals(frozen, motion.verticalDistanceAt(tiles(12f)), 1e-9)
        assertEquals(frozen, motion.verticalDistanceAt(tiles(15f)), 1e-9)
    }

    @Test
    fun verticalLeg_doesNotDriftSideways() {
        val side = motion.lateralDistanceAt(tiles(15f))
        assertEquals(side, motion.lateralDistanceAt(tiles(19f)), 1e-6f)
        assertTrue(
            "El tramo recto debe avanzar",
            motion.verticalDistanceAt(tiles(19f)) > motion.verticalDistanceAt(tiles(15f))
        )
    }

    @Test
    fun lateralLeg_recoversExactlyTheDistanceOfItsClicks() {
        // 5 clics de lado → 5 × TILES_PER_CLICK, al mismo ritmo que el vertical.
        assertEquals(
            motion.legs[1].distance,
            motion.lateralDistanceAt(tiles(15f)),
            1e-6f
        )
    }

    @Test
    fun lateralAdvanceIsLinear() {
        // A mitad del tramo lateral, mitad del recorrido: sin rampa de entrada ni
        // de salida, igual que el vertical.
        assertEquals(
            motion.legs[1].distance / 2f,
            motion.lateralDistanceAt(tiles(12.5f)),
            1e-6f
        )
    }

    @Test
    fun lateral_isAScrollWithoutATope() {
        // Un ciclo con clics de más a un lado acumula deriva vuelta tras vuelta:
        // es un scroll infinito, no un corrimiento acotado que vuelve al centro.
        val oneSided = RouteBackgroundMotion(
            legs = listOf(
                RouteLeg(clicks = 5f),
                RouteLeg(clicks = 5f, direction = Direction.RIGHT)
            )
        )
        val first = oneSided.lateralDistanceAt(oneSided.cycleDistance.toDouble())
        val fifth = oneSided.lateralDistanceAt(oneSided.cycleDistance * 5.0)
        assertTrue("El lateral debe crecer", fifth > first)
        assertEquals(first * 5f, fifth, 1e-4f)
    }

    @Test
    fun balancedCycle_returnsToTheCentreLine() {
        assertEquals(0f, motion.lateralDistanceAt(motion.cycleDistance * 7.0), 1e-4f)
    }

    @Test
    fun lateral_neverJumpsBetweenSamples() {
        var previous = motion.lateralDistanceAt(0.0)
        var travelled = 0.0
        while (travelled < motion.cycleDistance * 3.0) {
            travelled += 0.0001
            val current = motion.lateralDistanceAt(travelled)
            assertTrue(
                "Corrimiento salta en $travelled: $previous -> $current",
                abs(current - previous) < 0.01f
            )
            previous = current
        }
    }

    @Test
    fun directionFollowsTheAxisOfEachStep() {
        assertEquals(Direction.BACK, motion.directionForStep(1))
        assertEquals(Direction.BACK, motion.directionForStep(10))
        assertEquals(Direction.RIGHT, motion.directionForStep(11))
        assertEquals(Direction.RIGHT, motion.directionForStep(15))
        assertEquals(Direction.BACK, motion.directionForStep(16))
        assertEquals(Direction.LEFT, motion.directionForStep(21))
        assertEquals(Direction.LEFT, motion.directionForStep(25))
        assertEquals(Direction.BACK, motion.directionForStep(26))
    }

    @Test
    fun frontLeg_isRejected() {
        // FRONT es el reposo del sprite, no una dirección de avance.
        var threw = false
        try {
            RouteBackgroundMotion(
                legs = listOf(RouteLeg(clicks = 5f, direction = Direction.FRONT))
            )
        } catch (expected: IllegalArgumentException) {
            threw = true
        }
        assertTrue("Un tramo FRONT no debería construirse", threw)
    }

    @Test
    fun defaultMotion_turnsRightAtFifteenClicks() {
        val default = RouteScrollDefaults.MOTION

        assertEquals(0f, default.lateralDistanceAt(tiles(14.99f)), 1e-6f)
        assertTrue(
            "El giro a la derecha debe empezar en el clic 15",
            default.lateralDistanceAt(tiles(15.5f)) > 0f
        )
    }

    @Test
    fun defaultMotion_holdsTheRightSideWhileItGoesStraight() {
        val default = RouteScrollDefaults.MOTION
        val step = 1f * RouteScrollDefaults.TILES_PER_CLICK

        // Del 15 al 16 se abre a la derecha; a partir del 16 se queda ahí
        // mientras sube recto otros 10 clics (hasta el 26).
        assertEquals(step, default.lateralDistanceAt(tiles(16f)), 1e-6f)
        assertEquals(step, default.lateralDistanceAt(tiles(21f)), 1e-6f)
        assertEquals(step, default.lateralDistanceAt(tiles(26f)), 1e-6f)
    }

    @Test
    fun defaultMotion_movesOnlyOneAxisPerLeg() {
        val default = RouteScrollDefaults.MOTION

        // Clics 15 → 16: se corre a la derecha y el vertical no se mueve.
        val frozen = default.verticalDistanceAt(tiles(15f))
        assertEquals(frozen, default.verticalDistanceAt(tiles(15.5f)), 1e-6)
        assertEquals(frozen, default.verticalDistanceAt(tiles(16f)), 1e-6)

        // Clics 16 → 26: sube recto 10 clics y el lateral no se mueve.
        val side = default.lateralDistanceAt(tiles(16f))
        assertEquals(side, default.lateralDistanceAt(tiles(21f)), 1e-6f)
        assertTrue(
            "Tras el tramo lateral el vertical debe volver a avanzar",
            default.verticalDistanceAt(tiles(26f)) > frozen + tiles(9f)
        )
    }

    @Test
    fun defaultMotion_cycleCountsAndTotals() {
        val default = RouteScrollDefaults.MOTION

        // 27 clics por ciclo: 15 + 1 + 10 + 1.
        assertEquals(tiles(27f), default.cycleDistance.toDouble(), 1e-6)
        // De ellos, 25 son avance vertical: una baldosa exacta.
        assertEquals(tiles(25f), default.verticalDistanceAt(tiles(27f)), 1e-6)
        // Y el lateral cierra en el centro porque los dos sentidos duran igual.
        assertEquals(0f, default.lateralDistanceAt(tiles(27f)), 1e-6f)
        assertEquals(tiles(25f * 7f), default.verticalDistanceAt(tiles(27f * 7f)), 1e-6)
        assertEquals(0f, default.lateralDistanceAt(tiles(27f * 7f)), 1e-5f)
    }

    @Test
    fun defaultMotion_returnsToTheSameArtworkEveryCycle() {
        val default = RouteScrollDefaults.MOTION
        val cycle = default.cycleDistance.toDouble()
        val vertical = default.verticalDistanceAt(cycle)

        // Sin esto, cada vuelta enseña el dibujo corrido y el giro parece llegar
        // antes aunque el recorrido sea idéntico.
        assertEquals(1.0, vertical, 1e-6)
        assertEquals(vertical * 2.0, default.verticalDistanceAt(cycle * 2.0), 1e-6)
        assertEquals(vertical * 5.0, default.verticalDistanceAt(cycle * 5.0), 1e-5)
    }

    @Test
    fun defaultMotion_directionFollowsTheAxisOfEachStep() {
        val default = RouteScrollDefaults.MOTION

        // 15 clics rectos → de espaldas; el 16 es el lateral derecho; 17 a 26
        // vuelven a recto; el 27 es el lateral izquierdo.
        assertEquals(Direction.BACK, default.directionForStep(1))
        assertEquals(Direction.BACK, default.directionForStep(15))
        assertEquals(Direction.RIGHT, default.directionForStep(16))
        assertEquals(Direction.BACK, default.directionForStep(17))
        assertEquals(Direction.BACK, default.directionForStep(26))
        assertEquals(Direction.LEFT, default.directionForStep(27))
        assertEquals(Direction.BACK, default.directionForStep(28))
    }
}
