/**
 * Fondo de ruta animado: repite [R.drawable.background_ruta] en bucle vertical y
 * lo desplaza **hacia abajo** mientras se pulsa la Pokéball, frenando con
 * amortiguación hasta pararse del todo cuando no se pulsa.
 *
 * Es una capa pura de UI: no toca el ViewModel ni el estado de juego. Lee el
 * recuento de pulsaciones que le pasa el caller y lo traduce a movimiento.
 */
package com.dsrs.pokeclic.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import com.dsrs.pokeclic.R
import kotlinx.coroutines.isActive
import kotlin.math.exp
import kotlin.math.floor

/**
 * Física del desplazamiento.
 *
 * La velocidad se mide en **alturas de baldosa por segundo** (t/s), donde una
 * baldosa es una repetición completa de la imagen. Al no medir en px ni en dp,
 * el recorrido se ve al mismo ritmo en pantallas de cualquier ancho y
 * densidad: una baldosa es siempre, aproximadamente, una pantalla.
 */
internal object RouteScrollDefaults {

    /** Velocidad que aporta cada pulsación de la Pokéball. */
    const val IMPULSE = 0.22f

    /**
     * Dirección del desplazamiento: `-1f` = hacia abajo (el contenido entra por
     * arriba de la pantalla), `1f` = hacia arriba. La velocidad es siempre una
     * magnitud positiva; este factor solo orienta la fase.
     */
    const val DIRECTION = -1f

    /** Tope de velocidad: evita que el mashing vuelva ilegible el fondo. */
    const val MAX_SPEED = 1.40f

    /** Amortiguación exponencial por segundo. Es la que frena entre pulsaciones. */
    const val FRICTION = 3.0f

    /**
     * Por debajo de esta velocidad se considera parado (≈ 6 dp/s en un teléfono
     * de 1080 px). Se corta del todo para no redibujar en reposo.
     */
    const val STOP_EPSILON = 0.008f

    /** Recorte del delta entre frames: evita un salto si la app vuelve de pausa. */
    const val MAX_FRAME_SECONDS = 0.05f

    /**
     * Fila del Fuente que pasa a ser el **borde superior** del bitmap enrollado.
     *
     * El PNG original no corta sin costura: su fila 1919 (borde inferior) no
     * coincide con la fila 0 (borde superior). Al cargar, las filas se enrollan
     * de forma cíclica para que la única unión del bucle caiga en la pareja
     * localmente más tranquila del arte, medidas filas 1268-1269 (zona plana de
     * pasto, diferencia de MAE = 0 entre ambas): el tile muestra la fila F[1269]
     * arriba y F[1268] abajo, que como filas consecutivas del original son
     * continuas por definición.
     */
    const val SEAM_CUT_ROW = 1269

    /** Tope de baldosas dibujadas por frame, por si la proporción es absurda. */
    const val MAX_TILES = 64
}

/**
 * Fondo de ruta en bucle vertical, listo para cualquier tamaño de pantalla.
 *
 * Tres decisiones dentro, todas reversibles:
 *
 * 1. **Escala**: la imagen se escala uniforme hasta llenar el ancho y la altura
 *    se cubre repitiendo, así que no hay recorte ni deformación y da igual si
 *    la pantalla es 720p, 1080p o 1440p.
 * 2. **Bucle sin costura, siempre derecho**: el PNG no empieza y termina igual,
 *    así que al cargar se enrolla cíclicamente ([RouteScrollDefaults.SEAM_CUT_ROW])
 *    hasta la pareja de filas consecutivas más parecida del arte. La unión entre
 *    repeticiones pasa a juntar dos filas consecutivas del original: continua
 *    por construcción, y **ninguna copia se dibuja reflejada**.
 * 3. **Filtro**: nearest-neighbour al ampliar, para conservar el pixel art;
 *    bilineal al reducir, porque nearest-neighbour deja moteado al perder filas.
 *
 * @param pulseCount número acumulado de pulsaciones sobre la Pokéball. Cada
 *   incremento da un empujón ([RouteScrollDefaults.IMPULSE]); mientras no llega
 *   ninguno, la fricción frena el fondo hasta pararlo.
 */
