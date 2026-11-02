/**
 * Pruebas del cargador puro del mapeo de Kanto: validación cruzada de catálogo
 * y mapeo, y tolerancia a documentos malformados.
 *
 * La parte de assets ([com.dsrs.pokeclic.data.loadKantoMapData]) necesita un
 * `Context`, así que queda para verificación manual en el emulador; aquí se
 * cubre la lógica que puede fallar y que la UI consume como `Result`.
 */
package com.dsrs.pokeclic.data

import com.dsrs.pokeclic.model.MapRegionsDataException
import com.dsrs.pokeclic.model.regionAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private val CATALOG = """
    {"ubicaciones": [
      {"id": 1,  "nombre": "Ruta 1",        "tipo": "ruta"},
      {"id": 26, "nombre": "Pueblo Paleta", "tipo": "ciudad_pueblo"}
    ]}
""".trimIndent()

class KantoMapDataTest {

    @Test
    fun parseKantoMapData_acceptsConsistentCatalogAndMape() {
        val regions = """
            {"version": 1, "regions": [
              {"id": "paleta", "catalogId": 26, "priority": 3,
               "geometry": {"kind": "circle", "cx": 0.5, "cy": 0.5, "r": 0.1}}
            ]}
        """.trimIndent()

        val loaded = parseKantoMapData(CATALOG, regions).getOrThrow()

        assertEquals(listOf(1, 26), loaded.locations.map { it.id })
        assertEquals("paleta", loaded.regions.regions.single().id)
        assertEquals("Pueblo Paleta", loaded.regions.regions.single().resolvedName(loaded.locations))
    }

    @Test
    fun parseKantoMapData_acceptsEmptyMape() {
        val loaded = parseKantoMapData(CATALOG, """{"version": 1, "regions": []}""").getOrThrow()
        assertTrue(loaded.regions.regions.isEmpty())
    }

    @Test
    fun parseKantoMapData_failsOnBrokenCatalogLink() {
        val regions = """
            {"version": 1, "regions": [
              {"id": "rota", "catalogId": 999, "priority": 3,
               "geometry": {"kind": "circle", "cx": 0.5, "cy": 0.5, "r": 0.1}}
            ]}
        """.trimIndent()

        val result = parseKantoMapData(CATALOG, regions)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is MapRegionsDataException)
        assertTrue("999" in error?.message!!)
    }

    @Test
    fun parseKantoMapData_failsOnMalformedMape() {
        val result = parseKantoMapData(CATALOG, """{"version": 7, "regions": []}""")
        assertTrue(result.isFailure)
    }

    @Test
    fun parseKantoMapData_failsOnMalformedCatalog() {
        val result = parseKantoMapData("""no es json""", """{"version": 1, "regions": []}""")
        assertTrue(result.isFailure)
    }

    /** La cadena completa que usará `onTap` de `MapScreen`, sin la UI. */
    @Test
    fun selectionFlow_resolvesTheMappedRegionUnderATapPoint() {
        val loaded = parseKantoMapData(
            CATALOG,
            """
            {"version": 1, "regions": [
              {"id": "paleta", "catalogId": 26, "priority": 3,
               "geometry": {"kind": "envelope", "x1": 0.4, "y1": 0.4, "x2": 0.6, "y2": 0.6}}
            ]}
            """.trimIndent()
        ).getOrThrow()

        val region = loaded.regions.regionAt(0.55f, 0.45f, loaded.locations)
        assertEquals("paleta", region?.id)
        assertEquals("Pueblo Paleta", region?.resolvedName(loaded.locations))
    }

    @Test
    fun selectionFlow_outsideEveryRegion_isNull() {
        val loaded = parseKantoMapData(
            CATALOG,
            """{"version": 1, "regions": []}"""
        ).getOrThrow()

        assertNull(loaded.regions.regionAt(0.5f, 0.5f))
    }
}
