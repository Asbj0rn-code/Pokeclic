package com.dsrs.pokeclic.viewmodel

import com.dsrs.pokeclic.data.GameStateStore
import com.dsrs.pokeclic.model.Direction
import com.dsrs.pokeclic.model.GameSnapshot
import com.dsrs.pokeclic.model.GymIntroPhase
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

    private fun viewModel(
        saved: GameSnapshot? = null,
        nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
        zones: List<Zone> = testZones
    ) = MainViewModel(
        FakeSprites(),
        FakeStore().apply { this.saved = saved },
        zones = zones,
        nowMs = nowMs
    )

    /** Toques sobre la pokéball (usados aquí solo como clics de zona). */
    private fun tapZone(vm: MainViewModel, times: Int) {
        repeat(times) { vm.onPokeBallTouch(0f, 0f, 100f) }
    }

    /**
     * Recorrido de laboratorio ciudad + prado: la ciudad no tiene fauna (como
     * Pueblo Paleta, Ciudad Verde y Ciudad Plateada) y a la salida sí hay hierba
     * con encuentros. Sirve para probar que una zona sin especies salvajes no
     * rompe el bucle de caminar de las zonas siguientes.
     */
    private val cityThenMeadowZones = listOf(
        Zone("z_ciudad", "Ciudad", 100L, emptyList()),
        Zone("z_prado", "Prado", 100L, listOf("pidgey"))
    )

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
        val intro = vm.uiState.value
        assertNull(intro.gym)
        assertEquals(GymIntroPhase.WELCOME, intro.gymIntro)

        // "Omitir": no arranca el combate de golpe, muestra el cartel del
        // arranque un segundo y solo entonces se crea el estado de combate.
        vm.onGymIntroSkip()
        assertEquals(GymIntroPhase.COUNTDOWN, vm.uiState.value.gymIntro)
        testScope.advanceTimeBy(1_000L)
        testScope.runCurrent()
        assertNull(vm.uiState.value.gymIntro)
        assertNotNull(vm.uiState.value.gym)

        // Se consume la oleada completa con el reloj virtual: como el tiempo
        // real apenas avanza, ningún proyectil impacta y el líder queda expuesto.
        testScope.advanceTimeBy(10_000L)
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

    /**
     * FUN-14 (issue #3): entrar al gimnasio con un combate ya en curso no debe
     * reiniciarlo; el reintento explícito es `onGymRetry`. Se entra con un
     * guardado ya en la zona del gimnasio (con la mecánica de avance manual ya
     * no hay encadenado de zonas que deje ahí a 40 toques).
     */
    @Test
    fun onEnterGym_doesNotRestartActiveCombat() {
        var now = 0L
        val vm = viewModel(
            saved = GameSnapshot(zoneIndex = 1, zoneClicks = 3L),
            nowMs = { now }
        )

        assertTrue(vm.uiState.value.isGymReady)
        vm.onEnterGym()
        assertEquals(GymIntroPhase.WELCOME, vm.uiState.value.gymIntro)

        // El combate arranca al caducar el cartel ("Omitir" + 1 s).
        vm.onGymIntroSkip()
        testScope.advanceTimeBy(1_000L)
        testScope.runCurrent()
        val started = vm.uiState.value.gym
        assertNotNull(started)

        testScope.advanceTimeBy(400L)
        testScope.runCurrent()
        val ticked = vm.uiState.value.gym
        assertNotNull(ticked)
        assertTrue(ticked!!.waveRemainingMs < started!!.waveDurationMs)

        vm.onEnterGym()

        val same = vm.uiState.value.gym
        assertNotNull(same)
        assertEquals(ticked!!.waveRemainingMs, same!!.waveRemainingMs)
        assertEquals(ticked.leaderHp, same.leaderHp)
    }

    // --- Entrada al gimnasio: bienvenida, tutorial y cartel ------------------

    /** Parte de una zona con gimnasio listo y abre la capa de entrada. */
    private fun enterGymReady(): MainViewModel {
        val vm = viewModel(
            saved = GameSnapshot(zoneIndex = 1, zoneClicks = 3L),
            nowMs = { 0L }
        )
        assertTrue(vm.uiState.value.isGymReady)
        vm.onEnterGym()
        assertEquals(GymIntroPhase.WELCOME, vm.uiState.value.gymIntro)
        assertNull(vm.uiState.value.gym)
        return vm
    }

    @Test
    fun onEnterGym_repeatedWhileWelcome_doesNotChangePhase() {
        val vm = enterGymReady()

        vm.onEnterGym()

        assertEquals(GymIntroPhase.WELCOME, vm.uiState.value.gymIntro)
        assertNull(vm.uiState.value.gym)
    }

    @Test
    fun onGymIntroWatchTutorial_advancesWelcomeToTutorial() {
        val vm = enterGymReady()

        vm.onGymIntroWatchTutorial()

        assertEquals(GymIntroPhase.TUTORIAL, vm.uiState.value.gymIntro)
        assertNull(vm.uiState.value.gym)
    }

    @Test
    fun onGymIntroAccepted_startsCombatAfterCountdown() {
        val vm = enterGymReady()
        vm.onGymIntroWatchTutorial()

        vm.onGymIntroAccepted()
        assertEquals(GymIntroPhase.COUNTDOWN, vm.uiState.value.gymIntro)

        // El cartel dura un segundo: vista, no hay combate ni por la mitad.
        testScope.advanceTimeBy(500L)
        testScope.runCurrent()
        assertNull(vm.uiState.value.gym)

        testScope.advanceTimeBy(500L)
        testScope.runCurrent()
        assertNull(vm.uiState.value.gymIntro)
        assertNotNull(vm.uiState.value.gym)
    }

    /**
     * "Repetir tutorial" es interno de la pantalla del tutorial; aquí solo se
     * cubre que la ruta del cartel ("Omitir") arranca el combate tras 1 s.
     */
    @Test
    fun onGymIntroSkip_arrancaElCombateTrasElCartel() {
        val vm = enterGymReady()

        vm.onGymIntroSkip()
        assertEquals(GymIntroPhase.COUNTDOWN, vm.uiState.value.gymIntro)

        testScope.advanceTimeBy(1_000L)
        testScope.runCurrent()

        assertNull(vm.uiState.value.gymIntro)
        assertNotNull(vm.uiState.value.gym)
    }

    @Test
    fun onLeaveGym_duringCountdown_cancelsThePendingStart() {
        val vm = enterGymReady()
        vm.onGymIntroSkip()
        testScope.advanceTimeBy(500L)

        vm.onLeaveGym()

        assertNull(vm.uiState.value.gymIntro)
        assertNull(vm.uiState.value.gym)
        // El job del cartel ya está cancelado: caducar el tiempo no arranca nada.
        testScope.advanceTimeBy(1_000L)
        testScope.runCurrent()
        assertNull(vm.uiState.value.gym)
    }

    @Test
    fun onGymRetry_restartsAfterTheCountdownBanner() {
        val vm = enterGymReady()
        vm.onGymIntroSkip()
        testScope.advanceTimeBy(1_000L)
        testScope.runCurrent()
        assertNotNull(vm.uiState.value.gym)

        // El retry pasa por el cartel aunque no exista capa de intro pendiente.
        vm.onGymRetry()
        assertEquals(GymIntroPhase.COUNTDOWN, vm.uiState.value.gymIntro)
        // El estado perdido queda en pantalla hasta que caduca el cartel.
        val warmed = vm.uiState.value.gym
        assertNotNull(warmed)

        testScope.advanceTimeBy(1_000L)
        testScope.runCurrent()
        assertNull(vm.uiState.value.gymIntro)
        val fresh = vm.uiState.value.gym
        assertNotNull(fresh)
        // Combate nuevo: la oleada vuelve a su duración completa y sin rocas.
        assertEquals(fresh!!.waveDurationMs, fresh.waveRemainingMs)
        assertTrue(fresh.projectiles.isEmpty())
    }

    /**
     * Zonas sin fauna (Pueblo Paleta, Ciudad Verde, Ciudad Plateada): caminar en
     * hierba alta no abre encuentros (sin crash de `random()` sobre una lista
     * vacía), y el bucle de caminar sigue vivo en la zona siguiente, donde la
     * hierba sí genera encuentros.
     */
    @Test
    fun zoneWithoutWilds_neverSpawnsEncounter() {
        var now = 0L
        val vm = viewModel(nowMs = { now }, zones = cityThenMeadowZones)
        vm.onFinishIntro("Ash", "charmander")

        // 1 · Ciudad: no aparece nada aunque el temporizador de caminar cruce
        //     el umbral del encuentro.
        vm.onTallGrassToggle()
        vm.onPokeBallTouch(0.5f, 0.9f, 1f) // activa el temporizador de caminar

        testScope.advanceTimeBy(4_000L)
        testScope.runCurrent()

        with(vm.uiState.value) {
            assertNull(encounter)
            assertNull(fishing)
            assertEquals(0, zoneIndex)
        }

        // 2 · Prado: con el avance manual, llena la barra y pulsa **Avanzar**;
        //     la hierba vuelve a funcionar en la zona de prado.
        vm.onTallGrassToggle() // sale de la hierba (resetea el caminar)
        tapZone(vm, 100) // llena la barra de avance de la ciudad
        assertTrue(vm.uiState.value.canAdvanceZone)
        vm.onAdvanceZone() // botón Avanzar: cruza a la zona siguiente
        assertEquals(1, vm.uiState.value.zoneIndex)

        vm.onTallGrassToggle()
        vm.onPokeBallTouch(0.5f, 0.9f, 1f)

        testScope.advanceTimeBy(4_000L)
        testScope.runCurrent()

        val afterMeadow = vm.uiState.value
        assertNotNull(afterMeadow.encounter)
        assertNotNull(afterMeadow.fishing)
    }
}
