/**
 * Proyección pura de la Pokédex: contadores de cuatro estados y memoización
 * por firma, sin ViewModel ni Android.
 */
package com.dsrs.pokeclic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class PokedexProjectorTest {

    private fun catalog() = PokedexCatalog(
        listOf(
            PokedexSpecies("charmander", 1, "Charmander", "Fuego"),
            PokedexSpecies("pidgey", 2, "Pidgey", "Volador"),
            PokedexSpecies("rattata", 3, "Rattata", "Normal"),
            PokedexSpecies("caterpie", 4, "Caterpie", "Bicho")
        )
    )

    private fun projector() = PokedexProjector(catalog())

    @Test
    fun counts_seenAsKnownAndCaughtAsCaughtEver() {
        val projection = projector().project(
            ownedSpecies = setOf("pidgey"),
            starterSpeciesId = "charmander",
            seenSpecies = setOf("charmander", "pidgey", "rattata"),
            caughtSpecies = setOf("charmander", "rattata")
        )

        // charmander CAUGHT (starter), pidgey CAUGHT (owned), rattata REGISTERED.
        assertEquals(3, projection.seenCount)
        assertEquals(3, projection.caughtCount)
        assertEquals(
            DexStatus.REGISTERED,
            projection.entries.first { it.speciesId == "rattata" }.status
        )
        assertEquals(
            DexStatus.UNKNOWN,
            projection.entries.first { it.speciesId == "caterpie" }.status
        )
    }

    @Test
    fun reset_keepsTheCaughtCounterStable() {
        val projector = projector()
        val before = projector.project(
            ownedSpecies = setOf("pidgey"),
            starterSpeciesId = "charmander",
            seenSpecies = setOf("charmander", "pidgey"),
            caughtSpecies = setOf("charmander", "pidgey")
        )
        val after = projector.project(
            ownedSpecies = emptySet(),
            starterSpeciesId = "charmander",
            seenSpecies = setOf("charmander", "pidgey"),
            caughtSpecies = setOf("charmander", "pidgey")
        )

        assertEquals(2, before.caughtCount)
        assertEquals(2, after.caughtCount)
        assertEquals(
            DexStatus.REGISTERED,
            after.entries.first { it.speciesId == "pidgey" }.status
        )
    }

    @Test
    fun project_reusesTheSameRowsWhileTheSignatureIsUnchanged() {
        val projector = projector()

        val first = projector.project(
            ownedSpecies = emptySet(),
            starterSpeciesId = "charmander",
            seenSpecies = setOf("charmander"),
            caughtSpecies = setOf("charmander")
        )
        val second = projector.project(
            ownedSpecies = emptySet(),
            starterSpeciesId = "charmander",
            seenSpecies = setOf("charmander"),
            caughtSpecies = setOf("charmander")
        )

        assertSame(first.entries, second.entries)
    }

    @Test
    fun project_rebuildsWhenTheHistoryChanges() {
        val projector = projector()

        val first = projector.project(
            ownedSpecies = emptySet(),
            starterSpeciesId = "charmander",
            seenSpecies = setOf("charmander"),
            caughtSpecies = setOf("charmander")
        )
        val second = projector.project(
            ownedSpecies = emptySet(),
            starterSpeciesId = "charmander",
            seenSpecies = setOf("charmander", "pidgey"),
            caughtSpecies = setOf("charmander")
        )

        assertNotSame(first.entries, second.entries)
        assertEquals(
            DexStatus.SEEN,
            second.entries.first { it.speciesId == "pidgey" }.status
        )
    }
}
