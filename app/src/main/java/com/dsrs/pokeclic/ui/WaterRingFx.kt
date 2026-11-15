/**
 * Efectos de agua del minijuego de anillos de Misty.
 *
 * Los dos artes salieron de pixelfx (`water/hydro-burst` y `water/hydro-implode`)
 * y se exportaron como sprite sheets de 208×208 por fotograma; aquí solo se
 * reproducen. Cada hoja alinea su aro con el radio que tenía el anillo al
 * resolverse (`RingFeedback.ringProgress`), así el efecto continúa exactamente
 * donde estaba el círculo en lugar de saltar al radio del jugador.
 *
 * El color es parte del arte, no un tinte: el acierto se exportó con la rampa
 * verde de `PAL.heal` y el fallo con la roja de `PAL.blood`, porque el resultado
 * tiene que leerse de un vistazo sin depender del texto.
 */
package com.dsrs.pokeclic.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.RingOutcome
import kotlin.math.roundToInt

/** Una de las dos hojas del resultado de un anillo. */
internal enum class WaterRingSheet(
    @DrawableRes val resId: Int,
    val columns: Int,
    val frameCount: Int
) {
    /** Acierto: el aro revienta hacia fuera y salpica gotas. Verde. */
    BURST(R.drawable.hydro_burst, columns = 5, frameCount = 25),

    /** Fallo: el aro se cierra sobre el sprite y estalla en el centro. Rojo. */
    IMPLODE(R.drawable.hydro_implode, columns = 6, frameCount = 31);

    val durationMs: Long get() = frameCount * 1000L / FPS

    /** Fotograma que toca a [elapsedMs] de vida; puede pasarse de [frameCount]. */
    fun frameAt(elapsedMs: Long): Int = (elapsedMs * FPS / 1000L).toInt()

    companion object {
        const val FPS = 30

        /** Lado del fotograma exportado, en píxeles. */
        const val FRAME_SIZE = 208

        /**
         * Radio, en píxeles del fotograma, al que el arte alinea el aro. El efecto
         * se escala por `radio real / DESIGN_RADIUS`, así el aro del primer
         * fotograma cae justo donde estaba el anillo del minijuego.
         */
        const val DESIGN_RADIUS = 66f

        fun forOutcome(outcome: RingOutcome): WaterRingSheet = when (outcome) {
            RingOutcome.HIT -> BURST
            RingOutcome.MISS -> IMPLODE
        }
    }
}

/**
 * Dibuja el fotograma [frame] de [spec] centrado en [center], con el aro del arte
 * escalado a [radiusPx]. Pixel-art a escala entera: `FilterQuality.None`.
 */
internal fun DrawScope.drawWaterRingFx(
    sheet: ImageBitmap,
    spec: WaterRingSheet,
    frame: Int,
    center: Offset,
    radiusPx: Float
) {
    if (radiusPx <= 0f || frame < 0 || frame >= spec.frameCount) return
    val sidePx = WaterRingSheet.FRAME_SIZE * (radiusPx / WaterRingSheet.DESIGN_RADIUS)
    val column = frame % spec.columns
    val row = frame / spec.columns
    drawImage(
        image = sheet,
        srcOffset = IntOffset(column * WaterRingSheet.FRAME_SIZE, row * WaterRingSheet.FRAME_SIZE),
        srcSize = IntSize(WaterRingSheet.FRAME_SIZE, WaterRingSheet.FRAME_SIZE),
        dstOffset = IntOffset(
            (center.x - sidePx / 2f).roundToInt(),
            (center.y - sidePx / 2f).roundToInt()
        ),
        dstSize = IntSize(sidePx.roundToInt(), sidePx.roundToInt()),
        filterQuality = FilterQuality.None
    )
}
