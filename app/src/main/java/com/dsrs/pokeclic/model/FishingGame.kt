package com.dsrs.pokeclic.model

import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

/**
 * Reglas y estado del minijuego de pesca (Etapa 3), el que sustituye a la
 * captura por lanzamientos.
 *
 * Mecánica, al estilo del minijuego de pesca de Stardew Valley:
 * - En un riel vertical vive el sprite del salvaje, que se mueve de forma
 *   errática: avanza suavemente hacia objetivos aleatorios y, con dificultad,
 *   da "dardos" bruscos hacia cualquier punto del riel.
 * - La barra de captura del jugador sube mientras se mantiene pulsado y cae
 *   por gravedad al soltar. Se captura llenando el progreso (se llena solo
 *   cuando la barra solapa al sprite); si el progreso llega a 0, el salvaje
 *   escapa.
 *
 * Todo aquí es independiente de Android y del reloj real (`step` recibe el
 * tiempo transcurrido y un [Random] inyectado) para poder probar la mecánica
 * en la JVM, igual que `GymCombat`. El ViewModel se limita a lanzar el bucle
 * de ticks y a reaccionar al resultado; la UI solo pinta el estado ya
 * calculado.
 */

/** Fase del minijuego. La partida termina con un único `CAPTURED` o `ESCAPED`. */
enum class FishingPhase { PLAYING, CAPTURED, ESCAPED }

/**
 * Parámetros de una partida de pesca, ya calculados para la especie en liza.
 *
 * Llevarlos dentro de [FishingState] (como hace `GymState` con sus duraciones)
 * mantiene el estado autocontenido: `step` no necesita más entradas.
 *
 * @param barSize tamaño de la barra de captura, como fracción del riel.
 * @param barRiseAccel aceleración de subida al mantener pulsado, en fracciones
 *   del riel por segundo al cuadrado.
 * @param gravity gravedad al soltar (negativa: la barra cae).
 * @param maxBarSpeed velocidad máxima absoluta de la barra, en fracciones/s.
 * @param fillRate progreso que se gana por segundo con solape.
 * @param drainRate progreso que se pierde por segundo sin solape.
 * @param initialGraceSeconds segundo(s) iniciales en los que el progreso a 0 no
 *   hace escapar al salvaje, para no castigar la reacción del jugador.
 * @param smoothingSeconds tiempo de convergencia del movimiento suave del
 *   sprite (menor = más brusco).
 * @param dartChancePerSecond probabilidad por segundo de un dardo brusco.
 * @param spriteMargin margen (como fracción del riel) dentro del que se mueve
 *   el sprite, para que no asome fuera de la pista.
 * @param wanderRange desviación máxima del vagabundeo suave con respecto a la
 *   posición actual del sprite.
 */
data class FishingConfig(
    val barSize: Float = FishingDefaults.BAR_SIZE,
    val barRiseAccel: Float = FishingDefaults.BAR_RISE_ACCEL,
    val gravity: Float = FishingDefaults.GRAVITY,
    val maxBarSpeed: Float = FishingDefaults.MAX_BAR_SPEED,
    val fillRate: Float = FishingDefaults.FILL_RATE,
    val drainRate: Float = FishingDefaults.DRAIN_RATE,
    val initialGraceSeconds: Float = FishingDefaults.INITIAL_GRACE_SECONDS,
    val smoothingSeconds: Float,
    val dartChancePerSecond: Float,
    val spriteMargin: Float = FishingDefaults.SPRITE_MARGIN,
    val wanderRange: Float = FishingDefaults.WANDER_RANGE
)

/**
 * Estado transitorio de una partida de pesca.
 *
 * Las posiciones están normalizadas a la altura del riel: `0f` es el borde
 * inferior y `1f` el superior (así la gravedad negativa tira hacia abajo).
 *
 * @param config parámetros de esta partida.
 * @param barPos centro de la barra de captura (0..1).
 * @param barVel velocidad de la barra (fracciones/s; negativa al caer).
 * @param spritePos posición del centro del sprite (0..1).
 * @param spriteTarget objetivo actual del movimiento suave.
 * @param dartBoostRemaining segundos restantes de movimiento acelerado tras
 *   un dardo.
 * @param progress progreso de captura (0 = escapa, 1 = capturado).
 * @param elapsed segundos transcurridos de partida.
 * @param phase fase actual.
 */
