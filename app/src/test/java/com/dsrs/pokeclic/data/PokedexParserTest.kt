/**
 * Pruebas del parser del catálogo de la Pokédex y del propio asset:
 * validez de los identificadores, referencias cruzadas con `SpeciesCatalog` y
 * errores de esquema.
 */
package com.dsrs.pokeclic.data

import com.dsrs.pokeclic.model.PokemonType
import com.dsrs.pokeclic.model.SpeciesCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Lee el **catálogo de ejecución** (`pokedex_catalogo.json`) desde disco,
 * igual que el APK lo tomaría del asset: el working dir de los tests es el
 * módulo `app`, con un respaldo para invocaciones desde la raíz.
 */
private fun assetText(name: String): String {
    val candidates = listOf(
        File("src/main/assets/$name"),
        File("app/src/main/assets/$name")
    )
    val file = candidates.firstOrNull { it.exists() }
        ?: error("No se encontró el asset $name en $candidates")
    return file.readText()
}

class PokedexParserTest {

    // --- catálogo adaptado --------------------------------------------------

    @Test
    fun adaptedCatalog_holds151ConsecutiveEntries() {
        val catalog = parsePokedexCatalog(assetText(DEX_CATALOG_ASSET))

        assertEquals(151, catalog.entries.size)
        assertEquals((1..151).toList(), catalog.entries.map { it.dexNumber })
    }

    @Test
    fun adaptedCatalog_usesStableExplicitIds() {
        val catalog = parsePokedexCatalog(assetText(DEX_CATALOG_ASSET))

        val ids = catalog.entries.map { it.speciesId }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { id ->
            assertEquals(true, id.matches(Regex("[a-z0-9_]+")))
        }

        // Los cuatro casos especiales tienen id explícito y estable: el slug NO
        // se deriva del nombre en cada carga.
        assertEquals("nidoran_f", catalog.bySpeciesId("nidoran_f")?.speciesId)
        assertEquals(29, catalog.bySpeciesId("nidoran_f")?.dexNumber)
        assertEquals("nidoran_m", catalog.bySpeciesId("nidoran_m")?.speciesId)
        assertEquals(32, catalog.bySpeciesId("nidoran_m")?.dexNumber)
        assertEquals("farfetchd", catalog.bySpeciesId("farfetchd")?.speciesId)
        assertEquals(83, catalog.bySpeciesId("farfetchd")?.dexNumber)
        assertEquals("mr_mime", catalog.bySpeciesId("mr_mime")?.speciesId)
        assertEquals(122, catalog.bySpeciesId("mr_mime")?.dexNumber)
    }

    @Test
    fun adaptedCatalog_containsEveryGameSpecies() {
        val catalog = parsePokedexCatalog(assetText(DEX_CATALOG_ASSET))
        // Se deriva del catálogo de juego: una especie nueva sin entrada en la
        // dex rompe este test en vez de pasar desapercibida.
        val missing = SpeciesCatalog.all.map { it.id }.filter { catalog.bySpeciesId(it) == null }
        assertEquals(emptyList<String>(), missing)
    }

    /**
     * Discrepancia documentada (no operativa): Caterpie y Weedle entran en la
     * dex como **Bicho** (según el JSON de la primera generación), mientras que
     * el combate provisional les declara GRASS y POISON en `SpeciesCatalog`.
     * El test candado impide que esta divergencia se "arregle sola": si cambia
     * algo, se revisan las dos fuentes **juntas**.
     */
    @Test
    fun adaptedCatalog_typesDivergeFromCombatCatalogOnCaterpieAndWeedle() {
        val catalog = parsePokedexCatalog(assetText(DEX_CATALOG_ASSET))
        assertEquals("Bicho", catalog.bySpeciesId("caterpie")?.typeName)
        assertEquals("Bicho", catalog.bySpeciesId("weedle")?.typeName)

        val combat = SpeciesCatalog.byId("caterpie")
        assertEquals(PokemonType.GRASS, combat?.type)
        val weedle = SpeciesCatalog.byId("weedle")
        assertEquals(PokemonType.POISON, weedle?.type)
    }

    // --- parser puro --------------------------------------------------------

    @Test
    fun parse_rejectsDocumentWithoutSpecies() {
        try {
            parsePokedexCatalog("""{"schemaVersion": 1}""")
            fail("Debía rechazar un documento sin \"especies\"")
        } catch (expected: PokedexDataException) {
            assertEquals(true, "especies" in expected.message!!)
        }
    }

    @Test
    fun parse_rejectsMissingFields() {
        try {
            parsePokedexCatalog("""{"especies": [{"id": "pidgey", "dex": 2, "nombre": "Pidgey"}]}""")
            fail("Debía rechazar la entrada sin \"tipo\"")
        } catch (expected: PokedexDataException) {
            assertEquals(true, "tipo" in expected.message!!)
        }
    }

    @Test
    fun parse_rejectsMalformedJson() {
        try {
            parsePokedexCatalog("{ no es json")
            fail("Debía rechazar JSON malformado")
        } catch (expected: PokedexDataException) {
            assertEquals(true, "JSON" in expected.message!!)
        }
    }
}
