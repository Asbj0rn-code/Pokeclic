package com.dsrs.pokeclic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun zoneWithPendingGymCapsAtThreshold() {
        val zone = Zone("z", "Z", 100L, listOf("pidgey"), gymId = "brock")
        val advance = advanceZone(zone, 0, 90L, 20L, medals = emptySet(), lastIndex = 2)
        assertEquals(0, advance.zoneIndex)
        assertEquals(100L, advance.zoneClicks)
    }

    @Test
    fun medaledGymZoneIsSkipped() {
        val zone = Zone("z", "Z", 100L, listOf("pidgey"), gymId = "brock")
        val advance = advanceZone(zone, 0, 90L, 20L, medals = setOf("brock"), lastIndex = 2)
        assertEquals(1, advance.zoneIndex)
        assertEquals(0L, advance.zoneClicks)
    }

    @Test
    fun normalZoneAdvancesAtThreshold() {
        val zone = Zone("z", "Z", 100L, listOf("pidgey"))
        val advance = advanceZone(zone, 0, 50L, 60L, medals = emptySet(), lastIndex = 2)
        assertEquals(1, advance.zoneIndex)
        assertEquals(0L, advance.zoneClicks)
    }

    @Test
    fun lastZoneCapsProgress() {
        val zone = Zone("z", "Z", 100L, listOf("pidgey"), gymId = "brock")
        val advance = advanceZone(zone, 2, 90L, 20L, medals = setOf("brock"), lastIndex = 2)
        assertEquals(2, advance.zoneIndex)
        assertEquals(100L, advance.zoneClicks)
    }

    // --- Combate de gimnasio --------------------------------------------------

    @Test
    fun breakingAProjectileRemovesIt() {
        val state = combatState().copy(
            projectiles = listOf(
                Projectile(1L, 0, 0L, 1_000L),
                Projectile(2L, 1, 0L, 1_000L)
            )
        )
        val next = GymCombat.breakProjectile(state, 1L)
        assertEquals(listOf(2L), next.projectiles.map { it.id })
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
    fun waveDrainsWithTimeAndExposesLeader() {
        val state = combatState(waveRemainingMs = 10_000L).copy(
            projectiles = listOf(Projectile(1L, 0, 0L, 1_000L))
        )

        val almost = GymCombat.advanceWave(state, 9_000L)
        assertEquals(GymPhase.ATTACKING, almost.phase)
        assertEquals(1_000L, almost.waveRemainingMs)

        val exposed = GymCombat.advanceWave(almost, 1_000L)
        assertEquals(GymPhase.EXPOSED, exposed.phase)
        assertEquals(0L, exposed.waveRemainingMs)
        assertEquals(2_500L, exposed.exposureRemainingMs)
        assertEquals(emptyList<Projectile>(), exposed.projectiles)
    }

    @Test
    fun exposureEndsAndStartsAFullWave() {
        val exposed = combatState().copy(
            phase = GymPhase.EXPOSED,
            exposureRemainingMs = 2_500L
        )
        val almost = GymCombat.advanceExposure(exposed, 2_000L)
        assertEquals(GymPhase.EXPOSED, almost.phase)
        assertEquals(500L, almost.exposureRemainingMs)

        val resumed = GymCombat.advanceExposure(almost, 500L)
        assertEquals(GymPhase.ATTACKING, resumed.phase)
        assertEquals(10_000L, resumed.waveRemainingMs)
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

    private fun combatState(
        playerHp: Int = 5,
        waveRemainingMs: Long = 10_000L
    ) = GymState(
        gymId = "brock",
        leaderName = "Brock",
        gymType = PokemonType.ROCK,
        leaderHp = 60,
        leaderMaxHp = 60,
        playerHp = playerHp,
        maxHp = 5,
        columns = 5,
        waveDurationMs = 10_000L,
        waveRemainingMs = waveRemainingMs,
        exposureDurationMs = 2_500L
    )
}
