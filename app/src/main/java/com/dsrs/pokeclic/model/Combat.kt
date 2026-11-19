package com.dsrs.pokeclic.model

import kotlin.math.ceil
import kotlin.random.Random

/**
 * Modelo del combate: tipos elementales, gimnasios, fases, proyectiles, anillos
 * de ritmo, Voltorb cargados y las reglas puras del combate.
 *
 * Todo aquí es independiente de Android y del reloj real (las funciones reciben
 * el tiempo transcurrido), para poder probar la mecánica en la JVM. El
 * ViewModel se encarga de los bucles, de crear proyectiles, anillos y Voltorb y
 * de persistir la victoria; la UI solo pinta el estado ya calculado.
 */

/** Menos del 25 % de vida: el líder entra en furia y sus oleadas son más duras. */
const val RAGE_HP_FRACTION = 0.25f

/**
 * Tipos elementales usados por el juego.
 *
 * Son los **15 tipos** de la tabla aprobada para Pokeclic: los de la primera
 * generación sin Siniestro, Acero ni Hada. Cada especie tiene un único tipo y
 * todas las constantes tienen entrada en las dos tablas de efectividad, aunque
 * su lista esté vacía. Las relaciones de inmunidad del juego original se
 * recogen como ×0,5: no hay multiplicadores ×0.
 *
 * Añadir un tipo es añadir una constante, sus entradas en las tablas y su rama
 * de presentación (`typeColor`).
 */
enum class PokemonType {
    NORMAL,
    FIRE,
    WATER,
    GRASS,
    ELECTRIC,
    ROCK,
    FLYING,
    POISON,
    BUG,
    GROUND,
    FIGHTING,
    PSYCHIC,
    ICE,
    GHOST,
    DRAGON;

    /**
     * Efectividad de este tipo (atacante) contra [defender].
     *
     * 2f = súper eficaz (ventaja para el atacante), 0.5f = poco eficaz
     * (desventaja, incluidas las inmunidades del juego original) y 1f = neutro.
     * Es la tabla completa de 15 × 15 tipos: no se deducen relaciones por
     * simetría. Solo la lee [typeDamageMultiplier] para el daño de gimnasio.
     */
    fun effectiveness(defender: PokemonType): Float = when {
        defender in strongAgainst.getValue(this) -> 2f
        defender in weakAgainst.getValue(this) -> 0.5f
        else -> 1f
    }

    private companion object {
        val strongAgainst: Map<PokemonType, Set<PokemonType>> = mapOf(
            NORMAL to emptySet(),
            FIRE to setOf(GRASS, BUG, ICE),
            WATER to setOf(FIRE, ROCK, GROUND),
            GRASS to setOf(WATER, ROCK, GROUND),
            ELECTRIC to setOf(WATER, FLYING),
            ROCK to setOf(FIRE, FLYING, BUG, ICE),
            FLYING to setOf(GRASS, BUG, FIGHTING),
            POISON to setOf(GRASS),
            BUG to setOf(GRASS, PSYCHIC),
            GROUND to setOf(FIRE, ELECTRIC, ROCK, POISON),
            FIGHTING to setOf(NORMAL, ROCK, ICE),
            PSYCHIC to setOf(POISON, FIGHTING),
            ICE to setOf(GRASS, FLYING, GROUND, DRAGON),
            GHOST to setOf(PSYCHIC, GHOST),
            DRAGON to setOf(DRAGON)
        )

        val weakAgainst: Map<PokemonType, Set<PokemonType>> = mapOf(
            NORMAL to setOf(ROCK, GHOST),
            FIRE to setOf(FIRE, WATER, ROCK, DRAGON),
            WATER to setOf(WATER, GRASS, DRAGON),
            GRASS to setOf(FIRE, GRASS, FLYING, POISON, BUG, DRAGON),
            ELECTRIC to setOf(GRASS, ELECTRIC, GROUND, DRAGON),
            ROCK to setOf(GROUND, FIGHTING),
            FLYING to setOf(ELECTRIC, ROCK),
            POISON to setOf(ROCK, POISON, GROUND, GHOST),
            BUG to setOf(FIRE, FLYING, POISON, FIGHTING, GHOST),
            GROUND to setOf(GRASS, FLYING, BUG),
            FIGHTING to setOf(FLYING, POISON, BUG, PSYCHIC, GHOST),
            PSYCHIC to setOf(PSYCHIC),
            ICE to setOf(FIRE, WATER, ICE),
            GHOST to setOf(NORMAL),
            DRAGON to emptySet()
        )
    }
}

/**
 * Multiplicador de daño del ataque según los tipos.
 *
 * El tipo del principal se compara con el del gimnasio: súper eficaz → ×2
 * (`effectiveness` = 2f), poco eficaz → ×0.5, neutro o sin principal → ×1.
 */
fun typeDamageMultiplier(leadType: PokemonType?, gymType: PokemonType): Float =
    leadType?.effectiveness(gymType) ?: 1f

