package com.dsrs.pokeclic.model

/**
 * Modelo del combate: tipos elementales, gimnasios, fases, proyectiles y las
 * reglas puras del combate.
 *
 * Todo aquí es independiente de Android y del reloj real (las funciones reciben
 * el tiempo transcurrido), para poder probar la mecánica en la JVM. El
 * ViewModel se encarga de los bucles, de crear proyectiles y de persistir la
 * victoria; la UI solo pinta el estado ya calculado.
 */

/** Menos del 25 % de vida: el líder entra en furia y sus oleadas son más duras. */
const val RAGE_HP_FRACTION = 0.25f

/**
 * Tipos elementales usados por el juego.
 *
 * Es un subconjunto de los tipos de la primera generación: solo los que
 * aparecen en el contenido planificado. Añadir un tipo es añadir una constante y
 * sus entradas en las tablas de efectividad.
 */
enum class PokemonType {
    NORMAL,
    FIRE,
    WATER,
    GRASS,
    ELECTRIC,
    ROCK,
    FLYING,
    POISON;

    /**
     * Efectividad de este tipo (atacante) contra [defender].
     *
     * 2f = súper eficaz (ventaja para el atacante), 0.5f = poco eficaz
     * (desventaja) y 1f = neutro. La tabla es una simplificación del juego
     * original, suficiente para el minijuego de gimnasio y la captura.
     */
    fun effectiveness(defender: PokemonType): Float = when {
        defender in strongAgainst.getValue(this) -> 2f
        defender in weakAgainst.getValue(this) -> 0.5f
        else -> 1f
    }

