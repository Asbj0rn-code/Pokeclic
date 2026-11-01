package com.dsrs.pokeclic.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Punto de partida de una zona: dónde cae el **borde inferior del arte** al
 * entrar, con cero clics. Es cálculo puro, así que se comprueba sin emulador.
 *
 * La cuenta vive aparte de la capa de dibujo a propósito: el ancla solo decide
 * por dónde se empieza a ver el arte, nunca la trayectoria ni la dirección.
 */
class RouteStartShiftTest {

    private val tileH = 1800f
    private val screenH = 2400f

    @Test
    fun withoutAnchor_theArtworkStartsAtItsTop() {
        assertEquals(0f, routeStartShift(tileH, screenH, anchor = null), 0f)
    }

    @Test
    fun centreAnchor_putsTheArtworkBottomAtTheCentreOfTheScreen() {
        val shift = routeStartShift(tileH, screenH, 0.5f)
        // La fila final del arte se dibuja en `tileH - shift`: tiene que caer en
        // el centro de la pantalla, que es donde está el entrenador.
        assertEquals(screenH / 2f, tileH - shift, 0.001f)
    }

    @Test
    fun shift_alwaysStaysInsideOneTile() {
        listOf(null, 0f, 0.25f, 0.5f, 1f, 2f, -1f).forEach { anchor ->
            val shift = routeStartShift(tileH, screenH, anchor)
            assertTrue("ancla $anchor fuera de baldosa: $shift", shift >= 0f && shift < tileH)
        }
    }

    @Test
    fun bottomAnchor_putsTheArtworkBottomAtTheBottomOfTheScreen() {
        val shift = routeStartShift(tileH, screenH, 1f)
        // El borde inferior puede caer en una repetición anterior: lo que importa
        // es que quede alineado con el borde de abajo de la pantalla.
        assertEquals(screenH % tileH, tileH - shift, 0.001f)
    }
}