/**
 * Mecánica de defensa de un gimnasio. Cada líder ataca a su manera, pero todas
 * comparten las mismas fases ([GymPhase]) y la fase expuesta.
 *
 * - [PROJECTILES]: caen rocas por columnas y hay que romperlas (Brock).
 * - [TIMING_RINGS]: círculos que se cierran sobre el jugador y hay que aciertar
 *   el instante en que tocan el círculo fijo (Misty).
 * - [VOLTORB_GRID]: una cuadrícula de Voltorb que se cargan de a uno y hay que
 *   apagar antes de que exploten (Lt. Surge).
 */
enum class GymMechanic {
    PROJECTILES,
    TIMING_RINGS,
    VOLTORB_GRID
}

/**
 * Un gimnasio: el muro de cada tramo del mapa.
 *
 * Los parámetros del combate se guardan como datos para que añadir gimnasios sea
 * añadir entradas al catálogo, sin código nuevo.
 *
 * Los campos `columns`, `spawnIntervalMs`, `projectileTravelMs`,
 * `projectileDamage`, `projectilesPerSpawn` y sus variantes `rage*` solo se usan
 * con [GymMechanic.PROJECTILES]. Los campos `ring*` solo se usan con
 * [GymMechanic.TIMING_RINGS]. Los campos `rows` y `voltorb*` solo se usan con
 * [GymMechanic.VOLTORB_GRID].
 *
 * @param id clave estable para medallas y persistencia.
 * @param leaderName nombre del líder, mostrado en el overlay.
 * @param type tipo del gimnasio; se compara con el del Pokémon principal para el
 *   multiplicador de daño en la fase expuesta.
 * @param coinReward monedas que paga al ganar.
 * @param leaderHp vida del líder (cuánto daño hay que hacerle para ganar).
 * @param mechanic minijuego de defensa que usa el líder.
 * @param columns número de columnas por las que caen los proyectiles.
 * @param spawnIntervalMs tiempo entre ráfagas de proyectiles, en ms.
 * @param projectileTravelMs tiempo que tarda un proyectil en caer de arriba a
 *   abajo, en ms.
 * @param projectileDamage HP que resta cada proyectil que impacta en el jugador.
 * @param postureHits aciertos seguidos que hacen falta para **romper la postura**
 *   del líder y abrir su ventana de vulnerabilidad. Cada acierto de la mecánica
 *   suma uno; los fallos no suman y además restan HP al jugador.
 * @param exposureDurationMs duración de la fase expuesta, en ms. Es la ventana
 *   para castigar al líder y sigue funcionando **por tiempo**.
 * @param projectilesPerSpawn máximo de proyectiles por ráfaga (la ráfaga real
 *   elige al azar entre 1 y este valor).
 * @param rageSpawnIntervalMs cadencia de las ráfagas durante la furia (por
 *   debajo del umbral [RAGE_HP_FRACTION]).
 * @param rageProjectilesPerSpawn proyectiles por ráfaga durante la furia.
 * @param rageProjectileTravelMs caída (más rápida) durante la furia.
 * @param ringSpawnIntervalMs tiempo entre anillos durante la oleada, en ms.
 * @param ringTravelMinMs cierre más rápido de un anillo, en ms.
 * @param ringTravelMaxMs cierre más lento de un anillo, en ms. Cada anillo elige
 *   un valor entre ambos: de ahí sale la cadencia distinta de cada círculo.
 * @param ringWindowMs margen de acierto alrededor de la convergencia, en ms.
 * @param ringMissDamage HP que resta cada anillo fallado (pulsado fuera de la
 *   ventana o dejado caducar).
 * @param rageRingSpawnIntervalMs cadencia de anillos durante la furia.
 * @param rageRingTravelMinMs cierre más rápido durante la furia.
 * @param rageRingTravelMaxMs cierre más lento durante la furia.
 * @param rows número de filas de la cuadrícula de Voltorb. Con `columns` define
 *   cuántas celdas tiene el tablero (`rows * columns`).
 * @param voltorbWindowMs tiempo que tarda en explotar un Voltorb cargado, en ms.
 * @param rageVoltorbWindowMs ventana (más corta) durante la furia.
 * @param voltorbSpawnIntervalMs tiempo **mínimo** entre el inicio de una carga y
 *   el de la siguiente, en ms. Si el tablero está ocupado, la carga pendiente
 *   entra en cuanto se libera la celda.
 * @param rageVoltorbSpawnIntervalMs margen mínimo durante la furia.
 * @param voltorbExplosionDamage HP que resta un Voltorb que explota.
 * @param voltorbShockDamage HP que resta tocar una celda apagada.
 */
