/**
 * Capa del **mundo**: el entrenador.
 *
 * Vive en el mismo `Box` que el fondo de ruta y usa la misma proyección
 * ([WorldFrame]), así que no hay dos sistemas de coordenadas que puedan
 * desincronizarse: lo que se coloca aquí cae siempre en el mismo punto del arte,
 * mida lo que mida el móvil. Nada de esto es cromo: ni `dp` ni porcentajes de
 * pantalla.
 *
 * No consume toques.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.dsrs.pokeclic.R
import kotlin.math.roundToInt

/**
 * Entrenador colocado en coordenadas de arte.
 *
 * Se apila en un [Box] alineado arriba y centrado, y se desplaza hasta la línea de
 * [WorldDefaults.TRAINER_CENTER_ART_Y] con el marco del mundo. El desplazamiento
 * se calcula en píxeles y se aplica en el `offset` de layout (no `graphicsLayer`),
 * para que el redondeo sea el mismo que el del fondo.
 *
 * El tamaño llega ya convertido desde [WorldFrame] (px de arte escalados), así que
 * el sprite mide siempre la misma fracción del ancho de pantalla, sea cual sea la
 * densidad del móvil.
 */
@Composable
internal fun BoxScope.WorldTrainer(
    trainerFrameRes: Int,
    world: WorldFrame
) {
    val density = LocalDensity.current
    val boxSize: Dp = with(density) { world.screenPx(WorldDefaults.TRAINER_BOX_ART_SIZE).toDp() }
    val topPx = world.screenY(WorldDefaults.TRAINER_CENTER_ART_Y) -
        world.screenPx(WorldDefaults.TRAINER_BOX_ART_SIZE) / 2f

    PixelImage(
        res = trainerFrameRes,
        contentDescription = stringResource(R.string.trainer_desc),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset { IntOffset(0, topPx.roundToInt()) }
            .size(boxSize)
    )
}