data class FishingState(
    val config: FishingConfig,
    val barPos: Float,
    val barVel: Float,
    val spritePos: Float,
    val spriteTarget: Float,
    val dartBoostRemaining: Float = 0f,
    val progress: Float,
    val elapsed: Float,
    val phase: FishingPhase = FishingPhase.PLAYING
)

/**
 * Constantes sintonizables del minijuego. Números de partida revisables: si el
 * equilibrio no convence, se retocan aquí sin tocar lógica.
 *
 * La dificultad (`Species.captureDifficulty`, 0..1) solo mapea el movimiento
 * del sprite: suavizado (1,2 s fácil → 0,3 s difícil) y cadencia de dardos
 * (0,4/s fácil → 1,6/s difícil). Los ratios de la barra de progreso son fijos:
 * llenarla requiere ≈2,5 s de solape acumulado y perderla ≈5 s sin él.
 */
object FishingDefaults {

    /** Tamaño de la barra de captura, como fracción del riel. */
    const val BAR_SIZE = 0.22f

    /** Aceleración de subida al mantener pulsado (fracciones/s²). */
    const val BAR_RISE_ACCEL = 3.0f

    /** Gravedad al soltar (fracciones/s²; negativa porque tira hacia abajo). */
    const val GRAVITY = -3.0f

    /** Velocidad máxima absoluta de la barra (fracciones/s). */
    const val MAX_BAR_SPEED = 1.2f

    /** Progreso ganado por segundo con solape. */
    const val FILL_RATE = 0.40f

    /** Progreso perdido por segundo sin solape. */
    const val DRAIN_RATE = 0.20f

    /** Segundo(s) iniciales sin huida aunque el progreso esté a 0. */
    const val INITIAL_GRACE_SECONDS = 1.0f

    /** Suavizado del sprite en la dificultad mínima. */
    const val SMOOTHING_EASY = 1.2f

    /** Suavizado del sprite en la dificultad máxima. */
    const val SMOOTHING_HARD = 0.3f

    /** Probabilidad de dardo por segundo en la dificultad mínima. */
    const val DART_EASY = 0.4f

    /** Probabilidad de dardo por segundo en la dificultad máxima. */
    const val DART_HARD = 1.6f

    /** Duración del boost de velocidad tras un dardo, en segundos. */
    const val DART_BOOST_SECONDS = 0.5f

    /** Cuánto se acelera el suavizado durante un dardo (divisor). */
    const val DART_BOOST_SPEEDUP = 3f

    /** Amplitud del vagabundeo suave, como fracción del riel. */
    const val WANDER_RANGE = 0.35f

    /** Margen interior del sprite, como fracción del riel. */
    const val SPRITE_MARGIN = 0.1f

    /** Umbral a partir del cual el sprite considera "alcanzado" su objetivo. */
    const val ARRIVAL_TOLERANCE = 0.02f

    /**
     * Traduce la dificultad de captura de la especie (0..1) a los parámetros de
     * movimiento. Las entradas fuera de rango se recortan.
     */
    fun configFor(captureDifficulty: Float): FishingConfig {
        val difficulty = captureDifficulty.coerceIn(0f, 1f)
        return FishingConfig(
            smoothingSeconds = lerp(SMOOTHING_EASY, SMOOTHING_HARD, difficulty),
            dartChancePerSecond = lerp(DART_EASY, DART_HARD, difficulty)
        )
    }

    private fun lerp(from: Float, to: Float, factor: Float): Float = from + (to - from) * factor
}

/**
 * Reglas puras del minijuego de pesca, sin reloj ni corrutinas: reciben un
 * [FishingState] y devuelven el siguiente ([step]), igual que `GymCombat`.
 */
object FishingGame {

    /**
     * Abre la partida: barra parada en el centro, progreso a 0 y el sprite en
     * una posición aleatoria dentro del riel.
     */
    fun initialState(config: FishingConfig, random: Random): FishingState {
        val start = randomSpriteTarget(random)
        return FishingState(
            config = config,
            barPos = 0.5f,
            barVel = 0f,
            spritePos = start,
            spriteTarget = start,
            progress = 0f,
            elapsed = 0f
        )
    }