data class Gym(
    val id: String,
    val leaderName: String,
    val type: PokemonType,
    val coinReward: Long,
    val leaderHp: Int,
    val mechanic: GymMechanic = GymMechanic.PROJECTILES,
    val columns: Int,
    val spawnIntervalMs: Long,
    val projectileTravelMs: Long,
    val projectileDamage: Int,
    val postureHits: Int,
    val exposureDurationMs: Long,
    val projectilesPerSpawn: Int = 1,
    val rageSpawnIntervalMs: Long = spawnIntervalMs,
    val rageProjectilesPerSpawn: Int = projectilesPerSpawn,
    val rageProjectileTravelMs: Long = projectileTravelMs,
    val ringSpawnIntervalMs: Long = 750L,
    val ringTravelMinMs: Long = 900L,
    val ringTravelMaxMs: Long = 1_600L,
    val ringWindowMs: Long = 170L,
    val ringMissDamage: Int = 1,
    val rageRingSpawnIntervalMs: Long = ringSpawnIntervalMs,
    val rageRingTravelMinMs: Long = ringTravelMinMs,
    val rageRingTravelMaxMs: Long = ringTravelMaxMs,
    val rows: Int = 1,
    val voltorbWindowMs: Long = 1_400L,
    val rageVoltorbWindowMs: Long = 850L,
    val voltorbSpawnIntervalMs: Long = 900L,
    val rageVoltorbSpawnIntervalMs: Long = 600L,
    val voltorbExplosionDamage: Int = 1,
    val voltorbShockDamage: Int = 1
)

/**
 * Fase del combate de gimnasio.
 *
 * Las dos fases son **mutuamente excluyentes**: mientras el líder ataca no se le
 * puede hacer daño, y mientras está expuesto no lanza ataques. Eso evita que
 * el jugador pueda atacar y defenderse a la vez (el antiguo exploit de dos manos).
 *
 * - [ATTACKING]: el líder ataca con su mecánica; cada acierto del jugador llena
 *   la barra de **postura** y cada fallo le resta HP. Al llenarla, la postura se
 *   rompe y el líder pasa a [EXPOSED].
 * - [EXPOSED]: sin ataques enemigos; tocar al líder le resta vida mientras se
 *   recupera. La ventana sigue funcionando **por tiempo**.
 */
enum class GymPhase { ATTACKING, EXPOSED }

/**
 * Resultado global del intento de gimnasio.
 *
 * [WON] no cierra la partida por sí solo: deja el combate detenido para que la
 * capa de victoria muestre la despedida del líder y solo entonces se cobre la
 * medalla. [LOST] mantiene el panel de derrota de siempre.
 */
enum class GymOutcome { ACTIVE, LOST, WON }

/**
 * Un proyectil del combate de gimnasio (estado **transitorio**).
 *
 * No guarda su posición: se deriva del tiempo transcurrido desde [spawnedAtMs],
 * así que no hace falta recalcularla en cada fotograma. La vista la interpola y
 * el ViewModel la usa para detectar aciertos e impactos.
 *
 * @param id identificador único dentro del combate.
 * @param column columna (0..columns-1) por la que cae.
 * @param spawnedAtMs instante de aparición, en el reloj del juego.
 * @param travelMs tiempo total de caída de arriba a abajo, en ms.
 * @param hitsRemaining toques que le faltan para romperse (hoy 1; reservado para
 *   proyectiles que aguantan dos toques).
 */
data class Projectile(
    val id: Long,
    val column: Int,
    val spawnedAtMs: Long,
    val travelMs: Long,
    val hitsRemaining: Int = 1
) {
    /** Progreso de la caída, `0f` (arriba) a `1f` (alcanza al jugador). */
    fun progressAt(nowMs: Long): Float {
        if (travelMs <= 0L) return 1f
        return ((nowMs - spawnedAtMs).toFloat() / travelMs).coerceIn(0f, 1f)
    }

    /** ¿Ha llegado ya al jugador? */
    fun hasLanded(nowMs: Long): Boolean = nowMs - spawnedAtMs >= travelMs
}

/**
 * Geometría de los carriles de rocas, en píxeles de pantalla.
 *
 * La mide la capa de UI, que es su layout; la regla de acierto la aplica el
 * modelo. Se pasa como datos planos para que el modelo no dependa de Compose.
 */
data class ProjectileLaneGeometry(
    val laneLeft: Float,
    val cellWidth: Float,
    val spawnY: Float,
    val impactY: Float,
    val hitRadius: Float
)

/** Roca golpeada y el punto donde estaba, para que la UI pinte el estallido. */
data class ProjectileHit(
    val projectile: Projectile,
    val centerX: Float,
    val centerY: Float
)

/**
 * Un anillo del minijuego de ritmo ([GymMechanic.TIMING_RINGS]).
 *
 * Nace en [spawnedAtMs] con radio grande y se cierra hasta tocar el círculo fijo
 * del jugador en [convergeAtMs]. Su posición no se guarda: se deriva del tiempo
 * transcurrido, igual que [Projectile], para que la UI lo interpole sin que el
 * ViewModel actualice cada fotograma.
 *
 * @param id identificador único dentro del combate.
 * @param spawnedAtMs instante de aparición, en el reloj del juego.
 * @param travelMs tiempo que tarda en converger con el círculo fijo, en ms. Es
 *   la cadencia del anillo: dos anillos con distinto [travelMs] se cierran a
 *   velocidades distintas.
 */
