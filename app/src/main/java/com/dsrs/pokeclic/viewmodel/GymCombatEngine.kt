package com.dsrs.pokeclic.viewmodel

import com.dsrs.pokeclic.model.Gym
import com.dsrs.pokeclic.model.GymCombat
import com.dsrs.pokeclic.model.GymMechanic
import com.dsrs.pokeclic.model.GymOutcome
import com.dsrs.pokeclic.model.GymPhase
import com.dsrs.pokeclic.model.GymState
import com.dsrs.pokeclic.model.TimingRing
import com.dsrs.pokeclic.model.Voltorb
import com.dsrs.pokeclic.model.withDefeatIfPlayerDown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Cadencia del bucle de combate. */
private const val GYM_TICK_MS = 50L

/** Respiro de arranque de ronda antes del primer ataque. */
private const val COMBAT_FIRST_SPAWN_MS = 800L

/**
 * Motor del combate de gimnasio: reloj, cadencia y colocación de ataques.
 *
 * Extraído de [MainViewModel] para que el ViewModel deje de acumular
 * responsabilidades. Este motor **solo orquesta**: todas las reglas (impacto,
 * postura, furia, exposición, ventanas) viven en [GymCombat] y se testean sin
 * Android ni corrutinas. El motor se prueba a través del ViewModel, que es lo
 * que ya hacen los tests de la suite.
 *
 * Si una regla de aquí no se pudiera probar sin el ViewModel, es señal de que
 * debería ser una función pura en el modelo.
 *
 * @param state lectura del estado actual; el ViewModel es su único dueño.
 * @param commit escritura con la misma semántica que el resto del ViewModel:
 *   deriva y publica. La persistencia la decide quien construye el motor.
 */
