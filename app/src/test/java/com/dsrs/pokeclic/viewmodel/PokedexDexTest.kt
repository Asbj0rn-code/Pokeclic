/**
 * Pruebas de la Pokédex en el ViewModel: navegación, bloqueo en estados
 * incompatibles, pausa del caminar, historial de capturas y compatibilidad con
 * guardados anteriores a `caughtSpecies`.
 */
package com.dsrs.pokeclic.viewmodel

import com.dsrs.pokeclic.data.GameStateStore
import com.dsrs.pokeclic.model.Direction
import com.dsrs.pokeclic.model.DexStatus
import com.dsrs.pokeclic.model.FishingPhase
import com.dsrs.pokeclic.model.GameSnapshot
import com.dsrs.pokeclic.model.PokedexCatalog
import com.dsrs.pokeclic.model.PokedexSpecies
import com.dsrs.pokeclic.model.SpeciesCatalog
import com.dsrs.pokeclic.model.SpriteRepository
import com.dsrs.pokeclic.model.Zone
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PokedexDexTest {

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

    /// Duplicados a propósito de los dobles privados de `MainViewModelTest`
    /// (no hay forma de reutilizarlos sin hacerlos internos a producción).

    private class FakeStore : GameStateStore {
        var saved: GameSnapshot? = null
        override fun load(): GameSnapshot? = saved
        override fun save(snapshot: GameSnapshot) { saved = snapshot }
        override fun clear(): Boolean { saved = null; return true }
    }

    private class FakeSprites : SpriteRepository {
        override fun frameCount(direction: Direction): Int = 1
        override fun frame(direction: Direction, index: Int): Int = 0
    }

    /** Catálogo de dex de laboratorio (números de posición, no de Kanto). */
    private fun dexCatalog() = PokedexCatalog(
        listOf(
            PokedexSpecies("charmander", 1, "Charmander", "Fuego"),
            PokedexSpecies("pidgey", 2, "Pidgey", "Volador"),
            PokedexSpecies("rattata", 3, "Rattata", "Normal"),
            PokedexSpecies("caterpie", 4, "Caterpie", "Bicho")
        )
    )

    /**
     * Zona única con **una sola** fauna (pidgey): el spawn determinista evita
     * depender de la aleatoriedad para las pruebas de avistamiento.
     */
    private val grassZones = listOf(Zone("z_hierba", "Hierba", 100L, listOf("pidgey")))

    private fun viewModel(
        saved: GameSnapshot? = null,
        nowMs: () -> Long = { 0L },
        zones: List<Zone> = grassZones,
        random: Random = Random.Default,
        catalog: PokedexCatalog = dexCatalog(),
        loadFailed: Boolean = false,
        store: FakeStore = FakeStore().apply { this.saved = saved }
    ) = MainViewModel(
        FakeSprites(),
        store,
        zones = zones,
        random = random,
        nowMs = nowMs,
        pokedex = catalog,
        pokedexLoadFailed = loadFailed
    )

    // --- Navegación ---------------------------------------------------------

    @Test
    fun onOpenDex_closesOptionsAndOpensTheDex() {
        val vm = viewModel()
        vm.onFinishIntro("Ash", "charmander")
        vm.onOpenOptions()
        assertTrue(vm.uiState.value.isOptionsOpen)

        vm.onOpenDex()

        val state = vm.uiState.value
        assertFalse(state.isOptionsOpen)
        assertTrue(state.isPokedexOpen)
        // La dex lista el catálogo completo con el starter capturado.
        assertEquals(4, state.dexEntries.size)
        assertEquals(DexStatus.CAUGHT, state.dexEntries.first { it.speciesId == "charmander" }.status)
        assertEquals(1, state.dexCaughtCount)
        assertEquals(1, state.dexSeenCount)
    }

    @Test
    fun onCloseDex_returnsToTheGame() {
        val vm = viewModel()
        vm.onFinishIntro("Ash", "charmander")
        vm.onOpenDex()
        assertTrue(vm.uiState.value.isPokedexOpen)

        vm.onCloseDex()

        assertFalse(vm.uiState.value.isPokedexOpen)
    }

    // --- Bloqueos -----------------------------------------------------------

    @Test
    fun onOpenDex_isBlockedWithoutStarter() {
        val vm = viewModel() // intro en curso: needsStarter

        vm.onOpenDex()

        assertFalse(vm.uiState.value.isPokedexOpen)
    }

    @Test
    fun onOpenDex_isBlockedDuringAnEncounter() {
        val vm = viewModel()
        vm.onFinishIntro("Ash", "charmander")
        vm.onTallGrassToggle()
        vm.onPokeBallTouch(Direction.BACK)
        testScope.advanceTimeBy(4_000L)
        testScope.runCurrent()
        assertNotNull(vm.uiState.value.encounter)

        vm.onOpenDex()

        assertFalse(vm.uiState.value.isPokedexOpen)
    }

    @Test
    fun onOpenDex_isBlockedWhileTheLeadOverlayIsOpen() {
        val vm = viewModel()
        vm.onFinishIntro("Ash", "charmander")
        vm.onOpenLead()

        vm.onOpenDex()

        assertFalse(vm.uiState.value.isPokedexOpen)
    }

    @Test
    fun onOpenDex_isBlockedInGymIntroAndDuringTheGymFight() {
        val zones = listOf(
            Zone("z1", "Z1", 2L, listOf("pidgey")),
            Zone("z2", "Z2", 3L, listOf("rattata"), gymId = "brock")
        )
        val vm = viewModel(zones = zones)
        vm.onFinishIntro("Ash", "charmander")
        // Llena z1, avanza a la zona del gimnasio y llena también su barra.
        repeat(5) { vm.onPokeBallTouch(Direction.BACK) }
        assertTrue(vm.uiState.value.canAdvanceZone)
        vm.onAdvanceZone()
        repeat(5) { vm.onPokeBallTouch(Direction.BACK) }
        assertTrue(vm.uiState.value.isGymReady)
        vm.onEnterGym()
        // Capa de entrada del gimnasio: bloqueada.
        vm.onOpenDex()
        assertFalse(vm.uiState.value.isPokedexOpen)

        // "Omitir" consigue la cuenta atrás y, al caducar, el combate arranca:
        // sigue bloqueada.
        vm.onGymIntroSkip()
        testScope.advanceTimeBy(2_000L)
        testScope.runCurrent()
        assertNull(vm.uiState.value.gymIntro)
        assertNotNull(vm.uiState.value.gym)
        vm.onOpenDex()
        assertFalse(vm.uiState.value.isPokedexOpen)
    }

    // --- Pausa del caminar --------------------------------------------------

    /**
     * Con la dex abierta, el hierba alta no genera encuentros ni avanza: el
     * bucle se ignora en pos de la pantalla de la dex, y al cerrarla hace falta
     * una **pulsación nueva** (el instante del cierre invalida el temporizador).
     */
    @Test
    fun dexOpen_pausesWalkingAndClosingRequiresANewTap() {
        var now = 5_000L
        val vm = viewModel(nowMs = { now })
        vm.onFinishIntro("Ash", "charmander")
        vm.onTallGrassToggle()
        vm.onPokeBallTouch(Direction.BACK)
        testScope.advanceTimeBy(1_000L)
        testScope.runCurrent()
        val progressAtOpen = vm.uiState.value.walkProgress
        assertTrue(progressAtOpen > 0f)

        vm.onOpenDex()
        testScope.advanceTimeBy(5_000L)
        testScope.runCurrent()

        // Congelado: ni crecimiento ni spawns debajo de la dex.
        assertNull(vm.uiState.value.encounter)
        val frozen = vm.uiState.value.walkProgress
        assertTrue(abs(frozen - progressAtOpen) < 1e-4f)
        // Y el avistamiento de pidgey **no** llega: sigues sin verlo.
        val pidgeyRow = vm.uiState.value.dexEntries.first { it.speciesId == "pidgey" }
        assertEquals(DexStatus.UNKNOWN, pidgeyRow.status)

        // Cierre: la ventana de actividad queda invalidada (lastWalkTapMs = 0).
        vm.onCloseDex()
        now = 100_000L
        testScope.advanceTimeBy(3_000L)
        testScope.runCurrent()
        assertNull(vm.uiState.value.encounter)
        assertTrue(abs(vm.uiState.value.walkProgress - frozen) < 1e-4f)

        // Una pulsación nueva reactiva el temporizador de caminar.
        vm.onPokeBallTouch(Direction.BACK)
        testScope.advanceTimeBy(3_000L)
        testScope.runCurrent()
        assertNotNull(vm.uiState.value.encounter)
        // Y el avistamiento llega a la dex.
        assertEquals(2, vm.uiState.value.dexSeenCount)
    }

    // --- Historial de capturas ---------------------------------------------

    @Test
    fun reset_keepsCaughtAndSeenHistory() {
        val vm = viewModel(
            saved = GameSnapshot(
                starterSpeciesId = "charmander",
                seenSpecies = setOf("charmander", "pidgey"),
                caughtSpecies = setOf("charmander")
            )
        )
        // El avistamiento de esta run: se suma al historial (compatibilidad).
        assertEquals(setOf("charmander", "pidgey"), vm.uiState.value.seenSpecies)

        vm.onReset()

        val state = vm.uiState.value
        assertEquals(setOf("charmander"), state.caughtSpecies)
        assertEquals(setOf("charmander", "pidgey"), state.seenSpecies)
        // Historia permanente ≠ posesión: tras el reset solo el inicial
        // elegido sigue CAUGHT; pidgey ha sido conocida, pero no poseída.
        assertEquals(DexStatus.CAUGHT, state.dexEntries.first { it.speciesId == "charmander" }.status)
        assertEquals(DexStatus.SEEN, state.dexEntries.first { it.speciesId == "pidgey" }.status)
        assertEquals(1, state.dexCaughtCount)
        assertEquals(2, state.dexSeenCount)
    }

    @Test
    fun restore_deducesCaughtFromSaveWithoutCaughtField() {
        // Guardado antiguo (sin clave "caught"): se deducen capturas de
        // posesiones, recompensas y starter, y toda captura deducida es vista.
        val vm = viewModel(
            saved = GameSnapshot(
                starterSpeciesId = "charmander",
                rewardedSpecies = setOf("rattata"),
                ownedSpecies = setOf("caterpie")
            )
        )
        val state = vm.uiState.value

        assertEquals(
            setOf("charmander", "rattata", "caterpie"),
            state.caughtSpecies
        )
        assertEquals(
            setOf("charmander", "rattata", "caterpie"),
            state.seenSpecies
        )
        // La dex lo refleja: starter (CAUGHT) + caterpie en posesión (CAUGHT);
        // rattata es historia permanente (recompensa anterior) → SEEN;
        // pidgey nunca conocida → UNKNOWN.
        assertEquals(2, state.dexCaughtCount)
        assertEquals(DexStatus.CAUGHT, state.dexEntries.first { it.speciesId == "charmander" }.status)
        assertEquals(DexStatus.CAUGHT, state.dexEntries.first { it.speciesId == "caterpie" }.status)
        assertEquals(DexStatus.SEEN, state.dexEntries.first { it.speciesId == "rattata" }.status)
        assertEquals(DexStatus.UNKNOWN, state.dexEntries.first { it.speciesId == "pidgey" }.status)
        assertEquals((1..4).toList(), state.dexEntries.map { it.dexNumber })
    }

    // --- Error de carga -----------------------------------------------------

    @Test
    fun catalogLoadFailure_showsErrorButGameKeepsWorking() {
        val vm = viewModel(loadFailed = true, catalog = PokedexCatalog.EMPTY)
        vm.onFinishIntro("Ash", "charmander")

        val state = vm.uiState.value
        assertTrue(state.dexLoadFailed)
        assertTrue(state.dexEntries.isEmpty())
        assertEquals(0, state.dexCaughtCount)

        // El juego sigue funcionando: clics, hierba y navegación no se ven afectados.
        vm.onPokeBallTouch(Direction.BACK)
        assertEquals(1L, vm.uiState.value.zoneClicks)
        vm.onOpenDex()
        assertTrue(vm.uiState.value.isPokedexOpen)
    }

    // --- Captura confirmada (determinista) -----------------------------------

    /**
     * Doble de `Random` que **fija la cola del riel**: todo `nextFloat` devuelve
     * [tail]. Con la pesca esto es determinista sin depender de la suerte:
     * - El sprite nace y vagabundea pegado a la zona alta del riel (≈0,82–0,9),
     *   siempre dentro del margen de solape de la barra cuando el jugador
     *   mantiene pulsado (la barra sube hasta ≈0,89).
     * - Los dardos (probabilidad por tick) nunca salen porque
     *   `[tail] > dartChance·dt`.
     * Mantener `held = true` desde que aparece el encuentro hace que el solape
     * sea continuo y el progreso llegue a 1: fase `CAPTURED` garantizada.
     */
    private class HighRielRandom(private val tail: Float = 0.9f) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextFloat(): Float = tail
    }

    /** Avanza el reloj virtual en tandas de 100 ms hasta que hay encuentro. */
    private fun advanceUntilEncounter(vm: MainViewModel) {
        repeat(60) {
            testScope.advanceTimeBy(100L)
            testScope.runCurrent()
            if (vm.uiState.value.encounter != null) return
        }
        fail("La hierba alta no generó un encuentro")
    }

    /** Pone en marcha la partida y la lleva, manteniendo pulsado, a CAPTURED. */
    private fun playToCaptured(vm: MainViewModel) {
        advanceUntilEncounter(vm)
        assertEquals("pidgey", vm.uiState.value.encounter?.speciesId)
        // Pulso inmediato: el progreso no puede quedarse a 0 con la gracia agotada.
        vm.onFishingHold(true)
        testScope.advanceTimeBy(7_000L)
        testScope.runCurrent()
        assertEquals(FishingPhase.CAPTURED, vm.uiState.value.fishing?.phase)
    }

    /**
     * Captura confirmada sin ganar la partida al azar: se prepara el `CAPTURED`
     * con el `Random` inyectado del ViewModel (mecanismo de prueba ya existente)
     * y se verifica que continuar registra captura, avistamiento y posesión,
     * con la recompensa **una única vez**.
     */
    @Test
    fun fishingResultContinue_capturedRegistersHistoryAndRewardsOnce() {
        val vm = viewModel(random = HighRielRandom())
        vm.onFinishIntro("Ash", "charmander")
        vm.onTallGrassToggle()

        // Primera captura de la run.
        vm.onPokeBallTouch(Direction.BACK)
        playToCaptured(vm)
        // Al confirmar todavía no está capturada en el historial.
        assertTrue("pidgey" !in vm.uiState.value.caughtSpecies)

        val coinsBefore = vm.uiState.value.coins
        val expectedCoins = SpeciesCatalog.require("pidgey").coinReward.toLong()
        vm.onFishingResultContinue()

        with(vm.uiState.value) {
            assertTrue(caughtSpecies.contains("pidgey"))
            assertTrue(seenSpecies.contains("pidgey"))
            assertTrue(ownedSpecies.contains("pidgey"))
            assertEquals(setOf("charmander", "pidgey"), caughtSpecies)
            assertNull(encounter)
            assertNull(fishing)
        }
        assertEquals(coinsBefore + expectedCoins, vm.uiState.value.coins)
        assertTrue("pidgey" in vm.uiState.value.rewardedSpecies)
        // La dex lo refleja: starter + pidgey capturadas…
        assertEquals(2, vm.uiState.value.dexCaughtCount)
        // …y la fila de pidgey pasa a CAUGHT.
        val pidgeyRow = vm.uiState.value.dexEntries.first { it.speciesId == "pidgey" }
        assertEquals(DexStatus.CAUGHT, pidgeyRow.status)

        // Segunda captura de la MISMA especie: el registro no se altera y la
        // recompensa no se vuelve a pagar (solo se paga la primera vez).
        vm.onPokeBallTouch(Direction.BACK)
        playToCaptured(vm)
        val coinsAfterFirst = vm.uiState.value.coins
        vm.onFishingResultContinue()

        with(vm.uiState.value) {
            assertEquals(setOf("charmander", "pidgey"), caughtSpecies)
            // El starter no está en `ownedSpecies` (las posesiones son de
            // salvajes capturados: el starter cuenta aparte).
            assertEquals(setOf("pidgey"), ownedSpecies)
            assertEquals(coinsAfterFirst, coins)
        }
    }

    /**
     * Flujo completo tras la recategorización del estado: capturar una salvaje
     * la pone en posesión (CAUGHT); **el reset vacía la posesión** y la deja en
     * SEEN quedando solo el inicial como CAUGHT; el selector de principal solo
     * ofrece el inicial; los estados sobreviven a guardar/cargar; y
     * recapturarla la vuelve a poner en posesión **sin repetir la recompensa**.
     */
    @Test
    fun captureThenReset_dropsOwnedToSeenKeepsStarterAndRewardsOnce() {
        val store = FakeStore()
        val vm = viewModel(random = HighRielRandom(), store = store)
        vm.onFinishIntro("Ash", "charmander")
        vm.onTallGrassToggle()

        // Captura de pidgey: +5 monedas (primera vez), en posesión.
        vm.onPokeBallTouch(Direction.BACK)
        playToCaptured(vm)
        vm.onFishingResultContinue()
        assertEquals(DexStatus.CAUGHT, vm.uiState.value.dexEntries.first { it.speciesId == "pidgey" }.status)
        assertEquals(5L, vm.uiState.value.coins)

        // Reset: la posesión de la run se vacía; queda solo el starter.
        vm.onReset()
        with(vm.uiState.value) {
            assertTrue(ownedSpecies.isEmpty())
            assertEquals("charmander", leadSpeciesId)
            // El selector de principal solo ofrece al inicial.
            assertEquals(listOf("charmander"), leadOptions.map { it.id })
            // Historia permanente intacta…
            assertEquals(setOf("charmander", "pidgey"), caughtSpecies)
            assertEquals(setOf("charmander", "pidgey"), seenSpecies)
            // …pero los estados de la dex siguen la posesión: pidgey → SEEN.
            assertEquals(DexStatus.CAUGHT, dexEntries.first { it.speciesId == "charmander" }.status)
            assertEquals(DexStatus.SEEN, dexEntries.first { it.speciesId == "pidgey" }.status)
            assertEquals(DexStatus.UNKNOWN, dexEntries.first { it.speciesId == "rattata" }.status)
            assertEquals(1, dexCaughtCount)
            assertEquals(2, dexSeenCount)
            assertEquals(5L, coins) // el reset conserva la meta
        }

        // Guardado y carga: los mismos estados justo después de persistir.
        val restored = viewModel(saved = store.saved)
        with(restored.uiState.value) {
            assertEquals(DexStatus.CAUGHT, dexEntries.first { it.speciesId == "charmander" }.status)
            assertEquals(DexStatus.SEEN, dexEntries.first { it.speciesId == "pidgey" }.status)
            assertTrue(ownedSpecies.isEmpty())
            assertEquals(listOf("charmander"), leadOptions.map { it.id })
        }

        // Recaptura de pidgey: vuelve a CAUGHT y las monedas no se repiten.
        // (El reset baja la hierba alta: hay que volver a activarla.)
        vm.onTallGrassToggle()
        vm.onPokeBallTouch(Direction.BACK)
        playToCaptured(vm)
        vm.onFishingResultContinue()
        with(vm.uiState.value) {
            assertEquals(DexStatus.CAUGHT, dexEntries.first { it.speciesId == "pidgey" }.status)
            assertEquals(setOf("pidgey"), ownedSpecies)
            assertEquals(5L, coins)
        }
    }
}