data class TimingRing(
    val id: Long,
    val spawnedAtMs: Long,
    val travelMs: Long
) {
    /** Progreso del cierre, `0f` (recién nacido) a `1f` (converge con el fijo). */
    fun progressAt(nowMs: Long): Float {
        if (travelMs <= 0L) return 1f
        return ((nowMs - spawnedAtMs).toFloat() / travelMs).coerceIn(0f, 1f)
    }

    /** Instante exacto en el que el anillo toca el círculo fijo. */
    fun convergeAtMs(): Long = spawnedAtMs + travelMs

    /** Distancia temporal (siempre positiva) hasta la convergencia. */
    fun distanceToConvergenceMs(nowMs: Long): Long =
        kotlin.math.abs(nowMs - convergeAtMs())

    /** ¿Cae [nowMs] dentro del margen de acierto [windowMs]? */
    fun isInWindow(nowMs: Long, windowMs: Long): Boolean =
        distanceToConvergenceMs(nowMs) <= windowMs

    /** ¿Pasó ya su ventana de acierto sin resolverse? */
    fun hasExpired(nowMs: Long, windowMs: Long): Boolean =
        nowMs - convergeAtMs() > windowMs
}

/** Resultado de un anillo del minijuego de ritmo. */
enum class RingOutcome {
    HIT,
    MISS
}

/**
 * Feedback visual del último anillo resuelto (estado **transitorio**).
 *
 * El modelo decide si el anillo se acertó o falló; la UI solo necesita saber qué
 * ocurrió y cuándo para lanzar su animación. [ringId] actúa de clave: cada anillo
 * produce como máximo un resultado, así que un id nuevo reinicia el efecto.
 *
 * @param ringId anillo que produjo el resultado.
 * @param outcome acierto o fallo.
 * @param count anillos cobrados en el mismo evento; solo puede ser mayor que 1
 *   cuando varios caducan en el mismo tick.
 * @param ringProgress avance del anillo al resolverse, `0` recién nacido y `1`
 *   cuando ya tocó el círculo fijo. La UI lo necesita para arrancar el efecto
 *   justo en el radio en el que estaba el anillo: un acierto dentro de la ventana
 *   todavía no ha llegado del todo, y un fallo por pulsar antes lo coge más lejos.
 * @param combo aciertos seguidos que lleva el jugador **después** de este evento:
 *   sube con cada acierto y vuelve a `0` con cualquier fallo. Es lo que la UI
 *   cuenta como combo.
 */
data class RingFeedback(
    val ringId: Long,
    val outcome: RingOutcome,
    val count: Int = 1,
    val ringProgress: Float = 1f,
    val combo: Int = 0
)

/**
 * Un Voltorb cargado de la cuadrícula ([GymMechanic.VOLTORB_GRID]).
 *
 * Igual que [Projectile] y [TimingRing], no guarda animación: su "calor" se
 * deriva del tiempo transcurrido desde [chargedAtMs], así que la vista lo pinta
 * sin que el ViewModel actualice cada fotograma.
 *
 * @param id identificador único dentro del combate.
 * @param cell celda de la cuadrícula, de `0` a `rows * columns - 1`, en orden de
 *   filas.
 * @param chargedAtMs instante en que empezó a cargarse.
 * @param windowMs tiempo que tarda en explotar desde [chargedAtMs], en ms. Cada
 *   Voltorb nace con la ventana de su oleada, así un cambio de furia a mitad de
 *   vuelo no alarga ni acorta la que ya estaba corriendo.
 */
data class Voltorb(
    val id: Long,
    val cell: Int,
    val chargedAtMs: Long,
    val windowMs: Long
) {
    /** Calor acumulado, `0f` (recién cargado) a `1f` (a punto de explotar). */
    fun progressAt(nowMs: Long): Float {
        if (windowMs <= 0L) return 1f
        return ((nowMs - chargedAtMs).toFloat() / windowMs).coerceIn(0f, 1f)
    }

    /** ¿Se le agotó la ventana? */
    fun hasExploded(nowMs: Long): Boolean = nowMs - chargedAtMs >= windowMs
}

/** Resultado de un Voltorb de la cuadrícula. */
enum class VoltorbOutcome {
    /** Apagado a tiempo: no hubo daño. */
    DEFUSED,

    /** Se agotó su ventana y explotó. */
    EXPLODED,

    /** Tocado cuando su celda estaba apagada: descarga. */
    SHOCK
}

/**
 * Feedback visual del último Voltorb resuelto (estado **transitorio**).
 *
 * @param cell celda principal del resultado; en una explosión múltiple es la de
 *   mayor índice, pero la UI debe usar [cells] para pintar todas.
 * @param outcome apagado, explosión o descarga.
 * @param count Voltorb cobrados en el mismo tick; solo puede ser mayor que 1
 *   cuando varios explotan a la vez.
 * @param cells todas las celdas que produjeron el resultado. Un solo apagado o
 *   una descarga traen una; varias explosiones en el mismo tick traen todas, de
 *   modo que la escena puede pintar una explosión por celda.
 */
data class VoltorbFeedback(
    val cell: Int,
    val outcome: VoltorbOutcome,
    val count: Int = 1,
    val cells: List<Int> = listOf(cell)
)

