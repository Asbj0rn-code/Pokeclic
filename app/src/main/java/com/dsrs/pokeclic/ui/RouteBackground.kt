/**
 * Fondo de ruta animado: repite [R.drawable.background_ruta] en bucle y lo
 * desplaza un paso seco por clic. No hay inercia: al dejar de pulsar la Pokéball
 * el fondo se detiene en el acto, sin deslizamiento residual.
 *
 * El eje de avance depende de la trayectoria ([RouteScrollAxis]): en vertical el
 * fondo baja por la pantalla, como en las rutas de siempre; en horizontal se corre
 * de lado y el entrenador camina de izquierda a derecha.
 *
 * Encima de ese avance, el fondo sigue una **trayectoria** de tramos
 * ([RouteLeg]): unos avanzan recto y otros se corren a un lado, de modo que el
 * entrenador del centro parece recorrer un camino que no es una línea recta. La
 * trayectoria se puede cambiar por localización pasando otra
 * [RouteBackgroundMotion].
 *
 * Cada tramo mueve **un solo eje**: mientras se corre de lado el fondo no baja,
 * y mientras baja no se corre. Nunca hay movimiento diagonal.
 *
 * Los dos ejes son **infinitos**. El vertical sube sin tope porque el arte se
 * repite en bucle; el lateral se corre sin tope porque las columnas se dibujan
 * contiguas **como losetas**: al borde derecho de una le sigue el borde izquierdo
 * del arte otra vez, sin reflejarlo. Un clic recorre lo mismo en cualquier
 * dirección, y ni la distancia ni el sentido tienen que compensarse al cerrar el
 * ciclo.
 *
 * Es una capa pura de UI: no toca el ViewModel ni el estado de juego. Lee el
 * recuento de pulsaciones que le pasa el caller y lo traduce a movimiento.
 */
package com.dsrs.pokeclic.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.annotation.DrawableRes
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
import com.dsrs.pokeclic.model.Direction
import kotlinx.coroutines.isActive
import kotlin.math.floor

/**
 * Un tramo de la trayectoria del fondo.
 *
 * @param clicks cuántos clics dura el tramo. Es la unidad en la que se percibe el
 *   recorrido al jugar, y la única que hay que tocar al ajustar la animación.
 * @param direction eje y sentido del tramo: [Direction.BACK] avanza recto (en un
 *   fondo vertical el fondo sube y al entrenador se le ve de espaldas); en un
 *   fondo horizontal es caminar a la derecha. [Direction.RIGHT] y [Direction.LEFT]
 *   son los laterales, siempre en el marco del eje.
 *
 * Cada tramo mueve **un solo eje** y avanza **lo mismo por clic** en cualquiera
 * de los dos ([RouteScrollDefaults.TILES_PER_CLICK] baldosas), así que cuatro
 * clics de lado recorren exactamente la misma distancia que cuatro clics rectos.
 * La distancia lateral se mide en **alturas de baldosa** y el renderer la
 * multiplica por el alto del arte: el ritmo en píxeles es idéntico en los dos
 * ejes aunque el dibujo no sea cuadrado.
 */
internal data class RouteLeg(
    val clicks: Float,
    val direction: Direction = Direction.BACK
) {
    /** Recorrido del tramo, en alturas de baldosa. */
    val distance: Float
        get() = clicks * RouteScrollDefaults.TILES_PER_CLICK

    /** `true` si el tramo se corre de lado (y por tanto no avanza recto). */
    val isLateral: Boolean
        get() = direction != Direction.BACK

    /** +1 RIGHT, −1 LEFT, 0 recto. */
    val sideSign: Float
        get() = when (direction) {
            Direction.RIGHT -> 1f
            Direction.LEFT -> -1f
            else -> 0f
        }
}

/**
 * Eje por el que avanza el fondo.
 *
 * [VERTICAL] es el de las rutas de siempre: los tramos rectos suben al entrenador
 * por la pantalla y los laterales lo corren a izquierda o derecha.
 *
 * [HORIZONTAL] es el mismo marco girado 90° a la derecha: el tramo recto camina
 * de izquierda a derecha, el lateral [Direction.LEFT] sube por la pantalla y el
 * [Direction.RIGHT] baja. El arte se mueve siempre al revés que el entrenador.
 */
internal enum class RouteScrollAxis {
    VERTICAL,
    HORIZONTAL
}

