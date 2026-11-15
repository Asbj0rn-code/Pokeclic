/**
 * Estallido de roca al romper un proyectil del gimnasio.
 *
 * El arte salió de pixelfx (`impacts/rock-shatter`) y se exportó como sprite
 * sheet de 13 fotogramas; aquí solo se reproduce. Es pixel-art a escala entera
 * respecto del proyectil, así que se dibuja con `FilterQuality.None`.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Un estallido activo, en coordenadas de la escena. [size] es el tamaño del
 * proyectil que se rompió: el efecto se escala con él para que el tamaño de
 * píxel coincida con el de la roca.
 */
internal data class RockBurst(
    val id: Long,
    val center: Offset,
    val size: Dp
)

/** Medidas del sprite sheet exportado; los tests las contrastan con el PNG. */
internal object RockShatterFx {

    const val COLUMNS = 5
    const val FRAME_WIDTH = 64
    const val FRAME_HEIGHT = 80

    /** Fotogramas del ciclo (el último ya está vacío). */
    const val FRAME_COUNT = 13
    const val FPS = 24

    /** Punto de impacto dentro del frame: el pivote que publica el exportador. */
    const val PIVOT_X = 32
    const val PIVOT_Y = 32

    /**
     * Escala del efecto respecto al proyectil. La natural es 2 (el frame mide
     * 64 px y `rock.png` 32, así que 2 conserva el tamaño de píxel); 2,5 agranda
     * el estallido un 25 % para que se lea sobre el suelo claro del gimnasio.
     */
    const val SCALE = 2.5f

    val ROWS: Int = (FRAME_COUNT + COLUMNS - 1) / COLUMNS
    val SHEET_WIDTH: Int = COLUMNS * FRAME_WIDTH
    val SHEET_HEIGHT: Int = ROWS * FRAME_HEIGHT

    /** Fotograma que toca a [elapsedMs] de vida; puede pasarse de [FRAME_COUNT]. */
    fun frameAt(elapsedMs: Long): Int = (elapsedMs * FPS / 1000L).toInt()

    /** Tamaño en pantalla del efecto para un proyectil de [rockSize]. */
    fun widthFor(rockSize: Dp): Dp = rockSize * SCALE

    fun heightFor(width: Dp): Dp = width * (FRAME_HEIGHT.toFloat() / FRAME_WIDTH)
}

/**
 * Reproduce el estallido una vez y avisa con [onFinished] al terminar, para que
 * el llamante lo saque de su lista.
 */
@Composable
internal fun RockShatterBurst(
    sheet: ImageBitmap,
    center: Offset,
    rockSize: Dp,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var frame by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        var startNanos = -1L
        while (true) {
            val nanos = withFrameNanos { it }
            if (startNanos < 0L) startNanos = nanos
            val next = RockShatterFx.frameAt((nanos - startNanos) / 1_000_000L)
            if (next >= RockShatterFx.FRAME_COUNT) {
                onFinished()
                break
            }
            frame = next
        }
    }

    val width = RockShatterFx.widthFor(rockSize)
    val height = RockShatterFx.heightFor(width)
    val density = LocalDensity.current
    // El pivote no está en el centro del frame: el efecto crece hacia abajo
    // porque los trozos caen.
    val pivotX = RockShatterFx.PIVOT_X.toFloat() / RockShatterFx.FRAME_WIDTH
    val pivotY = RockShatterFx.PIVOT_Y.toFloat() / RockShatterFx.FRAME_HEIGHT
    val x = with(density) { (center.x - width.toPx() * pivotX).toDp() }
    val y = with(density) { (center.y - height.toPx() * pivotY).toDp() }

    Canvas(
        modifier = modifier
            .offset(x = x, y = y)
            .size(width, height)
    ) {
        val column = frame % RockShatterFx.COLUMNS
        val row = frame / RockShatterFx.COLUMNS
        drawImage(
            image = sheet,
            srcOffset = IntOffset(
                column * RockShatterFx.FRAME_WIDTH,
                row * RockShatterFx.FRAME_HEIGHT
            ),
            srcSize = IntSize(RockShatterFx.FRAME_WIDTH, RockShatterFx.FRAME_HEIGHT),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.None
        )
    }
}
