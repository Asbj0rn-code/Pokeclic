/**
 * Fuente de píxeles 5×7 portada de pixelfx (`src/core/font.js`, la fuente "big").
 *
 * El cartel del resultado se dibuja con rectángulos en vez de con `Text` a
 * propósito: la tipografía del sistema no es pixel art y desentonaba con el resto
 * del juego. Los glifos están recortados a sus columnas entintadas, así que la "I"
 * ocupa menos que la "M" y el texto no queda lleno de huecos.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

internal object PixelFont {

    /** Alto de cada glifo, en píxeles de fuente. */
    const val HEIGHT = 7

    /** Separación entre glifos, en píxeles de fuente. */
    private const val GAP = 1

    /** Ancho reservado al espacio, en píxeles de fuente. */
    private const val SPACE = 3

    private val RAW = mapOf(
        'A' to ".###.|#...#|#...#|#####|#...#|#...#|#...#",
        'B' to "####.|#...#|#...#|####.|#...#|#...#|####.",
        'C' to ".###.|#...#|#....|#....|#....|#...#|.###.",
        'D' to "####.|#...#|#...#|#...#|#...#|#...#|####.",
        'E' to "#####|#....|#....|####.|#....|#....|#####",
        'F' to "#####|#....|#....|####.|#....|#....|#....",
        'G' to ".###.|#...#|#....|#.###|#...#|#...#|.####",
        'H' to "#...#|#...#|#...#|#####|#...#|#...#|#...#",
        'I' to "###|.#.|.#.|.#.|.#.|.#.|###",
        'J' to "..###|...#.|...#.|...#.|...#.|#..#.|.##..",
        'K' to "#...#|#..#.|#.#..|##...|#.#..|#..#.|#...#",
        'L' to "#....|#....|#....|#....|#....|#....|#####",
        'M' to "#...#|##.##|#.#.#|#.#.#|#...#|#...#|#...#",
        'N' to "#...#|#...#|##..#|#.#.#|#..##|#...#|#...#",
        'O' to ".###.|#...#|#...#|#...#|#...#|#...#|.###.",
        'P' to "####.|#...#|#...#|####.|#....|#....|#....",
        'Q' to ".###.|#...#|#...#|#...#|#.#.#|#..#.|.##.#",
        'R' to "####.|#...#|#...#|####.|#.#..|#..#.|#...#",
        'S' to ".####|#....|#....|.###.|....#|....#|####.",
        'T' to "#####|..#..|..#..|..#..|..#..|..#..|..#..",
        'U' to "#...#|#...#|#...#|#...#|#...#|#...#|.###.",
        'V' to "#...#|#...#|#...#|#...#|#...#|.#.#.|..#..",
        'W' to "#...#|#...#|#...#|#.#.#|#.#.#|#.#.#|.#.#.",
        'X' to "#...#|#...#|.#.#.|..#..|.#.#.|#...#|#...#",
        'Y' to "#...#|#...#|.#.#.|..#..|..#..|..#..|..#..",
        'Z' to "#####|....#|...#.|..#..|.#...|#....|#####",
        '0' to ".###.|#...#|#..##|#.#.#|##..#|#...#|.###.",
        '1' to ".#.|##.|.#.|.#.|.#.|.#.|###",
        '2' to ".###.|#...#|....#|...#.|..#..|.#...|#####",
        '3' to "#####|...#.|..#..|...#.|....#|#...#|.###.",
        '4' to "...#.|..##.|.#.#.|#..#.|#####|...#.|...#.",
        '5' to "#####|#....|####.|....#|....#|#...#|.###.",
        '6' to "..##.|.#...|#....|####.|#...#|#...#|.###.",
        '7' to "#####|....#|...#.|..#..|.#...|.#...|.#...",
        '8' to ".###.|#...#|#...#|.###.|#...#|#...#|.###.",
        '9' to ".###.|#...#|#...#|.####|....#|...#.|.##..",
        '!' to "#|#|#|#|#|.|#",
        // La apertura de exclamación es el mismo signo del revés.
        '¡' to "#|.|#|#|#|#|#",
        '?' to ".###.|#...#|....#|...#.|..#..|.....|..#..",
        // El signo de multiplicar reutiliza la "x" minúscula del original.
        '×' to ".....|.....|#...#|.#.#.|..#..|.#.#.|#...#",
        '.' to ".|.|.|.|.|.|#",
        ',' to "..|..|..|..|..|.#|#.",
        ':' to ".|.|#|.|.|#|.",
        '-' to "....|....|....|####|....|....|....",
        '+' to ".....|..#..|..#..|#####|..#..|..#..|.....",
        '/' to "....#|....#|...#.|..#..|.#...|#....|#....",
        '%' to "##...|##..#|...#.|..#..|.#...|#..##|...##",
        '(' to "..#|.#.|#..|#..|#..|.#.|..#",
        ')' to "#..|.#.|..#|..#|..#|.#.|#.."
    )

    /** Glifo ya rasterizado: filas de píxeles encendidos. */
    private class Glyph(val width: Int, val rows: Array<BooleanArray>)

    private val glyphs: Map<Char, Glyph> = RAW.mapValues { (_, src) ->
        val rows = src.split('|')
        require(rows.size == HEIGHT) { "glifo con ${rows.size} filas, se esperaban $HEIGHT" }
        val width = rows.first().length
        Glyph(width, Array(HEIGHT) { y -> BooleanArray(width) { x -> rows[y][x] == '#' } })
    }

    /**
     * Glifo de [ch], o `null` si no existe. Las minúsculas caen en su mayúscula,
     * así los textos no necesitan venir ya en mayúsculas.
     */
    private fun glyphOf(ch: Char): Glyph? =
        glyphs[ch] ?: glyphs[ch.uppercaseChar()]

    /** Ancho del texto en píxeles de fuente, con sus separaciones. */
    fun widthOf(text: String): Int {
        var width = 0
        text.forEach { ch ->
            val glyph = glyphOf(ch)
            width += (glyph?.width ?: SPACE) + GAP
        }
        return (width - GAP).coerceAtLeast(0)
    }

    /** ¿Hay glifo para [ch]? El espacio no cuenta: se resuelve como hueco. */
    fun hasGlyph(ch: Char): Boolean = ch == ' ' || glyphOf(ch) != null

    /**
     * Dibuja [text] con su esquina superior izquierda en [origin]. [pixel] es el
     * lado de un píxel de fuente en px de pantalla. Con [shadow], primero pinta
     * una copia desplazada un píxel abajo a la derecha: el escalón duro de siempre.
     */
    fun DrawScope.drawText(
        text: String,
        origin: Offset,
        pixel: Float,
        color: Color,
        shadow: Color? = null
    ) {
        if (shadow != null) {
            plot(text, Offset(origin.x + pixel, origin.y + pixel), pixel, shadow)
        }
        plot(text, origin, pixel, color)
    }

    private fun DrawScope.plot(text: String, origin: Offset, pixel: Float, color: Color) {
        var penX = 0
        text.forEach { ch ->
            val glyph = glyphOf(ch)
            if (glyph == null) {
                penX += SPACE + GAP
                return@forEach
            }
            for (y in 0 until HEIGHT) {
                val row = glyph.rows[y]
                for (x in 0 until glyph.width) {
                    if (!row[x]) continue
                    drawRect(
                        color = color,
                        topLeft = Offset(
                            origin.x + (penX + x) * pixel,
                            origin.y + y * pixel
                        ),
                        size = Size(pixel, pixel)
                    )
                }
            }
            penX += glyph.width + GAP
        }
    }
}
