package com.dsrs.pokeclic.viewmodel

import com.dsrs.pokeclic.model.Gym
import com.dsrs.pokeclic.model.GymCatalog
import com.dsrs.pokeclic.model.GymIntroPhase
import com.dsrs.pokeclic.model.GymCombat
import com.dsrs.pokeclic.model.GymMechanic
import com.dsrs.pokeclic.model.GymOutcome
import com.dsrs.pokeclic.model.GymPhase
import com.dsrs.pokeclic.model.GymState
import com.dsrs.pokeclic.model.TimingRing
import com.dsrs.pokeclic.model.isFreePlay
import com.dsrs.pokeclic.model.Voltorb
import com.dsrs.pokeclic.model.typeDamageMultiplier
import com.dsrs.pokeclic.model.withDefeatIfPlayerDown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
import kotlin.random.Random

/** Cadencia del bucle de combate. */
private const val GYM_TICK_MS = 50L

/** Respiro de arranque de ronda antes del primer ataque. */
private const val COMBAT_FIRST_SPAWN_MS = 800L

/** Duración del cartel "que comience el combate" antes del combate. */
private const val GYM_COUNTDOWN_MS = 1_000L

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
    private val gymIdForZone: (Int) -> String?,
    private val commit: (persist: Boolean, block: GameUiState.() -> GameUiState) -> Unit
) {

    private var job: Job? = null
    private var introJob: Job? = null
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
        introJob?.cancel()
        introJob = null
    }

    /** Cancela el bucle y reinicia la numeración de ataques. */
    fun reset() {
        stop()
        nextCombatId = 0L
    }

    /**
     * Golpea al líder. Solo cuenta si está **expuesto**: en fase de ataque no se
     * le puede hacer daño, que es lo que cierra el exploit de atacar y defender a
     * la vez. El daño combina la potencia de clic con la ventaja de tipo.
     *
     * La primera vez que un golpe fuese a cruzar el umbral de furia (o a matarlo)
     * sin haber jugado una ronda de furia, [GymCombat.gateFirstRage] frena el daño
     * en el umbral y devuelve al líder a [GymPhase.ATTACKING]: la furia es
     * obligatoria al menos una vez por combate.
     */
    fun onGymEnemyHit() {
        val state = state()
        val gym = state.gym ?: return
        if (gym.outcome != GymOutcome.ACTIVE || gym.phase != GymPhase.EXPOSED) return
        val config = GymCatalog.require(gym.gymId)

        val multiplier = typeDamageMultiplier(state.leadType, config.type)
        val damage = (state.clickPower * multiplier).roundToLong().coerceAtLeast(1L).toInt()

        // Un solo `updateGym`: golpe, puerta de furia y victoria se deciden sobre
        // el estado más reciente.
        updateGym { current ->
            if (current.outcome != GymOutcome.ACTIVE || current.phase != GymPhase.EXPOSED) {
                return@updateGym current
            }
            val newLeaderHp = (current.leaderHp - damage).coerceAtLeast(0)
            val gated = GymCombat.gateFirstRage(current, newLeaderHp)
            when {
                gated != null -> gated
                // El combate se queda en WON: la capa de victoria hunde al Pokémon
                // del líder y muestra su despedida antes de cobrar la medalla
                // (`onGymVictoryDone`).
                newLeaderHp <= 0 -> current.copy(leaderHp = 0, outcome = GymOutcome.WON)
                else -> current.copy(
                    leaderHp = newLeaderHp,
                    rage = GymCombat.isRaging(newLeaderHp, current.leaderMaxHp)
                )
            }
        }
    }

    /**
     * Cierra la capa de victoria y cobra la medalla.
     *
     * La llama la UI cuando el líder termina su despedida (o directamente si el
     * gimnasio aún no tiene guion propio), que es lo que separa la victoria del
     * cierre real del combate.
     */
    fun onGymVictoryDone() {
        val gym = state().gym ?: return
        if (gym.outcome != GymOutcome.WON) return
        winGym(GymCatalog.require(gym.gymId))
    }

    /**
     * Cierra el discurso de furia del líder y devuelve el combate a la acción.
     *
     * El bucle sigue corriendo durante el discurso, pero no avanza el combate
     * mientras [GymState.rageSpeech] esté activo; al apagarlo, la siguiente
     * vuelta retoma la mecánica con la furia ya en marcha.
     */
    fun onGymRageSpeechDone() {
        updateGym { current ->
            if (current.rageSpeech) current.copy(rageSpeech = false) else current
        }
    }

    /**
     * Pulsación en el minijuego de anillos de Misty.
     *
     * Juzga el anillo **más cercano** a su convergencia: dentro de la ventana es
     * acierto y el anillo desaparece **sumando postura**; fuera, fallo, resta
     * vida, no suma y el anillo también desaparece. Un anillo se cobra una sola
     * vez, de modo que pulsar repetidamente no permite encadenar aciertos ni
     * acumula daño sobre el mismo anillo. En fase expuesta no hace nada: entonces
     * el toque válido es sobre el sprite del líder ([onGymEnemyHit]).
     */
    fun onGymRingTap() {
        val state = state()
        val gym = state.gym ?: return
        if (gym.outcome != GymOutcome.ACTIVE) return
        if (gym.mechanic != GymMechanic.TIMING_RINGS) return
        if (gym.phase != GymPhase.ATTACKING) return
        val config = GymCatalog.require(gym.gymId)
        val now = nowMs()

        updateGym { current ->
            val ring = GymCombat.nearestRing(current, now) ?: return@updateGym current
            val onBeat = GymCombat.isRingOnBeat(ring, current, now)
            val resolved = GymCombat.resolveRings(current, listOf(ring), onBeat, now)
            if (onBeat) GymCombat.addPosture(resolved)
            else applyRingMiss(GymCombat.failPosture(resolved), config.ringMissDamage)
        }
    }

    /**
     * Clic fallado: el toque no acertó a ninguna roca.
     *
     * Resta postura, igual que un fallo de anillo o un Voltorb apagado, para que
     * machacar la pantalla no llene la barra. En fase expuesta no hace nada.
     */
    fun onGymMiss() {
        val gym = state().gym ?: return
        if (gym.outcome != GymOutcome.ACTIVE) return
        if (gym.phase != GymPhase.ATTACKING) return
        updateGym { current -> GymCombat.failPosture(current) }
    }

    /**
     * Destruye (o resta un toque a) el proyectil tocado por el jugador.
     *
     * La lectura y la escritura van dentro de `updateGym` para no pisar un golpe
     * del jugador que llegue entre el cálculo y la actualización.
     */
    fun onGymProjectileHit(projectileId: Long) {
        if (state().gym?.outcome != GymOutcome.ACTIVE) return

        updateGym { current -> GymCombat.breakProjectile(current, projectileId) }
    }

    /**
     * Toque en una celda de la cuadrícula de Lt. Surge.
     *
     * Si esa celda tiene un Voltorb cargado lo apaga: acierto sin daño. Si está
     * apagada, la descarga resta vida. En fase expuesta no hace nada: entonces el
     * toque válido es sobre el sprite del líder ([onGymEnemyHit]).
     */
    fun onGymVoltorbTap(cell: Int) {
        val state = state()
        val gym = state.gym ?: return
        if (gym.outcome != GymOutcome.ACTIVE) return
        if (gym.mechanic != GymMechanic.VOLTORB_GRID) return
        if (gym.phase != GymPhase.ATTACKING) return
        val config = GymCatalog.require(gym.gymId)

        updateGym { current ->
            if (GymCombat.voltorbAt(current, cell) != null) {
                GymCombat.defuseVoltorb(current, cell)
            } else {
                val shocked = GymCombat.registerShock(
                    GymCombat.failPosture(current),
                    cell,
                    config.voltorbShockDamage
                )
                if (shocked.playerHp <= 0) shocked.copy(outcome = GymOutcome.LOST) else shocked
            }
        }
    }

    /**
     * Victoria del gimnasio: cierra el combate y da sus recompensas, pero **no**
     * autoavanza la zona. Persiste: medalla, gimnasio ganado, monedas y vínculo
     * sobreviven al cierre de la app.
     */
    private fun winGym(config: Gym) {
        stop()
        commit(true) {
            copy(
                gym = null,
                medals = medals + config.id,
                gymClearedRun = gymClearedRun + config.id,
                coins = coins + config.coinReward,
                starterBond = if (starterSpeciesId != null) starterBond + 1 else starterBond
            )
        }
    }

    // --- Gimnasio (combate clicker) ----------------------------------------

    /** Empieza el intento de gimnasio de la zona actual, si está disponible. */
    fun onEnterGym() {
        val state = state()
        // Guard del tutorial: el gimnasio solo opera en juego libre.
        if (!state.tutorialPhase.isFreePlay) return
        // Ignora la entrada si ya hay un combate en curso (incluso perdido: el
        // reintento explícito es onGymRetry, y salir es onLeaveGym), una capa de
        // intro abierta o un gimnasio no disponible.
        if (state.gym != null) return
        if (state.gymIntro != null) return
        if (!state.isGymReady) return
        val gymId = gymIdForZone(state.zoneIndex) ?: return
        commit(false) { copy(gymIntro = GymIntroPhase.WELCOME) }
    }

    /** Desde la bienvenida, abre la explicación del minijuego. */
    fun onGymIntroWatchTutorial() {
        if (state().gymIntro != GymIntroPhase.WELCOME) return
        commit(false) { copy(gymIntro = GymIntroPhase.TUTORIAL) }
    }

    /** "Omitir" (bienvenida): salta el tutorial y arma el cartel de arranque. */
    fun onGymIntroSkip() = beginGymCombatCountdown()

    /** "Entendido" (tutorial): cierra la explicación y arma el cartel de arranque. */
    fun onGymIntroAccepted() = beginGymCombatCountdown()

    /**
     * Reintenta el gimnasio actual desde cero (tras una derrota), pasando por
     * el cartel de arranque para que el jugador se prepare.
     */
    fun onGymRetry() {
        if (state().gym == null) return
        beginGymCombatCountdown()
    }

    /**
     * **Abandona el intento sin penalización**; se puede reintentar. También
     * limpia la capa de intro: si el combate estaba arrancando con el cartel,
     * dejarlo a medias replicaría un arranque fantasma.
     */
    fun onLeaveGym() {
        stop()
        commit(false) { copy(gym = null, gymIntro = null) }
    }

    /**
     * Pasa a fase **countdown**: muestra el cartel "que comience el combate"
     * durante [GYM_COUNTDOWN_MS] y solo entonces arranca el combate real, para
     * que el jugador no entre por sorpresa.
     */
    private fun beginGymCombatCountdown() {
        val phase = state().gymIntro
        // Desde una derrota no se exige fase: llega con el combate perdido ya
        // detenido (`onGymRetry` llega sin la capa de intro compuesta).
        val fromIntro = phase == GymIntroPhase.WELCOME || phase == GymIntroPhase.TUTORIAL
        val fromRetry = state().gym != null
        // Solo hay un punto de partida válido: la intro o el reintento, nunca ambos.
        if (fromIntro == fromRetry) return

        introJob?.cancel()
        introJob = scope.launch {
            delay(GYM_COUNTDOWN_MS)
            val state = state()
            if (state.gymIntro != GymIntroPhase.COUNTDOWN) return@launch
            val gymId = gymIdForZone(state.zoneIndex)
                ?: return@launch commit(false) { copy(gymIntro = null) }
            startGymCombat(GymCatalog.require(gymId))
        }
        commit(false) { copy(gymIntro = GymIntroPhase.COUNTDOWN) }
    }

    private fun startGymCombat(config: Gym) {
        commit(false) {
            val maxHp = maxHp
            copy(
                walkProgress = 0f,
                encounter = null,
                catching = null,
                gymIntro = null,
                gym = GymState(
                    gymId = config.id,
                    leaderName = config.leaderName,
                    gymType = config.type,
                    leaderHp = config.leaderHp,
                    leaderMaxHp = config.leaderHp,
                    playerHp = maxHp,
                    maxHp = maxHp,
                    mechanic = config.mechanic,
                    columns = config.columns,
                    rows = config.rows,
                    ringWindowMs = config.ringWindowMs,
                    postureMax = config.postureHits,
                    posture = 0,
                    exposureDurationMs = config.exposureDurationMs,
                    // El principal efectivo es el elegido o, si no hay, el starter.
                    leadSpeciesId = leadSpeciesId ?: starterSpeciesId,
                    leadName = leadName,
                    leadType = leadType
                )
            )
        }
        start(config)
    }

    /**
     * Bucle de combate. En fase de ataque aplica la mecánica del líder —ráfagas
     * de proyectiles, anillos que se cierran o Voltorb que se cargan—; la postura
     * solo sube con los aciertos del jugador, nunca por tiempo. En fase expuesta
     * avanza la recuperación por tiempo. Al bajar del 25 % de vida la mecánica se
     * acelera (furia). Se detiene al ganar o perder.
     */
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

    /** Daño de un anillo fallado; cierra el combate si deja al jugador a 0. */
    fun applyRingMiss(state: GymState, damage: Int): GymState =
        GymCombat.registerImpact(state, damage).withDefeatIfPlayerDown()

    /** Actualiza el gimnasio y deriva. Mismo camino que el resto del estado. */
    private fun updateGym(block: (GymState) -> GymState) = commit(false) {
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
