package com.dsrs.pokeclic.ui

import com.dsrs.pokeclic.model.ZoneCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Todas las zonas tienen que poder animarse. Las que no tengan fondo propio caen
 * en [RouteBackgrounds.DEFAULT], pero la trayectoria tiene que cumplir las mismas
 * reglas que el default o el bucle se descuadra en esa zona.
 */
class RouteBackgroundsTest {

    @Test
    fun everyZone_resolvesToAUsableBackground() {
        ZoneCatalog.zones.forEach { zone ->
            val background = RouteBackgrounds.forZone(zone.id)
            assertTrue("${zone.id} sin arte", background.imageRes != 0)
            assertTrue("${zone.id} sin tramos", background.motion.legs.isNotEmpty())
        }
    }

    @Test
    fun everyZone_closesItsLateralCycle() {
        ZoneCatalog.zones.forEach { zone ->
            val motion = RouteBackgrounds.forZone(zone.id).motion
            val drift = motion.legs.sumOf { it.drift.toDouble() }
            assertEquals("${zone.id} no cierra el lateral", 0.0, drift, 1e-4)
            assertEquals(
                "${zone.id} con deriva lateral",
                0f,
                motion.offsetAt(motion.cycleDistance * 5.0),
                1e-3f
            )
        }
    }

    @Test
    fun everyZone_advancesAWholeNumberOfTiles() {
        ZoneCatalog.zones.forEach { zone ->
            val motion = RouteBackgrounds.forZone(zone.id).motion
            val vertical = motion.verticalDistanceAt(motion.cycleDistance.toDouble())
            assertTrue("${zone.id} sin avance vertical", vertical > 0.0)
            assertEquals(
                "${zone.id} no cierra sobre el mismo píxel del arte",
                vertical.roundToInt().toDouble(),
                vertical,
                1e-6
            )
        }
    }

    @Test
    fun everyZone_changesDirection() {
        ZoneCatalog.zones.forEach { zone ->
            val motion = RouteBackgrounds.forZone(zone.id).motion
            assertTrue("${zone.id} sin tramo recto", motion.legs.any { !it.isLateral })
            assertTrue("${zone.id} sin tramo lateral", motion.legs.any { it.isLateral })
        }
    }

    @Test
    fun unknownZone_fallsBackToTheDefault() {
        assertEquals(RouteBackgrounds.DEFAULT, RouteBackgrounds.forZone("zona_inexistente"))
        assertEquals(RouteBackgrounds.DEFAULT, RouteBackgrounds.forZone(""))
    }
}
