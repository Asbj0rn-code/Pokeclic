package com.dsrs.pokeclic.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La pantalla nunca puede quedar con una franja sin dibujar. Con el arte a escala
 * 1:1 mide exactamente un ancho de pantalla, así que cualquier corrimiento asoma
 * por un borde: lo único que lo tapa es que la primera columna empiece en ≤ 0 y
 * llegue a 0.
 *
 * El lateral es un scroll sin tope, así que el barrido cubre varias pantallas a
 * cada lado, no un margen pequeño alrededor del centro.
 */
class RouteBackgroundCoverageTest {

    private val screenWidth = 1080f
    private val tileW = 1080f // imagen nativa, escala 1:1

    private fun firstStart(left0: Float, shiftH: Float, tile: Float): Float =
        routeColumnStart(routeFirstColumn(left0, shiftH, tile), left0, shiftH, tile)

    @Test
    fun atRestOneColumnCoversTheScreen() {
        val start = firstStart(left0 = 0f, shiftH = 0f, tile = tileW)
        assertTrue("La copia debe empezar en el borde izquierdo", start <= 0f)
        assertTrue("La copia debe llegar al borde izquierdo", start + tileW >= screenWidth)
    }

    @Test
    fun anyShiftStaysCovered() {
        // Tres pantallas a cada lado, que es más de lo que da un ciclo entero.
        var shift = -3f * screenWidth
        while (shift <= 3f * screenWidth) {
            val start = firstStart(left0 = 0f, shiftH = shift, tile = tileW)
            assertTrue("Hueco a la izquierda con shift=$shift", start <= 0f)
            assertTrue("Columna fuera de la pantalla con shift=$shift", start + tileW > 0f)
            shift += screenWidth * 0.01f
        }
    }

    @Test
    fun wrappedShiftDrawsTheSameScreen() {
        // El renderer enrolla el desplazamiento cada ancho de columna. Como todas
        // las columnas son la misma loseta, enrollar no puede mover lo que se ve:
        // si lo moviera, el fondo daría un salto al cruzar una pantalla.
        val period = tileW
        var shift = -3f * screenWidth
        while (shift <= 3f * screenWidth) {
            var wrapped = shift % period
            if (wrapped < 0f) wrapped += period
            val directStart = firstStart(0f, shift, tileW)
            val wrappedStart = firstStart(0f, wrapped, tileW)
            assertEquals(
                "La columna dibujada cambia: $shift -> $wrapped",
                directStart,
                wrappedStart,
                0.01f
            )
            shift += screenWidth * 0.01f
        }
    }
}