/**
 * Trayectoria completa del fondo, formada por tramos que se repiten en bucle.
 *
 * Todo se calcula como **posición en función de lo recorrido**, no como
 * velocidad acumulada: así no puede derivar nunca.
 *
 * Los dos ejes son **scrolls infinitos**: el vertical sube sin tope y el lateral
 * se corre sin tope, porque el arte se repite en vertical (en bucle) y en
 * horizontal (columnas contiguas, como losetas). Ninguno tiene que volver a su
 * origen al cerrar el ciclo: uno con más clics a la derecha que a la izquierda
 * deja al personaje más a la derecha en cada vuelta, igual que uno recto lo deja
 * más arriba.
 *
 * Los tramos son de un eje: [verticalDistanceAt] avanza solo en los rectos y
 * [lateralDistanceAt] solo en los laterales. Un clic recorre
 * [RouteScrollDefaults.TILES_PER_CLICK] baldosas en cualquiera de los dos, así que
 * no hay magnitudes que ajustar por tramo.
 *
 * @param legs tramos en orden; se repiten indefinidamente. Se escriben siempre en
 *   el marco del eje: `BACK` es avanzar y `LEFT`/`RIGHT` son los laterales. En un
 *   fondo [RouteScrollAxis.HORIZONTAL] ese marco está girado 90°, así que `BACK`
 *   camina a la derecha y los laterales suben o bajan.
 * @param axis eje por el que avanza el fondo.
 */
internal class RouteBackgroundMotion(
    val legs: List<RouteLeg>,
    val axis: RouteScrollAxis = RouteScrollAxis.VERTICAL
) {
    init {
        require(legs.isNotEmpty()) { "La trayectoria necesita al menos un tramo" }
        require(legs.all { it.distance > 0f }) { "Cada tramo debe avanzar algo" }
        require(legs.none { it.direction == Direction.FRONT }) {
            "Un tramo avanza recto (BACK) o de lado (LEFT/RIGHT)"
        }
    }

    /** Largo de un ciclo completo, en baldosas. */
    val cycleDistance: Float = legs.sumOf { it.distance.toDouble() }.toFloat()

    /** Baldosas que baja el fondo en un ciclo: solo las de los tramos rectos. */
    private val verticalPerCycle: Double =
        legs.filterNot { it.isLateral }.sumOf { it.distance.toDouble() }

    /** Alturas de baldosa que se corre el fondo en un ciclo, con signo. */
    private val lateralPerCycle: Double =
        legs.sumOf { (it.sideSign * it.distance).toDouble() }

    /**
     * Corrimiento lateral tras [travelled] baldosas de recorrido, en **alturas de
     * baldosa**: positivo = el personaje se ha corrido hacia su `RIGHT`. **Sin
     * tope**: si un ciclo deja más clics a un lado que al otro, el valor crece
     * vuelta tras vuelta.
     *
     * En un fondo vertical es un corrimiento a derecha o izquierda por la
     * pantalla; en uno horizontal, subir (`LEFT`) o bajar (`RIGHT`).
     *
     * Permanece **constante** durante los tramos rectos (no hay movimiento
     * diagonal) y dentro de los laterales avanza **lineal**, a la misma velocidad
     * que el recto: un clic de lado recorre lo mismo que un clic recto.
     */
    fun lateralDistanceAt(travelled: Double): Float {
        if (travelled <= 0.0) return 0f
        val cycle = cycleDistance.toDouble()
        val cycles = floor(travelled / cycle)
        var cursor = travelled - cycles * cycle
        var lateral = cycles * lateralPerCycle

        for (leg in legs) {
            if (cursor <= 0.0) break
            val take = minOf(cursor, leg.distance.toDouble())
            if (leg.isLateral) lateral += take * leg.sideSign
            cursor -= take
        }
        return lateral.toFloat()
    }

    /**
     * Dirección en la que debe animarse el entrenador en el clic [step] (1 = el
     * primero). Es la del tramo por el que pasa ese paso, leída en el marco del
     * eje: en vertical el tramo ya es la dirección del sprite; en horizontal el
     * marco está girado 90° (ver [RouteScrollAxis]).
     *
     * Se muestrea el **centro** del paso para que un clic que arranca justo en el
     * borde entre dos tramos no herede la dirección del anterior.
     */
    fun directionForStep(step: Long): Direction {
        if (step <= 0L) return Direction.FRONT
        val leg = legForStep(step)
        return when (axis) {
            RouteScrollAxis.VERTICAL -> leg.direction
            // El marco horizontal es el vertical girado 90° a la derecha: el
            // avance recto se ve como caminar a la derecha, el lateral LEFT como
            // subir y el RIGHT como bajar.
            RouteScrollAxis.HORIZONTAL -> when (leg.direction) {
                Direction.BACK -> Direction.RIGHT
                Direction.RIGHT -> Direction.FRONT
                Direction.LEFT -> Direction.BACK
                Direction.FRONT -> Direction.FRONT
            }
        }
    }

    /** Tramo por el que pasa el clic [step] (1 = el primero). */
    private fun legForStep(step: Long): RouteLeg {
        val cycle = cycleDistance.toDouble()
        var cursor = wrapped(
            (step - 0.5) * RouteScrollDefaults.TILES_PER_CLICK.toDouble(),
            cycle
        )
        for (leg in legs) {
            if (cursor < leg.distance) return leg
            cursor -= leg.distance
        }
        return legs.last()
    }

    /**
     * Baldosas de **avance recto** tras [travelled] baldosas de recorrido: lo que
     * el fondo baja en vertical o se corre de lado en horizontal.
     *
     * Solo avanza en los tramos rectos: mientras dura un tramo lateral el valor se
     * queda clavado y la imagen se mueve solo en el otro eje.
     */
    fun verticalDistanceAt(travelled: Double): Double {
        if (travelled <= 0.0) return 0.0
        val cycle = cycleDistance.toDouble()
        val cycles = floor(travelled / cycle)
        var cursor = travelled - cycles * cycle
        var vertical = cycles * verticalPerCycle

        for (leg in legs) {
            if (cursor <= 0.0) break
            val take = minOf(cursor, leg.distance.toDouble())
            if (!leg.isLateral) vertical += take
            cursor -= take
        }
        return vertical
    }

    /** Resto positivo de [value] sobre [cycle]. */
    private fun wrapped(value: Double, cycle: Double): Double {
        var remainder = value % cycle
        if (remainder < 0.0) remainder += cycle
        return remainder
    }
}