    private companion object {
        val strongAgainst: Map<PokemonType, Set<PokemonType>> = mapOf(
            NORMAL to emptySet(),
            FIRE to setOf(GRASS),
            WATER to setOf(FIRE, ROCK),
            GRASS to setOf(WATER, ROCK),
            ELECTRIC to setOf(WATER, FLYING),
            ROCK to setOf(FIRE, FLYING),
            FLYING to setOf(GRASS),
            POISON to setOf(GRASS)
        )

        val weakAgainst: Map<PokemonType, Set<PokemonType>> = mapOf(
            NORMAL to setOf(ROCK),
            FIRE to setOf(WATER, ROCK),
            WATER to setOf(GRASS, ELECTRIC),
            GRASS to setOf(FIRE, FLYING, POISON),
            ELECTRIC to setOf(GRASS),
            ROCK to setOf(WATER, GRASS),
            FLYING to setOf(ELECTRIC, ROCK),
            POISON to setOf(ROCK)
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
 * Un gimnasio: el muro de cada tramo del mapa.
 *
 * Los parámetros del combate se guardan como datos para que añadir gimnasios sea
 * añadir entradas al catálogo, sin código nuevo.
 *
 * @param id clave estable para medallas y persistencia.
 * @param leaderName nombre del líder, mostrado en el overlay.
 * @param type tipo del gimnasio; se compara con el del Pokémon principal para el
 *   multiplicador de daño en la fase expuesta.
 * @param coinReward monedas que paga al ganar.
 * @param leaderHp vida del líder (cuánto daño hay que hacerle para ganar).
 * @param columns número de columnas por las que caen los proyectiles.
 * @param spawnIntervalMs tiempo entre ráfagas de proyectiles, en ms.
 * @param projectileTravelMs tiempo que tarda un proyectil en caer de arriba a
 *   abajo, en ms.
 * @param projectileDamage HP que resta cada proyectil que impacta en el jugador.
 * @param waveDurationMs duración de la oleada: el jugador debe aguantarla entera
 *   (la barra de stamina baja por tiempo) para dejar al líder expuesto.
 * @param exposureDurationMs duración de la fase expuesta, en ms.
 * @param projectilesPerSpawn máximo de proyectiles por ráfaga (la ráfaga real
 *   elige al azar entre 1 y este valor).
 * @param rageSpawnIntervalMs cadencia de las ráfagas durante la furia (por
 *   debajo del umbral [RAGE_HP_FRACTION]).
 * @param rageProjectilesPerSpawn proyectiles por ráfaga durante la furia.
 * @param rageProjectileTravelMs caída (más rápida) durante la furia.
 */
data class Gym(
    val id: String,
    val leaderName: String,
    val type: PokemonType,
    val coinReward: Long,
    val leaderHp: Int,
    val columns: Int,
    val spawnIntervalMs: Long,
    val projectileTravelMs: Long,
    val projectileDamage: Int,
    val waveDurationMs: Long,
    val exposureDurationMs: Long,
    val projectilesPerSpawn: Int = 1,
    val rageSpawnIntervalMs: Long = spawnIntervalMs,
    val rageProjectilesPerSpawn: Int = projectilesPerSpawn,
    val rageProjectileTravelMs: Long = projectileTravelMs
)

/**
 * Fase del combate de gimnasio.
 *
 * Las dos fases son **mutuamente excluyentes**: mientras el líder ataca no se le
 * puede hacer daño, y mientras está expuesto no lanza proyectiles. Eso evita que
 * el jugador pueda atacar y defenderse a la vez (el antiguo exploit de dos manos).
 *
 * - [ATTACKING]: cae una oleada de proyectiles; la stamina del líder baja por
 *   tiempo y hay que aguantarla entera rompiendo rocas.
 * - [EXPOSED]: sin proyectiles; tocar al líder le resta vida mientras se recupera.
 */
enum class GymPhase { ATTACKING, EXPOSED }

/**
 * Resultado global del intento de gimnasio. No existe `WON`: al ganar se cierra
 * la escena y se actualiza la partida, así que no hace falta un estado ganador.
 */
enum class GymOutcome { ACTIVE, LOST }

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
 * Estado **transitorio** del combate de gimnasio (no se persiste).
 *
 * Incluye las fracciones de barra ya calculadas para que la UI no haga cuentas
 * de negocio: solo lee `leaderHpFraction`, `playerHpFraction` y
 * `staminaFraction`.
 *
 * @param leaderHp vida restante del líder; llega a 0 → victoria.
 * @param leaderMaxHp vida inicial, para la barra (y el umbral de furia).
 * @param playerHp HP restante del jugador; llega a 0 → derrota.
 * @param maxHp HP máximo del jugador.
 * @param columns columnas por las que caen los proyectiles.
 * @param projectiles proyectiles activos en este instante.
 * @param waveDurationMs duración total de la oleada, en ms.
 * @param waveRemainingMs tiempo de oleada restante; la barra de stamina es este
 *   contador bajando por tiempo.
 * @param exposureDurationMs duración total de la fase expuesta, en ms.
 * @param phase fase actual del combate.
 * @param exposureRemainingMs tiempo que le queda al líder para volver a atacar.
 * @param rage el líder está por debajo del 25 %: oleadas más densas y rápidas.
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
    val columns: Int,
    val projectiles: List<Projectile> = emptyList(),
    val waveDurationMs: Long = 0L,
    val waveRemainingMs: Long = 0L,
    val exposureDurationMs: Long = 0L,
    val phase: GymPhase = GymPhase.ATTACKING,
    val exposureRemainingMs: Long = 0L,
    val rage: Boolean = false,
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
     * Fracción de stamina para la barra. En ataque es el tiempo de oleada
     * restante (baja sola); en fase expuesta se rellena mientras corre la
     * recuperación.
     */
    val staminaFraction: Float
        get() = when {
            phase == GymPhase.EXPOSED && exposureDurationMs > 0L ->
                (1f - exposureRemainingMs.toFloat() / exposureDurationMs).coerceIn(0f, 1f)
            waveDurationMs <= 0L -> 0f
            else -> (waveRemainingMs.toFloat() / waveDurationMs).coerceIn(0f, 1f)
        }
}

/**
 * Reglas puras del combate de gimnasio, sin reloj ni corrutinas.
 *
 * Están separadas del ViewModel para poder probarlas en la JVM: reciben un
 * [GymState] y devuelven el siguiente. El ViewModel se encarga de los tiempos, de
 * crear proyectiles y de persistir la victoria.
 */
object GymCombat {

    /**
     * ¿El líder está en furia? Debe quedar **estrictamente** por debajo del 25 %
     * de su vida máxima: al 25 % exacto todavía no entra.
     */
    fun isRaging(leaderHp: Int, leaderMaxHp: Int): Boolean =
        leaderMaxHp > 0 && leaderHp.toFloat() / leaderMaxHp < RAGE_HP_FRACTION

    /** Variante que lee la vida directamente del estado del combate. */
    fun isRaging(state: GymState): Boolean = isRaging(state.leaderHp, state.leaderMaxHp)

    /**
     * Destruye el proyectil tocado (o le resta un toque si le quedaban varios).
     *
     * Romper rocas **no** gasta stamina: el tiempo de oleada corre solo. Si el
     * proyectil ya no existe o no estamos en fase de ataque, no cambia nada.
     */
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
        return state.copy(projectiles = projectiles)
    }

    /**
     * Registra uno o varios proyectiles que han impactado al jugador.
     *
     * Resta [damage] HP. La stamina ya no cambia: la oleada sigue su cuenta atrás
     * aunque el jugador falle. No actúa fuera de la fase de ataque.
     */
    fun registerImpact(state: GymState, damage: Int): GymState {
        if (state.phase != GymPhase.ATTACKING) return state

        return state.copy(playerHp = (state.playerHp - damage).coerceAtLeast(0))
    }

    /**
     * Avanza el temporizador de la oleada.
     *
     * Al agotarse (la barra de stamina llega a 0) el líder queda
     * [GymPhase.EXPOSED]: se vacían los proyectiles y empieza la cuenta de
     * exposición. Si no está en fase de ataque, no cambia nada.
     */
    fun advanceWave(state: GymState, elapsedMs: Long): GymState {
        if (state.phase != GymPhase.ATTACKING) return state

        // La furia se refresca en cada tick (no solo al recibir daño) para que
        // HUD y lógica no puedan quedar desincronizados.
        val rage = isRaging(state)
        val remaining = (state.waveRemainingMs - elapsedMs).coerceAtLeast(0L)
        return if (remaining == 0L) {
            state.copy(
                waveRemainingMs = 0L,
                phase = GymPhase.EXPOSED,
                exposureRemainingMs = state.exposureDurationMs,
                projectiles = emptyList(),
                rage = rage
            )
        } else {
            state.copy(waveRemainingMs = remaining, rage = rage)
        }
    }

    /**
     * Avanza la recuperación durante la fase expuesta.
     *
     * Cuando se agota el tiempo vuelve a [GymPhase.ATTACKING] con una oleada
     * nueva y completa. El ViewModel reinicia entonces el temporizador de
     * aparición para dar un respiro antes de la siguiente lluvia de rocas.
     */
    fun advanceExposure(state: GymState, elapsedMs: Long): GymState {
        if (state.phase != GymPhase.EXPOSED) return state

        val rage = isRaging(state)
        val remaining = state.exposureRemainingMs - elapsedMs
        return if (remaining <= 0L) {
            state.copy(
                phase = GymPhase.ATTACKING,
                waveRemainingMs = state.waveDurationMs,
                exposureRemainingMs = 0L,
                rage = rage
            )
        } else {
            state.copy(exposureRemainingMs = remaining, rage = rage)
        }
    }
}
