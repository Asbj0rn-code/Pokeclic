/**
 * Pruebas del modelo puro de la Pokédex: estados derivados del historial,
 * filas de UI y validación de invariantes del catálogo.
 */
package com.dsrs.pokeclic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class PokedexCatalogTest {

    /**
     * Mini catálogo de laboratorio: tres especies consecutivas. Los números son
     * de posición, no los de Kanto, así se ve que el orden manda.
     */
    private fun labCatalog() = PokedexCatalog(
        listOf(
            PokedexSpecies("charmander", 1, "Charmander", "Fuego"),
            PokedexSpecies("pidgey", 2, "Pidgey", "Volador"),
            PokedexSpecies("rattata", 3, "Rattata", "Normal")
        )
    )

    // --- dexStatusFor -------------------------------------------------------

    @Test
    fun dexStatusFor_caughtWhenOwned() {
        assertEquals(
            DexStatus.CAUGHT,
            dexStatusFor(
                "pidgey",
                ownedSpecies = setOf("pidgey"),
                starterSpeciesId = null,
                seenSpecies = emptySet(),
                caughtSpecies = emptySet()
            )
        )
    }

    @Test
    fun dexStatusFor_caughtWhenStarter() {
        // El inicial elegido es posesión por definición, sin estar en `ownedSpecies`.
        assertEquals(
            DexStatus.CAUGHT,
            dexStatusFor(
                "charmander",
                ownedSpecies = emptySet(),
                starterSpeciesId = "charmander",
                seenSpecies = emptySet(),
                caughtSpecies = emptySet()
            )
        )
    }

    @Test
    fun dexStatusFor_seenWithHistoryButNotOwned() {
        // La captura histórica no marca la posesión de la run actual: queda SEEN.
        assertEquals(
            DexStatus.SEEN,
            dexStatusFor(
                "pidgey",
                ownedSpecies = emptySet(),
                starterSpeciesId = "charmander",
                seenSpecies = emptySet(),
                caughtSpecies = setOf("pidgey")
            )
        )
        assertEquals(
            DexStatus.SEEN,
            dexStatusFor(
                "pidgey",
                ownedSpecies = emptySet(),
                starterSpeciesId = null,
                seenSpecies = setOf("pidgey"),
                caughtSpecies = emptySet()
            )
        )
    }

    @Test
    fun dexStatusFor_unknownWithoutHistory() {
        assertEquals(
            DexStatus.UNKNOWN,
            dexStatusFor(
                "pidgey",
                ownedSpecies = emptySet(),
                starterSpeciesId = "charmander",
                seenSpecies = setOf("charmander"),
                caughtSpecies = setOf("charmander")
            )
        )
    }

    // --- filas de UI --------------------------------------------------------

    @Test
    fun rowsFor_unknownRowsHideNameTypeAndDescription() {
        val rows = labCatalog().rowsFor(
            ownedSpecies = emptySet(),
            starterSpeciesId = "charmander",
            seenSpecies = setOf("charmander"),
            caughtSpecies = emptySet()
        )

        assertEquals(3, rows.size)

        val unknown = rows.first { it.speciesId == "pidgey" }
        assertNull(unknown.name)
        assertNull(unknown.typeName)
        assertEquals(DexStatus.UNKNOWN, unknown.status)
        assertEquals(2, unknown.dexNumber)

        val caught = rows.first { it.speciesId == "charmander" }
        assertEquals("Charmander", caught.name)
        assertEquals("Fuego", caught.typeName)
        assertEquals(DexStatus.CAUGHT, caught.status)
    }

    @Test
    fun rowsFor_ownedWinsOverHistory() {
        val rows = labCatalog().rowsFor(
            ownedSpecies = setOf("pidgey"),
            starterSpeciesId = "charmander",
            seenSpecies = setOf("charmander", "pidgey"),
            caughtSpecies = setOf("charmander", "pidgey")
        )

        // Posesión de la run actual: charmander (starter) y pidgey (en posesión).
        assertEquals(listOf("charmander", "pidgey"), rows.filter { it.status == DexStatus.CAUGHT }.map { it.speciesId })
    }

    @Test
    fun rowsFor_resetDropsOwnedExceptStarter() {
        val rows = labCatalog().rowsFor(
            ownedSpecies = emptySet(),           // tras el reset
            starterSpeciesId = "charmander",
            seenSpecies = setOf("charmander", "pidgey"),
            caughtSpecies = setOf("charmander", "pidgey")
        )

        assertEquals(listOf("charmander"), rows.filter { it.status == DexStatus.CAUGHT }.map { it.speciesId })
        assertEquals(DexStatus.SEEN, rows.first { it.speciesId == "pidgey" }.status)
        assertEquals(DexStatus.UNKNOWN, rows.first { it.speciesId == "rattata" }.status)
    }

    // --- invariantes del catálogo ------------------------------------------

    @Test
    fun catalog_rejectsNumbersSkip() {
        try {
            PokedexCatalog(
                listOf(
                    PokedexSpecies("charmander", 2, "Charmander", "Fuego"),
                    PokedexSpecies("pidgey", 4, "Pidgey", "Volador")
                )
            )
            fail("Debía rechazar números no consecutivos")
        } catch (expected: IllegalArgumentException) {
            assertEquals(true, "consecutivo" in expected.message!!)
        }
    }

    @Test
    fun catalog_rejectsDuplicateIds() {
        try {
            PokedexCatalog(
                listOf(
                    PokedexSpecies("pidgey", 1, "Pidgey", "Volador"),
                    PokedexSpecies("pidgey", 2, "Pidgey", "Volador")
                )
            )
            fail("Debía rechazar ids duplicados")
        } catch (expected: IllegalArgumentException) {
            assertEquals(true, "duplicado" in expected.message!!)
        }
    }

    @Test
    fun catalog_rejectsMalformedIds() {
        try {
            PokedexCatalog(
                listOf(PokedexSpecies("Pidgey-1", 1, "Pidgey", "Volador"))
            )
            fail("Debía rechazar ids con formato no estable")
        } catch (expected: IllegalArgumentException) {
            assertEquals(true, "formato" in expected.message!!)
        }
    }

    @Test
    fun catalog_rejectsBlankTypeAndName() {
        try {
            PokedexCatalog(listOf(PokedexSpecies("pidgey", 1, "Pidgey", "")))
            fail("Debía rechazar el tipo en blanco")
        } catch (expected: IllegalArgumentException) {
            assertEquals(true, "tipo" in expected.message!!)
        }
        try {
            PokedexCatalog(listOf(PokedexSpecies("pidgey", 1, "  ", "Volador")))
            fail("Debía rechazar el nombre en blanco")
        } catch (expected: IllegalArgumentException) {
            assertEquals(true, "nombre" in expected.message!!)
        }
    }

    @Test
    fun emptyCatalog_isAValidCatalogWithNoEntries() {
        val empty = PokedexCatalog.EMPTY
        assertEquals(0, empty.size)
        assertEquals(
            emptyList<PokedexRow>(),
            empty.rowsFor(
                ownedSpecies = emptySet(),
                starterSpeciesId = null,
                seenSpecies = emptySet(),
                caughtSpecies = emptySet()
            )
        )
    }

    @Test
    fun bySpeciesId_resolvesIds() {
        val catalog = labCatalog()
        assertEquals("Pidgey", catalog.bySpeciesId("pidgey")?.name)
        assertNull(catalog.bySpeciesId("zubat"))
    }
}