/**
 * Estado **transitorio** del combate de gimnasio (no se persiste).
 *
 * Incluye las fracciones de barra ya calculadas para que la UI no haga cuentas
 * de negocio: solo lee `leaderHpFraction`, `playerHpFraction` y
 * `postureFraction`.
 *
 * @param leaderHp vida restante del líder; llega a 0 → victoria.
 * @param leaderMaxHp vida inicial, para la barra (y el umbral de furia).
 * @param playerHp HP restante del jugador; llega a 0 → derrota.
 * @param maxHp HP máximo del jugador.
 * @param mechanic minijuego de defensa activo; decide qué pinta la escena y qué
 *   eventos acepta.
 * @param columns columnas por las que caen los proyectiles.
 * @param rows filas de la cuadrícula de Voltorb.
 * @param projectiles proyectiles activos en este instante.
 * @param rings anillos activos del minijuego de ritmo.
 * @param ringWindowMs margen de acierto de los anillos, en ms. La UI lo usa para
 *   resaltar el anillo que está en su momento exacto.
 * @param ringFeedback último acierto o fallo, para la animación de la escena.
 * @param voltorbs Voltorb cargados en este instante.
 * @param voltorbFeedback último apagado, explosión o descarga, para la animación
 *   de la escena.
 * @param posture aciertos acumulados en la ronda actual (0..[postureMax]).
 *   Al llegar a [postureMax] el líder queda [GymPhase.EXPOSED].
 * @param postureMax aciertos necesarios para romper la postura del líder.
 * @param exposureDurationMs duración total de la fase expuesta, en ms.
 * @param phase fase actual del combate.
 * @param exposureRemainingMs tiempo que le queda al líder para volver a atacar.
 * @param rage el líder está por debajo del 25 %: oleadas más densas y rápidas.
 * @param rageRoundPlayed ya se jugó una ronda completa de furia; hasta entonces
 *   el líder no puede morir, para que la furia nunca se pueda saltar.
 * @param rageSpeech el líder está dando su discurso de furia: el combate queda
 *   **en pausa** (el bucle no avanza) hasta que el jugador lo cierra.
 * @param leadSpeciesId especie del Pokémon principal, para elegir su sprite.
 * @param leadName nombre visible del principal (placeholder si no hay arte).
 * @param leadType tipo del principal; decide el multiplicador y el color del
 *   destello de ataque.
 * @param outcome resultado global del intento.
 */
data class GymState(
    val gymId: String,
    val leaderName: String,
    val gymType: PokemonType,
    val leaderHp: Int,
    val leaderMaxHp: Int,
    val playerHp: Int,
    val maxHp: Int,
    val mechanic: GymMechanic = GymMechanic.PROJECTILES,
    val columns: Int,
    val rows: Int = 1,
    val projectiles: List<Projectile> = emptyList(),
    val rings: List<TimingRing> = emptyList(),
    val ringWindowMs: Long = 0L,
    val ringFeedback: RingFeedback? = null,
    /** Aciertos seguidos del minijuego de anillos; cualquier fallo lo devuelve a 0. */
    val ringCombo: Int = 0,
    val voltorbs: List<Voltorb> = emptyList(),
    val voltorbFeedback: VoltorbFeedback? = null,
    val posture: Int = 0,
    val postureMax: Int = 0,
    val exposureDurationMs: Long = 0L,
    val phase: GymPhase = GymPhase.ATTACKING,
    val exposureRemainingMs: Long = 0L,
    val rage: Boolean = false,
    val rageRoundPlayed: Boolean = false,
    val rageSpeech: Boolean = false,
    val leadSpeciesId: String? = null,
    val leadName: String? = null,
    val leadType: PokemonType? = null,
    val outcome: GymOutcome = GymOutcome.ACTIVE
) {
    /** Fracción 0f..1f de vida del líder, lista para su barra. */
    val leaderHpFraction: Float
        get() = if (leaderMaxHp <= 0) 0f
        else (leaderHp.toFloat() / leaderMaxHp).coerceIn(0f, 1f)

    /** Fracción 0f..1f de vida del jugador, lista para su barra. */
    val playerHpFraction: Float
        get() = if (maxHp <= 0) 0f
        else (playerHp.toFloat() / maxHp).coerceIn(0f, 1f)

    /**
     * Fracción de la barra de **postura**. Mientras el líder ataca crece con
     * cada acierto (rompe la postura al llenarse); en fase expuesta se rellena
     * con el tiempo de recuperación que queda.
     */
    val postureFraction: Float
        get() = when {
            phase == GymPhase.EXPOSED && exposureDurationMs > 0L ->
                (1f - exposureRemainingMs.toFloat() / exposureDurationMs).coerceIn(0f, 1f)
            postureMax <= 0 -> 0f
            else -> (posture.toFloat() / postureMax).coerceIn(0f, 1f)
        }
}

/**
 * Cierra el combate como derrota si el jugador se ha quedado sin vida.
 *
 * Vive en el modelo porque es una regla pura: la usan tanto el motor del bucle
 * como los manejadores de eventos del ViewModel.
 */