@Composable
internal fun RouteScrollingBackground(
    pulseCount: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // `drawable-nodpi` evita el reescalado por densidad: siempre se carga a
    // tamaño nativo y el escalado lo controla el dibujo. Al salir, la imagen se
    // enrolla (ver [RouteScrollDefaults.SEAM_CUT_ROW]) para que el bucle sea
    // continuo y las copias vayan todas derechos, sin espejar ninguna.
    val bitmap = remember(context) {
        val source = BitmapFactory.decodeResource(context.resources, R.drawable.background_ruta)
            ?: return@remember null
        val rolled = Bitmap.createBitmap(
            source.width, source.height, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(rolled)
        val cut = RouteScrollDefaults.SEAM_CUT_ROW
        // Parte superior: filas cut..height-1 del Fuente, en la parte de arriba
        // del tile. Llega hasta `size.height - cut` y no más allá.
        canvas.drawBitmap(source, 0f, -cut.toFloat(), null)
        // Cierre cíclico: filas 0..cut-1 del Fuente, abajo del todo.
        canvas.drawBitmap(source, 0f, (source.height - cut).toFloat(), null)
        source.recycle()
        rolled.asImageBitmap()
    }

    // El contador se lee dentro del bucle de frames, no en composición: así el
    // fondo no redibuja ni recompone nada por cada pulsación, solo la física.
    val pulses by rememberUpdatedState(pulseCount)

    // Fase dentro de una baldosa, 0f..1f. Se guarda normalizada y no en px
    // acumulados para que la precisión del float no se degrade en sesiones
    // larguísimas: la fase siempre vive entre 0 y 1.
    val phase = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        var speed = 0f
        var seenPulses = pulses

        while (isActive) {
            withFrameNanos { now ->
                val dt = if (lastFrameNanos == 0L) {
                    0f
                } else {
                    ((now - lastFrameNanos) / 1_000_000_000f)
                        .coerceAtMost(RouteScrollDefaults.MAX_FRAME_SECONDS)
                }
                lastFrameNanos = now

                // Impulso: cada pulsación nueva suma velocidad, con un tope.
                val current = pulses
                if (current != seenPulses) {
                    val delta = (current - seenPulses).coerceIn(0L, 3L)
                    seenPulses = current
                    if (delta > 0L) {
                        speed = (speed + RouteScrollDefaults.IMPULSE * delta)
                            .coerceAtMost(RouteScrollDefaults.MAX_SPEED)
                    }
                }

                if (speed > 0f) {
                    speed *= exp(-RouteScrollDefaults.FRICTION * dt)
                    if (speed < RouteScrollDefaults.STOP_EPSILON) speed = 0f

                    if (speed > 0f) {
                        // `DIRECTION` solo orienta: la fase puede quedar negativa
                        // y `floor` la vuelve a [0f, 1f) sin salto visible (el
                        // tile se repite con período exacto, así que el wrap es
                        // continuo por construcción).
                        var next = phase.floatValue + RouteScrollDefaults.DIRECTION * speed * dt
                        next -= floor(next) // vuelve a [0f, 1f) sin salto visible
                        phase.floatValue = next
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val bmp = bitmap ?: return@drawBehind
                val srcW = bmp.width
                val srcH = bmp.height
                if (srcW <= 0 || srcH <= 0) return@drawBehind

                val scale = size.width / srcW
                val tileHeight = srcH * scale
                if (tileHeight <= 1f) return@drawBehind

                val filter = if (scale >= 1f) FilterQuality.None else FilterQuality.Medium
                val shift = phase.floatValue * tileHeight
                var top = -shift
                var drawn = 0

                while (top < size.height && drawn < RouteScrollDefaults.MAX_TILES) {
                    // Todas las copias se dibujan idénticas y derechas: el bucle
                    // ya es continuo gracias al enrollado de la carga.
                    withTransform({
                        translate(0f, top)
                        scale(scale, scale, Offset.Zero)
                    }) {
                        drawImage(image = bmp, filterQuality = filter)
                    }
                    top += tileHeight
                    drawn++
                }
            }
    )
}
