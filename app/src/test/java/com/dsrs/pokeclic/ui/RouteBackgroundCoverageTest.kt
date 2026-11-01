package com.dsrs.pokeclic.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La pantalla nunca puede quedar con una franja sin dibujar. Con el arte a escala
 * 1:1 mide exactamente un ancho de pantalla, así que cualquier corrimiento asoma
 * por un borde: lo único que lo tapa es que la primera columna empiece en ≤ 0 y
 * llegue a 0.
 */
class RouteBackgroundCoverageTest {

    private val screenWidth = 1080f
    private val tileW = 1080f // imagen nativa, sin margen lateral

    private fun firstStart(left0: Float, shiftH: Float, tile: Float): Float =
        routeColumnStart(routeFirstColumn(left0, shiftH, tile), left0, shiftH, tile)

    @Test
    fun atRestOneColumnCoversTheScreen() {
        val start = firstStart(left0 = 0f, shiftH = 0f, tile = tileW)
        assertTrue("La copia debe empezar en el borde izquierdo", start <= 0f)
        assertTrue("La copia debe llegar al borde izquierdo", start + tileW >= screenWidth)
    }

    @Test
    fun withoutMargin_everyShiftStaysCovered() {
        // Recorrido lateral completo, sin zoom: el hueco lo tapa la copia espejada.
        var shift = -0.08f * screenWidth
        while (shift <= 0.08f * screenWidth) {
            val start = firstStart(left0 = 0f, shiftH = shift, tile = tileW)
            assertTrue("Hueco a la izquierda con shift=$shift", start <= 0f)
            assertTrue("Columna fuera de la pantalla con shift=$shift", start + tileW > 0f)
            shift += screenWidth * 0.01f
        }
    }

    @Test
    fun withMargin_theMirrorNeverStepsIn() {
        // Con zoom, la columna normal ya cubre la pantalla en todo el recorrido.
        val slack = routeSlackFactor(0.08f)
        val tile = screenWidth * slack
        val left0 = (screenWidth - tile) / 2f
        var shift = -0.08f * screenWidth
        while (shift <= 0.08f * screenWidth) {
            val start = firstStart(left0 = left0, shiftH = shift, tile = tile)
            assertTrue("Hueco con shift=$shift", start <= 0f)
            assertTrue("Falta cobertura con shift=$shift", start + tile >= screenWidth)
            shift += screenWidth * 0.01f
        }
    }
}