internal fun GymState.withDefeatIfPlayerDown(): GymState =
    if (playerHp <= 0) copy(outcome = GymOutcome.LOST) else this

/**
 * Reglas puras del combate de gimnasio, sin reloj ni corrutinas.
 *
 * Están separadas del ViewModel para poder probarlas en la JVM: reciben un
 * [GymState] y devuelven el siguiente. El ViewModel se encarga de los tiempos, de
 * crear proyectiles y de persistir la victoria.
 */
object GymCombat {

    /**
     * Cuánto varía la caída de cada roca respecto de la duración del gimnasio.
     * Es lo que rompe la fila: dos rocas de la misma ráfaga no bajan en línea.
     */
    private const val PROJECTILE_TRAVEL_JITTER = 0.2

    /**
     * Arranque adelantado máximo de una roca, como fracción de su propia caída:
     * nace algo más abajo, así la ráfaga no aparece alineada en el borde. Se
     * mantiene bajo para que la roca no "aparezca" de golpe a media caída.
     */
    private const val PROJECTILE_HEAD_START = 0.1

    /**
     * ¿El líder está en furia? Debe quedar **estrictamente** por debajo del 25 %
     * de su vida máxima: al 25 % exacto todavía no entra.
     */
    fun isRaging(leaderHp: Int, leaderMaxHp: Int): Boolean =
        leaderMaxHp > 0 && leaderHp.toFloat() / leaderMaxHp < RAGE_HP_FRACTION

    /** Variante que lee la vida directamente del estado del combate. */
    fun isRaging(state: GymState): Boolean = isRaging(state.leaderHp, state.leaderMaxHp)

    /**
     * Primera vida (incluida) en la que el líder está en furia, es decir, el
     * mayor HP que cumple `hp / max < 25 %`. Es el valor al que se clava la vida
     * cuando el primer golpe intenta cruzar el umbral.
     */
    fun rageThresholdHp(leaderMaxHp: Int): Int {
        if (leaderMaxHp <= 0) return 0
        return (ceil(leaderMaxHp * RAGE_HP_FRACTION).toInt() - 1).coerceAtLeast(0)
    }

    /**
     * Puerta de la **furia obligatoria**.
     *
     * Si el golpe dejaría al líder por debajo del umbral de furia antes de haber
     * jugado una ronda completa, devuelve el estado con la vida clavada en
     * [rageThresholdHp], la furia activada y una ronda de ataque nueva: así el
     * jugador no puede matarlo ni encadenar la ventana actual para saltarse la
     * furia. Devuelve `null` si no hay nada que frenar.
     */
    fun gateFirstRage(state: GymState, newLeaderHp: Int): GymState? {
        if (state.rageRoundPlayed) return null
        val threshold = rageThresholdHp(state.leaderMaxHp)
        if (threshold <= 0 || newLeaderHp >= threshold) return null

        return state.copy(
            leaderHp = threshold,
            rage = true,
            // El combate se detiene para el discurso del líder: la furia se
            // presenta antes de que caiga la primera oleada.
            rageSpeech = true,
            phase = GymPhase.ATTACKING,
            posture = 0,
            exposureRemainingMs = 0L,
            projectiles = emptyList(),
            rings = emptyList(),
            voltorbs = emptyList()
        )
    }

    /**
     * Acierto de la mecánica: suma un punto a la postura del líder.
     *
     * Cada acierto (romper una roca, apagar un Voltorb, acertar un anillo) llama
     * aquí. Al alcanzar [GymState.postureMax] la postura se **rompe**: el líder
     * pasa a [GymPhase.EXPOSED], se abre su ventana por tiempo y se limpian los
     * ataques en curso. Si la postura se rompe durante la furia, además queda
     * jugada la ronda obligatoria ([GymState.rageRoundPlayed]). Los fallos nunca
     * llegan aquí: restan HP y no suman.
     */
    fun addPosture(state: GymState, hits: Int = 1): GymState {
        if (state.phase != GymPhase.ATTACKING) return state
        if (hits <= 0 || state.postureMax <= 0) return state

        val posture = (state.posture + hits).coerceAtMost(state.postureMax)
        val rage = isRaging(state)
        return if (posture >= state.postureMax) {
            state.copy(
                posture = state.postureMax,
                phase = GymPhase.EXPOSED,
                exposureRemainingMs = state.exposureDurationMs,
                projectiles = emptyList(),
                rings = emptyList(),
                voltorbs = emptyList(),
                rage = rage,
                // Romper la postura durante la furia cierra la ronda obligatoria:
                // desde aquí el líder ya puede morir.
                rageRoundPlayed = state.rageRoundPlayed || rage
            )
        } else {
            state.copy(posture = posture, rage = rage)
        }
    }

