/**
 * Dobles compartidos por los tests del ViewModel. Viven en un solo archivo
 * para que `MainViewModelTest` y `PokedexDexTest` no mantengan copias.
 */
package com.dsrs.pokeclic.viewmodel

import com.dsrs.pokeclic.data.GameStateStore
import com.dsrs.pokeclic.model.Direction
import com.dsrs.pokeclic.model.GameSnapshot
import com.dsrs.pokeclic.model.PokedexCatalogLoad
import com.dsrs.pokeclic.model.SpriteRepository
import com.dsrs.pokeclic.model.Zone
import kotlin.random.Random

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

/**
 * Único punto donde los tests construyen el [MainViewModel].
 *
 * Existe para que un cambio en su firma —por ejemplo al partirlo en
 * colaboradores— se resuelva aquí y no en tres archivos con sus propios valores
 * por defecto. Los valores obligatorios los sigue aportando cada test: esta
 * función no impone ninguno.
 */
internal fun testViewModel(
    sprites: SpriteRepository,
    store: GameStateStore,
    zones: List<Zone>,
    random: Random,
    nowMs: () -> Long,
    pokedexLoad: PokedexCatalogLoad = PokedexCatalogLoad.EMPTY
): MainViewModel = MainViewModel(
    sprites = sprites,
    store = store,
    zones = zones,
    random = random,
    nowMs = nowMs,
    pokedexLoad = pokedexLoad
)
