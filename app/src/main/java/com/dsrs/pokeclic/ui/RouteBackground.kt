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
 * El desplazamiento lateral está **acotado a propósito**: el arte original mide
 * exactamente un ancho de pantalla. Para poder correrse de lado hay dos modos
 * ([RouteScrollDefaults.LATERAL_MARGIN]): agrandar la imagen lo justo para que
 * nunca enseñe su borde, o dibujarla a escala 1:1 y tapar el hueco con la copia
 * espejada de la columna vecina.
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
import kotlin.math.abs
import kotlin.math.floor

/**
 * Un tramo de la trayectoria del fondo.
 *
 * @param clicks cuántos clics dura el tramo, contados desde la posición
 *   inicial. Es la unidad en la que se percibe el recorrido al jugar, y la que
 *   conviene tocar al ajustar la animación.
 * @param drift cuánto se corre el personaje **hacia un lado** a lo largo de este
 *   tramo, en **anchos de pantalla**. Positivo = hacia la derecha.
 *
 * Cada tramo mueve **un solo eje**: con `drift = 0f` el fondo baja (o sube) y no
 * se corre a ningún lado; con `drift != 0f` el fondo se corre de lado y **no
 * baja**. Así el movimiento nunca es diagonal.
 */
internal data class RouteLeg(
    val clicks: Float,
    val drift: Float
) {
    /**
     * Recorrido del tramo en **baldosas**. Un clic avanza
     * [RouteScrollDefaults.TILES_PER_CLICK], así que `clicks` es también el número
     * de pasos que dura el tramo. Una baldosa es una repetición completa de la
     * imagen, ≈ 1,8 anchos de pantalla en esta proporción.
     */
    val distance: Float
        get() = clicks * RouteScrollDefaults.TILES_PER_CLICK

    /** `true` si el tramo se corre de lado (y por tanto no avanza hacia arriba). */
    val isLateral: Boolean
        get() = drift != 0f

    /**
     * Dirección en la que camina el entrenador mientras este tramo está activo:
     * el recto baja, así que se le ve de espaldas; el lateral mira hacia donde se
     * corre (positivo = derecha).
     */
    val direction: Direction
        get() = when {
            !isLateral -> Direction.BACK
            drift > 0f -> Direction.RIGHT
            else -> Direction.LEFT
        }
}

/**
 * Trayectoria completa del fondo, formada por tramos que se repiten en bucle.
 *
 * Todo se calcula como **posición en función de lo recorrido**, no como
 * velocidad acumulada: así no puede derivar nunca. La suma de los `drift` de un
 * ciclo debe dar cero para que el bucle cierre; si no cierra, la diferencia se
 * reparte linealmente por el ciclo para que el salto no se vea.
 *
 * Los tramos son de un eje: [verticalDistanceAt] avanza solo en los tramos
 * rectos y [offsetAt] solo en los laterales.
 *
 * @param legs tramos en orden; deben cubrir el ciclo completo.
 */