    /**
     * Fallo del jugador: **resta postura**.
     *
     * Un clic fallado deshace parte del trabajo de los aciertos, que es lo que
     * impide llenar la barra a base de toques a ciegas. Se aplica en cada
     * mecánica cuando el toque no acierta: la roca que no estaba, el anillo que
     * no llegó o el Voltorb apagado. Nunca baja de cero y no actúa fuera de
     * [GymPhase.ATTACKING] (durante la ventana expuesta la barra ya corre por
     * tiempo).
     */
    fun failPosture(state: GymState, hits: Int = 1): GymState {
        if (state.phase != GymPhase.ATTACKING) return state
        if (hits <= 0 || state.posture <= 0) return state
        return state.copy(posture = (state.posture - hits).coerceAtLeast(0))
    }

    /**
     * Destruye el proyectil tocado (o le resta un toque si le quedaban varios).
     *
     * Romper rocas **es un acierto**: suma postura. Si el proyectil ya no existe
     * o no estamos en fase de ataque, no cambia nada.
     */
    /**
     * Roca que golpea un toque en ([tapX], [tapY]) sobre [lanes] a [nowMs].
     *
     * Se mide **dónde estaba la roca al apoyar el dedo**: al caer se movería y el
     * toque "se escaparía". Gana la más cercana dentro del radio; un toque fuera
     * de todos los radios es un fallo (`null`).
     */
    fun hitProjectile(
        state: GymState,
        tapX: Float,
        tapY: Float,
        lanes: ProjectileLaneGeometry,
        nowMs: Long
    ): ProjectileHit? = state.projectiles
        .map { projectile ->
            val centerX = lanes.laneLeft + lanes.cellWidth * (projectile.column + 0.5f)
            val centerY =
                lanes.spawnY + (lanes.impactY - lanes.spawnY) * projectile.progressAt(nowMs)
            val dx = tapX - centerX
            val dy = tapY - centerY
            ProjectileHit(projectile, centerX, centerY) to (dx * dx + dy * dy)
        }
        .filter { (_, distanceSq) -> distanceSq <= lanes.hitRadius * lanes.hitRadius }
        .minByOrNull { (_, distanceSq) -> distanceSq }
        ?.first

    fun breakProjectile(state: GymState, projectileId: Long): GymState {
        if (state.phase != GymPhase.ATTACKING) return state

        val projectile = state.projectiles.firstOrNull { it.id == projectileId }
            ?: return state

        val projectiles = if (projectile.hitsRemaining > 1) {
            state.projectiles.map {
                if (it.id == projectileId) it.copy(hitsRemaining = it.hitsRemaining - 1) else it
            }
        } else {
            state.projectiles.filterNot { it.id == projectileId }
        }
        return addPosture(state.copy(projectiles = projectiles))
    }

    /**
     * Ráfaga de rocas: una por columna distinta, sin filas.
     *
     * Antes todas las rocas de una ráfaga compartían duración y arranque, así que
     * bajaban alineadas como una fila. Ahora cada una lleva su propia caída: la
     * duración varía ±[PROJECTILE_TRAVEL_JITTER] y arranca algo más abajo
     * ([PROJECTILE_HEAD_START] de su recorrido), de modo que la ráfaga se abre en
     * cuanto aparece.
     */
    fun spawnSalvo(
        firstId: Long,
        columns: Int,
        maxPerSpawn: Int,
        travelMs: Long,
        nowMs: Long,
        random: Random
    ): List<Projectile> {
        if (columns <= 0 || maxPerSpawn <= 0) return emptyList()

        val salvoSize = random.nextInt(1, maxPerSpawn + 1).coerceAtMost(columns)
        val chosen = (0 until columns).shuffled(random).take(salvoSize)

        return chosen.mapIndexed { index, column ->
            val travel = (travelMs * random.nextDouble(
                1.0 - PROJECTILE_TRAVEL_JITTER,
                1.0 + PROJECTILE_TRAVEL_JITTER
            )).toLong().coerceAtLeast(1L)
            val headStart = (travel * random.nextDouble(0.0, PROJECTILE_HEAD_START)).toLong()
            Projectile(
                id = firstId + index,
                column = column,
                spawnedAtMs = nowMs - headStart,
                travelMs = travel
            )
        }
    }

    /**
     * Registra uno o varios ataques que han impactado al jugador.
     *
     * Resta [damage] HP y **no** suma postura: un fallo nunca acerca la ventana
     * del líder. No actúa fuera de la fase de ataque.
     */
    fun registerImpact(state: GymState, damage: Int): GymState {
        if (state.phase != GymPhase.ATTACKING) return state

        return state.copy(playerHp = (state.playerHp - damage).coerceAtLeast(0))
    }

    /**
     * Anillo que el jugador debería pulsar ahora: el más cercano a su
     * convergencia. Con varios anillos en pantalla, la pulsación se juzga contra
     * este, nunca contra uno que aún está lejos.
     */
    fun nearestRing(state: GymState, nowMs: Long): TimingRing? =
        state.rings.minByOrNull { it.distanceToConvergenceMs(nowMs) }

    /** ¿La pulsación sobre [ring] cae dentro del margen de acierto del combate? */
    fun isRingOnBeat(ring: TimingRing, state: GymState, nowMs: Long): Boolean =
        ring.isInWindow(nowMs, state.ringWindowMs)

