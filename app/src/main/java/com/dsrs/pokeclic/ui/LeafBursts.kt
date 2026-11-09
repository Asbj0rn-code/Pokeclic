/**
 * Hojas que salen de los pies del entrenador con cada clic mientras está en
 * modo hierba alta / captura.
 *
 * El arte es un sprite sheet de pixelfx (`nature/leaf-puff`): siete hojas que
 * suben y se dispersan durante 0.9 s. Cada clic lanza una copia y se permiten
 * hasta [MAX_LEAF_BURSTS] superpuestas, así que tocar rápido no acumula basura
 * ni tapa la pantalla. Sin clics no se dibuja nada.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.dsrs.pokeclic.R
import kotlin.random.Random

/**
 * @param tapCount contador de clics sobre la Pokébola (sube en cada toque).
 * @param active solo emite hojas con la hierba alta activa.
 * @param modifier caja donde vive el efecto; el origen del sprite queda abajo.
 */
@Composable
internal fun LeafBursts(
    tapCount: Long,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val sheet = ImageBitmap.imageResource(R.drawable.fx_leaf_puff)
    val bursts = remember { mutableStateListOf<LeafBurst>() }
    var nowNanos by remember { mutableLongStateOf(0L) }
    var lastTap by remember { mutableLongStateOf(tapCount) }

    LaunchedEffect(tapCount) {
        if (tapCount == lastTap) return@LaunchedEffect
        lastTap = tapCount
        if (!active) return@LaunchedEffect
        bursts.add(LeafBurst(System.nanoTime(), Random.nextInt(-8, 9).toFloat()))
        while (bursts.size > MAX_LEAF_BURSTS) bursts.removeAt(0)
    }

    val hasBursts = bursts.isNotEmpty()
    LaunchedEffect(hasBursts) {
        if (!hasBursts) return@LaunchedEffect
        while (true) {
            withFrameNanos { nowNanos = it }
            bursts.removeAll { nowNanos - it.startNanos >= LEAF_BURST_NANOS }
            if (bursts.isEmpty()) break
        }
    }

    Canvas(modifier) {
        for (burst in bursts) {
            val elapsed = nowNanos - burst.startNanos
            if (elapsed < 0L || elapsed >= LEAF_BURST_NANOS) continue
            val frame = (elapsed * LEAF_FPS / 1_000_000_000L)
                .toInt()
                .coerceIn(0, LEAF_FRAMES - 1)
            drawImage(
                image = sheet,
                srcOffset = IntOffset(
                    (frame % LEAF_COLUMNS) * LEAF_FRAME_PX,
                    (frame / LEAF_COLUMNS) * LEAF_FRAME_PX
                ),
                srcSize = IntSize(LEAF_FRAME_PX, LEAF_FRAME_PX),
                dstOffset = IntOffset(burst.offsetX.toInt(), 0),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.None
            )
        }
    }
}

private data class LeafBurst(val startNanos: Long, val offsetX: Float)

// Metadatos del sheet exportado por pixelfx (`nature/leaf-puff`).
private const val LEAF_FRAMES = 34
private const val LEAF_FRAME_PX = 80
private const val LEAF_COLUMNS = 6
private const val LEAF_FPS = 30
private const val MAX_LEAF_BURSTS = 6
private const val LEAF_BURST_NANOS = 1_150_000_000L
