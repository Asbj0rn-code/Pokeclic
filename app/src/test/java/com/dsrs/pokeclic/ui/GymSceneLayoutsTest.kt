package com.dsrs.pokeclic.ui

import com.dsrs.pokeclic.model.GymCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La composición de la escena es **por gimnasio**: el ajuste de un arte no debe
 * arrastrar a los demás. Brock se calibró para el fondo pixel-art 2:3; Misty y
 * Lt. Surge conservan el encuadre del arte de piscina.
 */
class GymSceneLayoutsTest {

    @Test
    fun configuredGyms_existInTheCatalog() {
        // Un id mal escrito en el mapa caería en DEFAULT sin avisar.
        val gymIds = GymCatalog.all.map { it.id }.toSet()
        GymSceneLayouts.configuredGymIds.forEach { id ->
            assertTrue("'$id' no es un gimnasio del catálogo", id in gymIds)
        }
    }

    @Test
    fun brock_usesTheTunedRockComposition() {
        val layout = GymSceneLayouts.forGym("brock")
        assertEquals("ancho del líder", 164f, layout.enemyWidth.value, 0f)
        assertEquals("alto del líder", 136f, layout.enemyHeight.value, 0f)
        assertEquals("ancla superior del líder", 152f, layout.enemyTop.value, 0f)
        assertEquals("ancho del jugador", 82f, layout.playerWidth.value, 0f)
        assertEquals("alto del jugador", 93f, layout.playerHeight.value, 0f)
        assertEquals("ancla inferior del jugador", 140f, layout.playerBottom.value, 0f)
    }

    @Test
    fun misty_keepsThePoolComposition() {
        val layout = GymSceneLayouts.forGym("misty")
        assertEquals("ancho del líder", 180f, layout.enemyWidth.value, 0f)
        assertEquals("alto del líder", 150f, layout.enemyHeight.value, 0f)
        assertEquals("ancla superior del líder", 178f, layout.enemyTop.value, 0f)
        assertEquals("ancho del jugador", 90f, layout.playerWidth.value, 0f)
        assertEquals("alto del jugador", 102f, layout.playerHeight.value, 0f)
        assertEquals("ancla inferior del jugador", 160f, layout.playerBottom.value, 0f)
    }

    @Test
    fun surge_reservesHeightForTheVoltorbGrid() {
        // Mismo encuadre que Misty salvo el alto del líder: la cuadrícula de
        // Voltorb necesita ese hueco.
        val misty = GymSceneLayouts.forGym("misty")
        val surge = GymSceneLayouts.forGym("surge")
        assertEquals("alto del líder", 116f, surge.enemyHeight.value, 0f)
        assertEquals(misty.copy(enemyHeight = surge.enemyHeight), surge)
    }

    @Test
    fun onlyTheRockGym_usesTheTunedComposition() {
        // El ajuste del arte de roca no debe filtrarse a los otros gimnasios.
        val rock = GymSceneLayouts.forGym("brock")
        assertNotEquals(rock, GymSceneLayouts.forGym("misty"))
        assertNotEquals(rock, GymSceneLayouts.forGym("surge"))
    }

    @Test
    fun unknownGym_fallsBackToTheDefault() {
        assertEquals(GymSceneLayouts.DEFAULT, GymSceneLayouts.forGym("gimnasio_inexistente"))
        assertEquals(GymSceneLayouts.DEFAULT, GymSceneLayouts.forGym(""))
    }
}
