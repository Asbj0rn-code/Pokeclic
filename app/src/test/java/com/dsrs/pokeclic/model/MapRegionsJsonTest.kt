/**
 * Pruebas de serialización JSON del mapeo y del catálogo con `org.json`:
 * round-trip, validación de esquema y referencias cruzadas.
 */
package com.dsrs.pokeclic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MapRegionsJsonTest {

    // ─────────────────────────── Catálogo ───────────────────────────

    @Test
    fun parseCatalog_readsTheThreeCanonicalTypes() {
        val catalog = parseCatalog(
            """
            {"ubicaciones": [
              {"id": 1,  "nombre": "Ruta 1",        "tipo": "ruta"},
              {"id": 26, "nombre": "Pueblo Paleta", "tipo": "ciudad_pueblo"},
              {"id": 36, "nombre": "Bosque Verde",  "tipo": "ubicacion_especial"}
            ]}
            """.trimIndent()
        )
        assertEquals(3, catalog.size)
        assertEquals(MapRegionType.ROUTE, catalog[0].type)
        assertEquals(MapRegionType.CITY, catalog[1].type)
        assertEquals(MapRegionType.SPECIAL, catalog[2].type)
    }

    @Test
    fun parseCatalog_rejectsDuplicateIds() {
        try {
            parseCatalog(
                """
                {"ubicaciones": [
                  {"id": 1, "nombre": "Ruta 1", "tipo": "ruta"},
                  {"id": 1, "nombre": "Ruta 2", "tipo": "ruta"}
                ]}
                """.trimIndent()
            )
            fail("Debía rechazar ids duplicados")
        } catch (expected: MapRegionsDataException) {
            assertTrue("duplicado" in expected.message!!)
        }
    }

    @Test
    fun parseCatalog_rejectsUnknownType() {
        try {
            parseCatalog(
                """
                {"ubicaciones": [{"id": 1, "nombre": "Ruta 1", "tipo": "mar"}]}
                """.trimIndent()
            )
            fail("Debía rechazar el tipo desconocido")
        } catch (expected: MapRegionsDataException) {
            assertTrue("mar" in expected.message!!)
        }
    }

    @Test
    fun parseCatalog_rejectsBlankNames() {
        try {
            parseCatalog(
                """
                {"ubicaciones": [{"id": 1, "nombre": " ", "tipo": "ruta"}]}
                """.trimIndent()
            )
            fail("Debía rechazar el nombre en blanco")
        } catch (expected: MapRegionsDataException) {
            assertTrue("nombre" in expected.message!!)
        }
    }

    // ─────────────────────────── Mapeo ───────────────────────────

    private val map = KantoRegionMap(
        version = 1,
        regions = listOf(
            MapRegion(
                id = "pueblo-paleta",
                geometry = MapRegionGeometry.Circle(0.5f, 0.875f, 0.031_25f),
                priority = 3,
                catalogId = 26
            ),
            MapRegion(
                id = "pueblo-paleta-lab",
                geometry = MapRegionGeometry.Envelope(0.437_5f, 0.85f, 0.468_75f, 0.90f),
                priority = 2,
                name = "Lab. del Prof. Oak",
                type = MapRegionType.SPECIAL
            ),
            MapRegion(
                id = "ruta-1",
                geometry = MapRegionGeometry.Polygon(
                    listOf(
                        UvPoint(0.5f, 0.75f),
                        UvPoint(0.45f, 0.55f),
                        UvPoint(0.48f, 0.35f)
                    )
                ),
                priority = 1,
                catalogId = 1
            )
        )
    )

    @Test
    fun parseRegions_roundTripsEveryGeometryAndBothLinkStyles() {
        val text = map.writeRegions()
        val parsed = parseRegions(text)
        assertEquals(map, parsed)
    }

    @Test
    fun parseRegions_preservesEmptyMape() {
        val empty = KantoRegionMap(version = 1, regions = emptyList())
        assertEquals(empty, parseRegions(empty.writeRegions()))
    }

    @Test
    fun parseRegions_rejectsUnsupportedVersion() {
        for (version in listOf(0, 2, -1)) {
            try {
                parseRegions("""{"version": $version, "regions": []}""".trimIndent())
                fail("Debía rechazar la versión $version")
            } catch (expected: MapRegionsDataException) {
                assertTrue("Versión" in expected.message!!)
            }
        }
    }

    @Test
    fun parseRegions_rejectsMissingVersion() {
        try {
            parseRegions("""{"regions": []}""")
            fail("Debía rechazar el mapeo sin version")
        } catch (expected: MapRegionsDataException) {
            assertTrue("Versión" in expected.message!!)
        }
    }

    @Test
    fun parseRegions_rejectsDuplicateRegionIds() {
        try {
            parseRegions(
                """
                {"version": 1, "regions": [
                  {"id": "misma", "priority": 1, "name": "Ruta", "type": "ROUTE",
                   "geometry": {"kind": "envelope", "x1": 0, "y1": 0, "x2": 0.1, "y2": 0.1}},
                  {"id": "misma", "priority": 1, "name": "Otra", "type": "SPECIAL",
                   "geometry": {"kind": "envelope", "x1": 0.2, "y1": 0, "x2": 0.3, "y2": 0.1}}
                ]}
                """.trimIndent()
            )
            fail("Debía rechazar ids de región duplicados")
        } catch (expected: MapRegionsDataException) {
            assertTrue("duplicado" in expected.message!!)
        }
    }

    @Test
    fun parseRegions_rejectsPolygonWithTwoVertices() {
        try {
            parseRegions(
                """
                {"version": 1, "regions": [
                  {"id": "corta", "priority": 1, "name": "Ruta", "type": "ROUTE",
                   "geometry": {"kind": "polygon", "points": [[0, 0], [1, 0]]}}
                ]}
                """.trimIndent()
            )
            fail("Debía rechazar el polígono de 2 vértices")
        } catch (expected: MapRegionsDataException) {
            assertTrue("3 vértices" in expected.message!!)
        }
    }

    @Test
    fun parseRegions_rejectsUnknownGeometryKind() {
        try {
            parseRegions(
                """
                {"version": 1, "regions": [
                  {"id": "misteriosa", "priority": 1, "name": "Zona", "type": "SPECIAL",
                   "geometry": {"kind": "hexagono", "points": []}}
                ]}
                """.trimIndent()
            )
            fail("Debía rechazar el kind desconocido")
        } catch (expected: MapRegionsDataException) {
            assertTrue("hexagono" in expected.message!!)
        }
    }

    @Test
    fun parseRegions_rejectsStandaloneRegionWithoutNameOrType() {
        try {
            parseRegions(
                """
                {"version": 1, "regions": [
                  {"id": "libre-a-medias", "priority": 2,
                   "geometry": {"kind": "circle", "cx": 0.5, "cy": 0.5, "r": 0.1}}
                ]}
                """.trimIndent()
            )
            fail("Debía rechazar la región libre incompleta")
        } catch (expected: MapRegionsDataException) {
            assertTrue("libre-a-medias" in expected.message!!)
        }
    }

    @Test
    fun parseRegions_rejectsLinkedRegionWithEmbeddedIdentity() {
        try {
            parseRegions(
                """
                {"version": 1, "regions": [
                  {"id": "mezclada", "priority": 3, "catalogId": 26, "name": "Copia",
                   "geometry": {"kind": "circle", "cx": 0.5, "cy": 0.5, "r": 0.1}}
                ]}
                """.trimIndent()
            )
            fail("Debía rechazar la región mezclando catálogo y datos propios")
        } catch (expected: MapRegionsDataException) {
            assertTrue("mezcl" in expected.message!!)
        }
    }

    @Test
    fun parseRegions_rejectsLinkedRegionWithoutExplicitPriority() {
        try {
            parseRegions(
                """
                {"version": 1, "regions": [
                  {"id": "sin-prioridad", "catalogId": 26,
                   "geometry": {"kind": "circle", "cx": 0.5, "cy": 0.5, "r": 0.1}}
                ]}
                """.trimIndent()
            )
            fail("Debía exigir prioridad explícita en regiones enlazadas")
        } catch (expected: MapRegionsDataException) {
            assertTrue("sin-prioridad" in expected.message!!)
        }
    }

    @Test
    fun parseRegions_derivesPriorityFromExplicitTypeWhenOmitted() {
        val parsed = parseRegions(
            """
            {"version": 1, "regions": [
              {"id": "sin-prioridad", "name": "Dentro", "type": "CITY",
               "geometry": {"kind": "circle", "cx": 0.5, "cy": 0.5, "r": 0.1}}
            ]}
            """.trimIndent()
        )
        assertEquals(
            MapRegionType.CITY.defaultPriority,
            parsed.regions.single().priority
        )
    }

    /** Región enlazada sin datos propios pero sin prioridad → error de esquema. */
    @Test
    fun parseRegions_standaloneRegionRoundTripsWithoutEmbeddedPriority() {
        val parsed = parseRegions(
            parseRegions(
                """
                {"version": 1, "regions": [
                  {"id": "especial", "name": "Torre", "type": "SPECIAL", "priority": 5,
                   "geometry": {"kind": "circle", "cx": 0.5, "cy": 0.25, "r": 0.1}}
                ]}
                """.trimIndent()
            ).writeRegions()
        )
        assertEquals(5, parsed.regions.single().priority)
        assertEquals("Torre", parsed.regions.single().name)
        assertNull(parsed.regions.single().catalogId)
    }

    @Test
    fun parseRegions_rejectsCatalogIdThatIsNotANumber() {
        try {
            parseRegions(
                """
                {"version": 1, "regions": [
                  {"id": "rara", "priority": 1, "catalogId": "ruta-1",
                   "geometry": {"kind": "circle", "cx": 0.5, "cy": 0.5, "r": 0.1}}
                ]}
                """.trimIndent()
            )
            fail("Debía rechazar catalogId no numérico")
        } catch (expected: MapRegionsDataException) {
            assertTrue("catalogId" in expected.message!!)
        }
    }

    // ─────────────────────── Referencias cruzadas ───────────────────────

    @Test
    fun writeThenParse_thenValidateReferencesAgainstParsedCatalog() {
        val catalog = parseCatalog(
            """
            {"ubicaciones": [
              {"id": 1,  "nombre": "Ruta 1",        "tipo": "ruta"},
              {"id": 26, "nombre": "Pueblo Paleta", "tipo": "ciudad_pueblo"}
            ]}
            """.trimIndent()
        )
        val exportedText = map.writeRegions(catalog)
        val parsed = parseRegions(exportedText)
        parsed.validateReferences(catalog)
    }

    @Test
    fun writeThenParse_detectsBrokenLinkAgainstTrimmedCatalog() {
        val catalog = parseCatalog("""{"ubicaciones": [{"id": 1, "nombre": "Ruta 1", "tipo": "ruta"}]}""")
        val parsed = parseRegions(map.writeRegions())
        try {
            parsed.validateReferences(catalog)
            fail("Debía detectar el catalogId 26 ausente del catálogo")
        } catch (expected: MapRegionsDataException) {
            assertTrue("26" in expected.message!!)
        }
    }
}
