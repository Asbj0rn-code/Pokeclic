package com.dsrs.pokeclic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ModelTest {

    @Test
    fun fire_isSuperEffectiveAgainstGrass() {
        assertEquals(2f, PokemonType.FIRE.effectiveness(PokemonType.GRASS))
    }

    @Test
    fun water_isSuperEffectiveAgainstFire() {
        assertEquals(2f, PokemonType.WATER.effectiveness(PokemonType.FIRE))
    }

    @Test
    fun fire_isNotVeryEffectiveAgainstWater() {
        assertEquals(0.5f, PokemonType.FIRE.effectiveness(PokemonType.WATER))
    }

    @Test
    fun sameTypeIsNeutral() {
        assertEquals(1f, PokemonType.NORMAL.effectiveness(PokemonType.NORMAL))
    }

    @Test
    fun water_doesDoubleDamageAgainstRock() {
        assertEquals(2f, typeDamageMultiplier(PokemonType.WATER, PokemonType.ROCK))
    }

    @Test
    fun fire_doesHalfDamageAgainstRock() {
        assertEquals(0.5f, typeDamageMultiplier(PokemonType.FIRE, PokemonType.ROCK))
    }

    @Test
    fun noLead_isNeutralDamage() {
        assertEquals(1f, typeDamageMultiplier(null, PokemonType.ROCK))
    }

    @Test
    fun projectileProgressGoesFromZeroToOne() {
        val projectile = Projectile(id = 1L, column = 0, spawnedAtMs = 1_000L, travelMs = 2_000L)
        assertEquals(0f, projectile.progressAt(1_000L))
        assertEquals(0.5f, projectile.progressAt(2_000L))
        assertEquals(1f, projectile.progressAt(3_000L))
    }

    @Test
    fun projectileProgressIsClamped() {
        val projectile = Projectile(id = 1L, column = 0, spawnedAtMs = 1_000L, travelMs = 2_000L)
        assertEquals(0f, projectile.progressAt(0L))
        assertEquals(1f, projectile.progressAt(9_999L))
    }

    @Test
    fun projectileLandsOnlyAfterTravelTime() {
        val projectile = Projectile(id = 1L, column = 0, spawnedAtMs = 1_000L, travelMs = 2_000L)
        assertEquals(false, projectile.hasLanded(2_999L))
        assertEquals(true, projectile.hasLanded(3_000L))
    }

    // --- Zonas: avance manual -------------------------------------------------

    @Test
    fun zoneClicksAccumulateBeforeThreshold() {
        val zone = Zone("z", "Z", 100L, listOf("pidgey"))
        assertEquals(60L, advanceZone(zone, 50L, 10L))
    }

    @Test
    fun normalZoneCapsAtThreshold() {
        val zone = Zone("z", "Z", 100L, listOf("pidgey"))
        assertEquals(100L, advanceZone(zone, 50L, 60L))
    }

    @Test
    fun gymZoneCapsAtThreshold() {
        val zone = Zone("z", "Z", 100L, listOf("pidgey"), gymId = "brock")
        assertEquals(100L, advanceZone(zone, 90L, 20L))
    }

    @Test
    fun lastZoneCapsProgress() {
        val zone = Zone("z", "Z", 100L, listOf("pidgey"), gymId = "brock")
        assertEquals(100L, advanceZone(zone, 90L, 20L))
    }

    @Test
    fun gymIsPendingUntilClearedThisRun() {
        val zone = Zone("z", "Z", 100L, listOf("pidgey"), gymId = "brock")
        assertTrue(isGymPending(zone, emptySet()))
        // Solo cuenta haber ganado ESTE gimnasio en la run: otra medalla no exime.
        assertFalse(isGymPending(zone, setOf("brock")))
        assertTrue(isGymPending(zone, setOf("misty")))
    }

    @Test
    fun zoneWithoutGym_isNeverPending() {
        val zone = Zone("z", "Z", 100L, listOf("pidgey"))
        assertFalse(isGymPending(zone, emptySet()))
    }

    // --- Combate de gimnasio --------------------------------------------------

    @Test
    fun breakingAProjectileRemovesItAndFillsPosture() {
        val state = combatState().copy(
            projectiles = listOf(
                Projectile(1L, 0, 0L, 1_000L),
                Projectile(2L, 1, 0L, 1_000L)
            )
        )
        val next = GymCombat.breakProjectile(state, 1L)
        assertEquals(listOf(2L), next.projectiles.map { it.id })
        assertEquals(1, next.posture)
        assertEquals(GymPhase.ATTACKING, next.phase)
    }

    @Test
    fun breakingWhileExposedDoesNothing() {
        val state = combatState().copy(
            phase = GymPhase.EXPOSED,
            projectiles = listOf(Projectile(1L, 0, 0L, 1_000L))
        )
        assertEquals(state, GymCombat.breakProjectile(state, 1L))
    }

    @Test
    fun impactDamagesPlayer() {
        val state = combatState(playerHp = 5)
        val next = GymCombat.registerImpact(state, damage = 2)
        assertEquals(3, next.playerHp)
    }

    @Test
    fun postureFillsWithHitsAndExposesLeader() {
        var state = combatState(posture = 0, postureMax = 3).copy(
            projectiles = listOf(Projectile(1L, 0, 0L, 1_000L))
        )

        state = GymCombat.addPosture(state, hits = 1)
        assertEquals(1, state.posture)
        assertEquals(GymPhase.ATTACKING, state.phase)

        state = GymCombat.addPosture(state, hits = 1)
        assertEquals(2, state.posture)
        assertEquals(GymPhase.ATTACKING, state.phase)

        val exposed = GymCombat.addPosture(state, hits = 1)
        assertEquals(3, exposed.posture)
        assertEquals(GymPhase.EXPOSED, exposed.phase)
        assertEquals(2_500L, exposed.exposureRemainingMs)
        assertEquals(emptyList<Projectile>(), exposed.projectiles)
    }

    @Test
    fun postureDoesNotOverflowItsMaximum() {
        val state = combatState(posture = 2, postureMax = 3)
        val exposed = GymCombat.addPosture(state, hits = 5)
        assertEquals(3, exposed.posture)
        assertEquals(GymPhase.EXPOSED, exposed.phase)
    }

    @Test
    fun postureDoesNotFillWhileExposed() {
        val exposed = combatState(posture = 3, postureMax = 3).copy(
            phase = GymPhase.EXPOSED,
            exposureRemainingMs = 2_500L
        )
        assertEquals(exposed, GymCombat.addPosture(exposed, hits = 1))
    }

    @Test
    fun exposureEndsAndResetsPosture() {
        val exposed = combatState(posture = 3, postureMax = 3).copy(
            phase = GymPhase.EXPOSED,
            exposureRemainingMs = 2_500L
        )
        val almost = GymCombat.advanceExposure(exposed, 2_000L)
        assertEquals(GymPhase.EXPOSED, almost.phase)
        assertEquals(500L, almost.exposureRemainingMs)

        val resumed = GymCombat.advanceExposure(almost, 500L)
        assertEquals(GymPhase.ATTACKING, resumed.phase)
        assertEquals(0, resumed.posture)
        assertEquals(0L, resumed.exposureRemainingMs)
    }

    @Test
    fun exposureTickOutsideExposedDoesNothing() {
        val state = combatState()
        assertEquals(state, GymCombat.advanceExposure(state, 1_000L))
    }

    @Test
    fun rageStartsStrictlyBelowAQuarterHp() {
        assertFalse(GymCombat.isRaging(60, 60))
        assertFalse(GymCombat.isRaging(15, 60))
        assertTrue(GymCombat.isRaging(14, 60))
    }

    @Test
    fun rageThresholdIsTheFirstRagingHp() {
        assertEquals(14, GymCombat.rageThresholdHp(60))
        assertEquals(22, GymCombat.rageThresholdHp(90))
        assertEquals(29, GymCombat.rageThresholdHp(120))
        assertEquals(0, GymCombat.rageThresholdHp(0))
    }

    @Test
    fun firstRageGateClampsLethalDamageAndForcesANewRound() {
        val state = combatState().copy(
            phase = GymPhase.EXPOSED,
            exposureRemainingMs = 2_500L,
            projectiles = listOf(Projectile(1L, 0, 0L, 1_000L))
        )

        val gated = GymCombat.gateFirstRage(state, newLeaderHp = 0)
        assertNotNull(gated)
        assertEquals(14, gated!!.leaderHp)
        assertTrue(gated.rage)
        assertFalse(gated.rageRoundPlayed)
        assertEquals(GymPhase.ATTACKING, gated.phase)
        assertEquals(0, gated.posture)
        assertEquals(0L, gated.exposureRemainingMs)
        assertEquals(emptyList<Projectile>(), gated.projectiles)
    }

    @Test
    fun firstRageGateOnlyTriggersBelowTheThreshold() {
        val state = combatState().copy(phase = GymPhase.EXPOSED)

        assertNull(GymCombat.gateFirstRage(state, newLeaderHp = 14))
        assertNotNull(GymCombat.gateFirstRage(state, newLeaderHp = 13))
    }

    @Test
    fun firstRageGateDoesNotRepeatOnceTheRageRoundWasPlayed() {
        val state = combatState().copy(phase = GymPhase.EXPOSED, rageRoundPlayed = true)

        assertNull(GymCombat.gateFirstRage(state, newLeaderHp = 0))
    }

    @Test
    fun breakingPostureWhileRagingMarksTheRageRoundAsPlayed() {
        val state = combatState(posture = 9, postureMax = 10).copy(
            leaderHp = 14,
            rage = true
        )

        val exposed = GymCombat.addPosture(state, hits = 1)

        assertEquals(GymPhase.EXPOSED, exposed.phase)
        assertTrue(exposed.rageRoundPlayed)
    }

    @Test
    fun breakingPostureWithoutRageDoesNotMarkTheRageRound() {
        val state = combatState(posture = 9, postureMax = 10)

        val exposed = GymCombat.addPosture(state, hits = 1)

        assertEquals(GymPhase.EXPOSED, exposed.phase)
        assertFalse(exposed.rageRoundPlayed)
    }

    // --- Minijuego de anillos (Misty) -----------------------------------------

    @Test
    fun timingRingProgressGoesFromZeroToOne() {
        val ring = TimingRing(id = 1L, spawnedAtMs = 1_000L, travelMs = 2_000L)
        assertEquals(0f, ring.progressAt(1_000L))
        assertEquals(0.5f, ring.progressAt(2_000L))
        assertEquals(1f, ring.progressAt(3_000L))
    }

    @Test
    fun timingRingProgressIsClamped() {
        val ring = TimingRing(id = 1L, spawnedAtMs = 1_000L, travelMs = 2_000L)
        assertEquals(0f, ring.progressAt(0L))
        assertEquals(1f, ring.progressAt(9_999L))
    }

    @Test
    fun timingRingConvergesWhenItsTravelEnds() {
        val ring = TimingRing(id = 1L, spawnedAtMs = 500L, travelMs = 1_200L)
        assertEquals(1_700L, ring.convergeAtMs())
    }

    @Test
    fun timingRingWindowIsInclusiveAroundConvergence() {
        val ring = TimingRing(id = 1L, spawnedAtMs = 1_000L, travelMs = 2_000L)
        // Converge en 3.000 ms con una ventana de 170 ms.
        assertTrue(ring.isInWindow(2_830L, 170L))
        assertTrue(ring.isInWindow(3_170L, 170L))
        assertFalse(ring.isInWindow(2_829L, 170L))
        assertFalse(ring.isInWindow(3_171L, 170L))
    }

    @Test
    fun timingRingExpiresOnlyAfterItsWindow() {
        val ring = TimingRing(id = 1L, spawnedAtMs = 1_000L, travelMs = 2_000L)
        assertFalse(ring.hasExpired(3_170L, 170L))
        assertTrue(ring.hasExpired(3_171L, 170L))
    }

    @Test
    fun nearestRingIsTheOneClosestToConvergence() {
        val state = combatState().copy(
            ringWindowMs = 170L,
            rings = listOf(
                TimingRing(1L, spawnedAtMs = 0L, travelMs = 1_000L),
                TimingRing(2L, spawnedAtMs = 0L, travelMs = 1_500L)
            )
        )

        assertEquals(1L, GymCombat.nearestRing(state, 900L)?.id)
        assertEquals(1L, GymCombat.nearestRing(state, 1_100L)?.id)
        assertEquals(2L, GymCombat.nearestRing(state, 1_260L)?.id)
    }

    @Test
    fun expiredRingsAreOnlyThosePastTheirWindow() {
        val state = combatState().copy(
            ringWindowMs = 170L,
            rings = listOf(
                TimingRing(1L, spawnedAtMs = 0L, travelMs = 1_000L),
                TimingRing(2L, spawnedAtMs = 0L, travelMs = 1_500L)
            )
        )

        assertEquals(emptyList<TimingRing>(), GymCombat.expiredRings(state, 1_170L))
        assertEquals(listOf(1L), GymCombat.expiredRings(state, 1_171L).map { it.id })
    }

    @Test
    fun fillingPostureClearsRingsAndExposesLeader() {
        val state = combatState(posture = 1, postureMax = 2).copy(
            mechanic = GymMechanic.TIMING_RINGS,
            ringWindowMs = 170L,
            rings = listOf(TimingRing(1L, spawnedAtMs = 0L, travelMs = 1_000L))
        )

        val exposed = GymCombat.addPosture(state, hits = 1)

        assertEquals(GymPhase.EXPOSED, exposed.phase)
        assertEquals(emptyList<TimingRing>(), exposed.rings)
    }

    // --- Minijuego de Voltorb (Lt. Surge) -------------------------------------

    @Test
    fun voltorbHeatsUpUntilItsWindowEnds() {
        val voltorb = Voltorb(id = 1L, cell = 0, chargedAtMs = 1_000L, windowMs = 1_400L)

        assertEquals(0f, voltorb.progressAt(1_000L))
        assertEquals(0.5f, voltorb.progressAt(1_700L))
        assertEquals(1f, voltorb.progressAt(2_400L))
        assertFalse(voltorb.hasExploded(2_399L))
        assertTrue(voltorb.hasExploded(2_400L))
    }

    @Test
    fun voltorbProgressIsClamped() {
        val voltorb = Voltorb(id = 1L, cell = 0, chargedAtMs = 1_000L, windowMs = 1_400L)

        assertEquals(0f, voltorb.progressAt(0L))
        assertEquals(1f, voltorb.progressAt(9_999L))
    }

    @Test
    fun defusingAChargedVoltorbRemovesItWithoutDamageAndFillsPosture() {
        val state = voltorbState(
            voltorbs = listOf(Voltorb(1L, cell = 5, chargedAtMs = 0L, windowMs = 1_400L))
        )

        val next = GymCombat.defuseVoltorb(state, 5)

        assertEquals(emptyList<Voltorb>(), next.voltorbs)
        assertEquals(5, next.playerHp)
        assertEquals(1, next.posture)
        assertEquals(VoltorbOutcome.DEFUSED, next.voltorbFeedback?.outcome)
        assertEquals(5, next.voltorbFeedback?.cell)
    }

    @Test
    fun defusingAnEmptyCellDoesNothing() {
        val state = voltorbState(
            voltorbs = listOf(Voltorb(1L, cell = 5, chargedAtMs = 0L, windowMs = 1_400L))
        )

        assertEquals(state, GymCombat.defuseVoltorb(state, 3))
    }

    @Test
    fun defusingWhileExposedDoesNothing() {
        val state = voltorbState(
            phase = GymPhase.EXPOSED,
            voltorbs = listOf(Voltorb(1L, cell = 0, chargedAtMs = 0L, windowMs = 1_400L))
        )

        assertEquals(state, GymCombat.defuseVoltorb(state, 0))
    }

    @Test
    fun touchingAnEmptyCellShocksThePlayer() {
        val state = voltorbState(playerHp = 5)

        val next = GymCombat.registerShock(state, cell = 7, damage = 1)

        assertEquals(4, next.playerHp)
        assertEquals(VoltorbOutcome.SHOCK, next.voltorbFeedback?.outcome)
        assertEquals(7, next.voltorbFeedback?.cell)
    }

    @Test
    fun explodedVoltorbsDamageThePlayerAndClearTheBoard() {
        val state = voltorbState(
            voltorbs = listOf(
                Voltorb(1L, cell = 0, chargedAtMs = 0L, windowMs = 1_000L),
                Voltorb(2L, cell = 6, chargedAtMs = 0L, windowMs = 1_000L)
            )
        )

        val before = GymCombat.explodeVoltorbs(state, 999L, damage = 1)
        assertEquals(state, before)

        val after = GymCombat.explodeVoltorbs(state, 1_000L, damage = 1)
        assertEquals(emptyList<Voltorb>(), after.voltorbs)
        assertEquals(3, after.playerHp)
        assertEquals(VoltorbOutcome.EXPLODED, after.voltorbFeedback?.outcome)
        assertEquals(2, after.voltorbFeedback?.count)
        assertEquals(listOf(0, 6), after.voltorbFeedback?.cells)
    }

    @Test
    fun freeVoltorbCellsAreTheOnesWithoutAVoltorb() {
        val state = voltorbState(
            voltorbs = listOf(Voltorb(1L, cell = 0, chargedAtMs = 0L, windowMs = 1_400L))
        )

        assertEquals((1 until 16).toList(), GymCombat.freeVoltorbCells(state, 16))
    }

    @Test
    fun aFullBoardHasNoFreeCells() {
        val state = voltorbState(
            voltorbs = (0 until 16).map {
                Voltorb(id = it.toLong(), cell = it, chargedAtMs = 0L, windowMs = 1_400L)
            }
        )

        assertEquals(emptyList<Int>(), GymCombat.freeVoltorbCells(state, 16))
    }

    @Test
    fun fillingPostureClearsVoltorbsAndExposesLeader() {
        val state = voltorbState(
            posture = 1,
            postureMax = 2,
            voltorbs = listOf(Voltorb(1L, cell = 0, chargedAtMs = 0L, windowMs = 1_400L))
        )

        val exposed = GymCombat.addPosture(state, hits = 1)

        assertEquals(GymPhase.EXPOSED, exposed.phase)
        assertEquals(emptyList<Voltorb>(), exposed.voltorbs)
    }

    // --- Minijuego de captura ---------------------------------------------------

    /**
     * Configuración de laboratorio: suavizado enorme (para que el sprite apenas
     * se mueva en un tick corto) y sin dardos, así los tests de barra y
     * progreso son deterministas.
     */
    private fun testConfig() = CatchingConfig(smoothingSeconds = 10f, dartChancePerSecond = 0f)

    private fun catchingState(
        config: CatchingConfig = testConfig(),
        barPos: Float = 0.5f,
        spritePos: Float = 0.5f,
        progress: Float = 0f,
        elapsed: Float = 0f
    ) = CatchingState(
        config = config,
        barPos = barPos,
        barVel = 0f,
        spritePos = spritePos,
        spriteTarget = spritePos,
        progress = progress,
        elapsed = elapsed
    )

    @Test
    fun barFallsGroundsWhenReleased() {
        val next = CatchingGame.step(catchingState(barPos = 0.6f), held = false, dt = 0.5f, random = Random(0))
        assertEquals(CatchingDefaults.BAR_SIZE / 2f, next.barPos)
        assertEquals(0f, next.barVel)
    }

    @Test
    fun barTopsOutWhenHeld() {
        val next = CatchingGame.step(catchingState(barPos = 0.5f), held = true, dt = 0.5f, random = Random(0))
        assertEquals(1f - CatchingDefaults.BAR_SIZE / 2f, next.barPos)
        assertEquals(0f, next.barVel)
    }

    @Test
    fun barSpeedIsCapped() {
        val next = CatchingGame.step(catchingState(barPos = CatchingDefaults.BAR_SIZE / 2f), held = true, dt = 0.5f, random = Random(0))
        assertEquals(CatchingDefaults.MAX_BAR_SPEED, next.barVel)
    }

    @Test
    fun progressFillsWhileOverlapping() {
        val next = CatchingGame.step(catchingState(), held = true, dt = 0.05f, random = Random(0))
        assertEquals(CatchingDefaults.FILL_RATE * 0.05f, next.progress)
    }

    @Test
    fun progressDrainsWithoutOverlap() {
        val state = catchingState(barPos = 0.89f, spritePos = 0.2f, progress = 0.2f)
        val next = CatchingGame.step(state, held = true, dt = 0.05f, random = Random(0))
        assertEquals(0.19f, next.progress)
    }

    @Test
    fun progressNeverDrainsBelowZero() {
        val next = CatchingGame.step(catchingState(barPos = 0.89f, spritePos = 0.2f), held = true, dt = 0.05f, random = Random(0))
        assertEquals(0f, next.progress)
        assertEquals(CatchingPhase.PLAYING, next.phase)
    }

    @Test
    fun escapeOnlyHappensAfterGrace() {
        val early = CatchingGame.step(catchingState(barPos = 0.89f, spritePos = 0.2f, elapsed = 0f), held = true, dt = 0.9f, random = Random(0))
        assertEquals(CatchingPhase.PLAYING, early.phase)

        val late = CatchingGame.step(catchingState(barPos = 0.89f, spritePos = 0.2f, elapsed = 0.9f), held = true, dt = 0.2f, random = Random(0))
        assertEquals(CatchingPhase.ESCAPED, late.phase)
    }

    @Test
    fun captureWhenProgressReachesOne() {
        val next = CatchingGame.step(catchingState(progress = 0.98f), held = true, dt = 0.1f, random = Random(0))
        assertEquals(CatchingPhase.CAPTURED, next.phase)
        assertEquals(1f, next.progress)
    }

    @Test
    fun finishedGameIsFrozen() {
        val state = catchingState().copy(phase = CatchingPhase.CAPTURED)
        assertEquals(state, CatchingGame.step(state, held = true, dt = 0.5f, random = Random(0)))
    }

    @Test
    fun configMapsDifficultyAcrossItsRange() {
        val easy = CatchingDefaults.configFor(0f)
        assertEquals(CatchingDefaults.SMOOTHING_EASY, easy.smoothingSeconds)
        assertEquals(CatchingDefaults.DART_EASY, easy.dartChancePerSecond)

        val hard = CatchingDefaults.configFor(1f)
        assertEquals(CatchingDefaults.SMOOTHING_HARD, hard.smoothingSeconds)
        assertEquals(CatchingDefaults.DART_HARD, hard.dartChancePerSecond)
    }

    @Test
    fun configClampsOutOfRangeDifficulty() {
        val below = CatchingDefaults.configFor(-5f)
        assertEquals(CatchingDefaults.SMOOTHING_EASY, below.smoothingSeconds)
        val above = CatchingDefaults.configFor(9f)
        assertEquals(CatchingDefaults.DART_HARD, above.dartChancePerSecond)
    }

    @Test
    fun initialCatchingStateStartsCentered() {
        val state = CatchingGame.initialState(CatchingDefaults.configFor(0.15f))
        assertEquals(0.5f, state.barPos)
        assertEquals(0f, state.barVel)
        assertEquals(0.5f, state.spritePos)
        assertEquals(0.5f, state.spriteTarget)
        assertEquals(0f, state.progress)
        assertEquals(0f, state.elapsed)
        assertEquals(CatchingPhase.PLAYING, state.phase)
    }

    private fun combatState(
        playerHp: Int = 5,
        posture: Int = 0,
        postureMax: Int = 10
    ) = GymState(
        gymId = "brock",
        leaderName = "Brock",
        gymType = PokemonType.ROCK,
        leaderHp = 60,
        leaderMaxHp = 60,
        playerHp = playerHp,
        maxHp = 5,
        columns = 5,
        posture = posture,
        postureMax = postureMax,
        exposureDurationMs = 2_500L
    )

    private fun voltorbState(
        playerHp: Int = 5,
        posture: Int = 0,
        postureMax: Int = 10,
        phase: GymPhase = GymPhase.ATTACKING,
        voltorbs: List<Voltorb> = emptyList()
    ) = GymState(
        gymId = "surge",
        leaderName = "Lt. Surge",
        gymType = PokemonType.ELECTRIC,
        leaderHp = 120,
        leaderMaxHp = 120,
        playerHp = playerHp,
        maxHp = 5,
        mechanic = GymMechanic.VOLTORB_GRID,
        columns = 4,
        rows = 4,
        voltorbs = voltorbs,
        posture = posture,
        postureMax = postureMax,
        exposureDurationMs = 2_500L,
        phase = phase
    )
}