/**
 * Desplazamiento que el recorrido produce en el dibujo, en las unidades que usa
 * el renderer.
 *
 * @property verticalPhase fase vertical normalizada en `[0f, 1f)`: cuánto ha
 *   bajado el arte, en alturas de baldosa. En un fondo horizontal la mueven los
 *   tramos laterales (subir o bajar por la pantalla).
 * @property lateral corrimiento horizontal en alturas de baldosa, con el signo de
 *   [RouteBackgroundMotion.lateralDistanceAt]: positivo = el personaje avanzó a
 *   la derecha, así que la imagen se ve corrida a la izquierda.
 */
internal data class RouteScrollShift(
    val verticalPhase: Float,
    val lateral: Float
)

/**
 * Traduce el recorrido [position] (en baldosas) al desplazamiento del dibujo.
 *
 * En un fondo **vertical** el avance de los tramos rectos baja la imagen y los
 * laterales la corren de lado. En uno **horizontal** el marco está girado 90°: el
 * avance recto corre la imagen de lado (el entrenador camina a la derecha) y los
 * laterales la suben o la bajan, a la misma velocidad de siempre.
 *
 * @param position recorrido acumulado, en baldosas (alturas de cámara).
 * @param artHeight alto del drawable en px. La cámara se mide en alturas de arte,
 *   no en alturas del PNG, así que la escala depende de este dato.
 */
internal fun routeScrollShiftAt(
    motion: RouteBackgroundMotion,
    position: Double,
    artHeight: Int
): RouteScrollShift {
    val cameraPerArt = RouteScrollDefaults.CAMERA_HEIGHT / artHeight
    return when (motion.axis) {
        RouteScrollAxis.VERTICAL -> {
            val vertical = motion.verticalDistanceAt(position) * cameraPerArt
            var phase = RouteScrollDefaults.DIRECTION * vertical
            phase -= floor(phase)
            RouteScrollShift(
                verticalPhase = phase.toFloat(),
                lateral = motion.lateralDistanceAt(position)
            )
        }
        // Marco girado 90°: el recto corre la imagen de lado y los laterales la
        // suben o la bajan. Un tramo RIGHT baja al entrenador, así que el arte
        // sube (fase positiva); uno LEFT lo sube y el arte baja.
        RouteScrollAxis.HORIZONTAL -> {
            val vertical = motion.lateralDistanceAt(position) * cameraPerArt
            var phase = vertical
            phase -= floor(phase)
            RouteScrollShift(
                verticalPhase = phase.toFloat(),
                lateral = motion.verticalDistanceAt(position).toFloat()
            )
        }
    }
}

