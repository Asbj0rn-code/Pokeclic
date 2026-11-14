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
        enemyWidth = 164.dp,
        enemyHeight = 136.dp,
        enemyTop = 152.dp,
        playerWidth = 82.dp,
        playerHeight = 93.dp,
        playerBottom = 140.dp
    )

    /** Misty: conserva la composición del arte de piscina. */
    private val MISTY = GymSceneLayout(
        enemyWidth = 180.dp,
        enemyHeight = 150.dp,
        enemyTop = 178.dp,
        playerWidth = 90.dp,
        playerHeight = 102.dp,
        playerBottom = 160.dp
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
