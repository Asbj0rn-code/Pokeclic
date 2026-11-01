/**
 * Catálogo de fondos infinitos por zona.
 *
 * Cada zona puede tener su **arte**, sus **clics** y sus **direcciones**. Todo lo
 * que hace falta para añadir una zona nueva está en [RouteBackgrounds.byZone]:
 * mientras no tenga entrada propia usa [RouteBackgrounds.DEFAULT], así que ninguna
 * zona puede quedarse sin fondo ni romper la animación.
 *
 * El vínculo con la pantalla es `GameUiState.zoneId`; el fondo **y** la dirección
 * en la que camina el entrenador salen de la misma instancia de
 * [ZoneBackground.motion], así que no pueden desincronizarse.
 */
package com.dsrs.pokeclic.ui

import androidx.annotation.DrawableRes
import com.dsrs.pokeclic.R

/**
 * Aspecto y trayectoria del fondo animado de una zona.
 *
 * @param imageRes drawable que se repite en vertical. Debe medir, idealmente, un
 *   ancho de pantalla: el bucle horizontal y el margen lateral se calculan a partir
 *   de su proporción.
 * @param motion clics y direcciones de la zona ([RouteLeg]).
 * @param seamCutRow **0 para el arte normal**: los fondos son patrones continuos,
 *   así que el borde inferior del dibujo coincide con el superior y se repiten sin
 *   costura. Solo el arte antiguo, dibujado como un tramo suelto, necesita aquí la
 *   fila por la que debe cortar el bucle (ver [RouteScrollDefaults.SEAM_CUT_ROW]).
 */
internal data class ZoneBackground(
    @DrawableRes val imageRes: Int,
    val motion: RouteBackgroundMotion,
    val seamCutRow: Int = 0
)

/**
 * Fondos disponibles, indexados por `Zone.id`.
 *
 * Para darle a una zona su propia animación basta con añadir una entrada a
 * [byZone]:
 *
 * ```
 * "ruta2" to ZoneBackground(
 *     imageRes = R.drawable.background_ruta2,
 *     motion = RouteBackgroundMotion(
 *         legs = listOf(
 *             RouteLeg(clicks = 20f, drift = 0f),
 *             RouteLeg(clicks = 2f, drift = 0.10f),
 *             RouteLeg(clicks = 12f, drift = 0f),
 *             RouteLeg(clicks = 2f, drift = -0.10f)
 *         )
 *     )
 * )
 * ```
 *
 * El arte se dibuja como **patrón continuo**: el borde inferior coincide con el
 * superior, así que no hay que indicar ninguna fila de corte. Solo [DEFAULT] lleva
 * `seamCutRow` porque su PNG es el antiguo, dibujado como un tramo suelto.
 * Reglas que [RouteBackgroundsTest] comprueba para **cada** zona del catálogo:
 *
 * - la suma de `drift` de un ciclo es cero (el lateral vuelve al centro),
 * - los clics de los tramos rectos suman un número entero de baldosas, para que
 *   cada vuelta empiece en el mismo píxel del arte,
 * - hay al menos un tramo recto y uno lateral, así que el entrenador cambia de
 *   dirección.
 */
internal object RouteBackgrounds {

    /** Fondo de las zonas que aún no tienen uno propio. */
    val DEFAULT = ZoneBackground(
        imageRes = R.drawable.background_ruta,
        motion = RouteScrollDefaults.MOTION,
        // Única excepción: este PNG es el arte antiguo, no continuo. Todo arte
        // nuevo se omite (patrón continuo, sin fila de corte).
        seamCutRow = RouteScrollDefaults.SEAM_CUT_ROW
    )

    /** Un fondo propio por zona. Las que falten aquí usan [DEFAULT]. */
    private val byZone: Map<String, ZoneBackground> = mapOf(
        // Añadir aquí cada zona que tenga arte o trayectoria propia, por ejemplo:
        // "bosque_verde" to ZoneBackground(
        //     imageRes = R.drawable.background_bosque,
        //     motion = RouteBackgroundMotion(legs = listOf(...)),
        //     seamCutRow = 1269
        // )
    )

    /** Fondo de [zoneId]; nunca es nulo. */
    fun forZone(zoneId: String): ZoneBackground = byZone[zoneId] ?: DEFAULT
}