/**
 * Columna (copia horizontal) que cubre el borde izquierdo de la pantalla.
 *
 * Las columnas se dibujan contiguas, `tileW` de ancho cada una, cada una con la
 * misma copia del arte: al borde derecho de una le sigue el borde izquierdo de la
 * siguiente. Devolver la primera que toca la pantalla es lo que evita que el
 * corrimiento lateral deje una franja vacía en un borde.
 */
internal fun routeFirstColumn(left0: Float, shiftH: Float, tileW: Float): Int =
    floor((shiftH - left0) / tileW).toInt()

/** Borde izquierdo de la columna [column], en px de pantalla. */
internal fun routeColumnStart(column: Int, left0: Float, shiftH: Float, tileW: Float): Float =
    left0 - shiftH + column * tileW

/**
 * Desplazamiento vertical inicial, en px de baldosa, que deja el **borde
 * inferior del arte** a la altura [anchor] de la pantalla cuando el recorrido
 * está a cero.
 *
 * Sirve para que una zona arranque por el final de su arte en vez de por el
 * principio: con `anchor = 0.5f` el final del dibujo cae en el centro de la
 * pantalla, que es donde está el entrenador, así que empieza pisando el borde
 * inferior del escenario y camina hacia arriba por él.
 *
 * @param tileH alto de una repetición del arte, ya escalado a la pantalla.
 * @param screenH alto de la pantalla.
 * @param anchor fracción de la altura de la pantalla (0 = arriba, 0,5 = centro,
 *   1 = abajo); `null` deja el arte empezando por arriba, como siempre.
 */
internal fun routeStartShift(tileH: Float, screenH: Float, anchor: Float?): Float {
    if (anchor == null || tileH <= 0f) return 0f
    val raw = tileH - anchor * screenH
    val wrapped = raw % tileH
    return if (wrapped < 0f) wrapped + tileH else wrapped
}

/**
 * Escala que hace que la **cámara** ([RouteScrollDefaults.CAMERA_WIDTH] ×
 * [RouteScrollDefaults.CAMERA_HEIGHT] píxeles de arte) entre entera en la
 * pantalla sin deformarse.
 *
 * Es `min` y no `max` a propósito: con `max` la cámara se recortaría y el ancho
 * visible dependería de la pantalla. Con `min` el ancho cámara (1152 px de arte)
 * se ve siempre completo, y en pantallas más altas que 1152:1920 se ve arte de
 * más por arriba y por abajo, que el bucle rellena.
 *
 * No recibe el tamaño del drawable **a propósito**: la escala de la cámara no
 * depende del arte. Un PNG de 1152 px y otro de 2176 se dibujan a la misma
 * escala, así que el segundo se ve más grande y más recortado.
 */
internal fun routeCameraScale(screenWidth: Float, screenHeight: Float): Float {
    if (screenWidth <= 0f || screenHeight <= 0f) return 0f
    return minOf(
        screenWidth / RouteScrollDefaults.CAMERA_WIDTH,
        screenHeight / RouteScrollDefaults.CAMERA_HEIGHT
    )
}

/**
 * Convierte clics en recorrido **sin inercia**: un clic pide un paso y el paso se
 * consume en un plazo corto; cuando termina, la posición queda clavada.
 *
 * Antes cada clic sumaba velocidad y una fricción la apagaba durante medio
 * segundo: ese era el deslizamiento que se notaba después del clic. Aquí no hay
 * velocidad: hay un destino (los clics dados) y un plazo para alcanzarlo.
 *
 * El plazo de cada paso se recorta a lo que tardaron en llegar los clics, así que
 * un machaque encadena pasos al ritmo de los clics en vez de acumular trabajo
 * pendiente, y al soltar, el último paso termina como mucho en
 * [RouteScrollDefaults.MAX_STEP_NANOS] y se para en seco.
 *
 * Es puro Kotlin para poder testearlo sin emulador.
 */
