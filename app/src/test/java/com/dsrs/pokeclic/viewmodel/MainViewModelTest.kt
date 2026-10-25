package com.dsrs.pokeclic.viewmodel

import com.dsrs.pokeclic.data.GameStateStore
import com.dsrs.pokeclic.model.Direction
import com.dsrs.pokeclic.model.GameSnapshot
import com.dsrs.pokeclic.model.SpriteRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()
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

    private fun viewModel(nowMs: () -> Long = { System.nanoTime() / 1_000_000L }) =
        MainViewModel(FakeSprites(), FakeStore(), nowMs = nowMs)

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

    /**
     * FUN-14 (issue #3): entrar al gimnasio con un combate ya en curso no debe
     * reiniciarlo; el reintento explícito es `onGymRetry`.
     */
    @Test
    fun onEnterGym_doesNotRestartActiveCombat() {
        var now = 0L
        val vm = viewModel(nowMs = { now })
        vm.onFinishIntro("Ash", "charmander")
        repeat(40) { vm.onPokeBallTouch(0.5f, 0.9f, 1f) }

        assertTrue(vm.uiState.value.isGymReady)
        vm.onEnterGym()
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
}
