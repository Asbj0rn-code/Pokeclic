package com.dsrs.pokeclic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsrs.pokeclic.data.GameStateStore
import com.dsrs.pokeclic.model.AppTab
import com.dsrs.pokeclic.model.Direction
import com.dsrs.pokeclic.model.Encounter
import com.dsrs.pokeclic.model.CatchingDefaults
import com.dsrs.pokeclic.model.CatchingGame
import com.dsrs.pokeclic.model.CatchingPhase
import com.dsrs.pokeclic.model.CatchingState
import com.dsrs.pokeclic.model.GameSnapshot
import com.dsrs.pokeclic.model.Gym
import com.dsrs.pokeclic.model.GymCombat
import com.dsrs.pokeclic.model.GymOutcome
import com.dsrs.pokeclic.model.GymPhase
import com.dsrs.pokeclic.model.GymState
import com.dsrs.pokeclic.model.GymCatalog
import com.dsrs.pokeclic.model.GymIntroPhase
import com.dsrs.pokeclic.model.GymMechanic
import com.dsrs.pokeclic.model.LeadOption
import com.dsrs.pokeclic.model.PokedexCatalogLoad
import com.dsrs.pokeclic.model.PokedexProjector
import com.dsrs.pokeclic.model.SpeciesCatalog
import com.dsrs.pokeclic.model.SpriteRepository
import com.dsrs.pokeclic.model.withDefeatIfPlayerDown
import com.dsrs.pokeclic.model.StarterOption
import com.dsrs.pokeclic.model.TimingRing
import com.dsrs.pokeclic.model.TutorialAction
import com.dsrs.pokeclic.model.TutorialInteraction
import com.dsrs.pokeclic.model.TutorialPhase
import com.dsrs.pokeclic.model.Voltorb
import com.dsrs.pokeclic.model.Zone
import com.dsrs.pokeclic.model.ZoneCatalog
import com.dsrs.pokeclic.model.advance
import com.dsrs.pokeclic.model.advanceZone
import com.dsrs.pokeclic.model.isFreePlay
import com.dsrs.pokeclic.model.isGymPending
import com.dsrs.pokeclic.model.isGuidedStep
import com.dsrs.pokeclic.model.tutorialInteractionAllowed
import com.dsrs.pokeclic.model.isLateral
import com.dsrs.pokeclic.model.startFrame
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
 * - Con un encuentro activo se juega el minijuego de captura (`CatchingGame`): la
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
 * @param pokedexLoad catálogo de la Pokédex y resultado de su carga, resueltos
 *   fuera (`MainActivity`). Un único objeto impide el par inconsistente
 *   "catálogo no vacío + carga fallida".
 */