internal class RouteClickWalker(
    private val stepTiles: Double = RouteScrollDefaults.TILES_PER_CLICK.toDouble(),
    private val maxStepNanos: Long = RouteScrollDefaults.MAX_STEP_NANOS,
    private val minStepNanos: Long = RouteScrollDefaults.MIN_STEP_NANOS,
    initialSteps: Long = 0L
) {
    private var lastPulseNanos = 0L
    private var stepFrom = initialSteps * stepTiles
    private var stepTarget = stepFrom
    private var stepStartNanos = 0L
    private var stepNanos = maxStepNanos

    /** Recorrido acumulado en baldosas, ya consumido hasta el último frame. */
    var travelled: Double = stepFrom
        private set

    /**
     * Aplica las pulsaciones acumuladas [totalPulses] y devuelve el recorrido que
     * corresponde en [nowNanos].
     *
     * El destino se calcula desde el **total** de pulsaciones, no sumando deltas:
     * así un fondo que se vuelve a componer (cambio de pestaña, de zona) arranca
     * exactamente donde estaba en vez de repetir clics.
     */
    fun positionAt(totalPulses: Long, nowNanos: Long): Double {
        val target = totalPulses * stepTiles
        when {
            target > stepTarget + POSITION_EPSILON -> {
                val gap = if (lastPulseNanos == 0L) {
                    Long.MAX_VALUE
                } else {
                    nowNanos - lastPulseNanos
                }
                lastPulseNanos = nowNanos
                stepNanos = gap.coerceIn(minStepNanos, maxStepNanos)
                stepFrom = travelled
                stepTarget = target
                stepStartNanos = nowNanos
            }

            target < stepTarget - POSITION_EPSILON -> {
                // El recorrido se reinició (zona nueva): se salta al origen sin
                // animar, para no "rebobinar" el fondo.
                travelled = target
                stepFrom = target
                stepTarget = target
            }
        }

        if (travelled < stepTarget) {
            val duration = stepNanos.coerceAtLeast(1L)
            val progress = ((nowNanos - stepStartNanos).toDouble() / duration.toDouble())
                .coerceIn(0.0, 1.0)
            travelled = if (progress >= 1.0) {
                stepTarget
            } else {
                stepFrom + (stepTarget - stepFrom) * progress
            }
        }
        return travelled
    }

    private companion object {
        const val POSITION_EPSILON = 1e-9
    }
}

/**
 * Constantes del desplazamiento.
 *
 * El fondo no tiene inercia: **un clic es un paso**. Cada pulsación pide un
 * avance de [TILES_PER_CLICK] y el paso se consume en un plazo corto
 * ([MAX_STEP_NANOS]) y se detiene en seco; al dejar de pulsar no queda ningún
 * deslizamiento residual. Al medir en alturas de baldosa y no en px ni dp, el
 * recorrido se ve al mismo ritmo en pantallas de cualquier ancho y densidad.
 */
internal object RouteScrollDefaults {

    /**
     * Clics rectos que hacen falta para avanzar **una altura de cámara**
     * ([CAMERA_HEIGHT] = 1920 px de arte), no una altura de drawable.
     *
     * Un clic recto avanza 1920/25 = 76,8 px de arte, mida lo que mida el PNG.
     * Por eso el número de clics para dar **una vuelta exacta al arte** ya no es
     * una constante: es
     *
     * ```
     * clicsRectosPorVuelta = altoDelArte * CLICKS_PER_TILE / CAMERA_HEIGHT
     * ```
     *
     * con arte de 1920 px son 25; con arte de 3072 px, 40. Si el ciclo de la
     * trayectoria no suma un múltiplo entero de eso, cada vuelta empieza en otro
     * píxel del dibujo (el modelo lo permite, pero el patrón deja de cerrar).
     *
     * Si se cambia este número hay que reajustar los tramos de [MOTION] en la
     * misma proporción (lo comprueba
     * `RouteBackgroundMotionTest.defaultMotion_returnsToTheSameArtworkEveryCycle`).
     */
    const val CLICKS_PER_TILE = 25f

    /** Avance de un clic, en alturas de cámara. */
    const val TILES_PER_CLICK = 1f / CLICKS_PER_TILE

    /**
     * Tamaño de la **cámara**, en píxeles de arte: lo que abarca la pantalla en
     * cada momento.
     *
     * No depende del drawable: un fondo de 2176×3072 y otro de 1152×1920 se
     * dibujan a la misma escala, así que la cámara siempre enseña 1152 px de
     * ancho del arte y, como mínimo, 1920 de alto. Si la pantalla es más alta que
     * 1152:1920 se ve arte de más por arriba y por abajo; nunca se deforma y
     * nunca quedan franjas sin dibujar.
     *
     * Con [background_ruta] y [background_bosque_verde] el resultado coincide con
     * el de antes (Bosque Verde mide exactamente 1152×1920).
     */
    const val CAMERA_WIDTH = 1152f
    const val CAMERA_HEIGHT = 1920f

