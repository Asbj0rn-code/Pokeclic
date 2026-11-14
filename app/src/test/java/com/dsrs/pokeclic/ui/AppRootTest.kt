package com.dsrs.pokeclic.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El mundo de ruta se congela en modo captura: con la hierba alta abierta o
 * un encuentro en curso, el clic de la Pokébola no avanza el fondo.
 */
class AppRootTest {

    @Test
    fun tap_outsideCaptureMode_advancesTheRouteWorld() {
        assertTrue(advancesRouteWorld(isTallGrass = false, encounterActive = false))
    }

    @Test
    fun tap_inTallGrass_freezesTheRouteWorld() {
        assertFalse(advancesRouteWorld(isTallGrass = true, encounterActive = false))
    }

    @Test
    fun tap_duringAnEncounter_freezesTheRouteWorld() {
        assertFalse(advancesRouteWorld(isTallGrass = false, encounterActive = true))
    }

    @Test
    fun tap_inACave_advancesTheRouteWorldEvenWithoutTallGrass() {
        // Cueva (Mt.Moon): hay salvajes sin modo hierba, pero el clic sigue
        // moviendo el fondo. Con un encuentro abierto sí se congela.
        assertTrue(
            advancesRouteWorld(
                isTallGrass = false,
                encounterActive = false,
                alwaysWilds = true
            )
        )
        assertFalse(
            advancesRouteWorld(
                isTallGrass = false,
                encounterActive = true,
                alwaysWilds = true
            )
        )
    }
}
