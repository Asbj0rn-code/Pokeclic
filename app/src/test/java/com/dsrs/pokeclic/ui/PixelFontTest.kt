package com.dsrs.pokeclic.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La fuente 5×7 del cartel de resultado viene de pixelfx (`src/core/font.js`). Si
 * falta un glifo, el carácter se pinta como un hueco en blanco y el mensaje queda
 * roto sin que nada falle: estos tests lo cazan antes.
 */
class PixelFontTest {

    private val strings = run {
        val candidates = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("No se encontró strings.xml en $candidates")
        file.readText()
    }

    private fun textOf(name: String): String {
        val match = Regex("<string name=\"$name\">(.*?)</string>").find(strings)
            ?: error("No existe la cadena $name")
        return match.groupValues[1]
    }

    @Test
    fun everyResultMessageCanBeDrawn() {
        // Los mensajes del cartel, con el hueco del número ya resuelto.
        val messages = listOf(
            textOf("gym_ring_hit"),
            textOf("gym_ring_miss"),
            textOf("gym_ring_miss_multi").replace("%d", "3"),
            textOf("gym_ring_combo").replace("%d", "12")
        )
        messages.forEach { message ->
            message.forEach { ch ->
                assertTrue(
                    "'$ch' de «$message» no tiene glifo en la fuente de píxeles",
                    PixelFont.hasGlyph(ch)
                )
            }
        }
    }

    @Test
    fun widthCountsTheGlyphAndTheGap() {
        assertEquals("la A mide 5", 5, PixelFont.widthOf("A"))
        // La I viene recortada a sus columnas entintadas: 3 en vez de 5.
        assertEquals("la I mide 3", 3, PixelFont.widthOf("I"))
        // 5 + hueco 1 + 5 = 11, sin hueco de cola.
        assertEquals("dos glifos no dejan hueco al final", 11, PixelFont.widthOf("AB"))
    }

    @Test
    fun lowercaseFallsBackToItsUppercase() {
        assertEquals(PixelFont.widthOf("COMBO"), PixelFont.widthOf("combo"))
        assertTrue("la x minúscula tiene glifo propio", PixelFont.hasGlyph('x'))
    }
}
