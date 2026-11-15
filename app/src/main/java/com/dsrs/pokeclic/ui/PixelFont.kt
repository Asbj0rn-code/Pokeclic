/**
 * Fuente de píxeles 5×7 portada de pixelfx (`src/core/font.js`, la fuente "big").
 *
 * El cartel del resultado se dibuja con rectángulos en vez de con `Text` a
 * propósito: la tipografía del sistema no es pixel art y desentonaba con el resto
 * del juego. Los glifos están recortados a sus columnas entintadas, así que la "I"
 * ocupa menos que la "M" y el texto no queda lleno de huecos.
 *
 * `drawText` hace el trabajo sucio del cartel de combo de pixelfx
 * (`ui/combo-counter`): contorno oscuro de un píxel, tramos de color arriba,
 * centro y abajo, ensanche horizontal para la mancha al entrar y un brillo que
 * barre las letras.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate

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
     * Dibuja [text] con su esquina superior izquierda en [origin].
     *
     * @param pixel lado de una celda, en px de pantalla.
     * @param stretchX ensancha cada celda en horizontal: la mancha de velocidad.
     * @param top color de las tres filas de arriba.
     * @param bottom color de las dos filas de abajo (el resto usa [color]).
     * @param outline si no es `null`, rodea cada píxel por sus cuatro lados.
     * @param shine columna, en celdas, que se pinta blanca: el brillo que barre.
     */
    fun DrawScope.drawText(
        text: String,
        origin: Offset,
        pixel: Float,
        color: Color,
        stretchX: Float = 1f,
        top: Color = color,
        bottom: Color = color,
        outline: Color? = null,
        shine: Float? = null
    ) {
        val bands = arrayOf(Path(), Path(), Path())
        val shinePath = Path()
        val cellW = pixel * stretchX

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
                    val cellX = penX + x
                    val rect = Rect(
                        offset = Offset(origin.x + cellX * cellW, origin.y + y * pixel),
                        size = Size(cellW, pixel)
                    )
                    // Filas 0-2 arriba, 5-6 abajo, 3-4 el color base.
                    val band = if (y < 3) 0 else if (y < 5) 1 else 2
                    bands[band].addRect(rect)
                    if (shine != null && kotlin.math.abs(cellX - shine) < 1.5f) shinePath.addRect(rect)
                }
            }
            penX += glyph.width + GAP
        }

        if (outline != null) {
            for (path in bands) {
                translate(pixel, 0f) { drawPath(path, outline) }
                translate(-pixel, 0f) { drawPath(path, outline) }
                translate(0f, pixel) { drawPath(path, outline) }
                translate(0f, -pixel) { drawPath(path, outline) }
            }
        }
        drawPath(bands[0], top)
        drawPath(bands[1], color)
        drawPath(bands[2], bottom)
        if (shine != null) drawPath(shinePath, Color.White)
    }
}
