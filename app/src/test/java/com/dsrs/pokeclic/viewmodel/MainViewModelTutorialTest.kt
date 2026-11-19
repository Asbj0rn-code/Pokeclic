/**
 * Pruebas JVM de la integración del tutorial en [MainViewModel]:
 * oferta tras Oak, omisión, confirmación, detección de acciones reales,
 * restauración exacta, reset y jugabilidad normal durante la espera.
 */
package com.dsrs.pokeclic.viewmodel

import com.dsrs.pokeclic.data.GameStateStore
import com.dsrs.pokeclic.model.AppTab
import com.dsrs.pokeclic.model.Direction
import com.dsrs.pokeclic.model.GameSnapshot
import com.dsrs.pokeclic.model.SpriteRepository
import com.dsrs.pokeclic.model.TutorialAction
import com.dsrs.pokeclic.model.TutorialPhase
import com.dsrs.pokeclic.model.isFreePlay
import com.dsrs.pokeclic.model.isMessageVisible
import com.dsrs.pokeclic.model.Zone
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTutorialTest {

    private val dispatcher = StandardTestDispatcher()

    /** Comparte el planificador virtual de `dispatcher`. */
    private val testScope = TestScope(dispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Zonas mínimas: primera zona con umbral 2 y una siguiente a la que avanzar. */
    private val testZones = listOf(
        Zone("z1", "Z1", 2L, listOf("pidgey")),
        Zone("z2", "Z2", 3L, listOf("rattata"))
    )

    private fun storeWith(saved: GameSnapshot? = null): FakeStore =
        FakeStore().apply { this.saved = saved }

    private fun viewModel(
        saved: GameSnapshot? = null,
        store: GameStateStore = storeWith(saved),
        zones: List<Zone> = testZones
    ) = testViewModel(
        sprites = FakeSprites(),
        store = store,
        zones = zones,
        random = Random(42),
        nowMs = { System.nanoTime() / 1_000_000L }
    )

    /** Toques sobre la Pokéball como clics de zona. */
    private fun tapZone(vm: MainViewModel, times: Int) {
        repeat(times) { vm.onPokeBallTouch(Direction.BACK) }
    }

    /** ViewModel con la intro cerrada y la oferta del tutorial en pantalla. */
    private fun vmAtOffer(): MainViewModel =
        viewModel().apply { onFinishIntro("Red", "charmander") }

    /** Desde la oferta, acepta y confirma el arranque del tutorial. */
    private fun vmAtDeployStep(messageClosed: Boolean = false): MainViewModel =
        vmAtOffer().apply {
            onTutorialAction(TutorialAction.ACCEPT_OFFER)            // CONFIRM
            onTutorialAction(TutorialAction.BEGIN_TUTORIAL)          // DEPLOY_POKEBALL
            if (messageClosed) onTutorialMessageTap()
        }

    /** Paso de apertura del mapa (el despliegue ya se completó). */
    private fun vmAtMapOpenStep(messageClosed: Boolean = false): MainViewModel =
        vmAtDeployStep(messageClosed = true).apply {
            onPokeBallExpanded(true)                                 // OPEN_MAP
            if (messageClosed) onTutorialMessageTap()
        }

    /** Paso de apertura de la Pokédex (mapa ya recorrido de ida). */
    private fun vmAtDexOpenStep(messageClosed: Boolean = false): MainViewModel =
        vmAtMapOpenStep(messageClosed = true).apply {
            onSelectTab(AppTab.Map)                                  // RETURN_MAP
            onTutorialMessageTap()
            onSelectTab(AppTab.Home)                                 // OPEN_POKEDEX
            if (messageClosed) onTutorialMessageTap()
        }

    /** Lleva el ViewModel hasta `FAREWELL` siguiendo el recorrido real de pasos. */
    private fun vmUpToFarewell(
        store: GameStateStore = storeWith(),
        zones: List<Zone> = testZones
    ): MainViewModel =
        viewModel(store = store, zones = zones).apply {
            onFinishIntro("Red", "charmander")                       // OFFER
            onTutorialAction(TutorialAction.ACCEPT_OFFER)            // CONFIRM
            onTutorialAction(TutorialAction.BEGIN_TUTORIAL)          // DEPLOY_POKEBALL
            onTutorialMessageTap()                                   // cierra el mensaje
            onPokeBallExpanded(true)                                 // OPEN_MAP
            onTutorialMessageTap()
            onSelectTab(AppTab.Map)                                  // RETURN_MAP
            onTutorialMessageTap()
            onSelectTab(AppTab.Home)                                 // OPEN_POKEDEX
            onTutorialMessageTap()
            onOpenDex()                                              // RETURN_POKEDEX
            onTutorialMessageTap()
            onCloseDex()                                             // FAREWELL
        }

    // ───────────────── Inicio: partida nueva y Oak ─────────────────

    @Test
    fun freshGame_startsPendingAndDoesNotOfferBeforeOak() {
        val vm = viewModel()

        assertEquals(TutorialPhase.PENDING, vm.uiState.value.tutorialPhase)
        assertTrue(vm.uiState.value.needsStarter)
    }

    @Test
    fun pending_beforeOakFinishes_onlyTheIntroIsReachable() {
        val vm = viewModel()   // PENDING, sin starter
        val before = vm.uiState.value

        // La intro de Oak es opaca y el menú no se compone (`menuHidden`
        // incluye `needsStarter`), así que en la UI real ningún evento de
        // juego puede llegar. Los eventos del tutorial que sí podrían llegar
        // son no-op: la máquina solo responde a `OAK_FINISHED` desde `PENDING`.
        vm.onTutorialAction(TutorialAction.DECLINE_OFFER)   // tardío: PENDING absorbe
        vm.onTutorialMessageTap()                          // sin mensaje: no-op

        assertEquals(before, vm.uiState.value)

        // Invariante frágil, documentada en el KDoc de `isFreePlay`: en
        // `PENDING` los guards del ViewModel están abiertos y el aislamiento
        // depende de las capas visuales (intro opaca + menú oculto por
        // `needsStarter`). Cambiar `isFreePlay` o `menuHidden` exige revisar
        // este contrato.
        assertTrue(TutorialPhase.PENDING.isFreePlay)
    }

    @Test
    fun oakFinished_offersTheTutorialInANewGame() {
        val store = storeWith()
        val vm = viewModel(store = store)

        vm.onFinishIntro("Red", "charmander")

        val state = vm.uiState.value
        assertEquals(TutorialPhase.OFFER, state.tutorialPhase)
        assertFalse(state.needsStarter)
        // La oferta queda persistida para la reapertura.
        assertEquals(TutorialPhase.OFFER, store.saved!!.tutorialPhase)
    }

    @Test
    fun oakFinishedTwice_doesNotRestartTheOffer() {
        val vm = vmAtOffer()

        vm.onFinishIntro("Red", "charmander")

        assertEquals(TutorialPhase.OFFER, vm.uiState.value.tutorialPhase)
    }

    // ───────────────── Respuestas iniciales ─────────────────

    @Test
    fun declineOffer_skipsTheTutorialForGood() {
        val vm = vmAtOffer()

        vm.onTutorialAction(TutorialAction.DECLINE_OFFER)

        assertEquals(TutorialPhase.SKIPPED, vm.uiState.value.tutorialPhase)
        // Respuestas y acciones tardías no reabren un tutorial omitido.
        vm.onTutorialAction(TutorialAction.ACCEPT_OFFER)
        vm.onTutorialAction(TutorialAction.BEGIN_TUTORIAL)
        vm.onFinishIntro("Red", "charmander")
        assertEquals(TutorialPhase.SKIPPED, vm.uiState.value.tutorialPhase)
    }

    @Test
    fun acceptOffer_confirmsAndBeginStartsTheFirstStep() {
        val vm = vmAtOffer()

        vm.onTutorialAction(TutorialAction.ACCEPT_OFFER)
        assertEquals(TutorialPhase.CONFIRM, vm.uiState.value.tutorialPhase)

        vm.onTutorialAction(TutorialAction.BEGIN_TUTORIAL)
        assertEquals(TutorialPhase.DEPLOY_POKEBALL, vm.uiState.value.tutorialPhase)
    }

    @Test
    fun skipFromConfirm_alsoSkips() {
        val vm = vmAtOffer().apply {
            onTutorialAction(TutorialAction.ACCEPT_OFFER)
        }

        vm.onTutorialAction(TutorialAction.SKIP_TUTORIAL)

        assertEquals(TutorialPhase.SKIPPED, vm.uiState.value.tutorialPhase)
    }

    // ───────────────── Acciones reales del jugador ─────────────────

    @Test
    fun pokeballDeployment_isDetectedThroughTheExistingEvent() {
        val vm = vmAtDeployStep()   // mensaje del paso visible

        // Con el mensaje visible la acción del paso aún no cuenta.
        vm.onPokeBallExpanded(true)
        assertEquals(TutorialPhase.DEPLOY_POKEBALL, vm.uiState.value.tutorialPhase)

        // Cerrar el mensaje no completa el paso; habilita su interacción.
        vm.onTutorialMessageTap()
        assertEquals(TutorialPhase.DEPLOY_POKEBALL, vm.uiState.value.tutorialPhase)
        assertTrue(vm.uiState.value.tutorialMessageClosed)

        // Plegar no hace nada: el paso espera el despliegue.
        vm.onPokeBallExpanded(false)
        assertEquals(TutorialPhase.DEPLOY_POKEBALL, vm.uiState.value.tutorialPhase)

        // El arrastre hacia arriba completado llega como `expanded = true`.
        vm.onPokeBallExpanded(true)
        assertEquals(TutorialPhase.OPEN_MAP, vm.uiState.value.tutorialPhase)
        // Cada paso nuevo arranca con su mensaje visible.
        assertFalse(vm.uiState.value.tutorialMessageClosed)
    }

    @Test
    fun mapOpenAndReturn_advanceThroughTheRealNavigation() {
        val vm = vmAtMapOpenStep()   // mensaje del paso visible

        vm.onTutorialMessageTap()   // cierra el mensaje del paso de mapa
        vm.onSelectTab(AppTab.Map)
        assertEquals(TutorialPhase.RETURN_MAP, vm.uiState.value.tutorialPhase)

        vm.onTutorialMessageTap()   // cierra el mensaje del regreso
        vm.onSelectTab(AppTab.Home)
        assertEquals(TutorialPhase.OPEN_POKEDEX, vm.uiState.value.tutorialPhase)
    }

    @Test
    fun mapReturnBeforeOpeningMap_isIgnored() {
        val vm = vmAtMapOpenStep(messageClosed = true)

        // Volver a casa sin haber abierto el mapa no avanza el paso.
        vm.onSelectTab(AppTab.Home)
        assertEquals(TutorialPhase.OPEN_MAP, vm.uiState.value.tutorialPhase)
    }

    @Test
    fun pokedexOpenAndClose_advanceThroughTheRealNavigation() {
        val vm = vmAtDexOpenStep()   // mensaje del paso visible

        vm.onTutorialMessageTap()   // cierra el mensaje del paso de la Pokédex
        vm.onOpenDex()
        assertEquals(TutorialPhase.RETURN_POKEDEX, vm.uiState.value.tutorialPhase)

        vm.onTutorialMessageTap()   // cierra el mensaje del regreso
        vm.onCloseDex()
        assertEquals(TutorialPhase.FAREWELL, vm.uiState.value.tutorialPhase)
    }

    // ───────────────── Guard completo del despliegue de la Pokéball ─────────────────

    @Test
    fun pokeballDeployment_whileBlocked_leavesTheStateUntouched() {
        // El guard del despliegue cubre toda la operación: bloqueado, el
        // estado se devuelve intacto (sin cambiar de pestaña ni cerrar la
        // hoja de opciones), igual que el resto de métodos con guard.
        listOf(
            "OFFER" to vmAtOffer(),
            "CONFIRM" to vmAtOffer().apply {
                onTutorialAction(TutorialAction.ACCEPT_OFFER)          // CONFIRM
            },
            "FAREWELL" to vmUpToFarewell(),
            "OPEN_MAP" to vmAtMapOpenStep(),
            "OPEN_POKEDEX" to vmAtDexOpenStep(),
            "RETURN_POKEDEX" to vmAtDexOpenStep(messageClosed = true)
        ).forEach { (phase, vm) ->
            val before = vm.uiState.value

            vm.onPokeBallExpanded(true)
            assertEquals("$phase: el bloqueado devuelve el estado intacto", before, vm.uiState.value)
        }
    }

    @Test
    fun pokeballDeployment_duringMapReturn_keepsTheMapTab() {
        // Misma clase de regresión que la del regreso de la Pokédex
        // (`7af231a`): con el mensaje cerrado, el despliegue bloqueado
        // cambiaba de pestaña estando en el Mapa. El guard completo lo impide.
        val vm = vmAtMapOpenStep(messageClosed = true).apply {
            onSelectTab(AppTab.Map)                                // RETURN_MAP
            onTutorialMessageTap()                                 // cierra el mensaje
        }

        assertEquals(TutorialPhase.RETURN_MAP, vm.uiState.value.tutorialPhase)
        assertEquals(AppTab.Map, vm.uiState.value.contentTab)

        vm.onPokeBallExpanded(true)
        assertEquals(TutorialPhase.RETURN_MAP, vm.uiState.value.tutorialPhase)
        assertEquals(AppTab.Map, vm.uiState.value.contentTab)
    }

    @Test
    fun pokeballDeployment_duringDexStep_keepsTheOptionsSheetOpen() {
        val vm = vmAtDexOpenStep(messageClosed = true).apply {
            onOpenOptions()                                        // hoja de opciones abierta
        }
        assertTrue(vm.uiState.value.isOptionsOpen)

        vm.onPokeBallExpanded(true)
        assertTrue(vm.uiState.value.isOptionsOpen)
        assertEquals(TutorialPhase.OPEN_POKEDEX, vm.uiState.value.tutorialPhase)
    }

    @Test
    fun pokeballDeployment_inFreePlay_navigatesNormally() {
        val vm = vmAtOffer().apply {
            onTutorialAction(TutorialAction.DECLINE_OFFER)         // SKIPPED
        }

        vm.onSelectTab(AppTab.Map)                                 // navegación libre
        assertEquals(AppTab.Map, vm.uiState.value.contentTab)

        vm.onPokeBallExpanded(true)
        assertEquals(AppTab.Home, vm.uiState.value.contentTab)
        assertEquals(TutorialPhase.SKIPPED, vm.uiState.value.tutorialPhase)
    }

    // ───────────────── Espera de experiencia: jugabilidad normal ─────────────────

    @Test
    fun expWait_keepsNormalGameplayFullyAvailable() {
        val vm = vmUpToFarewell().apply {
            onTutorialAction(TutorialAction.FAREWELL_DISMISSED)   // EXP_WAIT
        }

        assertEquals(TutorialPhase.EXP_WAIT, vm.uiState.value.tutorialPhase)

        // Sin bloqueos: la Pokéball sigue sumando clics de zona con normalidad.
        tapZone(vm, 1)
        assertEquals(1L, vm.uiState.value.zoneClicks)
        assertEquals(TutorialPhase.EXP_WAIT, vm.uiState.value.tutorialPhase)

        // La navegación sigue operable y no mueve la máquina.
        vm.onSelectTab(AppTab.Map)
        assertEquals(TutorialPhase.EXP_WAIT, vm.uiState.value.tutorialPhase)
    }

    @Test
    fun expBarFilled_showsTheAdvanceNotice() {
        val vm = vmUpToFarewell().apply {
            onTutorialAction(TutorialAction.FAREWELL_DISMISSED)   // EXP_WAIT
        }

        tapZone(vm, 2)   // umbral de la zona = 2

        assertTrue(vm.uiState.value.canAdvanceZone)
        assertEquals(TutorialPhase.EXP_NOTICE, vm.uiState.value.tutorialPhase)
    }

    @Test
    fun advanceNoticeDismissed_thenFirstAdvanceCompletesTheTutorial() {
        val vm = vmUpToFarewell().apply {
            onTutorialAction(TutorialAction.FAREWELL_DISMISSED)
            tapZone(this, 2)   // barra llena → EXP_NOTICE
        }

        vm.onTutorialAction(TutorialAction.EXP_NOTICE_DISMISSED)
        assertEquals(TutorialPhase.ADVANCE_WAIT, vm.uiState.value.tutorialPhase)

        // El primer avance real cierra el tutorial.
        vm.onAdvanceZone()
        assertEquals(TutorialPhase.COMPLETED, vm.uiState.value.tutorialPhase)
        assertEquals(1, vm.uiState.value.zoneIndex)
    }

    @Test
    fun laterAdvances_doNotDisturbACompletedTutorial() {
        // Tres zonas para poder verificar un segundo avance real.
        val zones = listOf(
            Zone("z1", "Z1", 2L, listOf("pidgey")),
            Zone("z2", "Z2", 3L, listOf("rattata")),
            Zone("z3", "Z3", 4L, listOf("caterpie"))
        )
        val store = storeWith()
        val vm = vmUpToFarewell(store, zones).apply {
            onTutorialAction(TutorialAction.FAREWELL_DISMISSED)
            tapZone(this, 2)
            onTutorialAction(TutorialAction.EXP_NOTICE_DISMISSED)
            onAdvanceZone()   // primer avance → COMPLETED
        }
        assertEquals(TutorialPhase.COMPLETED, vm.uiState.value.tutorialPhase)
        assertEquals(1, vm.uiState.value.zoneIndex)

        tapZone(vm, 3)      // llena la barra de la segunda zona
        vm.onAdvanceZone()  // segundo avance, ya con el tutorial terminado

        assertEquals(2, vm.uiState.value.zoneIndex)
        assertEquals(TutorialPhase.COMPLETED, vm.uiState.value.tutorialPhase)
    }

    // ───────────────── Restauración exacta ─────────────────

    @Test
    fun restore_conservesTheExactSavedPhase() {
        listOf(
            TutorialPhase.OFFER,
            TutorialPhase.CONFIRM,
            TutorialPhase.RETURN_MAP,
            TutorialPhase.FAREWELL,
            TutorialPhase.EXP_WAIT,
            TutorialPhase.ADVANCE_WAIT,
            TutorialPhase.SKIPPED,
            TutorialPhase.COMPLETED
        ).forEach { phase ->
            val vm = viewModel(
                saved = GameSnapshot(
                    starterSpeciesId = "charmander",
                    tutorialPhase = phase
                )
            )
            assertEquals(
                "La reapertura debe conservar $phase",
                phase,
                vm.uiState.value.tutorialPhase
            )
        }
    }

    @Test
    fun restore_thenNewSave_keepsThePhaseAndMomentInSnapshot() {
        val store = storeWith(
            saved = GameSnapshot(
                starterSpeciesId = "charmander",
                tutorialPhase = TutorialPhase.RETURN_MAP
            )
        )
        val vm = viewModel(store = store)

        // Se cierra el mensaje del paso y el tap de zona persiste el momento.
        vm.onTutorialMessageTap()
        assertEquals(TutorialPhase.RETURN_MAP, store.saved!!.tutorialPhase)
        assertEquals(true, store.saved!!.tutorialMessageClosed)

        // Con el mensaje cerrado, la interacción del paso ya cuenta.
        tapZone(vm, 1)

        assertEquals(TutorialPhase.RETURN_MAP, store.saved!!.tutorialPhase)
        assertEquals(true, store.saved!!.tutorialMessageClosed)
    }

    @Test
    fun legacySnapshot_defaultsToCompleted() {
        // Guardado creado antes de la característica: el campo toma su valor
        // por defecto en `GameSnapshot` (`COMPLETED`) y el tutorial no se ofrece.
        val vm = viewModel(
            saved = GameSnapshot(
                playerName = "Ash",
                starterSpeciesId = "bulbasaur"
            )
        )

        assertEquals(TutorialPhase.COMPLETED, vm.uiState.value.tutorialPhase)
    }

    @Test
    fun restore_expWaitWithFullBar_showsTheNotice() {
        // Guardado en EXP_WAIT con la barra ya llena: al reabrir se pasa al aviso.
        val vm = viewModel(
            saved = GameSnapshot(
                starterSpeciesId = "charmander",
                zoneClicks = 2L,   // umbral de z1
                tutorialPhase = TutorialPhase.EXP_WAIT
            )
        )

        assertTrue(vm.uiState.value.canAdvanceZone)
        assertEquals(TutorialPhase.EXP_NOTICE, vm.uiState.value.tutorialPhase)
    }

    // ───────────────── Reset y borrado total ─────────────────

    @Test
    fun reset_reoffersTheTutorialWithoutRepeatingOak() {
        val vm = viewModel().apply { onFinishIntro("Red", "charmander") }
        vm.onTutorialAction(TutorialAction.DECLINE_OFFER)

        vm.onReset()

        val state = vm.uiState.value
        assertEquals(TutorialPhase.OFFER, state.tutorialPhase)
        // El starter se conserva: no vuelve la introducción de Oak.
        assertFalse(state.needsStarter)
        assertEquals("charmander", state.starterSpeciesId)
    }

    @Test
    fun reset_midTutorial_restartsFromTheOffer() {
        val store = storeWith()
        val vm = vmUpToFarewell(store).apply {
            // Cerrar la despedida para volver al juego libre antes del reset.
            onTutorialAction(TutorialAction.FAREWELL_DISMISSED)
        }

        vm.onReset()

        assertEquals(TutorialPhase.OFFER, vm.uiState.value.tutorialPhase)
        // La oferta tras reset queda persistida.
        assertEquals(TutorialPhase.OFFER, store.saved!!.tutorialPhase)
    }

    @Test
    fun wipeGame_returnsToAFreshGameWithTutorialPending() {
        val store = storeWith()
        val vm = vmUpToFarewell(store).apply {
            onTutorialAction(TutorialAction.FAREWELL_DISMISSED)
        }

        vm.onWipeGame()

        val state = vm.uiState.value
        assertEquals(TutorialPhase.PENDING, state.tutorialPhase)
        assertTrue(state.needsStarter)
        assertEquals(null, store.saved)
    }

    // ───────────────── Bloqueos: pasos guiados (Fase 4.2) ─────────────────

    @Test
    fun guidedSteps_blockThePokeballGameClick() {
        val vm = vmAtDeployStep()   // mensaje del paso visible

        tapZone(vm, 3)

        // El clic de zona no suma y el paso no se mueve: la única interacción
        // del paso es el deslizamiento hacia arriba de la Pokéball.
        assertEquals(0L, vm.uiState.value.zoneClicks)
        assertEquals(TutorialPhase.DEPLOY_POKEBALL, vm.uiState.value.tutorialPhase)
    }

    @Test
    fun deployStep_blocksEverythingButTheDragUp() {
        val store = storeWith(
            saved = GameSnapshot(
                starterSpeciesId = "charmander",
                zoneClicks = 2L,   // barra llena: el avance estaría disponible
                tutorialPhase = TutorialPhase.DEPLOY_POKEBALL
            )
        )
        val vm = viewModel(store = store)

        vm.onSelectTab(AppTab.Map)
        vm.onTallGrassToggle()
        vm.onOpenOptions()
        vm.onOpenLead()
        vm.onSelectLead("charmander")
        vm.onEnterGym()
        vm.onReset()
        vm.onWipeGame()
        vm.onDebugJumpToZone("z2")
        vm.onAdvanceZone()

        val state = vm.uiState.value
        assertEquals(TutorialPhase.DEPLOY_POKEBALL, state.tutorialPhase)
        assertEquals(AppTab.Home, state.contentTab)
        assertFalse(state.isTallGrass)
        assertFalse(state.isOptionsOpen)
        assertFalse(state.isLeadOpen)
        assertNull(state.gymIntro)
        assertEquals(0, state.zoneIndex)
        // El guardado anterior a los bloqueos queda intacto.
        assertEquals(TutorialPhase.DEPLOY_POKEBALL, store.saved!!.tutorialPhase)
    }

    @Test
    fun mapStep_blocksReturnBeforeOpeningAndTheMapButtonAfterwards() {
        val vm = vmAtMapOpenStep()   // OPEN_MAP, mensaje visible

        // No se puede "volver" sin haber abierto el mapa (ni siquiera con el
        // mensaje ya cerrado).
        vm.onTutorialMessageTap()
        vm.onSelectTab(AppTab.Home)
        assertEquals(TutorialPhase.OPEN_MAP, vm.uiState.value.tutorialPhase)
        assertEquals(AppTab.Home, vm.uiState.value.contentTab)

        // Abrir el mapa es lo único permitido.
        vm.onSelectTab(AppTab.Map)
        assertEquals(TutorialPhase.RETURN_MAP, vm.uiState.value.tutorialPhase)
        assertEquals(AppTab.Map, vm.uiState.value.contentTab)

        // En el regreso, el botón Mapa queda inerte (la salida es la Pokéball).
        vm.onTutorialMessageTap()
        vm.onSelectTab(AppTab.Map)
        assertEquals(TutorialPhase.RETURN_MAP, vm.uiState.value.tutorialPhase)
        // Y ningún clic de juego se cuela en el paso.
        tapZone(vm, 2)
        assertEquals(0L, vm.uiState.value.zoneClicks)

        // El regreso real por la Pokéball avanza.
        vm.onSelectTab(AppTab.Home)
        assertEquals(TutorialPhase.OPEN_POKEDEX, vm.uiState.value.tutorialPhase)
    }

    @Test
    fun pokedexStep_blocksOtherOptionsRows_andBackCannotSkipTheStep() {
        val store = storeWith()
        val vm = viewModel(store = store).apply {
            onFinishIntro("Red", "charmander")
            onTutorialAction(TutorialAction.ACCEPT_OFFER)
            onTutorialAction(TutorialAction.BEGIN_TUTORIAL)
            onTutorialMessageTap()
            onPokeBallExpanded(true)
            onTutorialMessageTap()
            onSelectTab(AppTab.Map)
            onTutorialMessageTap()
            onSelectTab(AppTab.Home)   // OPEN_POKEDEX
        }

        // Filas de la hoja bloqueadas, especialmente las destructivas y de test.
        vm.onReset()
        vm.onWipeGame()
        vm.onDebugJumpToZone("z2")
        vm.onOpenLead()
        assertEquals(TutorialPhase.OPEN_POKEDEX, vm.uiState.value.tutorialPhase)
        assertFalse(vm.uiState.value.isLeadOpen)
        assertNotNull(store.saved)
        assertEquals(0, vm.uiState.value.zoneIndex)

        // El Atrás de Android (cierre de la dex) no puede saltarse el paso:
        // la dex ni siquiera está abierta, y la acción de cierre es no-op.
        vm.onCloseDex()
        assertEquals(TutorialPhase.OPEN_POKEDEX, vm.uiState.value.tutorialPhase)

        // El recorrido permitido: cerrar el mensaje → hoja → fila → regreso.
        vm.onTutorialMessageTap()
        vm.onOpenOptions()
        assertTrue(vm.uiState.value.isOptionsOpen)
        vm.onOpenDex()
        assertEquals(TutorialPhase.RETURN_POKEDEX, vm.uiState.value.tutorialPhase)
        assertTrue(vm.uiState.value.isPokedexOpen)
        vm.onTutorialMessageTap()
        vm.onCloseDex()
        assertEquals(TutorialPhase.FAREWELL, vm.uiState.value.tutorialPhase)
    }

    @Test
    fun guidedSteps_blockTheTallGrassToggle() {
        val vm = vmAtDeployStep()   // mensaje del paso visible

        vm.onTallGrassToggle()
        assertFalse(vm.uiState.value.isTallGrass)
    }

    @Test
    fun advanceNotice_blocksAdvanceUntilTheMessageIsClosed() {
        val vm = vmUpToFarewell().apply {
            onTutorialAction(TutorialAction.FAREWELL_DISMISSED)
            tapZone(this, 2)   // barra llena → EXP_NOTICE
        }

        // El aviso está en pantalla: el botón Avanzar no surte efecto todavía.
        vm.onAdvanceZone()
        assertEquals(TutorialPhase.EXP_NOTICE, vm.uiState.value.tutorialPhase)
        assertEquals(0, vm.uiState.value.zoneIndex)

        // Cerrar el mensaje es lo que habilita el primer avance.
        vm.onTutorialAction(TutorialAction.EXP_NOTICE_DISMISSED)
        vm.onAdvanceZone()
        assertEquals(TutorialPhase.COMPLETED, vm.uiState.value.tutorialPhase)
        assertEquals(1, vm.uiState.value.zoneIndex)
    }

    @Test
    fun restore_advanceNoticePending_blocksAdvanceUntilDismissed() {
        // El aviso pendiente de cierre se restaura exacto al reabrir: el avance
        // sigue bloqueado hasta que el mensaje se cierra.
        val vm = viewModel(
            saved = GameSnapshot(
                starterSpeciesId = "charmander",
                zoneClicks = 2L,
                tutorialPhase = TutorialPhase.EXP_NOTICE
            )
        )

        assertEquals(TutorialPhase.EXP_NOTICE, vm.uiState.value.tutorialPhase)
        vm.onAdvanceZone()
        assertEquals(0, vm.uiState.value.zoneIndex)

        vm.onTutorialAction(TutorialAction.EXP_NOTICE_DISMISSED)
        vm.onAdvanceZone()
        assertEquals(1, vm.uiState.value.zoneIndex)
        assertEquals(TutorialPhase.COMPLETED, vm.uiState.value.tutorialPhase)
    }

    @Test
    fun visibleMessage_blocksAllGameplay_includingQuestionsAndNotice() {
        // Regla principal de QA: con cualquier mensaje visible, nada del juego
        // responde — ni Pokéball, ni hierba, ni opciones, ni avance, ni reset.
        val offer = vmAtOffer()   // OFFER
        tapZone(offer, 1)
        offer.onTallGrassToggle()
        offer.onOpenOptions()
        offer.onReset()
        assertEquals(0L, offer.uiState.value.zoneClicks)
        assertFalse(offer.uiState.value.isTallGrass)
        assertFalse(offer.uiState.value.isOptionsOpen)
        assertEquals(TutorialPhase.OFFER, offer.uiState.value.tutorialPhase)

        // El aviso de experiencia también bloquea mientras se lee.
        val notice = vmUpToFarewell().apply {
            onTutorialAction(TutorialAction.FAREWELL_DISMISSED)
            tapZone(this, 2)   // barra llena → EXP_NOTICE
        }
        tapZone(notice, 1)
        notice.onTallGrassToggle()
        notice.onAdvanceZone()
        // La barra ya está al tope (capada en el umbral) y el toque extra no
        // generó nada nuevo: ni experiencia, ni hierba, ni avance.
        assertEquals(2L, notice.uiState.value.zoneClicks)
        assertFalse(notice.uiState.value.isTallGrass)
        assertEquals(0, notice.uiState.value.zoneIndex)
        assertEquals(TutorialPhase.EXP_NOTICE, notice.uiState.value.tutorialPhase)

        // Cerrado el aviso: juego normal, incluido Avanzar.
        notice.onTutorialAction(TutorialAction.EXP_NOTICE_DISMISSED)
        notice.onAdvanceZone()
        assertEquals(1, notice.uiState.value.zoneIndex)
        assertEquals(TutorialPhase.COMPLETED, notice.uiState.value.tutorialPhase)
    }

    @Test
    fun offerMessage_closesViaItsExplicitButtons() {
        val vm = vmAtOffer()   // OFFER

        // El toque no responde por el jugador; solo los botones.
        vm.onTutorialMessageTap()
        assertEquals(TutorialPhase.OFFER, vm.uiState.value.tutorialPhase)

        vm.onTutorialAction(TutorialAction.ACCEPT_OFFER)
        assertEquals(TutorialPhase.CONFIRM, vm.uiState.value.tutorialPhase)
    }

    @Test
    fun afterFarewell_everyInteractionIsBackWithoutBlocks() {
        val vm = vmUpToFarewell().apply {
            onTutorialAction(TutorialAction.FAREWELL_DISMISSED)   // EXP_WAIT
        }

        // Jugabilidad completa mientras se espera la barra y el primer avance.
        vm.onTallGrassToggle()
        assertTrue(vm.uiState.value.isTallGrass)
        vm.onTallGrassToggle()
        vm.onOpenOptions()
        assertTrue(vm.uiState.value.isOptionsOpen)
        vm.onCloseOptions()
        vm.onSelectTab(AppTab.Map)
        assertEquals(AppTab.Map, vm.uiState.value.contentTab)
        vm.onSelectTab(AppTab.Home)
        assertEquals(AppTab.Home, vm.uiState.value.contentTab)
        assertEquals(TutorialPhase.EXP_WAIT, vm.uiState.value.tutorialPhase)
    }

    // ───────────────── Mensaje: dos momentos por paso (ajuste 5.1) ─────────────────

    @Test
    fun messageTap_closesTheMessageWithoutCompletingTheStep() {
        val vm = viewModel().apply {
            onFinishIntro("Red", "charmander")
            onTutorialAction(TutorialAction.ACCEPT_OFFER)
            onTutorialAction(TutorialAction.BEGIN_TUTORIAL)   // DEPLOY_POKEBALL
        }

        vm.onTutorialMessageTap()

        // La fase se mantiene; solo cambia el momento del paso.
        assertEquals(TutorialPhase.DEPLOY_POKEBALL, vm.uiState.value.tutorialPhase)
        assertTrue(vm.uiState.value.tutorialMessageClosed)

        // Un segundo toque es no-op (el mensaje ya está cerrado).
        vm.onTutorialMessageTap()
        assertTrue(vm.uiState.value.tutorialMessageClosed)
    }

    @Test
    fun messageVisible_blocksTheStepAction_untilItIsClosed() {
        val vm = viewModel().apply {
            onFinishIntro("Red", "charmander")
            onTutorialAction(TutorialAction.ACCEPT_OFFER)
            onTutorialAction(TutorialAction.BEGIN_TUTORIAL)
            onTutorialMessageTap()
            onPokeBallExpanded(true)   // OPEN_MAP, mensaje reabierto
        }

        // Mensaje visible: la interacción del paso no cuenta todavía.
        vm.onSelectTab(AppTab.Map)
        assertEquals(TutorialPhase.OPEN_MAP, vm.uiState.value.tutorialPhase)
        assertEquals(AppTab.Home, vm.uiState.value.contentTab)
        assertFalse(vm.uiState.value.tutorialMessageClosed)

        // Cerrado el mensaje, la fase se mantiene y solo su interacción se
        // habilita.
        vm.onTutorialMessageTap()
        vm.onSelectTab(AppTab.Map)
        assertEquals(TutorialPhase.RETURN_MAP, vm.uiState.value.tutorialPhase)
    }

    @Test
    fun messageTap_onQuestions_requiresAnExplicitAnswer() {
        val vm = vmAtOffer()   // OFFER

        vm.onTutorialMessageTap()
        assertEquals(TutorialPhase.OFFER, vm.uiState.value.tutorialPhase)

        vm.onTutorialAction(TutorialAction.ACCEPT_OFFER)   // → CONFIRM
        vm.onTutorialMessageTap()
        assertEquals(TutorialPhase.CONFIRM, vm.uiState.value.tutorialPhase)
    }

    @Test
    fun messageTap_onFarewellAndNotice_triggersTheirSpecificTransitions() {
        val vm = vmUpToFarewell()

        vm.onTutorialMessageTap()   // FAREWELL → EXP_WAIT
        assertEquals(TutorialPhase.EXP_WAIT, vm.uiState.value.tutorialPhase)

        tapZone(vm, 2)   // barra llena → EXP_NOTICE
        vm.onTutorialMessageTap()   // EXP_NOTICE → ADVANCE_WAIT
        assertEquals(TutorialPhase.ADVANCE_WAIT, vm.uiState.value.tutorialPhase)
    }

    @Test
    fun restore_conservesTheMessageClosedFlag() {
        // Paso con el mensaje ya cerrado: al reabrir, la interacción del paso
        // está habilitada de inmediato.
        val open = viewModel(
            saved = GameSnapshot(
                starterSpeciesId = "charmander",
                tutorialPhase = TutorialPhase.DEPLOY_POKEBALL,
                tutorialMessageClosed = true
            )
        )
        open.onPokeBallExpanded(true)
        assertEquals(TutorialPhase.OPEN_MAP, open.uiState.value.tutorialPhase)

        // Paso con el mensaje sin cerrar: hay que cerrarlo primero.
        val waiting = viewModel(
            saved = GameSnapshot(
                starterSpeciesId = "charmander",
                tutorialPhase = TutorialPhase.DEPLOY_POKEBALL,
                tutorialMessageClosed = false
            )
        )
        waiting.onPokeBallExpanded(true)
        assertEquals(TutorialPhase.DEPLOY_POKEBALL, waiting.uiState.value.tutorialPhase)
        waiting.onTutorialMessageTap()
        waiting.onPokeBallExpanded(true)
        assertEquals(TutorialPhase.OPEN_MAP, waiting.uiState.value.tutorialPhase)
    }

    @Test
    fun messageVisibility_followsThePhaseAndTheFlag() {
        // OFFER/CONFIRM/FAREWELL/EXP_NOTICE: mensaje siempre visible.
        listOf(
            TutorialPhase.OFFER,
            TutorialPhase.CONFIRM,
            TutorialPhase.FAREWELL,
            TutorialPhase.EXP_NOTICE
        ).forEach { phase ->
            assertTrue(
                "$phase siempre muestra mensaje",
                phase.isMessageVisible(messageClosed = true)
            )
        }
        // Pasos guiados: visible hasta cerrarse el mensaje.
        listOf(
            TutorialPhase.DEPLOY_POKEBALL,
            TutorialPhase.OPEN_MAP,
            TutorialPhase.RETURN_MAP,
            TutorialPhase.OPEN_POKEDEX,
            TutorialPhase.RETURN_POKEDEX
        ).forEach { phase ->
            assertTrue(
                "$phase abre con mensaje",
                phase.isMessageVisible(messageClosed = false)
            )
            assertFalse(
                "$phase oculta el mensaje al cerrarlo",
                phase.isMessageVisible(messageClosed = true)
            )
        }
        // Resto: sin mensaje.
        listOf(
            TutorialPhase.PENDING,
            TutorialPhase.EXP_WAIT,
            TutorialPhase.ADVANCE_WAIT,
            TutorialPhase.SKIPPED,
            TutorialPhase.COMPLETED
        ).forEach { phase ->
            assertFalse(
                "$phase no muestra mensaje",
                phase.isMessageVisible(messageClosed = false)
            )
        }
    }

    // ───────────────── QA: Pokéball inactiva y regresos ─────────────────

    @Test
    fun openMap_pokeBallIsFullyInactive_evenWithTheMessageClosed() {
        val vm = viewModel().apply {
            onFinishIntro("Red", "charmander")
            onTutorialAction(TutorialAction.ACCEPT_OFFER)
            onTutorialAction(TutorialAction.BEGIN_TUTORIAL)
            onTutorialMessageTap()
            onPokeBallExpanded(true)   // OPEN_MAP, bola ya expandida
            onTutorialMessageTap()     // mensaje cerrado: solo vale el botón Mapa
        }

        // Pulsaciones: nada de experiencia, ni movimiento, ni pestaña.
        tapZone(vm, 3)
        assertEquals(0L, vm.uiState.value.zoneClicks)
        assertEquals(AppTab.Home, vm.uiState.value.contentTab)

        // El despliegue no vuelve a contar (no es la interacción del paso).
        vm.onPokeBallExpanded(true)
        assertEquals(TutorialPhase.OPEN_MAP, vm.uiState.value.tutorialPhase)

        // La única salida del paso es el botón del Mapa.
        vm.onSelectTab(AppTab.Map)
        assertEquals(TutorialPhase.RETURN_MAP, vm.uiState.value.tutorialPhase)
    }

    @Test
    fun returnFromMap_andPokedex_generateNoExperience() {
        val vm = viewModel().apply {
            onFinishIntro("Red", "charmander")
            onTutorialAction(TutorialAction.ACCEPT_OFFER)
            onTutorialAction(TutorialAction.BEGIN_TUTORIAL)
            onTutorialMessageTap()
            onPokeBallExpanded(true)
            onTutorialMessageTap()
            onSelectTab(AppTab.Map)
            onTutorialMessageTap()   // RETURN_MAP, bola habilitada solo para volver
        }

        // El clic de juego no existe en el regreso: cero experiencia.
        tapZone(vm, 3)
        assertEquals(0L, vm.uiState.value.zoneClicks)

        // El toque de la Pokéball (navegación) completa el paso.
        vm.onSelectTab(AppTab.Home)
        assertEquals(TutorialPhase.OPEN_POKEDEX, vm.uiState.value.tutorialPhase)

        // Igual en el regreso de la Pokédex.
        vm.onTutorialMessageTap()
        vm.onOpenDex()
        vm.onTutorialMessageTap()
        tapZone(vm, 3)
        assertEquals(0L, vm.uiState.value.zoneClicks)
        vm.onCloseDex()
        assertEquals(TutorialPhase.FAREWELL, vm.uiState.value.tutorialPhase)
    }

    @Test
    fun returnPokedex_theMapButtonCannotLeaveTheStepThroughTheBackDoor() {
        // Documenta la acción compuesta del botón Mapa de la pantalla de la
        // Pokédex (bloqueado en la UI por el shell): cerrar la dex es la
        // interacción del paso, pero la mitad de navegación a Mapa está
        // bloqueada — el regreso previsto es el toque de la Pokéball.
        val vm = vmAtDexOpenStep(messageClosed = true).apply {
            onOpenDex()              // RETURN_POKEDEX, mensaje reabierto
            onTutorialMessageTap()   // cierra el mensaje del regreso
        }

        vm.onCloseDex()              // mitad de cierre: interacción del paso
        assertEquals(TutorialPhase.FAREWELL, vm.uiState.value.tutorialPhase)

        vm.onSelectTab(AppTab.Map)   // mitad de navegación: bloqueada
        assertEquals(AppTab.Home, vm.uiState.value.contentTab)
    }

    // ───────────────── Auxiliares ─────────────────
}
