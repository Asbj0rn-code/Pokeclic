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
import com.dsrs.pokeclic.model.Direction

/**
 * Aspecto y trayectoria del fondo animado de una zona.
 *
 * Con solo [imageRes] ya funciona: hereda la trayectoria estándar
 * ([RouteScrollDefaults.MOTION]) y después se ajustan los clics.
 *
 * @param imageRes drawable que se repite. Debe medir, idealmente, un ancho de
 *   pantalla: las columnas horizontales se calculan a partir de su ancho.
 * @param motion clics y direcciones de la zona ([RouteLeg]).
 * @param seamCutRow **0 para el arte normal**: los fondos son patrones continuos,
 *   así que el borde inferior del dibujo coincide con el superior y se repiten sin
 *   costura. Solo el arte antiguo, dibujado como un tramo suelto, necesita aquí la
 *   fila por la que debe cortar el bucle (ver [RouteScrollDefaults.SEAM_CUT_ROW]).
 * @param startAnchor fracción de la altura de la pantalla donde cae el **borde
 *   inferior del arte** al entrar en la zona (`0,5` = el centro, donde está el
 *   entrenador). `null` arranca por el principio del arte.
 * @param startOffset posición lateral del personaje al entrar, en anchos de
 *   pantalla y con el mismo signo que [RouteBackgroundMotion.lateralDistanceAt]:
 *   positivo = personaje a la derecha (la imagen se ve corrida a la izquierda).
 */
internal data class ZoneBackground(
    @DrawableRes val imageRes: Int,
    val motion: RouteBackgroundMotion = RouteScrollDefaults.MOTION,
    val seamCutRow: Int = 0,
    val startAnchor: Float? = null,
    val startOffset: Float = 0f
)

/**
 * Fondos disponibles, indexados por `Zone.id`.
 *
 * ## Cómo añadir la animación de una zona
 *
 * 1. **Arte**: un PNG en `res/drawable-nodpi/` (sin reescalado por densidad). Se
 *    dibuja a un ancho de pantalla y se repite en los dos ejes. Para que el bucle
 *    no muestre costura tiene que ser **continuo en los dos ejes**: el borde
 *    inferior coincide con el superior y el derecho con el izquierdo. Si no lo es
 *    se nota una unión (el bosque tiene una suave en horizontal; el arte antiguo
 *    de ruta necesitó `seamCutRow` en vertical).
 * 2. **Trayectoria**: una lista de [RouteLeg] con **clics y dirección**. Alternar
 *    recto con laterales es lo que hace que el entrenador cambie de orientación;
 *    la dirección del sprite sale del tramo, no se configura aparte.
 * 3. **Arranque** (opcional): `startAnchor` para empezar por una parte concreta
 *    del arte y `startOffset` para correr la imagen de lado.
 * 4. Añadir la entrada a [byZone] con el `Zone.id` del catálogo de zonas. Con solo
 *    el arte ya se anima: hereda la trayectoria estándar y después se ajustan los
 *    clics.
 *
 * No hace falta nada más: ni tocar la pantalla, ni el ViewModel, ni el sprite.
 * `AppRoot` lee `RouteBackgrounds.forZone(state.zoneId)` y le pasa el resultado a
 * [RouteScrollingBackground]; una zona sin entrada usa [DEFAULT].
 *
 * Ejemplo mínimo de una zona con su propia trayectoria:
 *
 * ```
 * "ruta2" to ZoneBackground(
 *     imageRes = R.drawable.background_ruta2,
 *     motion = RouteBackgroundMotion(
 *         legs = listOf(
 *             RouteLeg(clicks = 20f),
 *             RouteLeg(clicks = 2f, direction = Direction.RIGHT),
 *             RouteLeg(clicks = 12f),
 *             RouteLeg(clicks = 2f, direction = Direction.LEFT)
 *         )
 *     )
 * )
 * ```
 *
 * ## Lo que [RouteBackgroundsTest] comprueba en **cada** zona
 *
 * - el arte existe y la trayectoria tiene tramos,
 * - el fondo avanza en vertical (si no, nunca se mueve),
 * - hay al menos un tramo recto y uno lateral (si no, el entrenador no cambia de
 *   orientación),
 * - cada tramo recorre exactamente `clics × TILES_PER_CLICK` en su eje.
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
        // Bosque Verde: arte y trayectoria propios. Los clics y las direcciones
        // salen de esta lista, en orden y contados en clics:
        //   2 recto · 3 derecha · 2 recto · 2 derecha · 6 recto ·
        //   6 izquierda · 6 recto · 5 derecha · 5 recto · 4 izquierda · 4 recto
        //
        // No hay magnitudes laterales que ajustar: un clic de lado recorre lo
        // mismo que un clic recto. Como los dos sentidos suman 10 clics, el
        // personaje vuelve solo a la línea central en cada vuelta; el zigzag se
        // aleja hasta 0,20 alturas de baldosa del centro.
        "bosque_verde" to ZoneBackground(
            imageRes = R.drawable.background_bosque_verde,
            motion = RouteBackgroundMotion(
                legs = listOf(
                    RouteLeg(clicks = 2f),
                    RouteLeg(clicks = 3f, direction = Direction.RIGHT),
                    RouteLeg(clicks = 2f),
                    RouteLeg(clicks = 2f, direction = Direction.RIGHT),
                    RouteLeg(clicks = 6f),
                    RouteLeg(clicks = 6f, direction = Direction.LEFT),
                    RouteLeg(clicks = 6f),
                    RouteLeg(clicks = 5f, direction = Direction.RIGHT),
                    RouteLeg(clicks = 4f),
                    RouteLeg(clicks = 4f, direction = Direction.LEFT),
                    RouteLeg(clicks = 5f)
                )
            ),
            startAnchor = 0.5f,
            startOffset = -0.08f
        ),
        // Añadir aquí cada zona que tenga arte o trayectoria propia, por ejemplo:
        // "ruta2" to ZoneBackground(
        //     imageRes = R.drawable.background_ruta2,
        //     motion = RouteBackgroundMotion(legs = listOf(...))
        // )
    )

    /** Fondo de [zoneId]; nunca es nulo. */
    fun forZone(zoneId: String): ZoneBackground = byZone[zoneId] ?: DEFAULT

    /**
     * Ids con entrada propia. Lo usa [RouteBackgroundsTest] para detectar un id
     * mal escrito: sin esto, esa zona caería en [DEFAULT] sin avisar.
     */
    internal val configuredZoneIds: Set<String> = byZone.keys
}