    /**
     * Dirección del desplazamiento vertical: `-1f` = el fondo baja por la
     * pantalla (el personaje sube), `1f` = al revés. Solo afecta a los fondos
     * verticales: en los horizontales el signo sale de
     * [RouteBackgroundMotion.lateralDistanceAt] (positivo = el personaje avanza a
     * la derecha, así que la imagen se corre a la izquierda).
     */
    const val DIRECTION = -1f

    /**
     * Plazo máximo de un paso: el avance de un clic se dibuja como mucho durante
     * este tiempo y después el fondo queda **quieto**. Es lo que quita la
     * sensación de deslizamiento tras el clic.
     */
    const val MAX_STEP_NANOS = 100_000_000L

    /**
     * Plazo mínimo de un paso. Si se machaca más rápido que esto, los pasos se
     * encadenan a la velocidad de los clics en vez de encogerse sin límite.
     */
    const val MIN_STEP_NANOS = 30_000_000L

    /**
     * Fila de corte del **arte antiguo** de ruta: excepción, no la norma.
     *
     * Los fondos son patrones continuos (el borde inferior coincide con el
     * superior) y se repiten sin tocar nada: su `seamCutRow` es `0`. Este PNG, en
     * cambio, es un tramo suelto: su fila 1919 (borde inferior) no coincide con la
     * 0 (borde superior), con una diferencia media de 14,58 por canal. Al cargarlo
     * se enrolla cíclicamente para que la única unión caiga en la pareja
     * 1268-1269 (pasto plano, diferencia 0): el tile muestra F[1269] arriba y
     * F[1268] abajo, que en el original eran consecutivas.
     *
     * Cuando ese arte se reemplace por uno continuo, este valor desaparece.
     */
    const val SEAM_CUT_ROW = 1269

    /** Tope de repeticiones dibujadas por frame, por si la proporción es absurda. */
    const val MAX_REPEATS = 64

    /**
     * Trayectoria por defecto de las rutas actuales, escrita en **clics**:
     *
     * 1. sube recto 15 clics → en el clic 15 empieza el giro a la derecha,
     * 2. se corre a la derecha durante 1 clic (hasta el 16) **sin bajar**,
     * 3. sube recto 10 clics más (hasta el 26),
     * 4. se corre a la izquierda 1 clic, volviendo a la línea central.
     *
     * Cada clic avanza lo mismo en cualquier eje, así que el paso lateral dura un
     * clic y recorre exactamente lo que recorre un clic recto: no hay que elegir
     * ninguna magnitud ni compensar nada.
     *
     * Como los dos sentidos duran lo mismo, el lateral vuelve solo a la línea
     * central en cada vuelta; si una zona tuviera más clics a un lado que al otro,
     * el personaje quedaría cada vez más corrido, igual que uno recto queda cada
     * vez más arriba. El arte se repite en los dos ejes, así que no hay borde.
     *
     * El ciclo dura 27 clics y sus tramos rectos suman **una baldosa** exacta, así
     * que cada vuelta empieza en el mismo píxel del arte. Para cambiar el número
     * de clics rectos hay que mover [TILES_PER_CLICK] a la vez (25 clics por
     * baldosa, o un múltiplo: 50 clics por cada dos baldosas).
     */
    val MOTION = RouteBackgroundMotion(
        legs = listOf(
            RouteLeg(clicks = 15f),                            // sube recto hasta el giro
            RouteLeg(clicks = 1f, direction = Direction.RIGHT), // se corre a la derecha
            RouteLeg(clicks = 10f),                            // sube recto de nuevo
            RouteLeg(clicks = 1f, direction = Direction.LEFT)   // se corre a la izquierda
        )
    )
}

