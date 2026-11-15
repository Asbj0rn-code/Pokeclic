/**
 * Utilidades de dibujo para sprites pixel art: recorte del margen
 * transparente y escalado sin interpolación.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntRect

/**
 * Caja opaca del sprite: recorta el padding transparente de los PNG.
 * Devuelve `IntRect.Zero` si el bitmap no tiene píxeles visibles.
 */
internal fun ImageBitmap.opaqueBounds(): IntRect {
    val source = asAndroidBitmap()
    val w = source.width
    val h = source.height
    if (w <= 0 || h <= 0) return IntRect.Zero

    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)

    var left = w
    var top = h
    var right = -1
    var bottom = -1
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            if (pixels[row + x] ushr 24 != 0) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
    }
    return if (right < left || bottom < top) {
        IntRect.Zero
    } else {
        IntRect(left, top, right + 1, bottom + 1)
    }
}
