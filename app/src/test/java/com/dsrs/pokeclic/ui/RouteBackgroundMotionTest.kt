package com.dsrs.pokeclic.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.dsrs.pokeclic.model.Direction
import kotlin.math.abs

/**
 * La trayectoria es la única parte del fondo que se puede comprobar sin emulador:
 * decide cuánto se corre el personaje a cada lado y debe ser acotada, continua y
 * cerrada. Si dejara de serlo, el arte original dejaría de cubrir la pantalla o el
 * bucle daría un salto.
 *
 * La trayectoria se escribe en clics, así que los tests convierten a baldosas con
 * [tiles], la misma ley que usa la física.
 */
class RouteBackgroundMotionTest {

    private fun tiles(clicks: Float): Double =
        (clicks * RouteScrollDefaults.TILES_PER_CLICK).toDouble()

    private val motion = RouteBackgroundMotion(
        legs = listOf(
            RouteLeg(clicks = 10f, drift = 0f),
            RouteLeg(clicks = 5f, drift = 0.1f),
            RouteLeg(clicks = 5f, drift = 0f),
            RouteLeg(clicks = 5f, drift = -0.1f)
        )
    )

    @Test
    fun cycleDistance_addsEveryLeg() {
        assertEquals(tiles(25f), motion.cycleDistance.toDouble(), 0.000001)
    }

    @Test
    fun straightLeg_keepsTheCharacterOnTheCentreLine() {
        assertEquals(0f, motion.offsetAt(tiles(8f)), 0.0001f)
    }

    @Test
    fun lateralLeg_doesNotScrollVertically() {
        // Del clic 10 al 15 el fondo se corre de lado: la distancia vertical no
        // puede moverse ni un píxel (nada de diagonales).
        val frozen = motion.verticalDistanceAt(tiles(10f))
        assertEquals(frozen, motion.verticalDistanceAt(tiles(12f)), 1e-6)
        assertEquals(frozen, motion.verticalDistanceAt(tiles(15f)), 1e-6)
        assertTrue("El tramo lateral debe correrse", motion.offsetAt(tiles(15f)) > 0.05f)
    }

    @Test
    fun verticalLeg_doesNotDriftSideways() {
        val off = motion.offsetAt(tiles(2f))
        assertEquals(off, motion.offsetAt(tiles(9f)), 1e-6f)
        assertTrue(
            "El tramo recto debe avanzar",
            motion.verticalDistanceAt(tiles(9f)) > motion.verticalDistanceAt(tiles(2f))
        )
    }

    @Test
    fun verticalDistance_onlyCountsStraightLegs() {
        // En un ciclo solo cuentan los tramos rectos: 10 + 5 clics.
        assertEquals(tiles(15f), motion.verticalDistanceAt(tiles(25f)), 1e-6)
        assertEquals(tiles(30f), motion.verticalDistanceAt(tiles(50f)), 1e-6)
    }

    @Test
    fun driftingLeg_reachesItsSideAtTheEnd() {
        assertEquals(0.1f, motion.offsetAt(tiles(15f)), 0.0001f)
        assertEquals(0.1f, motion.offsetAt(tiles(20f)), 0.0001f)
    }

    @Test
    fun cycle_endsBackOnTheCentreLine() {
        assertEquals(0f, motion.offsetAt(0.0), 0.0001f)
        assertEquals(0f, motion.offsetAt(tiles(25f)), 0.0001f)
        assertEquals(0f, motion.offsetAt(tiles(24.999f)), 0.0001f)
    }

    @Test
    fun offset_neverExceedsTheDeclaredDrift() {
        var travelled = 0.0
        while (travelled < motion.cycleDistance * 3.0) {
            travelled += 0.0001
            assertTrue(
                "Se corre demasiado en $travelled",
                abs(motion.offsetAt(travelled)) <= motion.maxDrift + 0.0001f
            )
        }
    }

    @Test
    fun offset_neverJumpsBetweenSamples() {
        var previous = motion.offsetAt(0.0)
        var travelled = 0.0
        while (travelled < motion.cycleDistance * 3.0) {
            travelled += 0.0001
            val current = motion.offsetAt(travelled)
            assertTrue(
                "Corrimiento salta en $travelled: $previous -> $current",
                abs(current - previous) < 0.01f
            )
            previous = current
        }
    }

    @Test
    fun offset_isPeriodic() {
        val cycle = motion.cycleDistance.toDouble()
        assertEquals(
            motion.offsetAt(tiles(9.3f)),
            motion.offsetAt(tiles(9.3f) + cycle * 5.0),
            0.0001f
        )
    }

    @Test
    fun nonClosingCycle_stillHasNoSeam() {
        // Una localización mal sumada no debe dar un salto en el bucle: el cierre
        // que falte se reparte por el ciclo.
        val broken = RouteBackgroundMotion(legs = listOf(RouteLeg(clicks = 10f, drift = 0.05f)))
        assertEquals(broken.offsetAt(0.0), broken.offsetAt(tiles(9.999f)), 0.001f)
        assertEquals(broken.offsetAt(0.0), broken.offsetAt(tiles(10.001f)), 0.001f)
    }

