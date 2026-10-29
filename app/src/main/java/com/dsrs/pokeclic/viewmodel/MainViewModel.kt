package com.dsrs.pokeclic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsrs.pokeclic.data.GameStateStore
import com.dsrs.pokeclic.model.AppTab
import com.dsrs.pokeclic.model.Direction
import com.dsrs.pokeclic.model.Encounter
import com.dsrs.pokeclic.model.FishingDefaults
import com.dsrs.pokeclic.model.FishingGame
import com.dsrs.pokeclic.model.FishingPhase
import com.dsrs.pokeclic.model.FishingState
import com.dsrs.pokeclic.model.GameSnapshot
import com.dsrs.pokeclic.model.Gym
import com.dsrs.pokeclic.model.GymCombat
import com.dsrs.pokeclic.model.GymOutcome
import com.dsrs.pokeclic.model.GymPhase
import com.dsrs.pokeclic.model.GymState
import com.dsrs.pokeclic.model.GymCatalog
import com.dsrs.pokeclic.model.GymIntroPhase
import com.dsrs.pokeclic.model.LeadOption
import com.dsrs.pokeclic.model.Projectile
import com.dsrs.pokeclic.model.SpeciesCatalog
import com.dsrs.pokeclic.model.SpriteRepository
import com.dsrs.pokeclic.model.StarterOption
import com.dsrs.pokeclic.model.Zone
import com.dsrs.pokeclic.model.ZoneCatalog
import com.dsrs.pokeclic.model.advanceZone
import com.dsrs.pokeclic.model.isGymPending
import com.dsrs.pokeclic.model.typeDamageMultiplier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * ViewModel de juego: contiene **toda** la lógica de negocio (clic de zona,
 * caminar por la hierba alta, captura, starter y principal, tipos, gimnasio,
 * reset, persistencia, navegación y la animación del entrenador). La UI solo lee
 * el único [uiState] y le envía eventos.
 *
 * Reglas clave:
 * - En una zona, cada toque suma [GameUiState.clickPower] clics de avance; el
 *   progreso **se capa en el umbral**: el avance a la siguiente zona es manual,
 *   vía `onAdvanceZone`. En zonas con gimnasio, el requisito extra es haberlo
 *   ganado dentro de la run actual (`gymClearedRun`).
 * - En hierba alta, los toques **no** cuentan para el avance: marcan actividad de
 *   caminar (temporizador que solo corre con toques recientes).
 * - Con un encuentro activo se juega el minijuego de pesca (`FishingGame`): la
 *   UI registra la pulsación en toda la pantalla y el ViewModel mueve barra y
 *   sprite en un bucle de ticks.
 * - Con un gimnasio activo, romper proyectiles deja al líder expuesto; solo
 *   entonces los toques sobre él le restan vida (fases excluyentes).
 *
 * Se piden por constructor el [SpriteRepository] y el [GameStateStore] (sin valor
 * por defecto) para que la instancia se cree a través de una `Factory` y sea
 * testeable en la JVM con dobles. El reloj es `System.nanoTime()`, que no
 * depende de Android.
 *
 * @param zones catálogo ordenado de zonas (inyectable para tests).
 * @param random fuente de aleatoriedad (inyectable para tests).
 * @param nowMs reloj en ms (inyectable para tests: se congelan los combates).
 */