internal class RouteBackgroundMotion(
    val legs: List<RouteLeg>
) {
    init {
        require(legs.isNotEmpty()) { "La trayectoria necesita al menos un tramo" }
        require(legs.all { it.distance > 0f }) { "Cada tramo debe avanzar algo" }
    }

    /** Largo de un ciclo completo, en baldosas. */
    val cycleDistance: Float = legs.sumOf { it.distance.toDouble() }.toFloat()

    /** Desplazamiento acumulado al final de cada tramo, en anchos de pantalla. */
    private val waypoints: FloatArray = FloatArray(legs.size).also { marks ->
        var accumulated = 0f
        legs.forEachIndexed { index, leg ->
            accumulated += leg.drift
            marks[index] = accumulated
        }
    }

    /** Cierre del ciclo: cero si los tramos están bien compensados. */
    private val residual: Float = waypoints.last()

    /** Baldosas que baja el fondo en un ciclo: solo las de los tramos rectos. */
    private val verticalPerCycle: Double =
        legs.filterNot { it.isLateral }.sumOf { it.distance.toDouble() }

    /**
     * Corrimiento máximo respecto a la línea central, en anchos de pantalla. Es
     * lo que decide cuánto margen lateral (y por tanto cuánto zoom) hace falta
     * para que no se vea nunca el borde del arte.
     */
    val maxDrift: Float = waypoints.maxOf { abs(it) } + abs(residual)

    /**
     * Corrimiento lateral tras [travelled] baldosas de recorrido, en anchos de
     * pantalla: positivo = el personaje se ha corrido a la derecha.
     *
     * Permanece **constante** durante los tramos rectos (no hay movimiento
     * diagonal) y dentro de los laterales avanza **lineal**: velocidad constante
     * de principio a fin, sin rampa de entrada ni de salida. Con `smoothstep` el
     * arranque y el final iban a velocidad casi cero y el paso se percibía lento
     * aunque el recorrido total fuese el mismo.
     */
    fun offsetAt(travelled: Double): Float {
        val cycle = cycleDistance.toDouble()
        val inCycle = wrapped(travelled, cycle)

        var cursor = inCycle
        var offset = 0f
        for (index in legs.indices) {
            if (cursor <= 0.0) break
            val leg = legs[index]
            val take = minOf(cursor, leg.distance.toDouble())
            val start = if (index == 0) 0f else waypoints[index - 1]
            val end = waypoints[index]
            val t = (take / leg.distance).toFloat().coerceIn(0f, 1f)
            offset = start + (end - start) * t
            cursor -= take
        }

        if (residual == 0f) return offset
        // Red de seguridad: reparte el cierre que falte por todo el ciclo para
        // que el bucle no dé un salto aunque una localización esté mal sumada.
        return offset - residual * (inCycle / cycle).toFloat()
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
 * Factor de escala extra que deja margen lateral para que el personaje pueda
 * correrse hasta [maxDrift] anchos de pantalla sin que el arte original deje de
 * cubrir la pantalla.
 *
 * Se deriva de la trayectoria a propósito: no hay dos números que puedan quedar
 * desincronizados. El precio es un zoom: `1f + 2f * (maxDrift + margen)`, es
 * decir, un 10 % de corrimiento a cada lado cuesta un ~22 % de zoom. Si una
 * localización necesita más recorrido lateral del que el zoom tolera, la salida
 * correcta es un arte preparado para repetirse en horizontal.
 */
internal fun routeSlackFactor(maxDrift: Float): Float =
    1f + 2f * (abs(maxDrift) + RouteScrollDefaults.SLACK_MARGIN)

/**
 * Columna (copia horizontal) que cubre el borde izquierdo de la pantalla.
 *
 * Las columnas se dibujan contiguas, `tileW` de ancho cada una, y alternando
 * normal y espejada. Devolver la primera que toca la pantalla es lo que evita que
 * el corrimiento lateral deje una franja vacía en un borde.
 */
internal fun routeFirstColumn(left0: Float, shiftH: Float, tileW: Float): Int =
    floor((shiftH - left0) / tileW).toInt()

/** Borde izquierdo de la columna [column], en px de pantalla. */
internal fun routeColumnStart(column: Int, left0: Float, shiftH: Float, tileW: Float): Float =
    left0 - shiftH + column * tileW

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
     * Margen lateral (zoom) del arte.
     *
     * - `true`: la imagen se agranda lo justo para que el corrimiento lateral nunca
     *   descubra su borde. Se ve **solo** el arte original, algo más grande.
     * - `false`: escala 1:1. Como la imagen mide exactamente un ancho de pantalla,
     *   al correrse de lado entra por un borde su **copia espejada**: es la única
     *   forma de tapar el hueco sin agrandar el arte.
     */
    const val LATERAL_MARGIN = false

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

    /** Tope de repeticiones verticales dibujadas por frame, por si la proporción es absurda. */
    const val MAX_REPEATS = 64

    /** Margen extra a cada lado para que el borde del arte no asome por redondeo. */
    const val SLACK_MARGIN = 0.01f

    /**
     * Trayectoria por defecto de las rutas actuales, escrita en **clics**:
     *
     * 1. sube recto 15 clics → en el clic 15 empieza el giro a la derecha,
     * 2. se corre a la derecha durante 1 clic (hasta el 16) **sin bajar**,
     * 3. sube recto 10 clics más (hasta el 26),
     * 4. se corre a la izquierda 1 clic, volviendo a la línea central.
     *
     * Los corrimientos laterales duran **un clic a propósito**: su velocidad es
     * `drift / (clicks · TILES_PER_CLICK · aspecto)` veces la del avance vertical.
     * Con 0,08 en 1 clic van a 1,12× la vertical, a velocidad **constante** de
     * principio a fin; con 2 clics caen al 56 % y con 5 al 22 %, que es lo que se
     * percibe como arrastre. Si hay que alargar el tramo, el `drift` tiene que
     * subir en la misma proporción para conservar la velocidad, y eso pide más
     * zoom para no enseñar el borde.
     *
     * Los dos corrimientos se compensan exactamente, así que la trayectoria se
     * repite sin desplazarse y el personaje nunca se sale del arte original.
     *
     * **El ciclo cierra también sobre el dibujo.** Dura 27 clics (25 rectos + 2
     * laterales) y sus tramos rectos suman exactamente **una baldosa**, así que
     * cada vuelta empieza en el mismo píxel del arte y con el lateral en cero:
     * todas las vueltas son idénticas, no solo en la estructura. Para cambiar el
     * número de clics rectos hay que mover [TILES_PER_CLICK] a la vez (25 clics
     * por baldosa, o un múltiplo: 50 clics por cada dos baldosas).
     */
    val MOTION = RouteBackgroundMotion(
        legs = listOf(
            RouteLeg(clicks = 15f, drift = 0f),       // sube recto hasta el giro
            RouteLeg(clicks = 1f, drift = 0.08f),     // se corre a la derecha
            RouteLeg(clicks = 10f, drift = 0f),       // sube recto de nuevo
            RouteLeg(clicks = 1f, drift = -0.08f)     // se corre a la izquierda
        )
    )
}

/**
 * Fondo de ruta en bucle vertical, listo para cualquier tamaño de pantalla.
 *
 * Decisiones dentro, todas reversibles:
 *
 * 1. **Escala**: la imagen se escala uniforme hasta llenar el ancho, más el
 *    margen que pide la trayectoria si [RouteScrollDefaults.LATERAL_MARGIN] está
 *    activo, y la altura se cubre repitiendo. Sin deformación y con el mismo
 *    aspecto en 720p, 1080p o 1440p.
 * 2. **Bucle vertical sin costura**: el arte es un patrón continuo, así que la
 *    fila inferior y la superior coinciden y la repetición no se nota. Solo si el
 *    drawable no cumple eso (el arte antiguo de ruta) se enrolla cíclicamente por
 *    `seamCutRow` para que la unión caiga en la pareja de filas más parecida.
 *    Ninguna copia se dibuja reflejada en vertical.
 * 3. **Horizontal**: con margen activo, el zoom tapa el recorrido lateral y nunca
 *    se ve una segunda columna. Sin margen (escala 1:1) la imagen mide justo el
 *    ancho de pantalla, así que las columnas impares se dibujan **espejadas**
 *    para que el corrimiento no deje un hueco: la unión es la misma columna de
 *    píxeles por los dos lados y no hay costura.
 * 4. **Filtro**: nearest-neighbour al ampliar, para conservar el pixel art;
 *    bilineal al reducir, porque nearest-neighbour deja moteado al perder filas.
 *
 * @param pulseCount número acumulado de pulsaciones sobre la Pokéball. Cada
 *   incremento pide un **paso seco** de [RouteScrollDefaults.TILES_PER_CLICK]
 *   baldosas que se consume en un plazo corto; al dejar de pulsar no queda
 *   deslizamiento, el fondo se para en el acto.
 * @param motion trayectoria a seguir: qué tramos suben recto y cuánto se corren
 *   de lado. Por defecto, la de las rutas actuales.
 * @param imageRes drawable que se repite. Cada zona puede traer el suyo.
 * @param seamCutRow fila del drawable por la que se enrolla el bucle vertical.
 *   **Los fondos son patrones continuos, así que el valor normal es `0`** (no
 *   enrollar). Solo el arte antiguo, dibujado como un tramo suelto, necesita un
 *   valor aquí.
 */
@Composable
internal fun RouteScrollingBackground(
    pulseCount: Long,
    modifier: Modifier = Modifier,
    motion: RouteBackgroundMotion = RouteScrollDefaults.MOTION,
    @DrawableRes imageRes: Int = R.drawable.background_ruta,
    seamCutRow: Int = 0
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

    // Corrimiento lateral vigente, en anchos de pantalla. Es pequeño y acotado,
    // así que no hay problema de precisión.
    val offset = remember { mutableFloatStateOf(0f) }

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

                offset.floatValue = motion.offsetAt(position)
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

                // Con margen lateral el arte se agranda lo justo para que el
                // corrimiento nunca descubra su borde; sin margen se dibuja a
                // escala 1:1 y el hueco lo tapa la copia espejada.
                val slack = if (RouteScrollDefaults.LATERAL_MARGIN) {
                    routeSlackFactor(motion.maxDrift)
                } else {
                    1f
                }
                val scale = size.width * slack / srcW
                val tileW = srcW * scale
                val tileH = srcH * scale
                if (tileH <= 1f || tileW <= 1f) return@drawBehind

                val filter = if (scale >= 1f) FilterQuality.None else FilterQuality.Medium
                val shiftV = phaseV.floatValue * tileH
                // Centrada con `offset` a cero; el corrimiento positivo (personaje
                // a la derecha) mueve el contenido hacia la izquierda.
                val left0 = (size.width - tileW) / 2f
                val shiftH = offset.floatValue * size.width

                // Primera columna que todavía toca la pantalla. Las pares van
                // derechas y las impares espejadas: la unión es la misma columna de
                // píxeles por los dos lados, así que no hay costura.
                var column = routeFirstColumn(left0, shiftH, tileW)
                var columnsDrawn = 0

                while (columnsDrawn < RouteScrollDefaults.MAX_REPEATS) {
                    val startX = routeColumnStart(column, left0, shiftH, tileW)
                    if (startX >= size.width) break
                    val mirrored = column % 2 != 0

                    var top = -shiftV
                    var rowsDrawn = 0
                    while (top < size.height && rowsDrawn < RouteScrollDefaults.MAX_REPEATS) {
                        withTransform({
                            if (mirrored) {
                                translate(startX + tileW, top)
                                scale(-scale, scale, Offset.Zero)
                            } else {
                                translate(startX, top)
                                scale(scale, scale, Offset.Zero)
                            }
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
