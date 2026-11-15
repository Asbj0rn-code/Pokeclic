package com.dsrs.pokeclic.ui

import androidx.compose.ui.unit.dp
import com.dsrs.pokeclic.model.GymCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La composición de la escena es **por gimnasio**: el anclaje de un arte no debe
 * arrastrar a los demás.
 *
 * Brock y Misty comparten **escala de sprites** porque los dos se pintan sobre un
 * fondo pixel-art 2:3 de 1536×2304; lo que cambia entre ellos es el anclaje, que
 * depende de dónde caen la tarima del líder y el suelo del jugador en cada
 * dibujo. Lt. Surge sigue con la ilustración, así que conserva la escala del arte
 * de piscina.
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
    fun misty_sharesThePixelArtSpriteScaleWithBrock() {
        // Los dos fondos son pixel-art 2:3: el sprite debe medir lo mismo en los
        // dos o uno de los dos se ve fuera de escala.
        val brock = GymSceneLayouts.forGym("brock")
        val misty = GymSceneLayouts.forGym("misty")
        assertEquals("ancho del líder", brock.enemyWidth, misty.enemyWidth)
        assertEquals("alto del líder", brock.enemyHeight, misty.enemyHeight)
        assertEquals("ancho del jugador", brock.playerWidth, misty.playerWidth)
        assertEquals("alto del jugador", brock.playerHeight, misty.playerHeight)
    }

    @Test
    fun misty_keepsItsOwnAnchors() {
        // El anclaje sí es de cada arte: la tarima y el suelo del dibujo nuevo
        // caen a otra altura que en el de roca.
        val layout = GymSceneLayouts.forGym("misty")
        assertEquals("ancla superior del líder", 178f, layout.enemyTop.value, 0f)
        assertEquals("ancla inferior del jugador", 180f, layout.playerBottom.value, 0f)
    }

    @Test
    fun surge_keepsTheIllustrationScale() {
        // El arte de Surge todavía es la ilustración: conserva la escala del
        // encuadre de piscina salvo el alto del líder, que deja hueco a la
        // cuadrícula de Voltorb.
        assertEquals(
            GymSceneLayouts.DEFAULT.copy(enemyHeight = 116.dp),
            GymSceneLayouts.forGym("surge")
        )
    }

    @Test
    fun onlyTheRockGym_usesTheTunedAnchors() {
        // El anclaje del arte de roca no debe filtrarse a los otros gimnasios.
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
