/**
 * Matemáticas y constantes compartidas del mapa de Kanto.
 *
 * Es la fuente única de verdad de la transformación imagen↔pantalla: la usan
 * `MapScreen` (juego) y, más adelante, el editor de zonas exclusivo de
 * desarrollo. Extraerlas aquí evita que dos pantallas copien la misma fórmula
 * y diverjan al ampliar o desplazar.
 *
 * Convención de coordenadas, la ya documentada en `MapScreen`:
 *
 * - La imagen se pinta ajustada (fit) y centrada en el contenedor, con tamaño
 *   `fitWidthPx × fitHeightPx` (el escalado inicial ya incluido).
 * - Sobre ese asiento se aplican `zoom` y `offset` vía `graphicsLayer`.
 * - Un punto **normalizado** `(u, v)` del PNG original — con `(0, 0)` en su
 *   esquina superior izquierda y `(1, 1)` en la inferior derecha, sobre los
 *   píxeles del recurso — se pinta en pantalla en
 *
 *   ```
 *   centroContenedor + offset + zoom * ((u - 0.5) * fitWidthPx,
 *                                     (v - 0.5) * fitHeightPx)
 *   ```
 *
 * `screenToUv` y `uvToScreen` son los dos sentidos de esa fórmula; los gestos,
 * el hit-testing de zonas y el editor comparten los mismos números.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dsrs.pokeclic.model.UvPoint
import kotlin.math.max
import kotlin.math.min

/**
 * Zoom que corresponde a la vista completa: el mapa entero encajado en pantalla.
 * Es el zoom mínimo y el estado al que se restaura la vista.
 */
internal const val MapFitZoom = 1f

/**
 * Zoom máximo del mapa, relativo a la vista completa (el mapa entero encajado en
 * pantalla). El mínimo es la propia vista completa.
 */
internal const val MapMaxZoom = 5f

/**
 * Zoom dado por el doble toque al partir de la vista completa.
 */
internal const val MapDoubleTapZoom = 2f

/**
 * Duración de la animación al restaurar la vista completa del mapa.
 */
internal const val MapResetAnimationMs = 300

/**
 * Duración de la animación del doble toque al ampliar a [MapDoubleTapZoom].
 */
internal const val MapZoomToggleAnimationMs = 220

/**
 * Color de relleno detrás del mapa, muestreado del borde inferior del PNG
 * (océano de Kanto, RGB ≈ 1/133/219). Se emplea para las zonas libres del
 * contenedor cuando la relación de aspecto de la pantalla no coincide con la
 * del mapa, de modo que el océano parece continuar más allá de la imagen.
 */
internal val MapOceanColor = Color(0xFF0185DB)

/**
 * Escala (píxeles de pantalla por píxel de la imagen) con la que el mapa encaja
 * completo en el contenedor respetando su relación de aspecto. `imageSize` es el
 * tamaño intrínseco del pintor (nativo del recurso) y `containerSize` el tamaño
 * real del contenedor. Devuelve `null` si aún no se han podido medir.
 */
internal fun computeMapFitScale(imageSize: Size, containerSize: IntSize, density: Density): Float? {
    if (containerSize.width <= 0 || containerSize.height <= 0) return null
    if (imageSize.width <= 0f || imageSize.height <= 0f) return null
    val imageWidthPx = with(density) { imageSize.width.dp.toPx() }
    val imageHeightPx = with(density) { imageSize.height.dp.toPx() }
    return min(
        containerSize.width / imageWidthPx,
        containerSize.height / imageHeightPx
    )
}

/**
 * Limita un desplazamiento candidato para que el mapa nunca abandone el
 * contenedor: en cada eje, si el mapa ampliado es más pequeño que la vista, el
 * eje queda centrado fijo (offset 0), y si es mayor, se frena en el momento en
 * que el borde del mapa toca el borde de la vista.
 *
 * Toma la mitad de cada rango directo de la fórmula de la imagen: el margen
 * sobrante a un lado del centro es `fit * zoom / 2 − contenedor / 2`.
 */
internal fun clampMapOffset(
    candidate: Offset,
    containerWidth: Float,
    containerHeight: Float,
    fitWidthPx: Float,
    fitHeightPx: Float,
    zoom: Float
): Offset {
    val rangeX = max(0f, fitWidthPx * zoom / 2f - containerWidth / 2f)
    val rangeY = max(0f, fitHeightPx * zoom / 2f - containerHeight / 2f)
    return Offset(
        candidate.x.coerceIn(-rangeX, rangeX),
        candidate.y.coerceIn(-rangeY, rangeY)
    )
}

/**
 * Punto de la pantalla (en píxeles, con el origen en la esquina del contenedor)
 * → punto **normalizado** `(u, v)` del PNG original. Es la inversa exacta de la
 * fórmula de composición (véase la KDoc del fichero), por lo que es la vía por
 * la que el modo normal decide qué zona está bajo una pulsación.
 *
 * Devuelve coordenadas **sin recortar** a `[0, 1]`: los toques fuera de la
 * imagen dan `(u, v)` fuera de rango y el llamante decide qué hacer con
 * ellos (ninguna zona los contendrá).
 */
internal fun screenToUv(
    screenX: Float,
    screenY: Float,
    containerCenterX: Float,
    containerCenterY: Float,
    offsetX: Float,
    offsetY: Float,
    zoom: Float,
    fitWidthPx: Float,
    fitHeightPx: Float
): UvPoint {
    require(zoom > 0f) { "El zoom debe ser positivo (recibido $zoom)" }
    return UvPoint(
        u = (screenX - containerCenterX - offsetX) / (zoom * fitWidthPx) + 0.5f,
        v = (screenY - containerCenterY - offsetY) / (zoom * fitHeightPx) + 0.5f
    )
}

/**
 * Punto normalizado `(u, v)` del PNG original → posición en pantalla. Es la
 * fórmula de la KDoc del fichero, explícita por simetría con [screenToUv] y
 * con el mismo uso previsto por el editor.
 */
internal fun uvToScreen(
    u: Float,
    v: Float,
    containerCenterX: Float,
    containerCenterY: Float,
    offsetX: Float,
    offsetY: Float,
    zoom: Float,
    fitWidthPx: Float,
    fitHeightPx: Float
): Offset {
    require(zoom > 0f) { "El zoom debe ser positivo (recibido $zoom)" }
    return Offset(
        x = containerCenterX + offsetX + zoom * (u - 0.5f) * fitWidthPx,
        y = containerCenterY + offsetY + zoom * (v - 0.5f) * fitHeightPx
    )
}