/**
 * Fondo de ruta en bucle vertical, listo para cualquier tamaño de pantalla.
 *
 * Decisiones dentro, todas reversibles:
 *
 * 1. **Escala**: la cámara mide siempre [RouteScrollDefaults.CAMERA_WIDTH] ×
 *    [RouteScrollDefaults.CAMERA_HEIGHT] píxeles de arte, sea cual sea el tamaño
 *    del drawable. El arte no se estira ni se encoge para llenar el ancho: un PNG
 *    de 2176 px se ve más grande (y más recortado) que uno de 1152. La escala es
 *    `min(ancho/1152, alto/1920)`, así que la cámara entra siempre entera, sin
 *    deformación y sin franjas.
 * 2. **Bucle vertical sin costura**: el arte es un patrón continuo, así que la
 *    fila inferior y la superior coinciden y la repetición no se nota. Solo si el
 *    drawable no cumple eso (el arte antiguo de ruta) se enrolla cíclicamente por
 *    `seamCutRow` para que la unión caiga en la pareja de filas más parecida.
 *    Ninguna copia se dibuja reflejada en vertical.
 * 3. **Horizontal infinito, como loseta**: las columnas se dibujan contiguas y
 *    **todas iguales**: al borde derecho de una le sigue el borde izquierdo del
 *    arte. El corrimiento lateral no tiene tope: por mucho que se corra, siempre
 *    hay una columna tapando el borde, y el dibujo se repite como el vertical.
 * 4. **Filtro**: nearest-neighbour al ampliar, para conservar el pixel art;
 *    bilineal al reducir, porque nearest-neighbour deja moteado al perder filas.
 *
 * @param pulseCount número acumulado de pulsaciones sobre la Pokéball. Cada
 *   incremento pide un **paso seco** de [RouteScrollDefaults.TILES_PER_CLICK]
 *   baldosas que se consume en un plazo corto; al dejar de pulsar no queda
 *   deslizamiento, el fondo se para en el acto.
 * @param motion trayectoria a seguir: qué tramos suben recto y cuáles se corren
 *   de lado. Por defecto, la de las rutas actuales.
 * @param imageRes drawable que se repite. Cada zona puede traer el suyo.
 * @param seamCutRow fila del drawable por la que se enrolla el bucle vertical.
 *   **Los fondos son patrones continuos, así que el valor normal es `0`** (no
 *   enrollar). Solo el arte antiguo, dibujado como un tramo suelto, necesita un
 *   valor aquí.
 * @param startAnchor fracción de la altura de la pantalla donde debe caer el
 *   **borde inferior del arte** al arrancar la zona con cero clics (`0,5` = el
 *   centro, donde está el entrenador). `null` arranca por el principio del arte.
 *   Solo cambia el punto de partida: la trayectoria y la dirección no se tocan.
 * @param startOffset corrimiento lateral con el que arranca la zona, en anchos de
 *   pantalla y con el mismo signo que [RouteBackgroundMotion.lateralDistanceAt]:
 *   positivo = personaje a la derecha, así que la imagen se ve corrida a la
 *   izquierda. Tampoco toca la trayectoria: es un punto de partida, no un tramo.
 */