    /** ¿El sprite está dentro de la barra de captura? */
    fun isOverlapping(state: FishingState): Boolean =
        abs(state.spritePos - state.barPos) <= state.config.barSize / 2f

    /**
     * Avanza la partida un tick de [dt] segundos.
     *
     * Orden deliberado: primero la barra (queda fijado su centro para el
     * cálculo del solape), después el sprite (los dardos y el vagabundeo se
     * deciden con la probabilidad de ese tick) y por último el progreso y la
     * fase, que solo cambian una vez por tick.
     *
     * @param held ¿está el jugador manteniendo pulsado?
     * @param dt segundos transcurridos desde el tick anterior.
     * @param random fuente de aleatoriedad (inyectada para poder testear).
     */
    fun step(state: FishingState, held: Boolean, dt: Float, random: Random): FishingState {
        if (state.phase != FishingPhase.PLAYING || dt <= 0f) return state
        val config = state.config
        val elapsed = state.elapsed + dt

        // Barra: integra la fuerza actual y se frena al tocar los bordes.
        val force = if (held) config.barRiseAccel else config.gravity
        val barVel = (state.barVel + force * dt).coerceIn(-config.maxBarSpeed, config.maxBarSpeed)
        val min = config.barSize / 2f
        val max = 1f - config.barSize / 2f
        val rawCenter = state.barPos + barVel * dt
        val barPos = rawCenter.coerceIn(min, max)
        val settledVel = if (barPos != rawCenter) 0f else barVel

        // Sprite: dardos brusco por probabilidad; si ya llegó a su objetivo,
        // sigue vagando suave por las cercanías.
        var dartBoost = (state.dartBoostRemaining - dt).coerceAtLeast(0f)
        var target = state.spriteTarget
        if (random.nextFloat() < config.dartChancePerSecond * dt) {
            target = randomSpriteTarget(random)
            dartBoost = FishingDefaults.DART_BOOST_SECONDS
        } else if (abs(target - state.spritePos) <= FishingDefaults.ARRIVAL_TOLERANCE) {
            target = wanderTarget(random, state.spritePos, config)
        }
        val smoothing = if (dartBoost > 0f) {
            config.smoothingSeconds / FishingDefaults.DART_BOOST_SPEEDUP
        } else {
            config.smoothingSeconds
        }
        val spritePos = approach(state.spritePos, target, smoothing, config.spriteMargin, dt)

        // Progreso y fase.
        val overlapping = abs(spritePos - barPos) <= config.barSize / 2f
        val rate = if (overlapping) config.fillRate else -config.drainRate
        val progress = (state.progress + rate * dt).coerceIn(0f, 1f)
        val phase = when {
            progress >= 1f -> FishingPhase.CAPTURED
            progress <= 0f && elapsed >= config.initialGraceSeconds -> FishingPhase.ESCAPED
            else -> FishingPhase.PLAYING
        }

        return state.copy(
            barPos = barPos,
            barVel = settledVel,
            spritePos = spritePos,
            spriteTarget = target,
            dartBoostRemaining = dartBoost,
            progress = progress,
            elapsed = elapsed,
            phase = phase
        )
    }

    /** Objetivo aleatorio (dardo o inicio) en todo el riel, respetando el margen. */
    private fun randomSpriteTarget(random: Random): Float {
        val margin = FishingDefaults.SPRITE_MARGIN
        return margin + random.nextFloat() * (1f - 2f * margin)
    }

    /** Objetivo cercano para el vagabundeo suave, recortado al riel. */
    private fun wanderTarget(random: Random, current: Float, config: FishingConfig): Float {
        val margin = config.spriteMargin
        val range = config.wanderRange
        return (current + (random.nextFloat() * 2f - 1f) * range).coerceIn(margin, 1f - margin)
    }

    /**
     * Movimiento suave: convergencia exponencial hacia el objetivo, que queda
     * insensible al tamaño del tick (misma traza con ticks de 50 ms que de 16).
     */
    private fun approach(current: Float, target: Float, smoothingSeconds: Float, margin: Float, dt: Float): Float {
        val factor = 1f - exp(-dt / smoothingSeconds)
        return (current + (target - current) * factor).coerceIn(margin, 1f - margin)
    }
}
