package com.dsrs.pokeclic.viewmodel

import com.dsrs.pokeclic.data.GameStateStore
import com.dsrs.pokeclic.model.Direction
import com.dsrs.pokeclic.model.GameSnapshot
import com.dsrs.pokeclic.model.SpriteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()

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
        override fun clear() { saved = null }
    }

    private class FakeSprites : SpriteRepository {
        override fun frameCount(direction: Direction): Int = 1
        override fun frame(direction: Direction, index: Int): Int = 0
    }

    private fun viewModel() = MainViewModel(FakeSprites(), FakeStore())

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
}
