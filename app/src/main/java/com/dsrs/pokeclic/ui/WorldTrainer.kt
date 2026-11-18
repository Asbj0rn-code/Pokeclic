/**
 * Capa del **mundo**: el entrenador y las capas de hierba alta.
 *
 * Vive en el mismo `Box` que el fondo de ruta y usa la misma proyección
 * ([WorldFrame]), así que no hay dos sistemas de coordenadas que puedan
 * desincronizarse: lo que se coloca aquí cae siempre en el mismo punto del arte,
 * mida lo que mida el móvil. Nada de esto es cromo: ni `dp` ni porcentajes de
 * pantalla.
 *
 * No consume toques: el botón de hierba alta sigue en `MainScreen`, que es donde
 * vive la interacción.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import com.dsrs.pokeclic.R
import kotlin.math.roundToInt

/**
 * Entrenador (y hierba, si toca) colocado en coordenadas de arte.
 *
 * Se apila en un [Box] alineado arriba y centrado, y se desplaza hasta la línea de
 * [WorldDefaults.TRAINER_CENTER_ART_Y] con el marco del mundo. El desplazamiento
 * se calcula en píxeles y se aplica en el `offset` de layout (no `graphicsLayer`),
 * para que el redondeo sea el mismo que el del fondo.
 */
@Composable
internal fun BoxScope.WorldTrainer(
    trainerFrameRes: Int,
    showTallGrass: Boolean,
    world: WorldFrame
) {
    val density = LocalDensity.current
    val boxSize: Dp = with(density) { world.screenPx(WorldDefaults.TRAINER_BOX_ART_SIZE).toDp() }
    val grassOffsetX: Dp = with(density) { world.screenPx(WorldDefaults.GRASS_OFFSET_X_ART).toDp() }
    val grassOffsetY: Dp = with(density) { world.screenPx(WorldDefaults.GRASS_OFFSET_Y_ART).toDp() }
    val topPx = world.screenY(WorldDefaults.TRAINER_CENTER_ART_Y) -
        world.screenPx(WorldDefaults.TRAINER_BOX_ART_SIZE) / 2f

    TrainerGrassStack(
        trainerFrameRes = trainerFrameRes,
        showTallGrass = showTallGrass,
        boxSize = boxSize,
        offsetX = grassOffsetX,
        offsetY = grassOffsetY,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset { IntOffset(0, topPx.roundToInt()) }
    )
}

/**
 * Grupo trainer + hierba alta.
 *
 * Las tres imágenes se apilan en un mismo [Box], así que comparten **exactamente**
 * la misma posición: basta con colocar el grupo para moverlas todas. El orden de
 * dibujo en un `Box` es de fondo a frente. Todos los pasajes pasan por `PixelImage`
 * (`FilterQuality.None`) para preservar el pixel art.
 *
 * El tamaño llega ya convertido desde [WorldFrame] (px de arte escalados), así que
 * el sprite mide siempre la misma fracción del ancho de pantalla, sea cual sea la
 * densidad del móvil.
 */
@Composable
private fun TrainerGrassStack(
    trainerFrameRes: Int,
    showTallGrass: Boolean,
    boxSize: Dp,
    offsetX: Dp,
    offsetY: Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(boxSize), contentAlignment = Alignment.Center) {
        // Capa 1 · fondo: hierba alta completa
        if (showTallGrass) {
            TallGrassLayer(
                drawableRes = R.drawable.tall_grass_full,
                size = boxSize,
                offsetX = offsetX,
                offsetY = offsetY
            )
        }

        // Capa 2 · medio: trainer
        PixelImage(
            res = trainerFrameRes,
            contentDescription = stringResource(R.string.trainer_desc),
            modifier = Modifier.size(boxSize)
        )

        // Capa 3 · frente: hierba alta parcial
        if (showTallGrass) {
            TallGrassLayer(
                drawableRes = R.drawable.tall_grass_mid,
                size = boxSize,
                offsetX = offsetX,
                offsetY = offsetY
            )
        }
    }
}

@Composable
private fun TallGrassLayer(drawableRes: Int, size: Dp, offsetX: Dp = 0.dp, offsetY: Dp = 0.dp) {
    PixelImage(
        res = drawableRes,
        contentDescription = stringResource(R.string.tall_grass_desc),
        modifier = Modifier
            .size(size)
            .offset(x = offsetX, y = offsetY)
    )
}
