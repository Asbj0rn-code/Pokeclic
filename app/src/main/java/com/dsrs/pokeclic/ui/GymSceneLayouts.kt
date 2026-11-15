/**
 * Medidas de la escena de combate de un gimnasio: tamaño y ancla de los sprites
 * del líder y del Pokémon del jugador.
 *
 * Se separó de las constantes de `GymOverlay` porque cada fondo tiene su propia
 * composición: la plataforma del líder y el suelo del jugador caen a distinta
 * altura en cada arte, y un ajuste pensado para uno desalineaba los otros.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * @param enemyWidth ancho del recuadro del líder.
 * @param enemyHeight alto del recuadro del líder. También es el alto que reserva
 *   la cuadrícula de Voltorb, así que en ese gimnasio es más bajo.
 * @param enemyTop distancia desde el borde superior hasta el recuadro del líder.
 * @param playerWidth ancho del recuadro del Pokémon del jugador.
 * @param playerHeight alto del recuadro del Pokémon del jugador.
 * @param playerBottom distancia desde el borde inferior hasta el recuadro del
 *   jugador: cuanto menor, más abajo queda el sprite.
 */
internal data class GymSceneLayout(
    val enemyWidth: Dp,
    val enemyHeight: Dp,
    val enemyTop: Dp,
    val playerWidth: Dp,
    val playerHeight: Dp,
    val playerBottom: Dp
)

/**
 * Una composición por gimnasio, indexada por `Gym.id`.
 *
 * Las hitboxes de las tres mecánicas se derivan de estas mismas medidas, así que
 * sprite y zona de toque nunca se separan.
 */
internal object GymSceneLayouts {

    /**
     * Escala de sprite de los fondos **pixel-art 2:3** (Brock y Misty): el dibujo
     * es el mismo tamaño en píxeles, así que el sprite tiene que medir lo mismo en
     * los dos o uno de los dos se ve fuera de escala. Un cambio aquí mueve ambos
     * gimnasios a la vez, a propósito.
     */
    private object PixelArtSprites {
        val ENEMY_WIDTH = 164.dp
        val ENEMY_HEIGHT = 136.dp
        val PLAYER_WIDTH = 82.dp
        val PLAYER_HEIGHT = 93.dp
    }

    /** Composición de los gimnasios que aún no tienen una propia. */
    val DEFAULT = GymSceneLayout(
        enemyWidth = 180.dp,
        enemyHeight = 150.dp,
        enemyTop = 178.dp,
        playerWidth = 90.dp,
        playerHeight = 102.dp,
        playerBottom = 160.dp
    )

    /**
     * Brock: el arte nuevo es 2:3 y su plataforma de roca queda más arriba que la
     * del resto, así que el líder sube y ambos sprites se achican un ~9 % para
     * dejar aire sobre la tarima.
     */
    private val BROCK = GymSceneLayout(
        enemyWidth = PixelArtSprites.ENEMY_WIDTH,
        enemyHeight = PixelArtSprites.ENEMY_HEIGHT,
        enemyTop = 152.dp,
        playerWidth = PixelArtSprites.PLAYER_WIDTH,
        playerHeight = PixelArtSprites.PLAYER_HEIGHT,
        playerBottom = 140.dp
    )

    /**
     * Misty: mismo arte pixel-art 2:3 que Brock, así que comparte su escala de
     * sprite. El anclaje sí es propio: el líder conserva su altura y el Pokémon
     * del jugador sube sobre la tarima de madera del dibujo.
     */
    private val MISTY = GymSceneLayout(
        enemyWidth = PixelArtSprites.ENEMY_WIDTH,
        enemyHeight = PixelArtSprites.ENEMY_HEIGHT,
        enemyTop = 178.dp,
        playerWidth = PixelArtSprites.PLAYER_WIDTH,
        playerHeight = PixelArtSprites.PLAYER_HEIGHT,
        playerBottom = 180.dp
    )

    /**
     * Lt. Surge: mismo encuadre que Misty, pero el líder se dibuja más bajo para
     * dejarle la altura a la cuadrícula de Voltorb.
     */
    private val SURGE = GymSceneLayout(
        enemyWidth = 180.dp,
        enemyHeight = 116.dp,
        enemyTop = 178.dp,
        playerWidth = 90.dp,
        playerHeight = 102.dp,
        playerBottom = 160.dp
    )

    private val byGym: Map<String, GymSceneLayout> = mapOf(
        "brock" to BROCK,
        "misty" to MISTY,
        "surge" to SURGE
    )

    /** Gimnasios con composición propia; los demás usan [DEFAULT]. */
    val configuredGymIds: Set<String> = byGym.keys

    /** Composición de [gymId]; nunca es nula. */
    fun forGym(gymId: String): GymSceneLayout = byGym[gymId] ?: DEFAULT
}