class MainViewModel(
    private val sprites: SpriteRepository,
    private val store: GameStateStore,
    private val zones: List<Zone> = ZoneCatalog.zones,
    private val random: Random = Random.Default,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val pokedexLoad: PokedexCatalogLoad = PokedexCatalogLoad.EMPTY
) : ViewModel() {

    /**
     * Proyección memoizada de la Pokédex (catálogo + historial → filas y
     * contadores). Declarada ANTES de `_uiState`: su inicializador ya llama a
     * `derive`.
     */
    private val pokedexProjector = PokedexProjector(pokedexLoad.catalog)

    /**
     * `derive()` corre en cada tick (16 ms durante una captura, 50 ms en un
     * gimnasio), pero estas dos listas solo cambian al capturar o al cambiar el
     * Pokémon principal. Se memoizan para no reasignarlas 60 veces por segundo:
     * la identidad estable es además lo que permite a Compose saltarse los
     * composables que las reciben.
     */
    private var leadOptionsKey: LeadOptionsKey? = null
    private var leadOptionsCache: List<LeadOption> = emptyList()
    private val starterOptionsCache: List<StarterOption> by lazy {
        SpeciesCatalog.starters.map { StarterOption(id = it.id, name = it.name, type = it.type) }
    }

    private data class LeadOptionsKey(val owned: Set<String>, val starter: String?)

    private val _uiState = MutableStateFlow(derive(restoreState()))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    /**
     * Motor del combate: reloj, cadencia y colocación de ataques. Escribe por
     * [mutate] sin persistir, igual que el resto del estado transitorio.
     */
    /** Motor del minijuego de captura: bucle de la barra y cierre del encuentro. */
    private val catchingEngine = CatchingEngine(
        scope = viewModelScope,
        nowMs = nowMs,
        random = random,
        state = { _uiState.value },
        commit = { persist, block -> mutate(persist) { it.block() } }
    )

    /** Motor de caminata y encuentros salvajes. */
    private val walkEngine = EncounterEngine(
        scope = viewModelScope,
        nowMs = nowMs,
        random = random,
        state = { _uiState.value },
        commit = { persist, block -> mutate(persist) { it.block() } },
        zoneFor = { zoneAt(it) },
        startCatching = { catchingEngine.start() }
    )

    /** Animación del entrenador: fotograma actual y vuelta al reposo. */
    private val animator = TrainerAnimator(
        scope = viewModelScope,
        sprites = sprites,
        commit = { persist, block -> mutate(persist) { it.block() } }
    )

    private val gymEngine = GymCombatEngine(
        scope = viewModelScope,
        nowMs = nowMs,
        random = random,
        state = { _uiState.value },
        gymIdForZone = { zoneAt(it).gymId },
        commit = { persist, block -> mutate(persist) { it.block() } }
    )

    init {
        walkEngine.start()
    }

    // --- Eventos de UI -----------------------------------------------------

    /**
     * Pulsación sobre la Pokébola.
     *
     * @param direction llega ya resuelta desde la UI: la marca el eje por el que
     *   se mueve el fondo (recto → de espaldas, lateral → derecha o izquierda).
     *   La Pokébola ya no elige dirección según el lado que se toca.
     *
     * Aplica el efecto del modo actual: caminar > avance de zona. Durante el
     * minijuego de captura la Pokébola no se dibuja (la escena ocupa toda la
     * pantalla), así que un toque que la alcanzara no cambia nada sustantivo.
     */
    fun onPokeBallTouch(direction: Direction) {
        // Guard del tutorial (regla principal de QA): el clic de
        // juego solo existe en juego libre. Con cualquier mensaje visible, en
        // pasos guiados o en el aviso de avance, la Pokéball no genera
        // experiencia ni movimiento.
        if (!_uiState.value.tutorialPhase.isFreePlay) return
        val now = nowMs()
        // Un clic nuevo manda: si quedaba pendiente el segundo fotograma de un
        // lateral anterior, ya no aplica.
        animator.cancelPendingStep()

        // Se lee **antes** del update: dentro del lambda de `updateAndGet` el valor
        // se recalcularía en cada reintento de CAS.
        val directionChanged = direction != _uiState.value.trainer.currentDirection
        mutate { state ->
            val animated = animator.animate(state, direction, directionChanged)

            val progressed = when {
                // Minijuego en curso: la pokéball no interviene en la partida.
                state.encounter != null -> animated
                state.isTallGrass -> {
                    walkEngine.noteActivity(now)
                    animated
                }
                // Cueva: el clic mueve el fondo y suma a la barra, pero además
                // deja actividad para que el encuentro pueda aparecer caminando.
                state.alwaysWilds -> {
                    walkEngine.noteActivity(now)
                    addZoneClicks(animated)
                }
                else -> addZoneClicks(animated)
            }
            progressed
        }

        // El paso de lado dura un solo clic: su segundo fotograma entra cuando el
        // desplazamiento ya está en marcha, no en el mismo instante del toque.
        if (directionChanged && direction.isLateral) {
            animator.scheduleLateralSecondFrame(direction)
        }
        // Un segundo sin pulsar devuelve al entrenador al reposo (front, frame 0).
        animator.scheduleReset()
    }

    /** Entra o sale de la hierba alta. Al salir se resuelve (huye) el encuentro. */
    fun onTallGrassToggle() = mutate { state ->
        // Guard del tutorial (regla principal de QA): la hierba alta
        // solo opera en juego libre; con un mensaje visible o un paso en curso,
        // nada de la partida responde.
        if (!state.tutorialPhase.isFreePlay) return@mutate state
        // Las cuevas no tienen modo que alternar: los salvajes ya están activos.
        if (state.alwaysWilds) {
            state
        } else if (state.isTallGrass) {
            catchingEngine.cancel()
            state.copy(isTallGrass = false, walkProgress = 0f, encounter = null, catching = null)
        } else {
            state.copy(isTallGrass = true)
        }
    }

    /**
     * Reset manual: conserva la meta (monedas, visto, recompensadas, medallas,
     * starter y su vínculo) y vacía lo de la run (en posesión, principal,
     * gimnasios ganados en la run, zona y progreso). El principal vuelve al
     * starter.
     *
     * El tutorial vuelve a `PENDING`: como el reset conserva el
     * starter, `derive` normaliza en el mismo gesto `PENDING` → `OFFER`, de
     * modo que la pregunta se vuelve a ofrecer **sin repetir la introducción
     * de Oak** (esa solo reaparece con el borrado total, `onWipeGame`).
     */
    fun onReset() {
        // Guard del tutorial (regla principal de QA): el reset es
        // una fila de la hoja de opciones y solo opera en juego libre — con un
        // mensaje visible o un paso en curso queda bloqueado.
        if (!_uiState.value.tutorialPhase.isFreePlay) return
        gymEngine.stop()
        catchingEngine.cancel()
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
                catching = null,
                gym = null,
                gymIntro = null,
                tutorialPhase = TutorialPhase.PENDING,
                tutorialMessageClosed = false
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
     * `nextCombatId`) para que ningún temporizador del estado anterior
     * "arrastre" el instante posterior al borrado.
     */
    fun onWipeGame() {
        // Guard del tutorial: fila de test, solo en juego libre
        // (ver `onReset`).
        if (!_uiState.value.tutorialPhase.isFreePlay) return
        gymEngine.stop()
        catchingEngine.cancel()
        animator.cancelAll()
        walkEngine.resetActivity()
        gymEngine.reset()
        store.clear()
        // Sin persistir a propósito: tras `store.clear()` un guardado nuevo
        // reabriría la partida con valores por defecto en el siguiente arranque.
        mutate(persist = false) {
            GameUiState(trainerFrameRes = sprites.frame(Direction.FRONT, 0))
        }
    }

    /**
     * **Salto de zona (provisional, solo para testear).** Deja la partida en la
     * zona [zoneId] con la barra ya llena, para poder entrar a su gimnasio sin
     * recorrer las zonas anteriores. No forma parte del diseño final.
     *
     * También limpia `gymClearedRun`: si la zona ya se había ganado en esta run,
     * su gimnasio dejaría de estar pendiente y `isGymReady` sería `false`.
     */
    fun onDebugJumpToZone(zoneId: String) {
        // Guard del tutorial: fila de test, solo en juego libre
        // (ver `onReset`).
        if (!_uiState.value.tutorialPhase.isFreePlay) return
        val index = zones.indexOfFirst { it.id == zoneId }
        if (index < 0) return
        gymEngine.stop()
        catchingEngine.cancel()
        walkEngine.resetActivity()
        gymEngine.reset()
        mutate { state ->
            state.copy(
                zoneIndex = index,
                zoneClicks = zones[index].clickRequirement,
                gymClearedRun = emptySet(),
                isTallGrass = false,
                walkProgress = 0f,
                encounter = null,
                catching = null,
                gym = null,
                gymIntro = null
            )
        }
    }

    /**
     * Cierra la introducción de la primera partida: fija el nombre del entrenador
     * (permanente) y el starter elegido en una sola operación. Se llama una vez,
     * cuando el jugador ya pasó el diálogo del Profesor Oak.
     *
     * El starter entra también en el historial de la Pokédex: queda **capturado
     * y avistado**. Es la captura inicial del jugador (decisión: se marca como
     * capturado para que la dex arranque con su primer «atrapado»; los otros
     * iniciales NO se marcan).
     *
     * Es también el disparador del tutorial: al terminar Oak la
     * fase `PENDING` avanza a `OFFER` con la acción [TutorialAction.OAK_FINISHED],
     * así que la pregunta «¿Quieres un tutorial?» aparece justo tras el
     * «adiós» del Profesor Oak en toda partida nueva.
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
                leadSpeciesId = starterSpeciesId,
                seenSpecies = it.seenSpecies + starterSpeciesId,
                caughtSpecies = it.caughtSpecies + starterSpeciesId
            ).withTutorial(TutorialAction.OAK_FINISHED)
        }
    }

    /** Equipa como principal una especie en posesión de esta run (o el starter). */
    fun onSelectLead(speciesId: String) = mutate { state ->
        // Guard del tutorial: equipar principal solo en juego libre.
        if (!state.tutorialPhase.isFreePlay) {
            state
        } else if (state.isLeadCandidate(speciesId)) {
            state.copy(leadSpeciesId = speciesId)
        } else {
            state
        }
    }

    // --- Navegación --------------------------------------------------------

    /**
     * Cambia de pantalla y cierra la hoja de opciones si estaba abierta.
     *
     * Los cambios de pestaña alimentan el tutorial: abrir el mapa
     * y volver a la aventura son dos de sus pasos. La máquina ignora toda
     * acción fuera de fase y el guard solo admite el cambio que el paso
     * espera; fuera de los pasos, navegación normal.
     */
    fun onSelectTab(tab: AppTab) = mutate { state ->
        val allowed = when (tab) {
            AppTab.Map -> state.tutorialAllows(TutorialInteraction.MENU_MAP)
            AppTab.Home -> state.tutorialAllows(TutorialInteraction.POKEBALL_TAP)
        }
        if (allowed) {
            state.copy(contentTab = tab, isOptionsOpen = false).withTutorial(
                if (tab == AppTab.Map) TutorialAction.MAP_OPENED else TutorialAction.MAP_CLOSED
            )
        } else {
            state
        }
    }

    /**
     * Abre la hoja de opciones (botón Pokédex del menú). Durante los pasos
     * guiados solo se admite en el paso de la Pokédex, que es el que recorre
     * «hoja → fila Pokédex»; el resto del tiempo se abre como siempre.
     */
    fun onOpenOptions() = mutate {
        if (it.tutorialAllows(TutorialInteraction.MENU_POKEDEX)) {
            it.copy(isOptionsOpen = true)
        } else {
            it
        }
    }

    fun onCloseOptions() = mutate(persist = false) { it.copy(isOptionsOpen = false) }

    /**
     * **Pokédex (TEST)**: abre la pantalla propia a pantalla completa y cierra
     * la hoja de opciones que la invocó.
     *
     * El VM manda: además del cierre de la hoja, **bloquea** la apertura en
     * estados incompatibles (sin starter, encuentro/captura, gimnasio o
     * modales en curso): la capa de opciones ya no es el único camino y
     * duplicar el guard aquí es lo que impide entrar con un minijuego abajo.
     * `encounter` ya implica `catching` (el minijuego arranca con el encuentro
     * y ambos se limpian juntos), así que no se comprueba por separado.
     *
     * La pantalla de Pokédex es una **rama de composición** de su propia
     * pantalla a pantalla completa: se pausa el avance de caminar
     * (`walkEngine.resetActivity()`) y, al cerrarla, la Pokébola exige una pulsación
     * nueva para reactivar el temporizador de caminar.
     *
     * Abrirla y cerrarla alimenta el tutorial: son su paso 3. La
     * máquina ignora las acciones fuera de fase, así que no cambia nada para
     * quien ya tiene el tutorial omitido o completado.
     */
    fun onOpenDex() {
        val state = _uiState.value
        // Guard del tutorial: la apertura solo se admite desde la
        // fila de la hoja en su propio paso, o fuera de los pasos guiados.
        if (!state.tutorialAllows(TutorialInteraction.POKEDEX_OPEN)) return
        val blocked = state.needsStarter ||
            state.isLeadOpen ||
            state.encounter != null ||
            state.gym != null ||
            state.gymIntro != null
        if (blocked) return
        walkEngine.resetActivity()
        mutate {
            it.copy(isOptionsOpen = false, isPokedexOpen = true)
                .withTutorial(TutorialAction.POKEDEX_OPENED)
        }
    }

    /**
     * Cierra la Pokédex y vuelve al juego sin contenido en curso.
     *
     * El guard del tutorial cubre también el **botón Atrás** de la
     * pantalla de Pokédex (`BackHandler` interno): el cierre solo puede cerrar
     * el paso de regreso a la Dex, nunca saltarse un paso ni abrir uno.
     */
    fun onCloseDex() {
        if (!_uiState.value.tutorialAllows(TutorialInteraction.POKEDEX_CLOSE)) return
        walkEngine.resetActivity()
        mutate {
            it.copy(isPokedexOpen = false).withTutorial(TutorialAction.POKEDEX_CLOSED)
        }
    }

    /** Abre el selector de Pokémon principal. */
    fun onOpenLead() = mutate {
        // Guard del tutorial: el selector solo opera en juego libre.
        if (it.tutorialPhase.isFreePlay) it.copy(isLeadOpen = true) else it
    }

    fun onCloseLead() = mutate(persist = false) { it.copy(isLeadOpen = false) }

    /**
     * Al expandir la Pokéball se muestra la pantalla principal, que es donde
     * tiene sentido usar la pokéball de juego. Al contraerla no cambia nada.
     *
     * El **despliegue** es el paso 1 del tutorial: la notificación
     * `expanded = true` llega ya existente desde el gesto del menú (arrastre
     * hacia arriba superado el umbral), así que el paso no necesita ningún
     * detector nuevo. El guard exige el mensaje del paso ya cerrado: cerrar el
     * mensaje no completa el paso, pero es lo que habilita el despliegue como
     * acción. La máquina ignora la acción fuera de fase.
     */
    fun onPokeBallExpanded(expanded: Boolean) = mutate { state ->
        // Guard del tutorial a toda la operación: si el despliegue está
        // bloqueado, el estado se devuelve intacto (sin cambiar de pestaña
        // ni cerrar la hoja de opciones), igual que el resto de métodos con
        // guard. En juego libre, `tutorialAllows(POKEBALL_DRAG)` es `true`
        // y el despliegue alimenta el paso 1 (`DEPLOY_POKEBALL`).
        if (!expanded || !state.tutorialAllows(TutorialInteraction.POKEBALL_DRAG)) {
            state
        } else {
            state.copy(contentTab = AppTab.Home, isOptionsOpen = false)
                .withTutorial(TutorialAction.POKEBALL_EXPANDED)
        }
    }

    // --- Tutorial general ----------------------------------------

    /**
     * Evento del tutorial: aplica una [TutorialAction] a la máquina de estados.
     *
     * Es la puerta de entrada para las respuestas iniciales (`ACCEPT_OFFER`,
     * `DECLINE_OFFER`, `BEGIN_TUTORIAL`, `SKIP_TUTORIAL`) y para los cierres de
     * mensaje (`FAREWELL_DISMISSED`, `EXP_NOTICE_DISMISSED`). Las acciones de
     * juego ya se detectan en sus propios métodos. La máquina descarta
     * cualquier acción que no aplique (no-op) y los finales absorben todo: una
     * respuesta tardía no reabre un tutorial omitido o completado.
     */
    fun onTutorialAction(action: TutorialAction) = mutate { it.withTutorial(action) }

    /**
     * Pulsación sobre el mensaje del tutorial (la caja de diálogo, como en la
     * introducción de Oak).
     *
     * Comportamiento por fase:
     * - **Paso guiado con mensaje visible**: cierra el mensaje **sin completar
     *   el paso**. La fase se mantiene y solo entonces se habilita la
     *   interacción del paso; la acción real sigue siendo la que lo termina.
     * - **FAREWELL / EXP_NOTICE**: dispara su transición específica
     *   (`FAREWELL_DISMISSED` / `EXP_NOTICE_DISMISSED`), que conservan su
     *   semántica de las fases anteriores.
     * - **OFFER / CONFIRM**: no-op. Exigen una respuesta explícita en sus
     *   botones; un toque en la caja no responde por el jugador.
     * - Resto de fases (sin mensaje): no-op.
     */
    fun onTutorialMessageTap() = mutate { state ->
        when {
            state.tutorialPhase.isGuidedStep && !state.tutorialMessageClosed ->
                state.copy(tutorialMessageClosed = true)
            state.tutorialPhase == TutorialPhase.FAREWELL ->
                state.withTutorial(TutorialAction.FAREWELL_DISMISSED)
            state.tutorialPhase == TutorialPhase.EXP_NOTICE ->
                state.withTutorial(TutorialAction.EXP_NOTICE_DISMISSED)
            else -> state
        }
    }

    /**
     * Aplica una acción del tutorial al estado, si la máquina la admite.
     * No-op en caso contrario, sin lanzar: misma regla de los guards.
     *
     * Toda transición de fase **reabre el mensaje**: el flag de mensaje
     * cerrado vuelve a `false` para que el paso siguiente empiece por su
     * explicación.
     */
    private fun GameUiState.withTutorial(action: TutorialAction): GameUiState {
        val next = tutorialPhase.advance(action)
        return if (next == tutorialPhase) {
            this
        } else {
            copy(tutorialPhase = next, tutorialMessageClosed = false)
        }
    }

    /**
     * ¿Admite el tutorial esta interacción de juego **ahora mismo**?
     *
     * Regla principal de QA: mientras **cualquier** mensaje esté
     * visible, nada del juego responde — la única interacción es el propio
     * diálogo. Con el mensaje cerrado: en pasos guiados, solo la interacción
     * del paso; en despedida cerrada, espera de experiencia, espera de avance
     * y finales, juego libre total. La lógica vive en el modelo
     * (`tutorialInteractionAllowed`) para que UI y ViewModel compartan la
     * misma regla.
     */
    private fun GameUiState.tutorialAllows(interaction: TutorialInteraction): Boolean =
        tutorialInteractionAllowed(tutorialPhase, tutorialMessageClosed, interaction)

    // --- Gimnasio (combate clicker) ----------------------------------------

    /** Empieza el intento de gimnasio de la zona actual, si está disponible. */
    fun onEnterGym() = gymEngine.onEnterGym()

    /** Desde la bienvenida, abre la explicación del minijuego. */
    fun onGymIntroWatchTutorial() = gymEngine.onGymIntroWatchTutorial()

    /** "Omitir" (bienvenida): salta el tutorial y arma el cartel de arranque. */
    fun onGymIntroSkip() = gymEngine.onGymIntroSkip()

    /** "Entendido" (tutorial): cierra la explicación y arma el cartel. */
    fun onGymIntroAccepted() = gymEngine.onGymIntroAccepted()

    /** Reintenta el gimnasio actual desde cero (tras una derrota). */
    fun onGymRetry() = gymEngine.onGymRetry()

    /** Abandona el intento sin penalización. */
    fun onLeaveGym() = gymEngine.onLeaveGym()

    /** Golpea al líder. Implementado en [GymCombatEngine.onGymEnemyHit]. */
    fun onGymEnemyHit() = gymEngine.onGymEnemyHit()

    /** Cierra la capa de victoria y cobra la medalla. */
    fun onGymVictoryDone() = gymEngine.onGymVictoryDone()

    /** Cierra el discurso de furia y devuelve el combate a la acción. */
    fun onGymRageSpeechDone() = gymEngine.onGymRageSpeechDone()

    /** Pulsación en el minijuego de anillos. */
    fun onGymRingTap() = gymEngine.onGymRingTap()

    /** Clic fallado: resta postura. */
    fun onGymMiss() = gymEngine.onGymMiss()

    /** Destruye el proyectil tocado por el jugador. */
    fun onGymProjectileHit(projectileId: Long) = gymEngine.onGymProjectileHit(projectileId)

    /** Toque en una celda de la cuadrícula de Voltorb. */
    fun onGymVoltorbTap(cell: Int) = gymEngine.onGymVoltorbTap(cell)

    // --- Captura (Etapa 3) ---------------------------------------------------

    /** Registra la pulsación de la partida en curso. */
    fun onCatchingHold(held: Boolean) = catchingEngine.onHold(held)

    /** Cierra el panel de resultado y aplica sus consecuencias. */
    fun onCatchingResultContinue() = catchingEngine.onResultContinue()

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
     * gimnasio, ganado en la run), pasa a la siguiente zona con el progreso a 0
     * y **fuera del modo captura**: la hierba alta se cierra y no se arrastra
     * ningún encuentro en curso; en caso contrario es un no-op. En la última
     * zona nunca sufre cambios.
     *
     * Un avance real cierra el tutorial: la acción
     * [TutorialAction.ADVANCE_ZONE_EXECUTED] solo transforma el estado en la
     * fase `ADVANCE_WAIT`, así que cualquier otro avance posterior (ya con el
     * tutorial terminado u omitido) es invisible para la máquina.
     */
    fun onAdvanceZone() = mutate { state ->
        // Guard del tutorial (regla principal de QA): con el aviso
        // de avance en pantalla (mensaje visible) el botón no surte efecto;
        // cerrar el mensaje es lo que habilita el primer avance. Fuera de eso,
        // el avance funciona como siempre.
        if (!state.tutorialAllows(TutorialInteraction.ADVANCE_ZONE)) {
            state
        } else if (state.canAdvanceZone) {
            catchingEngine.cancel()
            state.copy(
                zoneIndex = state.zoneIndex + 1,
                zoneClicks = 0L,
                isTallGrass = false
            ).withoutEncounter()
                .withTutorial(TutorialAction.ADVANCE_ZONE_EXECUTED)
        } else {
            state
        }
    }

    /**
     * Revierte un salvaje a la zona actual y abre su partida de captura: el
     * encuentro muestra la especie y `GameUiState.catching` guarda el estado del
     * minijuego con los parámetros según su dificultad de captura.
     *
     * Zonas sin hierba alta (`wildSpeciesIds` vacía, las ciudades) nunca abren
     * encuentros: caminar ahí no genera spawns ni toca el catálogo.
     */
    // --- Animación del entrenador -----------------------------------------

    // --- Estado derivado y persistencia -----------------------------------

    private fun restoreState(): GameUiState {
        val saved = store.load() ?: return GameUiState(
            trainerFrameRes = sprites.frame(Direction.FRONT, 0)
        )
        // Compatibilidad con guardados anteriores a `caughtSpecies`: la captura
        // histórica se deduce de lo que ya era cierto (posesiones de la run,
        // recompensas y el starter), y **toda especie deducida como capturada
        // también queda avistada**. Para guardados nuevos, `caught ∪ seen`
        // (la captura implica haber avistado) es idempotente.
        val caughtDeduced = saved.caughtSpecies +
            saved.ownedSpecies +
            saved.rewardedSpecies +
            setOfNotNull(saved.starterSpeciesId)
        val seenRestored = saved.seenSpecies + caughtDeduced
        // Progreso de run: un índice inválido no puede dejar al jugador en la
        // última zona, y un contador por encima del umbral no puede saltárselo.
        val hasValidIndex = saved.zoneIndex in zones.indices
        val zoneIndex = if (hasValidIndex) saved.zoneIndex else 0
        val zoneClicks = if (hasValidIndex) {
            saved.zoneClicks.coerceIn(0L, zones[zoneIndex].clickRequirement)
        } else {
            0L
        }
        return GameUiState(
            playerName = saved.playerName,
            coins = saved.coins,
            seenSpecies = seenRestored,
            rewardedSpecies = saved.rewardedSpecies,
            caughtSpecies = caughtDeduced,
            medals = saved.medals,
            starterSpeciesId = saved.starterSpeciesId,
            starterBond = saved.starterBond,
            ownedSpecies = saved.ownedSpecies,
            leadSpeciesId = saved.leadSpeciesId,
            gymClearedRun = saved.gymClearedRun,
            zoneIndex = zoneIndex,
            zoneClicks = zoneClicks,
            // El progreso del tutorial llega ya resuelto por el store (con su
            // compatibilidad de guardados antiguos): la reapertura conserva
            // exactamente la fase guardada y el momento del paso (mensaje
            // visible o ya cerrado).
            tutorialPhase = saved.tutorialPhase,
            tutorialMessageClosed = saved.tutorialMessageClosed,
            trainerFrameRes = sprites.frame(Direction.FRONT, 0)
        )
    }
    /** Actualiza el estado derivado y, salvo [persist] = false, lo persiste. */
    private fun mutate(persist: Boolean = true, block: (GameUiState) -> GameUiState) {
        val updated = _uiState.updateAndGet { derive(block(it)) }
        if (persist) store.save(updated.toSnapshot())
    }

    /**
     * Actualiza el gimnasio (estado transitorio, no se persiste).
     *
     * Delegado a [mutate] como el resto de escrituras: un solo escritor, siempre
     * derivando y con la persistencia explícita.
     */
    private fun updateGym(block: (GymState) -> GymState) {
        mutate(persist = false) { state ->
            val gym = state.gym ?: return@mutate state
            state.copy(gym = block(gym))
        }
    }

    /** Recalcula los campos que dependen de catálogos (zona, costes, principal…). */
    /**
     * Opciones del selector de principal, recalculadas solo cuando cambia la
     * posesión o el inicial. La clave se compara por contenido (`data class` con
     * `Set`), así que un `copy` con el mismo contenido reutiliza la lista.
     */
    private fun leadOptionsFor(owned: Set<String>, starterId: String?): List<LeadOption> {
        val key = LeadOptionsKey(owned, starterId)
        if (key != leadOptionsKey) {
            leadOptionsKey = key
            leadOptionsCache = (owned + setOfNotNull(starterId))
                .mapNotNull { SpeciesCatalog.byId(it) }
                .sortedBy { it.name }
                .map { LeadOption(id = it.id, name = it.name) }
        }
        return leadOptionsCache
    }

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
        // Memoizadas: `derive` corre en cada tick y solo cambian al capturar.
        val leadOptions = leadOptionsFor(state.ownedSpecies, starterId)
        val starterOptions = starterOptionsCache

        val gymPending = isGymPending(zone, state.gymClearedRun)
        val isGymReady = gymPending && state.zoneClicks >= zone.clickRequirement
        // La mecánica también se resuelve antes de crear `GymState`: la capa de
        // entrada (bienvenida/tutorial) se pinta con `gym == null` y necesita
        // saber qué minijuego explicar.
        val gymMechanic = zone.gymId?.let { GymCatalog.byId(it)?.mechanic }
            ?: GymMechanic.PROJECTILES

        // Botón Avanzar: barra llena y, en zonas con gimnasio, ganado en la run
        // (lo excluye `gymPending`). En la última zona no hay botón.
        val canAdvanceZone = state.zoneIndex < zones.lastIndex &&
            state.zoneClicks >= zone.clickRequirement &&
            !gymPending

        // Normalización del tutorial: dos transiciones que dependen
        // del estado del juego y no de un control concreto, así que viven aquí
        // y no en un evento de UI:
        // - `PENDING` + starter: Oak ya terminó (partida recién cerrada tras la
        //   intro o reset que conservó el starter) → se ofrece el tutorial sin
        //   repetir la introducción.
        // - `EXP_WAIT` + barra llena: el aviso de avance sustituye a la espera.
        // El resto de fases se respeta tal cual: la reapcripción conserva
        // exactamente lo guardado.
        val tutorialPhase = when {
            state.tutorialPhase == TutorialPhase.PENDING && starterId != null ->
                TutorialPhase.OFFER
            state.tutorialPhase == TutorialPhase.EXP_WAIT && canAdvanceZone ->
                TutorialPhase.EXP_NOTICE
            else -> state.tutorialPhase
        }

        // Pokédex: el proyector memoiza por firma (posesión de la run +
        // historia permanente), así que el resto de ticks reutiliza las 151
        // filas y sus contadores.
        val dex = pokedexProjector.project(
            ownedSpecies = state.ownedSpecies,
            starterSpeciesId = state.starterSpeciesId,
            seenSpecies = state.seenSpecies,
            caughtSpecies = state.caughtSpecies
        )

        return state.copy(
            zoneName = zone.name,
            zoneId = zone.id,
            zoneRequirement = zone.clickRequirement,
            ownedCount = ownedCount,
            clickPower = clickPower,
            maxHp = BASE_MAX_HP,
            leadName = lead?.name,
            leadType = lead?.type,
            isGymReady = isGymReady,
            hasWilds = zone.wildSpeciesIds.isNotEmpty(),
            alwaysWilds = zone.alwaysWilds,
            gymLeaderName = if (isGymReady) zone.gymId?.let { GymCatalog.byId(it)?.leaderName } else null,
            gymId = if (isGymReady) zone.gymId else null,
            gymMechanic = gymMechanic,
            canAdvanceZone = canAdvanceZone,
            needsStarter = starterId == null,
            leadOptions = leadOptions,
            starterOptions = starterOptions,
            tutorialPhase = tutorialPhase,
            dexEntries = dex.entries,
            dexSeenCount = dex.seenCount,
            dexCaughtCount = dex.caughtCount,
            dexLoadFailed = pokedexLoad.loadFailed
        )
    }

    private fun GameUiState.toSnapshot() = GameSnapshot(
        playerName = playerName,
        coins = coins,
        seenSpecies = seenSpecies,
        rewardedSpecies = rewardedSpecies,
        caughtSpecies = caughtSpecies,
        medals = medals,
        starterSpeciesId = starterSpeciesId,
        starterBond = starterBond,
        ownedSpecies = ownedSpecies,
        leadSpeciesId = leadSpeciesId,
        gymClearedRun = gymClearedRun,
        zoneIndex = zoneIndex,
        zoneClicks = zoneClicks,
        tutorialPhase = tutorialPhase,
        tutorialMessageClosed = tutorialMessageClosed
    )

    private fun zoneAt(index: Int): Zone = zones[index.coerceIn(0, zones.lastIndex)]

    private companion object {
        /** Cada especie en posesión suma este % a la potencia de clic. */
        const val PER_OWNED_SPECIES_BONUS = 0.05

        /** Extra de % de clic por nivel de vínculo del starter, cuando va equipado. */
        const val STARTER_BOND_CLICK_BONUS = 0.02

        /** HP base del entrenador antes de la mejora de vida. */
        const val BASE_MAX_HP = 5

    }
}

/** Sale del encuentro y deja la hierba lista para el siguiente. */
internal fun GameUiState.withoutEncounter(): GameUiState =
    copy(encounter = null, catching = null, walkProgress = 0f)