    /** Anillos que ya pasaron su ventana de acierto sin resolverse. */
    fun expiredRings(state: GymState, nowMs: Long): List<TimingRing> =
        state.rings.filter { it.hasExpired(nowMs, state.ringWindowMs) }

    /**
     * Resuelve [rings]: los saca de la lista, mueve el **combo** y publica el
     * feedback que pinta la UI.
     *
     * Es el único sitio que toca [GymState.ringCombo] a propósito: acertar suma
     * uno y fallar lo deja en cero, venga el fallo de una pulsación fuera de
     * ventana o de anillos que caducaron solos. Tenerlo aquí evita que un camino
     * nuevo se olvide de cortar la racha.
     *
     * @param hit `true` si la jugada acertó.
     * @param rings anillos que se cobran en este evento; suele ser uno, pero
     *   varios pueden caducar en el mismo tick.
     */
    fun resolveRings(state: GymState, rings: List<TimingRing>, hit: Boolean, nowMs: Long): GymState {
        if (rings.isEmpty()) return state
        val ids = rings.map { it.id }.toSet()
        val combo = if (hit) state.ringCombo + 1 else 0
        return state.copy(
            rings = state.rings.filterNot { it.id in ids },
            ringCombo = combo,
            ringFeedback = RingFeedback(
                ringId = rings.maxOf { it.id },
                outcome = if (hit) RingOutcome.HIT else RingOutcome.MISS,
                count = rings.size,
                ringProgress = rings.maxOf { it.progressAt(nowMs) },
                combo = combo
            )
        )
    }

    // --- cuadrícula de Voltorb (Lt. Surge) ---------------------------------

    /** Voltorb de [cell], o `null` si esa celda está apagada. */
    fun voltorbAt(state: GymState, cell: Int): Voltorb? =
        state.voltorbs.firstOrNull { it.cell == cell }

    /** Celdas del tablero que no tienen un Voltorb cargado. */
    fun freeVoltorbCells(state: GymState, totalCells: Int): List<Int> =
        (0 until totalCells).filter { cell -> state.voltorbs.none { it.cell == cell } }

    /**
     * Apaga el Voltorb de [cell] si estaba cargado: acierto sin daño que suma
     * postura.
     *
     * Tocar una celda apagada no se resuelve aquí porque la descarga necesita el
     * daño del gimnasio; eso lo hace [registerShock].
     */
    fun defuseVoltorb(state: GymState, cell: Int): GymState {
        if (state.phase != GymPhase.ATTACKING) return state

        val voltorb = voltorbAt(state, cell) ?: return state
        val defused = state.copy(
            voltorbs = state.voltorbs.filterNot { it.id == voltorb.id },
            voltorbFeedback = VoltorbFeedback(cell, VoltorbOutcome.DEFUSED)
        )
        return addPosture(defused)
    }

    /** Descarga por tocar una celda apagada: resta [damage] HP. */
    fun registerShock(state: GymState, cell: Int, damage: Int): GymState {
        if (state.phase != GymPhase.ATTACKING) return state

        return state.copy(
            playerHp = (state.playerHp - damage).coerceAtLeast(0),
            voltorbFeedback = VoltorbFeedback(cell, VoltorbOutcome.SHOCK)
        )
    }

    /** Voltorb a los que ya se les agotó la ventana. */
    fun explodedVoltorbs(state: GymState, nowMs: Long): List<Voltorb> =
        state.voltorbs.filter { it.hasExploded(nowMs) }

    /**
     * Cobra los Voltorb que explotaron antes de [nowMs]: los retira del tablero y
     * resta [damage] HP por cada uno. Sin explosiones no cambia nada.
     */
    fun explodeVoltorbs(state: GymState, nowMs: Long, damage: Int): GymState {
        if (state.phase != GymPhase.ATTACKING) return state

        val exploded = explodedVoltorbs(state, nowMs)
        if (exploded.isEmpty()) return state

        val ids = exploded.map { it.id }.toSet()
        return state.copy(
            voltorbs = state.voltorbs.filterNot { it.id in ids },
            playerHp = (state.playerHp - exploded.size * damage).coerceAtLeast(0),
            voltorbFeedback = VoltorbFeedback(
                cell = exploded.maxOf { it.cell },
                outcome = VoltorbOutcome.EXPLODED,
                count = exploded.size,
                cells = exploded.map { it.cell }
            )
        )
    }

    /**
     * Avanza la recuperación durante la fase expuesta.
     *
     * Cuando se agota el tiempo vuelve a [GymPhase.ATTACKING] con la postura a
     * cero. El ViewModel reinicia entonces el temporizador de aparición para dar
     * un respiro antes del siguiente ataque.
     */
    fun advanceExposure(state: GymState, elapsedMs: Long): GymState {
        if (state.phase != GymPhase.EXPOSED) return state

        val rage = isRaging(state)
        val remaining = state.exposureRemainingMs - elapsedMs
        return if (remaining <= 0L) {
            state.copy(
                phase = GymPhase.ATTACKING,
                posture = 0,
                exposureRemainingMs = 0L,
                rage = rage
            )
        } else {
            state.copy(exposureRemainingMs = remaining, rage = rage)
        }
    }
}
