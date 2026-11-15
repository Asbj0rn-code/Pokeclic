package com.dsrs.pokeclic.viewmodel

import com.dsrs.pokeclic.model.Direction
import com.dsrs.pokeclic.model.GameSnapshot
import com.dsrs.pokeclic.model.GymCatalog
import com.dsrs.pokeclic.model.GymCombat
import com.dsrs.pokeclic.model.GymIntroPhase
import com.dsrs.pokeclic.model.GymMechanic
import com.dsrs.pokeclic.model.GymOutcome
import com.dsrs.pokeclic.model.GymPhase
import com.dsrs.pokeclic.model.RingOutcome
import com.dsrs.pokeclic.model.SpriteRepository
import com.dsrs.pokeclic.model.TutorialAction
import com.dsrs.pokeclic.model.Zone
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
        zones: List<Zone> = testZones,
        sprites: SpriteRepository = FakeSprites(),
        random: Random = Random.Default
    ) = MainViewModel(
        sprites,
        FakeStore().apply { this.saved = saved },
        zones = zones,
        random = random,
        nowMs = nowMs
    )

    /** Toques sobre la pokéball (usados aquí solo como clics de zona). */
    private fun tapZone(vm: MainViewModel, times: Int) {
        repeat(times) { vm.onPokeBallTouch(Direction.BACK) }
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

    /**
     * Cueva (Mt.Moon): salvajes sin modo hierba alta. Los clics avanzan la barra
     * y, a la vez, alimentan el temporizador de encuentros.
     */
    private val caveZones = listOf(
        Zone("z_cueva", "Cueva", 100L, listOf("zubat"), alwaysWilds = true)
    )

    /** Mismo recorrido corto, pero con el gimnasio de Misty en la zona central. */
    private val mistyZones = listOf(
        Zone("z1", "Z1", 2L, listOf("pidgey")),
        Zone("z2", "Z2", 3L, listOf("rattata"), gymId = "misty"),
        Zone("z3", "Z3", 4L, listOf("caterpie"))
    )

    /**
     * Salto de desarrollo a una zona: deja el índice ahí, la barra llena y el
     * gimnasio listo para entrar aunque ya se hubiera ganado en esta run.
     */
    @Test
    fun debugJump_leavesTheZoneFullAndItsGymReady() {
        val zones = listOf(
            Zone("z1", "Z1", 2L, listOf("pidgey")),
            Zone("z_carmin", "Carmín", 3L, emptyList(), gymId = "surge")
        )
        val vm = viewModel(
            zones = zones,
            saved = GameSnapshot(zoneIndex = 0, gymClearedRun = setOf("surge"))
        )

        vm.onDebugJumpToZone("z_carmin")

        val state = vm.uiState.value
        assertEquals(1, state.zoneIndex)
        assertEquals("z_carmin", state.zoneId)
        assertEquals(3L, state.zoneClicks)
        assertTrue(state.isGymReady)
    }

    /** Un id de zona desconocido no mueve la partida. */
    @Test
    fun debugJump_toUnknownZoneDoesNothing() {
        val vm = viewModel()

        vm.onDebugJumpToZone("no-existe")

        assertEquals(0, vm.uiState.value.zoneIndex)
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

    /**
     * Avanzar estando en modo captura (hierba alta abierta): la zona nueva se
     * estrena fuera del modo, sin búsqueda ni encuentro arrastrados.
     */
    @Test
    fun onAdvanceZone_fromTallGrass_leavesCaptureMode() {
        val vm = viewModel()
        tapZone(vm, times = 2) // llena la barra de z1 (umbral 2)
        vm.onTallGrassToggle()

        vm.onAdvanceZone()

        val state = vm.uiState.value
        assertEquals(1, state.zoneIndex)
        assertFalse(state.isTallGrass)
        assertEquals(0f, state.walkProgress)
        assertNull(state.encounter)
        assertNull(state.catching)
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
        val clock = TestClock()
        val vm = viewModel(nowMs = { clock.now }, random = Random(7))
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

        // La postura ya no baja por tiempo: se llena rompiendo rocas. Cada
        // acierto suma, así que se rompen todas las que aparezcan hasta que el
        // líder quede expuesto.
        fillBrockPosture(vm, clock)
        assertEquals(GymPhase.EXPOSED, vm.uiState.value.gym?.phase)

        // La furia es obligatoria: al cruzar el umbral, el líder interrumpe la
        // ventana y se fuerza una ronda de furia antes de poder morir.
        var guard = 0
        while (vm.uiState.value.gym?.phase == GymPhase.EXPOSED && guard++ < 120) {
            vm.onGymEnemyHit()
        }
        val enraged = vm.uiState.value.gym
        assertNotNull(enraged)
        assertTrue(enraged!!.rage)
        assertEquals(GymPhase.ATTACKING, enraged.phase)
        assertEquals(GymCombat.rageThresholdHp(GymCatalog.brock.leaderHp), enraged.leaderHp)
        assertFalse(enraged.rageRoundPlayed)

        // La furia abre el discurso del líder, con el combate en pausa.
        dismissRageSpeech(vm)

        // Ronda de furia completa: al romper la postura queda jugada.
        fillBrockPosture(vm, clock)
        assertEquals(GymPhase.EXPOSED, vm.uiState.value.gym?.phase)
        assertTrue(vm.uiState.value.gym!!.rageRoundPlayed)

        // Potencia de clic básica (1): los HP restantes caen en la ventana.
        guard = 0
        while (vm.uiState.value.gym?.outcome == GymOutcome.ACTIVE && guard++ < 120) {
            vm.onGymEnemyHit()
        }

        // La victoria no cierra el combate: deja el estado en WON para que la capa
        // de despedida lo cuente, y la medalla se cobra al cerrarla.
        with(vm.uiState.value.gym!!) {
            assertEquals(GymOutcome.WON, outcome)
            assertEquals(0, leaderHp)
        }
        assertFalse("la medalla no puede cobrarse antes de la despedida", "brock" in vm.uiState.value.medals)
        vm.onGymVictoryDone()

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

    @Test
    fun lethalHitCannotSkipTheMandatoryRageRound() {
        val clock = TestClock()
        val vm = viewModel(
            saved = GameSnapshot(
                zoneIndex = 1,
                zoneClicks = 3L,
                starterSpeciesId = "bulbasaur",
                leadSpeciesId = "bulbasaur",
                starterBond = 5_000
            ),
            nowMs = { clock.now },
            random = Random(7)
        )
        vm.onEnterGym()
        vm.onGymIntroSkip()
        testScope.advanceTimeBy(1_000L)
        testScope.runCurrent()

        fillBrockPosture(vm, clock)
        // Un golpe bastaría para matarlo.
        assertTrue(vm.uiState.value.clickPower > GymCatalog.brock.leaderHp)

        vm.onGymEnemyHit()

        val enraged = vm.uiState.value.gym
        assertNotNull(enraged)
        with(enraged!!) {
            assertEquals(GymCombat.rageThresholdHp(GymCatalog.brock.leaderHp), leaderHp)
            assertTrue(rage)
            assertEquals(GymPhase.ATTACKING, phase)
            assertFalse(rageRoundPlayed)
        }

        // El discurso de furia frena el combate hasta que el jugador lo cierra.
        dismissRageSpeech(vm)

        // Ya no está expuesto: el golpe siguiente no le hace daño.
        vm.onGymEnemyHit()
        assertEquals(
            GymCombat.rageThresholdHp(GymCatalog.brock.leaderHp),
            vm.uiState.value.gym!!.leaderHp
        )

        // Ronda de furia obligatoria y, después, el golpe letal sí entra.
        fillBrockPosture(vm, clock)
        assertTrue(vm.uiState.value.gym!!.rageRoundPlayed)

        vm.onGymEnemyHit()

        assertEquals(GymOutcome.WON, vm.uiState.value.gym?.outcome)
        vm.onGymVictoryDone()

        assertNull(vm.uiState.value.gym)
        assertTrue("brock" in vm.uiState.value.medals)
    }

    @Test
    fun mandatoryRageRoundGetsTheSameBreatherAsANormalRound() {
        val clock = TestClock()
        val vm = viewModel(
            saved = GameSnapshot(
                zoneIndex = 1,
                zoneClicks = 3L,
                starterSpeciesId = "bulbasaur",
                leadSpeciesId = "bulbasaur"
            ),
            nowMs = { clock.now },
            random = Random(7)
        )
        vm.onEnterGym()
        vm.onGymIntroSkip()
        testScope.advanceTimeBy(1_000L)
        testScope.runCurrent()

        fillBrockPosture(vm, clock)
        assertEquals(GymPhase.EXPOSED, vm.uiState.value.gym?.phase)

        // El jugador tarda casi toda la ventana en golpear: mientras el líder
        // está expuesto su cadencia de aparición no corre.
        repeat(40) {
            clock.now += 50L
            tick()
        }
        assertEquals(GymPhase.EXPOSED, vm.uiState.value.gym?.phase)

        var guard = 0
        while (vm.uiState.value.gym?.phase == GymPhase.EXPOSED && guard++ < 120) {
            vm.onGymEnemyHit()
        }
        val enraged = vm.uiState.value.gym
        assertNotNull(enraged)
        assertTrue(enraged!!.rage)
        assertTrue(enraged.projectiles.isEmpty())

        // El discurso de furia se cierra antes de que arranque la ronda.
        dismissRageSpeech(vm)

        // La ronda de furia arranca con el mismo respiro que una ronda normal:
        // nada cae en el acto ni se cobra de golpe la espera de la exposición.
        tick()
        assertTrue(vm.uiState.value.gym!!.projectiles.isEmpty())

        repeat(20) {
            clock.now += 50L
            tick()
        }
        val projectiles = vm.uiState.value.gym!!.projectiles
        assertTrue(
            "ráfaga acumulada: ${projectiles.size} rocas en un segundo",
            projectiles.size <= 2
        )
    }

    @Test
    fun rageSpeech_freezesTheCombatUntilItIsClosed() {
        val clock = TestClock()
        val vm = viewModel(nowMs = { clock.now }, random = Random(7))
        tapZone(vm, times = 5)          // Z1 al límite
        vm.onAdvanceZone()              // → Z2 (gimnasio)
        tapZone(vm, times = 3)          // Z2 al límite
        vm.onEnterGym()
        vm.onGymIntroSkip()
        testScope.advanceTimeBy(1_000L)
        testScope.runCurrent()

        fillBrockPosture(vm, clock)
        var guard = 0
        while (vm.uiState.value.gym?.phase == GymPhase.EXPOSED && guard++ < 120) {
            vm.onGymEnemyHit()
        }

        val atSpeech = vm.uiState.value.gym!!
        assertTrue("la furia debería abrir su discurso", atSpeech.rageSpeech)
        assertTrue(atSpeech.projectiles.isEmpty())

        // Con el discurso abierto el combate no avanza: ni rocas ni daño, por
        // mucho tiempo de juego que pase.
        repeat(60) {
            clock.now += 400L
            tick()
        }
        with(vm.uiState.value.gym!!) {
            assertTrue("cayeron rocas durante el discurso", projectiles.isEmpty())
            assertEquals("la vida del líder cambió en pausa", atSpeech.leaderHp, leaderHp)
            assertEquals("la vida del jugador cambió en pausa", atSpeech.playerHp, playerHp)
        }

        // Al cerrarlo, la furia arranca de verdad.
        vm.onGymRageSpeechDone()
        assertFalse(vm.uiState.value.gym!!.rageSpeech)
        var spawned = false
        repeat(60) {
            clock.now += 400L
            tick()
            if (vm.uiState.value.gym!!.projectiles.isNotEmpty()) spawned = true
        }
        assertTrue("tras el discurso deberían volver a caer rocas", spawned)
    }

    @Test
    fun missedClicksDrainThePostureBar() {
        val clock = TestClock()
        val vm = viewModel(nowMs = { clock.now }, random = Random(7))
        tapZone(vm, times = 5)          // Z1 al límite
        vm.onAdvanceZone()              // → Z2 (gimnasio)
        tapZone(vm, times = 3)          // Z2 al límite
        vm.onEnterGym()
        vm.onGymIntroSkip()
        testScope.advanceTimeBy(1_000L)
        testScope.runCurrent()

        // Se rompe una roca para tener algo de postura que perder.
        var guard = 0
        while (vm.uiState.value.gym!!.projectiles.isEmpty() && guard++ < 20) {
            clock.now += 400L
            tick()
        }
        val rock = vm.uiState.value.gym!!.projectiles.first()
        vm.onGymProjectileHit(rock.id)

        val afterHit = vm.uiState.value.gym!!.posture
        assertTrue("el acierto debería sumar postura", afterHit > 0)

        vm.onGymMiss()
        assertEquals("un clic fallado resta postura", afterHit - 1, vm.uiState.value.gym!!.posture)

        // La barra se queda en cero, nunca negativa.
        repeat(50) { vm.onGymMiss() }
        assertEquals(0, vm.uiState.value.gym!!.posture)
    }

    // --- Gimnasio de Misty: anillos rítmicos ---------------------------------

    /** Entra al gimnasio de Misty y salta el cartel de arranque. */
    private fun startMistyCombat(nowMs: () -> Long): MainViewModel {
        val vm = viewModel(
            saved = GameSnapshot(zoneIndex = 1, zoneClicks = 3L),
            nowMs = nowMs,
            zones = mistyZones,
            random = Random(7)
        )
        assertTrue(vm.uiState.value.isGymReady)
        vm.onEnterGym()
        vm.onGymIntroSkip()
        testScope.advanceTimeBy(1_000L)
        testScope.runCurrent()
        return vm
    }

    /** Avanza un tick del bucle con el reloj del juego ya movido. */
    private fun tick() {
        testScope.advanceTimeBy(50L)
        testScope.runCurrent()
    }

    /** Reloj de juego mutable, para tests que necesitan avanzar `nowMs`. */
    private class TestClock(var now: Long = 0L)

    /**
     * Llena la postura de Brock rompiendo todas las rocas que aparezcan hasta
     * que el líder quede expuesto (o el combate termine).
     */
    private fun fillBrockPosture(vm: MainViewModel, clock: TestClock) {
        var guard = 0
        while (vm.uiState.value.gym?.phase == GymPhase.ATTACKING && guard++ < 300) {
            clock.now += 400L
            tick()
            vm.uiState.value.gym?.projectiles
                ?.map { it.id }
                ?.forEach { vm.onGymProjectileHit(it) }
        }
    }

    /**
     * Cierra el discurso de furia. Mientras está abierto el combate no avanza, así
     * que cualquier test que siga jugando tras entrar en furia tiene que pasar
     * por aquí.
     */
    private fun dismissRageSpeech(vm: MainViewModel) {
        assertTrue("la furia debería abrir su discurso", vm.uiState.value.gym?.rageSpeech == true)
        vm.onGymRageSpeechDone()
        assertFalse("el discurso no se cerró", vm.uiState.value.gym?.rageSpeech == true)
    }

    @Test
    fun brockGym_keepsItsProjectileMechanic() {
        val vm = viewModel(
            saved = GameSnapshot(zoneIndex = 1, zoneClicks = 3L),
            nowMs = { 0L }
        )
        vm.onEnterGym()
        vm.onGymIntroSkip()
        testScope.advanceTimeBy(1_000L)
        testScope.runCurrent()

        assertEquals(GymMechanic.PROJECTILES, vm.uiState.value.gym?.mechanic)
    }

    @Test
    fun mistyGym_usesTimingRingsInsteadOfProjectiles() {
        var now = 0L
        val vm = startMistyCombat(nowMs = { now })

        val started = vm.uiState.value.gym
        assertNotNull(started)
        assertEquals(GymMechanic.TIMING_RINGS, started!!.mechanic)
        assertTrue(started.projectiles.isEmpty())

        now = 900L
        tick()

        val ringed = vm.uiState.value.gym
        assertNotNull(ringed)
        assertEquals(1, ringed!!.rings.size)
        assertTrue(ringed.projectiles.isEmpty())
    }

    @Test
    fun ringTapOnBeat_removesTheRingWithoutDamageAndFillsPosture() {
        var now = 0L
        val vm = startMistyCombat(nowMs = { now })
        now = 900L
        tick()

        val ring = vm.uiState.value.gym!!.rings.single()
        val hpBefore = vm.uiState.value.gym!!.playerHp
        val postureBefore = vm.uiState.value.gym!!.posture

        now = ring.convergeAtMs()
        vm.onGymRingTap()

        with(vm.uiState.value.gym!!) {
            assertEquals(hpBefore, playerHp)
            assertEquals(postureBefore + 1, posture)
            assertTrue(rings.none { it.id == ring.id })
            assertEquals(RingOutcome.HIT, ringFeedback?.outcome)
            assertEquals(ring.id, ringFeedback?.ringId)
            // Acertar justo en la convergencia: el efecto arranca en el círculo fijo.
            assertEquals(1f, ringFeedback!!.ringProgress, 0.02f)
            assertEquals("el primer acierto abre el combo", 1, ringFeedback!!.combo)
            assertEquals(1, ringCombo)
        }
    }

    @Test
    fun ringTapOffBeat_damagesAndConsumesThatRing() {
        var now = 0L
        val vm = startMistyCombat(nowMs = { now })
        now = 900L
        tick()

        val ring = vm.uiState.value.gym!!.rings.single()
        val hpBefore = vm.uiState.value.gym!!.playerHp

        // Un instante después de nacer: muy lejos de la convergencia.
        now = ring.spawnedAtMs + 1L
        vm.onGymRingTap()

        with(vm.uiState.value.gym!!) {
            assertEquals(hpBefore - 1, playerHp)
            assertEquals(0, posture)
            assertTrue(rings.none { it.id == ring.id })
            assertEquals(RingOutcome.MISS, ringFeedback?.outcome)
            assertEquals(ring.id, ringFeedback?.ringId)
            // Pulsar nada más nacer: el anillo está casi en su radio de entrada, así
            // que el efecto de fallo arranca grande y se cierra sobre el sprite.
            assertTrue("progreso casi nulo", ringFeedback!!.ringProgress < 0.1f)
            assertEquals("un fallo no cuenta como combo", 0, ringFeedback!!.combo)
            assertEquals(0, ringCombo)
        }
    }

    @Test
    fun consecutiveHitsRaiseTheComboAndAMissResetsIt() {
        var now = 0L
        val vm = startMistyCombat(nowMs = { now })
        now = 900L
        tick()

        fun waitForANewRing(): Long {
            var guard = 0
            while (vm.uiState.value.gym!!.rings.isEmpty() && guard++ < 40) {
                now += 100L
                tick()
            }
            return vm.uiState.value.gym!!.rings.first().id
        }

        val first = vm.uiState.value.gym!!.rings.single()
        now = first.convergeAtMs()
        vm.onGymRingTap()
        assertEquals(1, vm.uiState.value.gym!!.ringCombo)

        // Nace el siguiente y también se acierta: el combo sube.
        waitForANewRing()
        val second = vm.uiState.value.gym!!.rings.first()
        now = second.convergeAtMs()
        vm.onGymRingTap()
        with(vm.uiState.value.gym!!) {
            assertEquals(RingOutcome.HIT, ringFeedback?.outcome)
            assertEquals("dos aciertos seguidos son combo 2", 2, ringFeedback?.combo)
            assertEquals(2, ringCombo)
        }

        // Un fallo lo devuelve a cero.
        waitForANewRing()
        val third = vm.uiState.value.gym!!.rings.first()
        now = third.spawnedAtMs + 1L
        vm.onGymRingTap()
        with(vm.uiState.value.gym!!) {
            assertEquals(RingOutcome.MISS, ringFeedback?.outcome)
            assertEquals(0, ringFeedback?.combo)
            assertEquals(0, ringCombo)
        }
    }

    @Test
    fun ringThatExpiresWithoutATap_damagesThePlayer() {
        var now = 0L
        val vm = startMistyCombat(nowMs = { now })
        now = 900L
        tick()

        val ring = vm.uiState.value.gym!!.rings.single()
        val hpBefore = vm.uiState.value.gym!!.playerHp

        now = ring.convergeAtMs() + 201L
        tick()

        with(vm.uiState.value.gym!!) {
            assertEquals(hpBefore - 1, playerHp)
            assertTrue(rings.none { it.id == ring.id })
            assertEquals(RingOutcome.MISS, ringFeedback?.outcome)
            assertEquals(ring.id, ringFeedback?.ringId)
            assertEquals(1, ringFeedback?.count)
            assertEquals("un anillo caducado corta el combo", 0, ringFeedback?.combo)
        }
    }

    @Test
    fun mistyExposure_clearsRingsAndAllowsRepeatedEnemyHits() {
        var now = 0L
        val vm = startMistyCombat(nowMs = { now })
        now = 900L
        tick()
        assertTrue(vm.uiState.value.gym!!.rings.isNotEmpty())

        // La postura ya no se agota por tiempo: se llena acertando anillos. Al
        // completarse, el líder queda expuesto y los anillos se limpian.
        var guard = 0
        while (vm.uiState.value.gym?.phase == GymPhase.ATTACKING && guard++ < 200) {
            now += 800L
            tick()
            val ring = vm.uiState.value.gym?.rings
                ?.minByOrNull { it.distanceToConvergenceMs(now) }
                ?: continue
            now = ring.convergeAtMs()
            vm.onGymRingTap()
        }

        val exposed = vm.uiState.value.gym
        assertNotNull(exposed)
        assertEquals(GymPhase.EXPOSED, exposed!!.phase)
        assertTrue(exposed.rings.isEmpty())

        val leaderBefore = exposed.leaderHp
        vm.onGymEnemyHit()

        assertEquals(leaderBefore - 1, vm.uiState.value.gym!!.leaderHp)
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
            nowMs = { now },
            random = Random(7)
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

        // Un acierto real (romper una roca) sube la postura del líder.
        now = 900L
        testScope.advanceTimeBy(50L)
        testScope.runCurrent()
        val projectile = vm.uiState.value.gym?.projectiles?.firstOrNull()
        assertNotNull(projectile)
        vm.onGymProjectileHit(projectile!!.id)
        val ticked = vm.uiState.value.gym
        assertNotNull(ticked)
        assertEquals(1, ticked!!.posture)

        vm.onEnterGym()

        val same = vm.uiState.value.gym
        assertNotNull(same)
        assertEquals(ticked.posture, same!!.posture)
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
        // Combate nuevo: la postura vuelve a cero y sin rocas.
        assertEquals(0, fresh!!.posture)
        assertTrue(fresh.postureMax > 0)
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
        vm.onTutorialAction(TutorialAction.DECLINE_OFFER)   // omite el tutorial: juego libre

        // 1 · Ciudad: no aparece nada aunque el temporizador de caminar cruce
        //     el umbral del encuentro.
        vm.onTallGrassToggle()
        vm.onPokeBallTouch(Direction.BACK) // activa el temporizador de caminar

        testScope.advanceTimeBy(4_000L)
        testScope.runCurrent()

        with(vm.uiState.value) {
            assertNull(encounter)
            assertNull(catching)
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
        vm.onPokeBallTouch(Direction.BACK)

        testScope.advanceTimeBy(4_000L)
        testScope.runCurrent()

        val afterMeadow = vm.uiState.value
        assertNotNull(afterMeadow.encounter)
        assertNotNull(afterMeadow.catching)
    }

    /**
     * Cueva (Mt.Moon): los clics suman a la barra **y** alimentan el temporizador
     * de encuentros, así que aparecen salvajes sin activar la hierba alta.
     */
    @Test
    fun caveZone_countsClicksAndSpawnsEncountersWhileAdvancing() {
        val vm = viewModel(nowMs = { 0L }, zones = caveZones)
        vm.onFinishIntro("Ash", "charmander")
        vm.onTutorialAction(TutorialAction.DECLINE_OFFER)   // omite el tutorial: juego libre

        tapZone(vm, 2)
        with(vm.uiState.value) {
            assertFalse(isTallGrass)
            assertTrue("la cueva debe sumar clics a la barra", zoneClicks > 0L)
            assertNull(encounter)
        }

        vm.onPokeBallTouch(Direction.BACK) // deja actividad reciente para el bucle
        testScope.advanceTimeBy(4_000L)
        testScope.runCurrent()

        with(vm.uiState.value) {
            assertNotNull("la cueva genera salvajes al avanzar", encounter)
            assertNotNull(catching)
            assertFalse("sin hierba alta no hay sprite ni modo", isTallGrass)
        }
    }

    /** En una cueva el botón de hierba no existe: pulsarlo no cambia nada. */
    @Test
    fun caveZone_ignoresTheTallGrassToggle() {
        val vm = viewModel(zones = caveZones)
        vm.onFinishIntro("Ash", "charmander")
        vm.onTutorialAction(TutorialAction.DECLINE_OFFER)   // omite el tutorial: juego libre

        vm.onTallGrassToggle()

        assertFalse(vm.uiState.value.isTallGrass)
    }

    /** Al cruzar de una ruta con hierba a la cueva, el modo hierba se cierra. */
    @Test
    fun caveZone_enteredFromTallGrass_clearsTheGrassMode() {
        val route = Zone("z_ruta", "Ruta", 2L, listOf("pidgey"))
        val vm = viewModel(zones = listOf(route, caveZones.single()))
        vm.onFinishIntro("Ash", "charmander")
        vm.onTutorialAction(TutorialAction.DECLINE_OFFER)   // omite el tutorial: juego libre

        tapZone(vm, 2)
        vm.onTallGrassToggle()
        assertTrue(vm.uiState.value.isTallGrass)

        vm.onAdvanceZone()

        with(vm.uiState.value) {
            assertEquals(1, zoneIndex)
            assertFalse(isTallGrass)
            assertTrue(alwaysWilds)
        }
    }

    /** Con el minijuego abierto no se acumulan clics ni aparece otro salvaje. */
    @Test
    fun caveZone_pausesWhileAnEncounterIsOpen() {
        val vm = viewModel(nowMs = { 0L }, zones = caveZones)
        vm.onFinishIntro("Ash", "charmander")
        vm.onTutorialAction(TutorialAction.DECLINE_OFFER)   // omite el tutorial: juego libre
        vm.onPokeBallTouch(Direction.BACK)
        testScope.advanceTimeBy(4_000L)
        testScope.runCurrent()

        val encounter = vm.uiState.value.encounter ?: error("la cueva no abrió encuentro")
        val clicks = vm.uiState.value.zoneClicks

        repeat(5) { vm.onPokeBallTouch(Direction.BACK) }
        testScope.advanceTimeBy(30_000L)
        testScope.runCurrent()

        assertEquals(clicks, vm.uiState.value.zoneClicks)
        assertEquals(encounter, vm.uiState.value.encounter)
    }

    /**
     * Modo captura: con la hierba alta abierta, los toques de la Pokébola
     * alimentan el temporizador de caminar pero no cuentan para el avance
     * de la zona.
     */
    @Test
    fun pokeBallTaps_inTallGrass_doNotCountForZoneProgress() {
        val vm = viewModel()
        vm.onTallGrassToggle()

        tapZone(vm, 3)

        assertEquals(0L, vm.uiState.value.zoneClicks)
    }

    /**
     * Modo captura: con el encuentro (partida de captura) en pantalla, un
     * toque que alcanzara la Pokébola tampoco cuenta para el avance.
     */
    @Test
    fun pokeBallTaps_duringAnEncounter_doNotCountForZoneProgress() {
        var now = 0L
        val vm = viewModel(nowMs = { now })
        vm.onTallGrassToggle()
        vm.onPokeBallTouch(Direction.BACK) // activa el temporizador de caminar

        testScope.advanceTimeBy(4_000L)
        testScope.runCurrent()
        assertNotNull(vm.uiState.value.encounter)

        tapZone(vm, 3)

        assertEquals(0L, vm.uiState.value.zoneClicks)
    }

    // --- Animación del entrenador -----------------------------------------

    /** Repositorio con cuatro fotogramas por dirección y un res distinto por frame. */
    private class CyclingSprites : SpriteRepository {
        override fun frameCount(direction: Direction): Int = 4
        override fun frame(direction: Direction, index: Int): Int = spriteRes(direction, index)
    }

    /**
     * La dirección ya no sale del lado de la Pokébola que se toca: la resuelve la
     * UI a partir del movimiento del fondo y el ViewModel solo encadena fotogramas.
     * Un segundo sin pulsar devuelve al reposo: front en su primer fotograma.
     */
    @Test
    fun pokeBallTouch_adoptsTheDirectionItReceives() {
        val vm = viewModel(sprites = CyclingSprites())

        vm.onPokeBallTouch(Direction.RIGHT)

        assertEquals(Direction.RIGHT, vm.uiState.value.trainer.currentDirection)
        assertEquals(spriteRes(Direction.RIGHT, 1), vm.uiState.value.trainerFrameRes)
    }

    @Test
    fun lateralDirections_startOnTheSecondFrame() {
        val vm = viewModel(sprites = CyclingSprites())

        vm.onPokeBallTouch(Direction.RIGHT)
        assertEquals(1, vm.uiState.value.trainer.currentFrame)

        vm.onPokeBallTouch(Direction.LEFT)
        assertEquals(1, vm.uiState.value.trainer.currentFrame)
        assertEquals(spriteRes(Direction.LEFT, 1), vm.uiState.value.trainerFrameRes)
    }

    @Test
    fun lateralStep_reachesTheSecondFrameDuringTheMovement() {
        val vm = viewModel(sprites = CyclingSprites())

        vm.onPokeBallTouch(Direction.RIGHT)
        // En el instante del clic se ve el arranque del ciclo.
        assertEquals(1, vm.uiState.value.trainer.currentFrame)

        // Con el desplazamiento ya en marcha entra el segundo.
        testScope.advanceTimeBy(61L)
        testScope.runCurrent()

        assertEquals(2, vm.uiState.value.trainer.currentFrame)
        assertEquals(spriteRes(Direction.RIGHT, 2), vm.uiState.value.trainerFrameRes)
    }

    @Test
    fun aNewClickCancelsThePendingLateralFrame() {
        val vm = viewModel(sprites = CyclingSprites())

        vm.onPokeBallTouch(Direction.RIGHT)
        vm.onPokeBallTouch(Direction.BACK) // el siguiente clic cambia de eje

        testScope.advanceTimeBy(200L)
        testScope.runCurrent()

        assertEquals(Direction.BACK, vm.uiState.value.trainer.currentDirection)
        assertEquals(0, vm.uiState.value.trainer.currentFrame)
    }

    @Test
    fun verticalDirection_startsOnTheFirstFrame() {
        val vm = viewModel(sprites = CyclingSprites())

        vm.onPokeBallTouch(Direction.BACK)

        assertEquals(0, vm.uiState.value.trainer.currentFrame)
        assertEquals(spriteRes(Direction.BACK, 0), vm.uiState.value.trainerFrameRes)
    }

    @Test
    fun state_exposesTheCurrentZoneId() {
        val vm = viewModel()

        assertEquals(testZones.first().id, vm.uiState.value.zoneId)
    }

    @Test
    fun everyTap_advancesOneFrame() {
        val vm = viewModel(sprites = CyclingSprites())

        repeat(3) { vm.onPokeBallTouch(Direction.BACK) }

        // El primero estrena dirección (frame 0); los siguientes avanzan.
        assertEquals(2, vm.uiState.value.trainer.currentFrame)
        assertEquals(spriteRes(Direction.BACK, 2), vm.uiState.value.trainerFrameRes)
    }

    @Test
    fun oneSecondWithoutTaps_returnsToFrontFirstFrame() {
        val vm = viewModel(sprites = CyclingSprites())

        vm.onPokeBallTouch(Direction.LEFT)
        assertEquals(Direction.LEFT, vm.uiState.value.trainer.currentDirection)

        testScope.advanceTimeBy(1_001L)
        testScope.runCurrent()

        with(vm.uiState.value) {
            assertEquals(Direction.FRONT, trainer.currentDirection)
            assertEquals(0, trainer.currentFrame)
            assertEquals(spriteRes(Direction.FRONT, 0), trainerFrameRes)
        }
    }

    @Test
    fun tapping_reschedulesTheRestTimer() {
        val vm = viewModel(sprites = CyclingSprites())

        vm.onPokeBallTouch(Direction.BACK)
        testScope.advanceTimeBy(900L)
        vm.onPokeBallTouch(Direction.BACK)
        testScope.advanceTimeBy(900L)
        testScope.runCurrent()

        // 1,8 s desde el primero, pero solo 0,9 s desde el último toque.
        assertEquals(Direction.BACK, vm.uiState.value.trainer.currentDirection)

        testScope.advanceTimeBy(200L)
        testScope.runCurrent()
        assertEquals(Direction.FRONT, vm.uiState.value.trainer.currentDirection)
    }
}

/** Res que devuelve el repositorio de sprites de prueba para (direcci�n, fotograma). */
private fun spriteRes(direction: Direction, index: Int): Int = direction.ordinal * 100 + index