internal class GymCombatEngine(
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
    private val random: Random,
    private val state: () -> GameUiState,
    private val commit: (GameUiState.() -> GameUiState) -> Unit
) {

    private var job: Job? = null
    private var nextCombatId: Long = 0L

    /** Arranca el bucle para [config], cancelando el anterior si lo hubiera. */
    fun start(config: Gym) {
        reset()
        job = scope.launch { loop(config) }
    }

    /** Cancela el bucle. */
    fun stop() {
        job?.cancel()
        job = null
    }

    /** Cancela el bucle y reinicia la numeración de ataques. */
    fun reset() {
        stop()
        nextCombatId = 0L
    }

    private suspend fun CoroutineScope.loop(config: Gym) {
        var nextSpawnMs = nowMs() + COMBAT_FIRST_SPAWN_MS
        // Se sigue la fase anterior para dar el respiro de arranque de ronda
        // venga el cambio de donde venga: fin de la exposición por tiempo o
        // puerta de furia, que devuelve al líder a [GymPhase.ATTACKING] fuera
        // del bucle. Durante la exposición la cadencia no corre, así que sin
        // este reinicio se cobraría de golpe en una ráfaga imposible de parar.
        var wasExposed = false
        while (isActive) {
            delay(GYM_TICK_MS)
            val gym = state().gym ?: break
            if (gym.outcome != GymOutcome.ACTIVE) break

            // Discurso de furia: el combate queda **congelado** hasta que el
            // jugador lo cierra. La cadencia se reanuda desde ahora para no
            // cobrar el tiempo pausado en una ráfaga imposible de parar.
            if (gym.rageSpeech) {
                nextSpawnMs = nowMs() + spawnIntervalMs(config, gym)
                continue
            }

            if (wasExposed && gym.phase == GymPhase.ATTACKING) {
                nextSpawnMs = nowMs() + COMBAT_FIRST_SPAWN_MS
            }
            wasExposed = gym.phase == GymPhase.EXPOSED

            var lost = false

            // Un solo `updateGym` por tick: así un golpe del jugador que caiga
            // a mitad no se pierde al reemplazar el estado.
            updateGym { current ->
                if (current.outcome != GymOutcome.ACTIVE) return@updateGym current

                // Fase expuesta: no hay ataque enemigo y la ventana corre por
                // tiempo. Al terminar, la postura vuelve a cero.
                if (current.phase == GymPhase.EXPOSED) {
                    return@updateGym GymCombat.advanceExposure(current, GYM_TICK_MS)
                }

                val now = nowMs()
                val spawnDue = now >= nextSpawnMs

                val idsBefore = nextCombatId
                val next = when (current.mechanic) {
                    GymMechanic.PROJECTILES ->
                        tickProjectiles(config, current, now, spawnDue)

                    GymMechanic.TIMING_RINGS ->
                        tickRings(config, current, now, spawnDue)

                    GymMechanic.VOLTORB_GRID ->
                        tickVoltorbs(config, current, now, spawnDue)
                }

                // La cadencia se cobra cuando la mecánica **coloca** su ataque.
                // La cuadrícula de Voltorb es la única que puede quedar
                // bloqueada —un Voltorb cargado a la vez—, así que si no
                // colocó, el turno sigue pendiente y entra en cuanto la celda
                // quede libre.
                val blocked = current.mechanic == GymMechanic.VOLTORB_GRID &&
                    nextCombatId == idsBefore
                if (spawnDue && !blocked) {
                    val interval = spawnIntervalMs(config, current)
                    // Si el turno venía muy atrasado (tablero lleno, pausa
                    // larga), se reanuda la cadencia desde ahora: nunca se
                    // cobran varios ataques de golpe por tiempo perdido.
                    nextSpawnMs = if (now - nextSpawnMs > interval) {
                        now + interval
                    } else {
                        nextSpawnMs + interval
                    }
                }

                if (next.outcome == GymOutcome.LOST) lost = true
                next
            }

            if (lost) break
        }
    }

    /** Actualiza el gimnasio y deriva. Mismo camino que el resto del estado. */
    private fun updateGym(block: (GymState) -> GymState) = commit {
        val gym = gym ?: return@commit this
        copy(gym = block(gym))
    }

    /** Cadencia de aparición del ataque en curso, más rápida durante la furia. */
    private fun spawnIntervalMs(config: Gym, state: GymState): Long =
        when (state.mechanic) {
            GymMechanic.PROJECTILES ->
                if (GymCombat.isRaging(state)) config.rageSpawnIntervalMs
                else config.spawnIntervalMs

            GymMechanic.TIMING_RINGS ->
                if (GymCombat.isRaging(state)) config.rageRingSpawnIntervalMs
                else config.ringSpawnIntervalMs

            GymMechanic.VOLTORB_GRID ->
                if (GymCombat.isRaging(state)) config.rageVoltorbSpawnIntervalMs
                else config.voltorbSpawnIntervalMs
        }

    /**
     * Tick de la lluvia de rocas: retira las que ya impactaron, aplica su daño y
     * lanza una ráfaga nueva si toca. [spawnDue] lo decide el bucle para compartir
     * la cadencia entre mecánicas.
     */
    private fun tickProjectiles(
        config: Gym,
        state: GymState,
        now: Long,
        spawnDue: Boolean
    ): GymState {
        val alive = state.projectiles.filterNot { it.hasLanded(now) }
        val landedCount = state.projectiles.size - alive.size

        val projectiles = if (spawnDue) {
            val raging = GymCombat.isRaging(state)
            val perSpawn = if (raging) config.rageProjectilesPerSpawn else config.projectilesPerSpawn
            val travel = if (raging) config.rageProjectileTravelMs else config.projectileTravelMs
            // La ráfaga no siempre es completa y cada roca cae a su aire: ni
            // todas las columnas ni todas las filas son iguales.
            val salvo = GymCombat.spawnSalvo(
                firstId = nextCombatId,
                columns = config.columns,
                maxPerSpawn = perSpawn,
                travelMs = travel,
                nowMs = now,
                random = random
            )
            nextCombatId += salvo.size
            alive + salvo
        } else {
            alive
        }

        if (landedCount == 0) return state.copy(projectiles = projectiles)

        val damaged = GymCombat.registerImpact(
            state = state.copy(projectiles = projectiles),
            damage = landedCount * config.projectileDamage
        )
        return damaged.withDefeatIfPlayerDown()
    }

    /**
     * Tick de los anillos de Misty: cobra los que caducaron sin acierto y lanza
     * uno nuevo si toca. Cada anillo elige su propia duración dentro del rango
     * del gimnasio, de ahí que se cierren con cadencias distintas.
     */
    private fun tickRings(
        config: Gym,
        state: GymState,
        now: Long,
        spawnDue: Boolean
    ): GymState {
        var next = state

        val expired = GymCombat.expiredRings(next, now)
        if (expired.isNotEmpty()) {
            // Varios pueden caducar en el mismo tick: los cobra todos de una vez y
            // el combo cae a cero en el mismo sitio que un fallo pulsado.
            next = GymCombat.resolveRings(next, expired, hit = false, nowMs = now)
            next = GymCombat.registerImpact(next, expired.size * config.ringMissDamage)
        }

        if (spawnDue) {
            val raging = GymCombat.isRaging(next)
            val minTravel = if (raging) config.rageRingTravelMinMs else config.ringTravelMinMs
            val maxTravel = if (raging) config.rageRingTravelMaxMs else config.ringTravelMaxMs
            val travel = if (maxTravel <= minTravel) {
                minTravel
            } else {
                random.nextLong(minTravel, maxTravel + 1)
            }
            next = next.copy(rings = next.rings + TimingRing(nextCombatId++, now, travel))
        }

        return next.withDefeatIfPlayerDown()
    }

    /**
     * Tick de la cuadrícula de Lt. Surge: cobra los Voltorb que explotaron y
     * carga uno nuevo si toca y queda celda libre.
     *
     * No hay tope de cargados a la vez: el único límite es el tablero, así que si
     * el jugador se atrasa los Voltorb se apilan. Las cargas siguen escalonadas
     * por [Gym.voltorbSpawnIntervalMs] —nunca dos en el mismo instante— y con el
     * tablero lleno el turno queda pendiente y entra en cuanto se libera una
     * celda.
     */
    private fun tickVoltorbs(
        config: Gym,
        state: GymState,
        now: Long,
        spawnDue: Boolean
    ): GymState {
        var next = GymCombat.explodeVoltorbs(state, now, config.voltorbExplosionDamage)

        val raging = GymCombat.isRaging(next)
        val totalCells = (config.rows * config.columns).coerceAtLeast(1)
        val freeCells = GymCombat.freeVoltorbCells(next, totalCells)

        if (spawnDue && freeCells.isNotEmpty()) {
            val window = if (raging) config.rageVoltorbWindowMs else config.voltorbWindowMs
            next = next.copy(
                voltorbs = next.voltorbs + Voltorb(
                    id = nextCombatId++,
                    cell = freeCells.random(random),
                    chargedAtMs = now,
                    windowMs = window
                )
            )
        }

        return if (next.playerHp <= 0) next.copy(outcome = GymOutcome.LOST) else next
    }
}