class MainViewModel(
    private val sprites: SpriteRepository,
    private val store: GameStateStore,
    private val zones: List<Zone> = ZoneCatalog.zones,
    private val random: Random = Random.Default,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L }
) : ViewModel() {

    private val _uiState = MutableStateFlow(derive(restoreState()))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    /** Job del reset de frame; se cancela al programar uno nuevo. */
    private var resetJob: Job? = null

    /** Job del bucle de combate del gimnasio. */
    private var gymJob: Job? = null

    /** Job del cartel de arranque del combate del gimnasio. */
    private var gymIntroJob: Job? = null

    /** Job del bucle del minijuego de pesca. */
    private var fishingJob: Job? = null

    /** ¿El jugador mantiene pulsado durante la partida de pesca? */
    private var fishingHeld: Boolean = false

    /** Siguiente id de proyectil del combate actual. */
    private var nextProjectileId: Long = 0L

    /** Momento del último toque en hierba alta; gobierna el temporizador de caminar. */
    private var lastWalkTapMs: Long = 0L

    init {
        startWalkingLoop()
        startPassiveIncomeLoop()
    }

    // --- Eventos de UI -----------------------------------------------------

    /**
     * Traduce el toque sobre la pokéball a una dirección y aplica el efecto del
     * modo actual: caminar > avance de zona. Durante el minijuego de pesca la
     * pokéball no se dibuja (la escena ocupa toda la pantalla), así que un toque
     * que la alcanzara no cambia nada sustantivo.
     */
    fun onPokeBallTouch(touchX: Float, touchY: Float, sizePx: Float) {
        val direction = directionFrom(touchX, touchY, sizePx)

        val now = nowMs()
        var directionChanged = false

        val updated = _uiState.updateAndGet { state ->
            directionChanged = direction != state.trainer.currentDirection
            val trainer = if (directionChanged) {
                state.trainer.copy(currentDirection = direction, currentFrame = 0)
            } else {
                val total = sprites.frameCount(direction)
                state.trainer.copy(currentFrame = (state.trainer.currentFrame + 1) % total)
            }
            val animated = state.copy(
                trainer = trainer,
                trainerFrameRes = sprites.frame(trainer.currentDirection, trainer.currentFrame)
            )

            val progressed = when {
                // Minijuego en curso: la pokéball no interviene en la partida.
                state.encounter != null -> animated
                state.isTallGrass -> {
                    lastWalkTapMs = now
                    animated
                }
                else -> addZoneClicks(animated)
            }
            derive(progressed)
        }
        store.save(updated.toSnapshot())

        if (directionChanged) resetJob?.cancel() else scheduleFrameReset()
    }

    /** Entra o sale de la hierba alta. Al salir se resuelve (huye) el encuentro. */
    fun onTallGrassToggle() = mutate { state ->
        if (state.isTallGrass) {
            cancelFishing()
            state.copy(isTallGrass = false, walkProgress = 0f, encounter = null, fishing = null)
        } else {
            state.copy(isTallGrass = true)
        }
    }

    /**
     * Reset manual: conserva la meta (monedas, visto, recompensadas, medallas,
     * starter y su vínculo) y vacía lo de la run (en posesión, principal,
     * gimnasios ganados en la run, zona y progreso). El principal vuelve al
     * starter.
     */
    fun onReset() {
        gymIntroJob?.cancel()
        gymJob?.cancel()
        cancelFishing()
        mutate { state ->
            state.copy(
                ownedSpecies = emptySet(),
                leadSpeciesId = state.starterSpeciesId,
                gymClearedRun = emptySet(),
                zoneIndex = 0,
                zoneClicks = 0L,
                isTallGrass = false,
                walkProgress = 0f,
                encounter = null,
                fishing = null,
                gym = null,
                gymIntro = null
            )
        }
    }

    /**
     * **Borrado total de la partida (provisional, solo para testear).** Elimina
     * todo lo persistido y devuelve el estado a una primera partida: la app vuelve
     * a la introducción del Profesor Oak. No forma parte del diseño final.
     *
     * `clear()` es síncrono (destructivo), y aquí se reinician los campos
     * transitorios que sobreviven al estado (`lastWalkTapMs`,
     * `nextProjectileId`) para que ningún temporizador del estado anterior
     * "arrastre" el instante posterior al borrado.
     */
    fun onWipeGame() {
        gymIntroJob?.cancel()
        gymJob?.cancel()
        cancelFishing()
        resetJob?.cancel()
        lastWalkTapMs = 0L
        nextProjectileId = 0L
        store.clear()
        _uiState.value = derive(GameUiState(trainerFrameRes = sprites.frame(Direction.FRONT, 0)))
    }

    /**
     * Cierra la introducción de la primera partida: fija el nombre del entrenador
     * (permanente) y el starter elegido en una sola operación. Se llama una vez,
     * cuando el jugador ya pasó el diálogo del Profesor Oak.
     */
    fun onFinishIntro(playerName: String, starterSpeciesId: String) {
        if (SpeciesCatalog.starters.none { it.id == starterSpeciesId }) return
        val name = playerName.trim()
        if (name.isBlank()) return
        mutate {
            it.copy(
                playerName = name,
                starterSpeciesId = starterSpeciesId,
                starterBond = 0,
                leadSpeciesId = starterSpeciesId
            )
        }
    }

    /** Equipa como principal una especie en posesión de esta run (o el starter). */
    fun onSelectLead(speciesId: String) = mutate { state ->
        if (state.isLeadCandidate(speciesId)) state.copy(leadSpeciesId = speciesId) else state
    }

    // --- Navegación --------------------------------------------------------

    /** Cambia de pantalla y cierra la hoja de opciones si estaba abierta. */
    fun onSelectTab(tab: AppTab) = _uiState.update {
        it.copy(contentTab = tab, isOptionsOpen = false)
    }

    fun onOpenOptions() = _uiState.update { it.copy(isOptionsOpen = true) }

    fun onCloseOptions() = _uiState.update { it.copy(isOptionsOpen = false) }

    /** Abre el selector de Pokémon principal. */
    fun onOpenLead() = _uiState.update { it.copy(isLeadOpen = true) }

    fun onCloseLead() = _uiState.update { it.copy(isLeadOpen = false) }

    /**
     * Al expandir la Pokéball se muestra la pantalla principal, que es donde
     * tiene sentido usar la pokéball de juego. Al contraerla no cambia nada.
     */
    fun onPokeBallExpanded(expanded: Boolean) = _uiState.update {
        if (expanded) it.copy(contentTab = AppTab.Home, isOptionsOpen = false) else it
    }

    // --- Gimnasio (combate clicker) ----------------------------------------

    /** Empieza el intento de gimnasio de la zona actual, si está disponible. */
    fun onEnterGym() {
        val state = _uiState.value
        // Ignora la entrada si ya hay un combate en curso (incluso perdido: el
        // reintento explícito es onGymRetry, y salir es onLeaveGym), una capa de
        // intro abierta o un gimnasio no disponible.
        if (state.gym != null) return
        if (state.gymIntro != null) return
        if (!state.isGymReady) return
        val gymId = zoneAt(state.zoneIndex).gymId ?: return
        _uiState.update { it.copy(gymIntro = GymIntroPhase.WELCOME) }
    }

    /** Desde la bienvenida, abre la explicación del minijuego. */
    fun onGymIntroWatchTutorial() {
        if (_uiState.value.gymIntro != GymIntroPhase.WELCOME) return
        _uiState.update { it.copy(gymIntro = GymIntroPhase.TUTORIAL) }
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
        if (_uiState.value.gym == null) return
        beginGymCombatCountdown()
    }

    /**
     * **Abandona el intento sin penalización**; se puede reintentar. También
     * limpia la capa de intro: si el combate estaba arrancando con el cartel,
     * dejarlo a medias replicaría un arranque fantasma.
     */
    fun onLeaveGym() {
        gymIntroJob?.cancel()
        gymIntroJob = null
        gymJob?.cancel()
        _uiState.update { derive(it.copy(gym = null, gymIntro = null)) }
    }

    /**
     * Pasa a fase **countdown**: muestra el cartel "que comience el combate"
     * durante [GYM_COUNTDOWN_MS] y solo entonces arranca el combate real, para
     * que el jugador no entre por sorpresa.
     */
    private fun beginGymCombatCountdown() {
        val phase = _uiState.value.gymIntro
        // Desde una derrota no se exige fase: llega con el combate perdido ya
        // detenido (`onGymRetry` llega sin la capa de intro compuesta).
        val fromIntro = phase == GymIntroPhase.WELCOME || phase == GymIntroPhase.TUTORIAL
        val fromRetry = _uiState.value.gym != null
        if (!fromIntro && !fromRetry) return
        if (fromIntro && fromRetry) return

        gymIntroJob?.cancel()
        gymIntroJob = viewModelScope.launch {
            delay(GYM_COUNTDOWN_MS)
            val state = _uiState.value
            if (state.gymIntro != GymIntroPhase.COUNTDOWN) return@launch
            val gymId = zoneAt(state.zoneIndex).gymId
                ?: return@launch _uiState.update { it.copy(gymIntro = null) }
            startGymCombat(GymCatalog.require(gymId))
        }
        _uiState.update { it.copy(gymIntro = GymIntroPhase.COUNTDOWN) }
    }

    /**
     * Golpea al líder. Solo cuenta si está **expuesto**: en fase de ataque no se
     * le puede hacer daño, que es lo que cierra el exploit de atacar y defender a
     * la vez. El daño combina la potencia de clic con la ventaja de tipo.
     */
    fun onGymEnemyHit() {
        val state = _uiState.value
        val gym = state.gym ?: return
        if (gym.outcome != GymOutcome.ACTIVE || gym.phase != GymPhase.EXPOSED) return
        val config = GymCatalog.require(gym.gymId)

        val multiplier = typeDamageMultiplier(state.leadType, config.type)
        val damage = (state.clickPower * multiplier).roundToLong().coerceAtLeast(1L).toInt()
        val newLeaderHp = (gym.leaderHp - damage).coerceAtLeast(0)

        if (newLeaderHp <= 0) winGym(config)
        else updateGym {
            it.copy(
                leaderHp = newLeaderHp,
                rage = GymCombat.isRaging(newLeaderHp, it.leaderMaxHp)
            )
        }
    }

    /**
     * Destruye (o resta un toque a) el proyectil tocado por el jugador.
     *
     * La lectura y la escritura van dentro de `updateGym` para no pisar un golpe
     * del jugador que llegue entre el cálculo y la actualización.
     */
    fun onGymProjectileHit(projectileId: Long) {
        if (_uiState.value.gym?.outcome != GymOutcome.ACTIVE) return

        updateGym { current -> GymCombat.breakProjectile(current, projectileId) }
    }

    private fun startGymCombat(config: Gym) {
        gymJob?.cancel()
        nextProjectileId = 0L
        _uiState.update { current ->
            val maxHp = current.maxHp
            derive(
                current.copy(
                    isTallGrass = false,
                    walkProgress = 0f,
                    encounter = null,
                    fishing = null,
                    gymIntro = null,
                    gym = GymState(
                        gymId = config.id,
                        leaderName = config.leaderName,
                        gymType = config.type,
                        leaderHp = config.leaderHp,
                        leaderMaxHp = config.leaderHp,
                        playerHp = maxHp,
                        maxHp = maxHp,
                        columns = config.columns,
                        waveDurationMs = config.waveDurationMs,
                        waveRemainingMs = config.waveDurationMs,
                        exposureDurationMs = config.exposureDurationMs,
                        // El principal efectivo es el elegido o, si no hay, el starter.
                        leadSpeciesId = current.leadSpeciesId ?: current.starterSpeciesId,
                        leadName = current.leadName,
                        leadType = current.leadType
                    )
                )
            )
        }
        startGymLoop(config)
    }

    /**
     * Bucle de combate. En fase de ataque corre la cuenta atrás de la oleada,
     * lanza ráfagas de proyectiles y aplica el daño de los que lleguen abajo; en
     * fase expuesta solo avanza la recuperación. Al bajar del 25 % de vida, las
     * ráfagas pasan a ser más densas y rápidas (furia). Se detiene al ganar o
     * perder.
     */
    private fun startGymLoop(config: Gym) {
        gymJob = viewModelScope.launch {
            var nextSpawnMs = nowMs() + COMBAT_FIRST_SPAWN_MS
            while (isActive) {
                delay(GYM_TICK_MS)
                val gym = _uiState.value.gym ?: break
                if (gym.outcome != GymOutcome.ACTIVE) break

                var lost = false
                var resumed = false

                // Un solo `updateGym` por tick: así un golpe del jugador que caiga
                // a mitad no se pierde al reemplazar el estado.
                updateGym { current ->
                    if (current.outcome != GymOutcome.ACTIVE) return@updateGym current

                    // Fase expuesta: no caen rocas y la barra se rellena sola.
                    if (current.phase == GymPhase.EXPOSED) {
                        val advanced = GymCombat.advanceExposure(current, GYM_TICK_MS)
                        resumed = advanced.phase == GymPhase.ATTACKING
                        return@updateGym advanced
                    }

                    // La stamina es el reloj de la oleada: baja aunque el jugador
                    // rompa todas las rocas. Al llegar a 0, el líder queda expuesto
                    // y se limpian los proyectiles que quedaran.
                    val advanced = GymCombat.advanceWave(current, GYM_TICK_MS)
                    if (advanced.phase == GymPhase.EXPOSED) return@updateGym advanced

                    val now = nowMs()
                    val alive = advanced.projectiles.filterNot { it.hasLanded(now) }
                    val landedCount = advanced.projectiles.size - alive.size

                    val projectiles = if (now >= nextSpawnMs) {
                        val raging = GymCombat.isRaging(advanced)
                        val interval = if (raging) config.rageSpawnIntervalMs else config.spawnIntervalMs
                        val perSpawn = if (raging) config.rageProjectilesPerSpawn else config.projectilesPerSpawn
                        val travel = if (raging) config.rageProjectileTravelMs else config.projectileTravelMs

                        nextSpawnMs += interval
                        // La ráfaga no siempre es completa: entre 1 y `perSpawn`
                        // rocas, así no todas las filas son iguales.
                        val salvoSize = random.nextInt(1, perSpawn + 1)
                        val columns = (0 until config.columns)
                            .shuffled(random)
                            .take(salvoSize)
                        alive + columns.map { column ->
                            Projectile(
                                id = nextProjectileId++,
                                column = column,
                                spawnedAtMs = now,
                                travelMs = travel
                            )
                        }
                    } else {
                        alive
                    }

                    if (landedCount == 0) {
                        if (projectiles.size == advanced.projectiles.size) advanced
                        else advanced.copy(projectiles = projectiles)
                    } else {
                        val damaged = GymCombat.registerImpact(
                            state = advanced.copy(projectiles = projectiles),
                            damage = landedCount * config.projectileDamage
                        )
                        if (damaged.playerHp <= 0) {
                            lost = true
                            damaged.copy(outcome = GymOutcome.LOST)
                        } else {
                            damaged
                        }
                    }
                }

                if (lost) break
                // Al recuperarse, un respiro antes de la siguiente oleada.
                if (resumed) nextSpawnMs = nowMs() + COMBAT_FIRST_SPAWN_MS
            }
        }
    }

    /**
     * Victoria del gimnasio: cierra el combate y da sus recompensas, pero **no
     * autoavanza** la zona: la medalla entra en la meta permanente y el gimnasio
     * en `gymClearedRun` (requisito del botón «Avanzar» en esta run). Si la
     * barra ya estaba al 100 %, el botón queda disponible inmediatamente.
     */
    private fun winGym(config: Gym) {
        gymJob?.cancel()
        mutate { state ->
            state.copy(
                gym = null,
                medals = state.medals + config.id,
                gymClearedRun = state.gymClearedRun + config.id,
                coins = state.coins + config.coinReward,
                starterBond = if (state.starterSpeciesId != null) state.starterBond + 1 else state.starterBond
            )
        }
    }

    // --- Mecánicas de zona y captura --------------------------------------

    // --- Pesca (Etapa 3) ---------------------------------------------------

    /** Registra la pulsación de la partida en curso (pulsar = barra sube). */
    fun onFishingHold(held: Boolean) {
        fishingHeld = held
    }

    /**
     * Cierra el panel de resultado y aplica sus consecuencias: **capturado** suma
     * la especie (y las monedas, solo la primera vez); **escapado** no cambia
     * nada. En ambos casos se vuelve a la hierba, lista para el siguiente
     * encuentro.
     */
    fun onFishingResultContinue() {
        val fishing = _uiState.value.fishing ?: return
        if (fishing.phase == FishingPhase.PLAYING) return
        cancelFishing()

        val captured = fishing.phase == FishingPhase.CAPTURED
        mutate { state ->
            val encounter = state.encounter ?: return@mutate state.withoutEncounter()
            if (captured) {
                val firstTime = encounter.speciesId !in state.rewardedSpecies
                val reward = if (firstTime) {
                    SpeciesCatalog.require(encounter.speciesId).coinReward.toLong()
                } else {
                    0L
                }
                state.copy(
                    ownedSpecies = state.ownedSpecies + encounter.speciesId,
                    rewardedSpecies = state.rewardedSpecies + encounter.speciesId,
                    coins = state.coins + reward
                ).withoutEncounter()
            } else {
                state.withoutEncounter()
            }
        }
    }

    /** Sale del encuentro y deja la hierba lista para el siguiente. */
    private fun GameUiState.withoutEncounter(): GameUiState =
        copy(encounter = null, fishing = null, walkProgress = 0f)

    /**
     * Bucle del minijuego de pesca: avanza barra y sprite a 50 ms con la
     * pulsación registrada. Se detiene al terminar la partida (captura o huida);
     * el panel de resultado espera el `onFishingResultContinue` del jugador.
     */
    private fun startFishingLoop() {
        fishingJob?.cancel()
        fishingHeld = false
        fishingJob = viewModelScope.launch {
            while (isActive) {
                delay(FISHING_TICK_MS)
                var finished = false
                updateFishing { current ->
                    val next = FishingGame.step(
                        state = current,
                        held = fishingHeld,
                        dt = FISHING_TICK_MS / 1000f,
                        random = random
                    )
                    finished = next.phase != FishingPhase.PLAYING
                    next
                }
                if (finished) break
            }
        }
    }

    /** Actualiza el minijuego (estado transitorio, no se persiste). */
    private fun updateFishing(block: (FishingState) -> FishingState) {
        _uiState.update { state ->
            val fishing = state.fishing ?: return@update state
            derive(state.copy(fishing = block(fishing)))
        }
    }

    /** Cancela el bucle de pesca y suelta la pulsación que quedara a medias. */
    private fun cancelFishing() {
        fishingJob?.cancel()
        fishingJob = null
        fishingHeld = false
    }

    /**
     * Suma [amount] clics de avance a la zona actual. El resultado se **capa en
     * el umbral** en todo caso: la zona queda a tope (barra al 100 %) y es el
     * botón Avanzar el que mueve al jugador en lugar del umbral. La llamada
     * también la usa la producción pasiva del bucle de CPS.
     */
    private fun addZoneClicks(state: GameUiState, amount: Long = state.clickPower): GameUiState {
        val zone = zoneAt(state.zoneIndex)
        return state.copy(zoneClicks = advanceZone(zone, state.zoneClicks, amount))
    }

    /**
     * Botón **Avanzar**: si `canAdvanceZone` (barra al 100 % y, en zonas con
     * gimnasio, ganado en la run), pasa a la siguiente zona con el progreso a 0;
     * en caso contrario es un no-op. En la última zona nunca sufre cambios.
     */
    fun onAdvanceZone() = mutate { state ->
        if (state.canAdvanceZone) {
            state.copy(zoneIndex = state.zoneIndex + 1, zoneClicks = 0L)
        } else {
            state
        }
    }

    /**
     * Revierte un salvaje a la zona actual y abre su partida de pesca: el
     * encuentro muestra la especie y `GameUiState.fishing` guarda el estado del
     * minijuego con los parámetros según su dificultad de captura.
     */
    private fun spawnEncounter() {
        val state = _uiState.value
        val zone = zoneAt(state.zoneIndex)
        val species = SpeciesCatalog.require(zone.wildSpeciesIds.random(random))
        val config = FishingDefaults.configFor(species.captureDifficulty)
        mutate {
            it.copy(
                encounter = Encounter(
                    speciesId = species.id,
                    speciesName = species.name,
                    speciesType = species.type
                ),
                fishing = FishingGame.initialState(config, random),
                walkProgress = 0f,
                seenSpecies = it.seenSpecies + species.id
            )
        }
        startFishingLoop()
    }

    // --- Bucles ------------------------------------------------------------

    /**
     * Temporizador de caminar: solo avanza si el jugador ha tocado hace poco y
     * no hay encuentro ni gimnasio en curso. Deja de tocar → no aparece nada.
     */
    private fun startWalkingLoop() {
        viewModelScope.launch {
            while (isActive) {
                delay(WALK_TICK_MS)
                val state = _uiState.value
                if (!state.isTallGrass || state.encounter != null || state.gym != null) continue
                if (nowMs() - lastWalkTapMs > WALK_ACTIVITY_WINDOW_MS) continue

                val next = state.walkProgress + WALK_TICK_MS / 1000f / ENCOUNTER_SECONDS
                if (next >= 1f) spawnEncounter()
                else mutate(persist = false) { it.copy(walkProgress = next) }
            }
        }
    }

    /**
     * Producción pasiva del género idle: suma [GameUiState.clicksPerSecond] clics
     * de zona por segundo cuando no se está en hierba alta. Hoy vale 0, pero
     * conectarla a una mejora no requerirá tocar la UI.
     */
    private fun startPassiveIncomeLoop() {
        viewModelScope.launch {
            while (isActive) {
                delay(CPS_TICK_MS)
                val state = _uiState.value
                val busy = state.isTallGrass || state.encounter != null || state.gym != null
                if (state.clicksPerSecond <= 0L || busy) continue
                mutate { addZoneClicks(it, it.clicksPerSecond) }
            }
        }
    }

    // --- Animación del entrenador -----------------------------------------

    private fun scheduleFrameReset() {
        resetJob?.cancel()
        resetJob = viewModelScope.launch {
            delay(FRAME_RESET_DELAY_MS)
            _uiState.update { state ->
                state.copy(
                    trainer = state.trainer.copy(currentFrame = 0),
                    trainerFrameRes = sprites.frame(state.trainer.currentDirection, 0)
                )
            }
        }
    }

    private fun directionFrom(touchX: Float, touchY: Float, sizePx: Float): Direction {
        val center = sizePx / 2f
        val dx = (touchX - center).toDouble()
        val dy = (touchY - center).toDouble()
        val angle = Math.toDegrees(atan2(dy, dx))

        return when {
            angle >= -45.0 && angle < 45.0 -> Direction.RIGHT
            angle >= 45.0 && angle < 135.0 -> Direction.FRONT
            angle >= 135.0 || angle < -135.0 -> Direction.LEFT
            else -> Direction.BACK
        }
    }

    // --- Estado derivado y persistencia -----------------------------------

    private fun restoreState(): GameUiState {
        val saved = store.load() ?: return GameUiState(
            trainerFrameRes = sprites.frame(Direction.FRONT, 0)
        )
        return GameUiState(
            playerName = saved.playerName,
            coins = saved.coins,
            seenSpecies = saved.seenSpecies,
            rewardedSpecies = saved.rewardedSpecies,
            medals = saved.medals,
            starterSpeciesId = saved.starterSpeciesId,
            starterBond = saved.starterBond,
            ownedSpecies = saved.ownedSpecies,
            leadSpeciesId = saved.leadSpeciesId,
            gymClearedRun = saved.gymClearedRun,
            zoneIndex = saved.zoneIndex,
            zoneClicks = saved.zoneClicks,
            trainerFrameRes = sprites.frame(Direction.FRONT, 0)
        )
    }

    /** Actualiza el estado derivado y, salvo [persist] = false, lo persiste. */
    private fun mutate(persist: Boolean = true, block: (GameUiState) -> GameUiState) {
        val updated = _uiState.updateAndGet { derive(block(it)) }
        if (persist) store.save(updated.toSnapshot())
    }

    /** Actualiza el gimnasio (estado transitorio, no se persiste). */
    private fun updateGym(block: (GymState) -> GymState) {
        _uiState.update { state ->
            val gym = state.gym ?: return@update state
            derive(state.copy(gym = block(gym)))
        }
    }

    /** Recalcula los campos que dependen de catálogos (zona, costes, principal…). */
    private fun derive(state: GameUiState): GameUiState {
        val zone = zoneAt(state.zoneIndex)

        val starterId = state.starterSpeciesId
        val ownedCount = state.ownedSpecies.size + if (starterId != null) 1 else 0
        val bondBonus = if (starterId != null && state.leadSpeciesId == starterId) {
            state.starterBond * STARTER_BOND_CLICK_BONUS
        } else {
            0.0
        }
        val bonus = ownedCount * PER_OWNED_SPECIES_BONUS + bondBonus
        val clickPower = (1.0 + bonus).roundToLong().coerceAtLeast(1L)

        val lead = (state.leadSpeciesId ?: starterId)?.let { SpeciesCatalog.byId(it) }

        // Opciones ya resueltas: la UI no conoce el catálogo ni ordena especies.
        val leadOptions = (state.ownedSpecies + setOfNotNull(starterId))
            .mapNotNull { SpeciesCatalog.byId(it) }
            .sortedBy { it.name }
            .map { LeadOption(id = it.id, name = it.name) }
        val starterOptions = SpeciesCatalog.starters.map {
            StarterOption(id = it.id, name = it.name, type = it.type)
        }

        val gymPending = isGymPending(zone, state.gymClearedRun)
        val isGymReady = gymPending && state.zoneClicks >= zone.clickRequirement

        // Botón Avanzar: barra llena y, en zonas con gimnasio, ganado en la run
        // (lo excluye `gymPending`). En la última zona no hay botón.
        val canAdvanceZone = state.zoneIndex < zones.lastIndex &&
            state.zoneClicks >= zone.clickRequirement &&
            !gymPending

        return state.copy(
            zoneName = zone.name,
            zoneRequirement = zone.clickRequirement,
            ownedCount = ownedCount,
            clickPower = clickPower,
            clicksPerSecond = 0L,
            maxHp = BASE_MAX_HP,
            leadName = lead?.name,
            leadType = lead?.type,
            isGymReady = isGymReady,
            gymLeaderName = if (isGymReady) zone.gymId?.let { GymCatalog.byId(it)?.leaderName } else null,
            canAdvanceZone = canAdvanceZone,
            needsStarter = starterId == null,
            leadOptions = leadOptions,
            starterOptions = starterOptions
        )
    }

    private fun GameUiState.toSnapshot() = GameSnapshot(
        playerName = playerName,
        coins = coins,
        seenSpecies = seenSpecies,
        rewardedSpecies = rewardedSpecies,
        medals = medals,
        starterSpeciesId = starterSpeciesId,
        starterBond = starterBond,
        ownedSpecies = ownedSpecies,
        leadSpeciesId = leadSpeciesId,
        gymClearedRun = gymClearedRun,
        zoneIndex = zoneIndex,
        zoneClicks = zoneClicks
    )

    private fun zoneAt(index: Int): Zone = zones[index.coerceIn(0, zones.lastIndex)]

    private companion object {
        const val FRAME_RESET_DELAY_MS = 1_000L
        const val CPS_TICK_MS = 1_000L
        const val WALK_TICK_MS = 100L

        /** Si no se toca en este margen, el temporizador de caminar se para. */
        const val WALK_ACTIVITY_WINDOW_MS = 1_000L

        /** Segundos de caminar continuo necesarios para que aparezca un salvaje. */
        const val ENCOUNTER_SECONDS = 3f

        /** Tick del bucle del minijuego de pesca. */
        const val FISHING_TICK_MS = 50L

        /** Cada especie en posesión suma este % a la potencia de clic. */
        const val PER_OWNED_SPECIES_BONUS = 0.05

        /** Extra de % de clic por nivel de vínculo del starter, cuando va equipado. */
        const val STARTER_BOND_CLICK_BONUS = 0.02

        /** HP base del entrenador antes de la mejora de vida. */
        const val BASE_MAX_HP = 5

        /** Tick del bucle de combate del gimnasio. */
        const val GYM_TICK_MS = 50L

        /** Respiro antes del primer proyectil del combate. */
        const val COMBAT_FIRST_SPAWN_MS = 800L

        /** Duración del cartel "que comience el combate" antes del combate. */
        const val GYM_COUNTDOWN_MS = 1_000L
    }
}
