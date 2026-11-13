/**
 * Catálogo de fondos infinitos por zona.
 *
 * Cada zona puede tener su **arte**, su **eje** y sus **clics**. Todo lo que hace
 * falta para añadir una zona nueva está en [RouteBackgrounds.byZone]:
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
 *   Con el arte a un ancho de pantalla, la fracción de arte donde queda el
 *   entrenador es `0,5 + startOffset`: `-0,5` = borde izquierdo, `0` = centro,
 *   `0,5` = borde derecho.
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
 *    la dirección del sprite sale del tramo, no se configura aparte. Los tramos se
 *    escriben en el marco del eje: con `axis = HORIZONTAL`, `BACK` camina a la
 *    derecha, `LEFT` sube por la pantalla y `RIGHT` baja.
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
 * - el fondo avanza por su eje (si no, nunca se mueve),
 * - hay al menos un tramo recto, y un fondo vertical alterna además laterales (si
 *   no, el entrenador no cambia de orientación),
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
        // Pueblo Paleta: arte y trayectoria propios; los clics y las direcciones
        // se ajustan en esta entrada.
        //
        // Los tramos rectos suman 31 clics y el arte mide 3072 px de alto, así
        // que una vuelta al dibujo pediría 40 clics rectos (3072 × 25 / 1920).
        // Con 31 el ciclo no cierra en el mismo píxel: la vuelta siguiente
        // empieza corrida 0,775 de baldosa. Los laterales sí están balanceados
        // (9 a cada lado), así que el personaje vuelve a la línea central.
        "pueblo_paleta" to ZoneBackground(
            imageRes = R.drawable.background_pallet_town,
            motion = RouteBackgroundMotion(
                legs = listOf(
                    RouteLeg(clicks = 3f),
                    RouteLeg(clicks = 6f, direction = Direction.LEFT),
                    RouteLeg(clicks = 13f),
                    RouteLeg(clicks = 9f, direction = Direction.RIGHT),
                    RouteLeg(clicks = 12f),
                    RouteLeg(clicks = 3f, direction = Direction.LEFT),
                    RouteLeg(clicks = 12f)
                )
            ),
            // El borde inferior del arte cae un poco por debajo del centro de la
            // pantalla, así que se empieza viendo la parte baja de la escena.
            startAnchor = 0.6f
        ),
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
        // Ciudad Verde: arte propio y trayectoria propia, escrita tramo a tramo.
        // Los rectos suman 40 clics (una vuelta exacta al arte de 3072 px) y los
        // laterales están balanceados (7 a cada lado), así que el personaje vuelve
        // a la línea central en cada vuelta.
        //
        // `startAnchor = 0.5f` deja el borde inferior del arte en el centro de la
        // pantalla, que es donde está el entrenador: la zona arranca pisando el
        // tramo bajo de la ciudad y camina hacia arriba por él.
        "ciudad_verde" to ZoneBackground(
            imageRes = R.drawable.background_ciudad_verde,
            motion = RouteBackgroundMotion(
                legs = listOf(
                    RouteLeg(clicks = 4f),
                    RouteLeg(clicks = 1f, direction = Direction.RIGHT),
                    RouteLeg(clicks = 13f),
                    RouteLeg(clicks = 7f, direction = Direction.LEFT),
                    RouteLeg(clicks = 13f),
                    RouteLeg(clicks = 6f, direction = Direction.RIGHT),
                    RouteLeg(clicks = 10f),
                )
            ),
            startAnchor = 0.5f
        ),
        // Ruta 3: la primera zona **horizontal**. El marco está girado 90° a la
        // derecha, así que la trayectoria se escribe igual que en las verticales:
        //   `BACK` (recto) camina a la derecha,
        //   `LEFT` sube por la pantalla,
        //   `RIGHT` baja por la pantalla.
        // El arte se mueve siempre al revés que el entrenador, y cada tramo sigue
        // moviendo un solo eje. Como en las demás zonas, alternar recto con
        // laterales es lo que hace que cambie de orientación.
        //
        // El bucle dura **dos vueltas**: la primera sube (0 → +7 → 0) y la segunda
        // baja (0 → -3 → 0). Lo que cierra es el superciclo completo, con dos sumas:
        //   - los rectos suman 30 clics (dos anchos de arte: 30 × 76,8 = 2304 px),
        //   - los `LEFT` y los `RIGHT` suman lo mismo (10 y 10 aquí), para volver a
        //     la misma altura.
        // Si una de las dos no cuadra, cada ciclo empieza en otro píxel o a otra
        // altura. Lo comprueba `rutaTres_closesItsLoop`.
        //
        // `startOffset = -0,5f` deja al entrenador **pegado al marco izquierdo** del
        // arte (0 % de su ancho): el signo es el de `lateralDistanceAt` (negativo =
        // personaje a la izquierda, imagen corrida a la derecha). Con `-0,25f`
        // arrancaría en el cuarto izquierdo y con `0f` en el centro.
        "ruta3" to ZoneBackground(
            imageRes = R.drawable.background_ruta3,
            motion = RouteBackgroundMotion(
                legs = listOf(
                    RouteLeg(clicks = 2f),
                    RouteLeg(clicks = 3f, direction = Direction.LEFT), // Hacia arriba
                    RouteLeg(clicks = 2f),
                    RouteLeg(clicks = 4f, direction = Direction.LEFT), // Hacia arriba
                    RouteLeg(clicks = 8f),
                    RouteLeg(clicks = 7f, direction = Direction.RIGHT), // Hacia abajo
                    RouteLeg(clicks = 3f),
                    // Segunda vuelta
                    RouteLeg(clicks = 2f),
                    RouteLeg(clicks = 3f, direction = Direction.RIGHT), // Hacia abajo
                    RouteLeg(clicks = 6f),
                    RouteLeg(clicks = 2f, direction = Direction.LEFT), // Hacia arriba
                    RouteLeg(clicks = 5f),
                    RouteLeg(clicks = 1f, direction = Direction.LEFT), // Hacia arriba
                    RouteLeg(clicks = 2f)
                ),
                axis = RouteScrollAxis.HORIZONTAL
            ),
            startOffset = -0.5f,
            // Deja el **eje central horizontal del arte** a la altura del
            // entrenador: el borde inferior del arte cae al 5,7 % de la pantalla,
            // detrás del HUD. Calibrado en 1080×2400; en una pantalla más baja el
            // eje del arte cae por debajo del entrenador.
            startAnchor = 0.057f
        ),
        // Ciudad Plateada: arte propio de 4608×3840, cuatro anchos de cámara por
        // dos altos. Al ser vertical, lo que cierra el bucle es **la altura**:
        //   3840 × CLICKS_PER_TILE / CAMERA_HEIGHT = 3840 × 25 / 1920 = 50 clics
        //   rectos por vuelta al dibujo.
        // Los tramos rectos de esta trayectoria suman 50 (una vuelta exacta al alto
        // del arte) y los laterales están balanceados (40 a cada lado), así que el
        // personaje vuelve a la línea central en cada vuelta. El ciclo completo
        // dura 130 clics. Es un borrador de arranque: mientras los rectos sigan
        // sumando múltiplos de 50 y los dos sentidos laterales sumen lo mismo, se
        // puede reescribir tramo a tramo.
        //
        // El arte es **cuatro veces más ancho** que la cámara, así que la pantalla
        // enseña solo la franja central (un cuarto del dibujo). Lo que se ve de la
        // ciudad lo decide el corrimiento lateral que van dejando los tramos.
        "ciudad_plateada" to ZoneBackground(
            imageRes = R.drawable.background_ciudad_plateada,
            motion = RouteBackgroundMotion(
                legs = listOf(
                    RouteLeg(clicks = 8f),
                    RouteLeg(clicks = 23f, direction = Direction.LEFT),
                    RouteLeg(clicks = 12f),
                    RouteLeg(clicks = 40f, direction = Direction.RIGHT),
                    RouteLeg(clicks = 12f),
                    RouteLeg(clicks = 15f, direction = Direction.LEFT),
                    RouteLeg(clicks = 8f),
                    RouteLeg(clicks = 2f, direction = Direction.LEFT),
                    RouteLeg(clicks = 10f)
                )
            ),
            startAnchor = 0.5f
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
     * `true` si [zoneId] tiene arte propio.
     *
     * Lo usa `AppRoot` para mostrar el fondo animado en zonas sin fauna: una
     * ciudad con arte propio (Pueblo Paleta, Ciudad Verde) también lo enseña,
     * mientras que las que no tienen entrada siguen con el fondo liso de siempre.
     */
    fun hasOwnArtwork(zoneId: String): Boolean = zoneId in byZone

    /**
     * Ids con entrada propia. Lo usa [RouteBackgroundsTest] para detectar un id
     * mal escrito: sin esto, esa zona caería en [DEFAULT] sin avisar.
     */
    internal val configuredZoneIds: Set<String> = byZone.keys
}