@Composable
internal fun RouteScrollingBackground(
    pulseCount: Long,
    modifier: Modifier = Modifier,
    motion: RouteBackgroundMotion = RouteScrollDefaults.MOTION,
    @DrawableRes imageRes: Int = R.drawable.background_ruta,
    seamCutRow: Int = 0,
    startAnchor: Float? = null,
    startOffset: Float = 0f
) {
    val context = LocalContext.current
    // `drawable-nodpi` evita el reescalado por densidad: siempre se carga a
    // tamaño nativo y el escalado lo controla el dibujo. Con arte continuo basta
    // con usarlo tal cual; solo el arte antiguo de ruta se enrolla por
    // `seamCutRow` para que la unión del bucle no se vea.
    val bitmap = remember(context, imageRes, seamCutRow) {
        val source = BitmapFactory.decodeResource(context.resources, imageRes)
            ?: return@remember null
        if (seamCutRow <= 0) return@remember source.asImageBitmap()

        val rolled = Bitmap.createBitmap(
            source.width, source.height, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(rolled)
        val cut = seamCutRow
        // Parte superior: filas cut..height-1 del Fuente, en la parte de arriba
        // del tile. Llega hasta `size.height - cut` y no más allá.
        canvas.drawBitmap(source, 0f, -cut.toFloat(), null)
        // Cierre cíclico: filas 0..cut-1 del Fuente, abajo del todo.
        canvas.drawBitmap(source, 0f, (source.height - cut).toFloat(), null)
        source.recycle()
        rolled.asImageBitmap()
    }

    // El contador se lee dentro del bucle de frames, no en composición: así el
    // fondo no redibuja ni recompone nada por cada pulsación, solo el avance.
    val pulses by rememberUpdatedState(pulseCount)

    // Fase vertical normalizada, 0f..1f sobre el alto de **una cámara**. Se
    // guarda normalizada y no en px acumulados para que la precisión del float no
    // se degrade en sesiones larguísimas. Cada frame se recalcula desde la
    // trayectoria: en un fondo vertical se congela durante los tramos laterales;
    // en uno horizontal son los laterales los que la mueven.
    val phaseV = remember { mutableFloatStateOf(0f) }

    // Corrimiento lateral vigente, en **alturas de baldosa** (la misma unidad que
    // el avance vertical de la trayectoria). Puede crecer sin tope: el arte se
    // repite en horizontal, así que no hay borde que descubrir. Se guarda en
    // baldosas y no en px para que el número siga siendo pequeño en sesiones
    // largas; el dibujo lo pasa a píxeles de cámara.
    val lateral = remember { mutableFloatStateOf(0f) }

    // Alto del arte en px: la cámara se mide en **alturas de arte**, así que el
    // paso de un clic tiene que traducirse con esta proporción.
    val artHeight = bitmap?.height ?: 0

    LaunchedEffect(motion, artHeight) {
        if (artHeight <= 0) return@LaunchedEffect
        // Arranca en la posición que ya llevaba el recorrido: al volver a componer
        // (otra pestaña, otra zona) no se repiten los clics anteriores.
        val walker = RouteClickWalker(initialSteps = pulses)
        var lastPosition = -1.0

        while (isActive) {
            withFrameNanos { now ->
                val position = walker.positionAt(pulses, now)
                if (position == lastPosition) return@withFrameNanos
                lastPosition = position

                // Las dos coordenadas salen de la trayectoria en el punto ya
                // recorrido. Así el movimiento de cada tramo cae en **un solo
                // eje**: en los laterales el vertical se queda clavado y viceversa.
                // Qué eje es el de avance lo decide `motion.axis`: en un fondo
                // horizontal el que se mueve es el lateral.
                val shift = routeScrollShiftAt(motion, position, artHeight)
                phaseV.floatValue = shift.verticalPhase
                lateral.floatValue = shift.lateral
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

                // La cámara mide siempre lo mismo en píxeles de arte ([CAMERA_WIDTH]
                // × [CAMERA_HEIGHT]), así que la escala NO depende del drawable: con
                // un PNG más grande se ve más grande, no más "world" per pantalla.
                // `min` deja la cámara entera dentro de la pantalla sin deformar; en
                // pantallas más altas que 1152:1920 se ve arte de más por arriba y
                // por abajo, que es lo que tapa el bucle.
                val scale = routeCameraScale(size.width, size.height)
                if (scale <= 0f) return@drawBehind
                val tileW = srcW * scale
                val tileH = srcH * scale
                if (tileH <= 1f || tileW <= 1f) return@drawBehind

                val filter = if (scale >= 1f) FilterQuality.None else FilterQuality.Medium
                // El punto de partida de la zona se suma a la fase: `phaseV` sigue
                // midiendo el recorrido (y por tanto la trayectoria), y el ancla
                // solo decide por dónde se empieza a ver el arte.
                val shiftV = (phaseV.floatValue * tileH +
                    routeStartShift(tileH, size.height, startAnchor)) % tileH
                // El lateral se mide en alturas de baldosa, así que se pasa a
                // píxeles de **cámara** (no de arte): un clic de lado recorre los
                // mismos píxeles que un clic recto aunque el dibujo no sea cuadrado
                // y aunque el drawable mida más o menos que la cámara. `startOffset`
                // viene en anchos de pantalla y se convierte aparte.
                val cameraPerArt = RouteScrollDefaults.CAMERA_HEIGHT / srcH
                val left0 = (size.width - tileW) / 2f
                // El patrón horizontal se repite **como un tile**: a la derecha de
                // una columna vuelve a empezar el borde izquierdo del arte, sin
                // reflejarlo. Enrollar el desplazamiento en el ancho de una columna
                // mantiene el número pequeño sin cambiar lo que se ve.
                val period = tileW
                var shiftH = (lateral.floatValue * cameraPerArt * tileH +
                    startOffset * size.width) % period
                if (shiftH < 0f) shiftH += period

                // Primera columna que todavía toca la pantalla. Todas son la misma
                // copia del arte, contiguas: el dibujo se repite como una loseta.
                var column = routeFirstColumn(left0, shiftH, tileW)
                var columnsDrawn = 0

                while (columnsDrawn < RouteScrollDefaults.MAX_REPEATS) {
                    val startX = routeColumnStart(column, left0, shiftH, tileW)
                    if (startX >= size.width) break

                    var top = -shiftV
                    var rowsDrawn = 0
                    while (top < size.height && rowsDrawn < RouteScrollDefaults.MAX_REPEATS) {
                        withTransform({
                            translate(startX, top)
                            scale(scale, scale, Offset.Zero)
                        }) {
                            drawImage(image = bmp, filterQuality = filter)
                        }
                        top += tileH
                        rowsDrawn++
                    }

                    column++
                    columnsDrawn++
                }
            }
    )
}
