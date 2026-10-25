package com.dsrs.pokeclic.viewmodel

import com.dsrs.pokeclic.data.GameStateStore
import com.dsrs.pokeclic.model.Direction
import com.dsrs.pokeclic.model.GameSnapshot
import com.dsrs.pokeclic.model.GymPhase
import com.dsrs.pokeclic.model.SpriteRepository
import com.dsrs.pokeclic.model.Zone
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

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

    /**
     * Zonas mínimas de prueba: normal (umbral 2) → gimnasio de Brock (3) →
     * última (4). Más cortas que el catálogo para tests con pocos taps.
     */
    private val testZones = listOf(
        Zone("z1", "Z1", 2L, listOf("pidgey")),
        Zone("z2", "Z2", 3L, listOf("rattata"), gymId = "brock"),
        Zone("z3", "Z3", 4L, listOf("caterpie"))
    )

    private fun viewModel(saved: GameSnapshot? = null) = MainViewModel(
        FakeSprites(),
        FakeStore().apply { this.saved = saved },
        zones = testZones
    )

    /** Toques sobre la pokéball (usados aquí solo como clics de zona). */
    private fun tapZone(vm: MainViewModel, times: Int) {
        repeat(times) { vm.onPokeBallTouch(0f, 0f, 100f) }
    }

    @Test
    fun blankName_doesNotFinishIntro() {
        val vm = viewModel()

        vm.onFinishIntro("   ", "charmander")

        assertNull(vm.uiState.value.playerName)
        assertNull(vm.uiState.value.starterSpeciesId)
    }

    @Test
    fun validName_isTrimmedAndStored() {
        val vm = viewModel()

        vm.onFinishIntro("  Ash  ", "charmander")

        assertEquals("Ash", vm.uiState.value.playerName)
        assertEquals("charmander", vm.uiState.value.starterSpeciesId)
    }

    // --- Avance de zona manual ------------------------------------------------

    @Test
    fun zoneClicks_capAtThresholdAndNeverAutoAdvance() {
        val vm = viewModel()

        tapZone(vm, times = 5)

        val state = vm.uiState.value
        assertEquals(0, state.zoneIndex)
        assertEquals(2L, state.zoneClicks)
        assertTrue(state.canAdvanceZone)
    }

    @Test
    fun onAdvanceZone_beforeThreshold_isNoop() {
        val vm = viewModel()

        vm.onAdvanceZone()

        val state = vm.uiState.value
        assertEquals(0, state.zoneIndex)
        assertEquals(0L, state.zoneClicks)
    }

    @Test
    fun onAdvanceZone_movesToNextZoneAndResetsProgress() {
        val vm = viewModel()
        tapZone(vm, times = 5)

        vm.onAdvanceZone()

        val state = vm.uiState.value
        assertEquals(1, state.zoneIndex)
        assertEquals(0L, state.zoneClicks)
        assertFalse(state.canAdvanceZone)
    }

    @Test
    fun onAdvanceZone_atLastZone_isNoop() {
        val vm = viewModel(saved = GameSnapshot(zoneIndex = 2, zoneClicks = 4L))

        assertFalse(vm.uiState.value.canAdvanceZone)

        vm.onAdvanceZone()

        val state = vm.uiState.value
        assertEquals(2, state.zoneIndex)
        assertEquals(4L, state.zoneClicks)
    }

    // --- Requisito de gimnasio dentro de la run -------------------------------

    @Test
    fun oldSave_gymZoneAtThreshold_showsGymAndNotAdvance() {
        // Guardado antiguo (sin `gym_cleared_run`): el campo por defecto carga
        // vacío, así que el gimnasio queda pendiente en la run.
        val vm = viewModel(saved = GameSnapshot(zoneIndex = 1, zoneClicks = 3L))

        assertTrue(vm.uiState.value.gymClearedRun.isEmpty())
        assertTrue(vm.uiState.value.isGymReady)
        assertFalse(vm.uiState.value.canAdvanceZone)
    }

    @Test
    fun gymZone_atThresholdWithPermanentMedal_requiresGymThisRun() {
        // Medalla vieja persistente: no exime de repetir el gimnasio.
        val vm = viewModel(
            saved = GameSnapshot(
                medals = setOf("brock"),
                zoneIndex = 1,
                zoneClicks = 3L
            )
        )

        val state = vm.uiState.value
        assertTrue(state.isGymReady)
        assertFalse(state.canAdvanceZone)
    }

    @Test
    fun gymClearedInRun_enablesAdvanceButton() {
        // Estado equivalente al que deja `winGym`: gimnasio ganado en la run y
        // barra al 100 %, sin haber avanzado de zona.
        val vm = viewModel(
            saved = GameSnapshot(
                medals = setOf("brock"),
                gymClearedRun = setOf("brock"),
                zoneIndex = 1,
                zoneClicks = 3L
            )
        )

        val state = vm.uiState.value
        assertFalse(state.isGymReady)
        assertTrue(state.canAdvanceZone)

        vm.onAdvanceZone()

        assertEquals(2, vm.uiState.value.zoneIndex)
        assertEquals(0L, vm.uiState.value.zoneClicks)
    }

    @Test
    fun onReset_clearsRunGymsButKeepsMedals() {
        val vm = viewModel(
            saved = GameSnapshot(
                medals = setOf("brock"),
                gymClearedRun = setOf("brock"),
                zoneIndex = 1,
                zoneClicks = 3L
            )
        )

        vm.onReset()

        val state = vm.uiState.value
        assertEquals(setOf("brock"), state.medals)
        assertTrue(state.gymClearedRun.isEmpty())
        assertEquals(0, state.zoneIndex)
        assertFalse(state.isGymReady)
        assertFalse(state.canAdvanceZone)
    }

    // --- Victoria de gimnasio: combate completo -------------------------------

    @Test
    fun winGym_closesCombatWithoutAutoAdvancing() {
        val vm = viewModel()
        tapZone(vm, times = 5)          // Z1 al límite
        vm.onAdvanceZone()              // → Z2 (gimnasio)
        tapZone(vm, times = 3)          // Z2 al límite

        assertTrue(vm.uiState.value.isGymReady)
        vm.onEnterGym()
        assertNotNull(vm.uiState.value.gym)

        // Se consume la oleada completa con el reloj virtual: como el tiempo
        // real apenas avanza, ningún proyectil impacta y el líder queda expuesto.
        testScope.advanceTimeBy(11_000L)
        testScope.runCurrent()
        assertEquals(GymPhase.EXPOSED, vm.uiState.value.gym?.phase)

        // Potencia de clic básica (1) contra Brock: 60 golpes expuestos.
        var guard = 0
        while (vm.uiState.value.gym != null && guard++ < 120) {
            vm.onGymEnemyHit()
        }

        val state = vm.uiState.value
        assertNull(state.gym)
        assertEquals(1, state.zoneIndex)        // NO autoavanza tras la victoria
        assertEquals(3L, state.zoneClicks)      // la barra sigue al 100 %
        assertTrue("brock" in state.medals)
        assertTrue("brock" in state.gymClearedRun)
        assertTrue(state.canAdvanceZone)

        vm.onAdvanceZone()
        assertEquals(2, vm.uiState.value.zoneIndex)
        assertEquals(0L, vm.uiState.value.zoneClicks)
    }
}
