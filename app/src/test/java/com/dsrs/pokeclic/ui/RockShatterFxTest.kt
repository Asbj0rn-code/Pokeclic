package com.dsrs.pokeclic.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * El sheet de `rock_shatter.png` sale de pixelfx (`impacts/rock-shatter`). Si el
 * arte se reexporta con otro tamaño o más fotogramas, estos tests avisan antes
 * de que el efecto se corte o desborde.
 */
class RockShatterFxTest {

    private val sheet = run {
        val candidates = listOf(
            File("src/main/res/drawable-nodpi/rock_shatter.png"),
            File("app/src/main/res/drawable-nodpi/rock_shatter.png")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("No se encontró el sheet en $candidates")
        ImageIO.read(file)
    }

    @Test
    fun sheetMatchesTheExportedGrid() {
        assertEquals(
            "ancho del sheet",
            RockShatterFx.SHEET_WIDTH,
            sheet.width
        )
        assertEquals(
            "alto del sheet",
            RockShatterFx.SHEET_HEIGHT,
            sheet.height
        )
        assertTrue(
            "La rejilla no alcanza para ${RockShatterFx.FRAME_COUNT} fotogramas",
            RockShatterFx.ROWS * RockShatterFx.COLUMNS >= RockShatterFx.FRAME_COUNT
        )
    }

    @Test
    fun frameAt_followsTheExportedFps() {
        assertEquals(0, RockShatterFx.frameAt(0))
        assertEquals("medio fotograma sigue en el primero", 0, RockShatterFx.frameAt(41))
        assertEquals("un fotograma completo avanza", 1, RockShatterFx.frameAt(42))
        assertEquals("el último fotograma con arte", 12, RockShatterFx.frameAt(541))
        assertEquals("al cumplir la duración se pasa del ciclo", 13, RockShatterFx.frameAt(542))
    }

    @Test
    fun effectScalesWithTheRockItBreaks() {
        // `rock.png` mide 32 px y el frame 64: 2 es la escala natural. Nunca por
        // debajo, o los trozos se ven más chicos que la roca que rompen.
        assertTrue("La escala no puede achicar el efecto", RockShatterFx.SCALE >= 2f)
        assertEquals(80f, RockShatterFx.widthFor(32.dp).value, 0f)
        assertEquals(100f, RockShatterFx.heightFor(RockShatterFx.widthFor(32.dp)).value, 0f)
    }

    @Test
    fun pivotSitsInsideTheFrame() {
        assertTrue(RockShatterFx.PIVOT_X in 0 until RockShatterFx.FRAME_WIDTH)
        assertTrue(RockShatterFx.PIVOT_Y in 0 until RockShatterFx.FRAME_HEIGHT)
    }

    @Test
    fun projectileFillsItsLaneWithoutOverflowing() {
        // Teléfono de 393 dp: carril de 283 dp entre 5 columnas.
        val cell = 56.6.dp
        val size = projectileSizeFor(cell)
        assertTrue("La roca desborda su carril ($size en $cell)", size <= cell)
        assertTrue("La roca quedó chica ($size)", size.value >= 46f)
        assertTrue(
            "La roca no crece con el carril",
            projectileSizeFor(60.dp) > projectileSizeFor(50.dp)
        )
    }

    @Test
    fun projectileTopsOutAtBothEnds() {
        assertEquals(36f, projectileSizeFor(20.dp).value, 0f)
        assertEquals(64f, projectileSizeFor(200.dp).value, 0f)
    }

    @Test
    fun burstAlwaysKeepsTheProjectileProportion() {
        // Agrandar la roca agranda su rotura: el estallido sale de `rockSize`.
        listOf(36f, 48f, 64f).forEach { rock ->
            val size = rock.dp
            assertEquals(
                "estallido para una roca de $rock dp",
                size.value * RockShatterFx.SCALE,
                RockShatterFx.widthFor(size).value,
                0.01f
            )
        }
    }
}
