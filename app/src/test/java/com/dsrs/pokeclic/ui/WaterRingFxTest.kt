package com.dsrs.pokeclic.ui

import com.dsrs.pokeclic.model.RingOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Las dos hojas del resultado de un anillo salen de pixelfx (`water/hydro-burst` y
 * `water/hydro-implode`). Si el arte se reexporta con otro tamaño o más
 * fotogramas, estos tests avisan antes de que el efecto se corte o desborde.
 */
class WaterRingFxTest {

    private fun sheetOf(name: String) = run {
        val candidates = listOf(
            File("src/main/res/drawable-nodpi/$name.png"),
            File("app/src/main/res/drawable-nodpi/$name.png")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("No se encontró el sheet en $candidates")
        ImageIO.read(file)
    }

    @Test
    fun burstSheetMatchesTheExportedGrid() {
        val sheet = sheetOf("hydro_burst")
        val rows = (WaterRingSheet.BURST.frameCount + WaterRingSheet.BURST.columns - 1) /
            WaterRingSheet.BURST.columns
        assertEquals(
            "ancho del sheet de acierto",
            WaterRingSheet.BURST.columns * WaterRingSheet.FRAME_SIZE,
            sheet.width
        )
        assertEquals(
            "alto del sheet de acierto",
            rows * WaterRingSheet.FRAME_SIZE,
            sheet.height
        )
    }

    @Test
    fun implodeSheetMatchesTheExportedGrid() {
        val sheet = sheetOf("hydro_implode")
        val rows = (WaterRingSheet.IMPLODE.frameCount + WaterRingSheet.IMPLODE.columns - 1) /
            WaterRingSheet.IMPLODE.columns
        assertEquals(
            "ancho del sheet de fallo",
            WaterRingSheet.IMPLODE.columns * WaterRingSheet.FRAME_SIZE,
            sheet.width
        )
        assertEquals(
            "alto del sheet de fallo",
            rows * WaterRingSheet.FRAME_SIZE,
            sheet.height
        )
    }

    @Test
    fun eachOutcomeUsesItsOwnArt() {
        // El acierto salpica y el fallo implosiona: no pueden compartir hoja.
        assertEquals(WaterRingSheet.BURST, WaterRingSheet.forOutcome(RingOutcome.HIT))
        assertEquals(WaterRingSheet.IMPLODE, WaterRingSheet.forOutcome(RingOutcome.MISS))
        assertNotEquals(WaterRingSheet.BURST.resId, WaterRingSheet.IMPLODE.resId)
    }

    @Test
    fun frameAt_followsTheExportedFps() {
        val spec = WaterRingSheet.IMPLODE
        assertEquals(0, spec.frameAt(0))
        assertEquals("medio fotograma sigue en el primero", 0, spec.frameAt(16))
        assertEquals("un fotograma completo avanza", 1, spec.frameAt(34))
        assertEquals(
            "el último fotograma con arte",
            spec.frameCount - 1,
            spec.frameAt(spec.durationMs - 1)
        )
    }

    @Test
    fun effectOutlastsTheLabelFade() {
        // La etiqueta "¡Acierto!" se desvanece con el mismo reloj: el efecto no
        // debería durar menos que eso o el golpe se corta a media salpicadura.
        assertTrue(
            "el efecto termina después de la etiqueta",
            WaterRingSheet.IMPLODE.durationMs >= 900L
        )
    }
}
