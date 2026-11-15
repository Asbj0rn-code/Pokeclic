/**
 * Dobles compartidos por los tests del ViewModel. Viven en un solo archivo
 * para que `MainViewModelTest` y `PokedexDexTest` no mantengan copias.
 */
package com.dsrs.pokeclic.viewmodel

import com.dsrs.pokeclic.data.GameStateStore
import com.dsrs.pokeclic.model.Direction
import com.dsrs.pokeclic.model.GameSnapshot
import com.dsrs.pokeclic.model.SpriteRepository

internal class FakeStore : GameStateStore {
    var saved: GameSnapshot? = null

    override fun load(): GameSnapshot? = saved

    override fun save(snapshot: GameSnapshot) {
        saved = snapshot
    }

    override fun clear(): Boolean {
        saved = null
        return true
    }
}

internal class FakeSprites : SpriteRepository {
    override fun frameCount(direction: Direction): Int = 1

    override fun frame(direction: Direction, index: Int): Int = 0
}
