/**
 * Fondo de ruta animado: repite [R.drawable.background_ruta] en bucle vertical y
 * lo desplaza **hacia abajo** un paso seco por clic. No hay inercia: al dejar de
 * pulsar la Pokéball el fondo se detiene en el acto, sin deslizamiento residual.
 *
 * Encima del avance vertical, el fondo sigue una **trayectoria** de tramos
 * ([RouteLeg]): unos suben recto y otros se corren a un lado, de modo que el
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
 * @param direction eje y sentido del tramo: [Direction.BACK] avanza recto (el
 *   fondo sube y al entrenador se le ve de espaldas); [Direction.RIGHT] y
 *   [Direction.LEFT] lo corren de lado sin avanzar en vertical.
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

    /** `true` si el tramo se corre de lado (y por tanto no avanza hacia arriba). */
    val isLateral: Boolean
        get() = direction != Direction.BACK

    /** +1 derecha, −1 izquierda, 0 recto. */
    val sideSign: Float
        get() = when (direction) {
            Direction.RIGHT -> 1f
            Direction.LEFT -> -1f
            else -> 0f
        }
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
 * @param legs tramos en orden; se repiten indefinidamente.
 */
internal class RouteBackgroundMotion(
    val legs: List<RouteLeg>
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
     * baldosa**: positivo = el personaje se ha corrido a la derecha. **Sin tope**:
     * si un ciclo deja más clics a un lado que al otro, el valor crece vuelta tras
     * vuelta.
     *
     * Permanece **constante** durante los tramos rectos (no hay movimiento
     * diagonal) y dentro de los laterales avanza **lineal**, a la misma velocidad
     * que el vertical: un clic de lado recorre lo mismo que un clic recto.
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
     * primero). Es la del tramo por el que pasa ese paso.
     *
     * Se muestrea el **centro** del paso para que un clic que arranca justo en el
     * borde entre dos tramos no herede la dirección del anterior.
     */
    fun directionForStep(step: Long): Direction {
        if (step <= 0L) return Direction.FRONT
        val cycle = cycleDistance.toDouble()
        var cursor = wrapped(
            (step - 0.5) * RouteScrollDefaults.TILES_PER_CLICK.toDouble(),
            cycle
        )
        for (leg in legs) {
            if (cursor < leg.distance) return leg.direction
            cursor -= leg.distance
        }
        return legs.last().direction
    }

    /**
     * Baldosas que el fondo **ha bajado** tras [travelled] baldosas de recorrido.
     *
     * Solo avanza en los tramos rectos: mientras dura un tramo lateral el valor
     * se queda clavado y la imagen se desliza de lado sin bajar ni un píxel.
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
     * Clics que hacen falta para avanzar una baldosa.
     *
     * 25 es el número que hace que el ciclo de la trayectoria (25 clics rectos)
     * caiga en una baldosa exacta, y con eso que cada vuelta empiece en el mismo
     * píxel del arte. Si se cambia, hay que reajustar los tramos de [MOTION] en la
     * misma proporción (lo comprueba
     * `RouteBackgroundMotionTest.defaultMotion_returnsToTheSameArtworkEveryCycle`).
     */
    const val CLICKS_PER_TILE = 25f

    /** Avance de un clic, en baldosas. */
    const val TILES_PER_CLICK = 1f / CLICKS_PER_TILE

    /**
     * Dirección del desplazamiento vertical: `-1f` = el fondo baja por la
     * pantalla (el personaje sube), `1f` = al revés.
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
 * 1. **Escala**: la imagen se escala uniforme hasta llenar el ancho y la altura
 *    se cubre repitiendo. Sin deformación y con el mismo aspecto en 720p, 1080p
 *    o 1440p.
 * 2. **Bucle vertical sin costura**: el arte es un patrón continuo, así que la
 *    fila inferior y la superior coinciden y la repetición no se nota. Solo si el
 *    drawable no cumple eso (el arte antiguo de ruta) se enrolla cíclicamente por
 *    `seamCutRow` para que la unión caiga en la pareja de filas más parecida.
 *    Ninguna copia se dibuja reflejada en vertical.
 * 3. **Horizontal infinito, como loseta**: la imagen mide justo el ancho de
 *    pantalla, así que las columnas se dibujan contiguas y **todas iguales**: al
 *    borde derecho de una le sigue el borde izquierdo del arte. El corrimiento
 *    lateral no tiene tope: por mucho que se corra, siempre hay una columna
 *    tapando el borde, y el dibujo se repite como el vertical.
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

    // Fase vertical normalizada, 0f..1f sobre el alto de una baldosa. Se guarda
    // normalizada y no en px acumulados para que la precisión del float no se
    // degrade en sesiones larguísimas. Cada frame se recalcula desde la
    // trayectoria: mientras dura un tramo lateral se queda congelada.
    val phaseV = remember { mutableFloatStateOf(0f) }

    // Corrimiento lateral vigente, en **alturas de baldosa** (la misma unidad que
    // el avance vertical). Puede crecer sin tope: el arte se repite en horizontal,
    // así que no hay borde que descubrir. Se guarda en baldosas y no en px para
    // que el número siga siendo pequeño en sesiones largas.
    val lateral = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(motion) {
        // Arranca en la posición que ya llevaba el recorrido: al volver a componer
        // (otra pestaña, otra zona) no se repiten los clics anteriores.
        val walker = RouteClickWalker(initialSteps = pulses)
        var lastPosition = -1.0

        while (isActive) {
            withFrameNanos { now ->
                val position = walker.positionAt(pulses, now)
                if (position == lastPosition) return@withFrameNanos
                lastPosition = position

                // Ambas coordenadas se leen de la trayectoria en el punto ya
                // recorrido. Así el movimiento de cada tramo cae en **un solo
                // eje**: en los laterales el vertical se queda clavado y viceversa.
                //
                // `DIRECTION` solo orienta el vertical; la fase puede quedar
                // negativa y `floor` la vuelve a [0f, 1f) sin salto visible (el
                // tile se repite con período exacto).
                val vertical = motion.verticalDistanceAt(position)
                var nextV = RouteScrollDefaults.DIRECTION * vertical
                nextV -= floor(nextV)
                phaseV.floatValue = nextV.toFloat()

                lateral.floatValue = motion.lateralDistanceAt(position)
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

                // Escala 1:1 al ancho: el arte mide justo una pantalla y el hueco
                // lateral lo tapa la columna vecina, que es la misma loseta.
                val scale = size.width / srcW
                val tileW = srcW * scale
                val tileH = srcH * scale
                if (tileH <= 1f || tileW <= 1f) return@drawBehind

                val filter = if (scale >= 1f) FilterQuality.None else FilterQuality.Medium
                // El punto de partida de la zona se suma a la fase: `phaseV` sigue
                // midiendo el recorrido (y por tanto la trayectoria), y el ancla
                // solo decide por dónde se empieza a ver el arte.
                val shiftV = (phaseV.floatValue * tileH +
                    routeStartShift(tileH, size.height, startAnchor)) % tileH
                // El lateral se mide en alturas de baldosa, así que se multiplica
                // por el ALTO del arte: un clic de lado recorre los mismos píxeles
                // que un clic recto aunque el dibujo no sea cuadrado. `startOffset`
                // viene en anchos de pantalla y se convierte aparte.
                val left0 = (size.width - tileW) / 2f
                // El patrón horizontal se repite **como un tile**: a la derecha de
                // una columna vuelve a empezar el borde izquierdo del arte, sin
                // reflejarlo. Enrollar el desplazamiento en el ancho de una columna
                // mantiene el número pequeño sin cambiar lo que se ve.
                val period = tileW
                var shiftH = (lateral.floatValue * tileH + startOffset * size.width) % period
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