    @Test
    fun defaultMotion_turnsRightAtFifteenClicks() {
        val default = RouteScrollDefaults.MOTION

        // Hasta el clic 15 el personaje no se ha movido de la línea central: ahí
        // empieza exactamente el giro a la derecha.
        assertEquals(0f, default.offsetAt(tiles(14.99f)), 0.0001f)
        assertTrue(
            "El giro a la derecha debe empezar en el clic 15",
            default.offsetAt(tiles(15.5f)) > 0f
        )
    }

    @Test
    fun defaultMotion_holdsTheRightSideForOneClick() {
        val default = RouteScrollDefaults.MOTION

        // Del 15 al 16 se abre a la derecha; a partir del 16 se queda en +0.08
        // mientras sube recto otros 10 clics (hasta el 26).
        assertEquals(0.08f, default.offsetAt(tiles(16f)), 0.0001f)
        assertEquals(0.08f, default.offsetAt(tiles(21f)), 0.0001f)
        assertEquals(0.08f, default.offsetAt(tiles(26f)), 0.0001f)
    }

    @Test
    fun defaultMotion_movesOnlyOneAxisPerLeg() {
        val default = RouteScrollDefaults.MOTION

        // Clics 15 → 16: se corre a la derecha y el vertical no se mueve.
        val frozen = default.verticalDistanceAt(tiles(15f))
        assertEquals(frozen, default.verticalDistanceAt(tiles(15.5f)), 1e-6)
        assertEquals(frozen, default.verticalDistanceAt(tiles(16f)), 1e-6)

        // Clics 16 → 26: sube recto 10 clics y el lateral no se mueve.
        val side = default.offsetAt(tiles(16f))
        assertEquals(side, default.offsetAt(tiles(21f)), 1e-6f)
        assertTrue(
            "Tras el tramo lateral el vertical debe volver a avanzar",
            default.verticalDistanceAt(tiles(26f)) > frozen + tiles(9f)
        )
    }

    @Test
    fun defaultMotion_lateralAdvanceIsLinear() {
        val default = RouteScrollDefaults.MOTION

        // Tramo lateral: del clic 15 al 16. Al 25 % del tramo el corrimiento debe
        // ser el 25 % de 0,08. Con `smoothstep` daría 0,0125 (arranque lento).
        assertEquals(0.02f, default.offsetAt(tiles(15.25f)), 0.0001f)
        assertEquals(0.04f, default.offsetAt(tiles(15.5f)), 0.0001f)
        assertEquals(0.06f, default.offsetAt(tiles(15.75f)), 0.0001f)
    }

    @Test
    fun defaultMotion_followsTheRequestedPath() {
        val default = RouteScrollDefaults.MOTION

        // Puntos de paso: recto (15), derecha (16), recto 10 clics (26),
        // vuelta al centro (27).
        assertEquals(0f, default.offsetAt(tiles(15f)), 0.0001f)
        assertEquals(0.08f, default.offsetAt(tiles(16f)), 0.0001f)
        assertEquals(0.08f, default.offsetAt(tiles(26f)), 0.0001f)
        assertEquals(0f, default.offsetAt(tiles(27f)), 0.0001f)
        assertEquals(0.08f, default.maxDrift, 0.0001f)
    }

    @Test
    fun defaultMotion_cycleCountsAndTotals() {
        val default = RouteScrollDefaults.MOTION

        // 27 clics por ciclo: 15 + 1 + 10 + 1.
        assertEquals(tiles(27f), default.cycleDistance.toDouble(), 1e-6)
        // De ellos, 25 son avance vertical (1,08 baldosas de recorrido total,
        // 1,0 verticales).
        assertEquals(tiles(25f), default.verticalDistanceAt(tiles(27f)), 1e-6)
        // El ciclo cierra exactamente en la línea central: sin deriva acumulada.
        assertEquals(0f, default.offsetAt(tiles(27f)), 0.00001f)
        assertEquals(0f, default.offsetAt(tiles(27f * 7f)), 0.00001f)
        assertEquals(tiles(25f * 7f), default.verticalDistanceAt(tiles(27f * 7f)), 1e-6)
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
        // Y el lateral vuelve a cero en cada vuelta.
        assertEquals(0f, default.offsetAt(cycle * 5.0), 1e-5f)
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
        // Y vuelve a empezar el ciclo.
        assertEquals(Direction.BACK, default.directionForStep(28))
    }

    @Test
    fun slackFactor_coversTheWholeLateralTravel() {
        val screenWidth = 1000f
        for (maxDrift in listOf(0f, 0.05f, 0.08f, 0.2f)) {
            val factor = routeSlackFactor(maxDrift)
            val tileWidth = screenWidth * factor
            // Con el personaje en cada extremo, el arte sigue tapando la pantalla.
            val leftAtRight = (screenWidth - tileWidth) / 2f - maxDrift * screenWidth
            val leftAtLeft = (screenWidth - tileWidth) / 2f + maxDrift * screenWidth
            assertTrue("Borde visible a la derecha con drift=$maxDrift", leftAtRight <= 0f)
            assertTrue(
                "Borde visible a la izquierda con drift=$maxDrift",
                leftAtLeft + tileWidth >= screenWidth
            )
        }
    }
}
