/**
 * Pruebas del modelo de zonas interactivas de Kanto: geometrías, prioridades
 * en solape y resolución nominal contra el catálogo.
 *
 * Son pruebas de JVM puras: el modelo no depende de Android.
 */
package com.dsrs.pokeclic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val T = 1e-6f

class MapRegionsTest {

    // ───────────────────────── Círculo ─────────────────────────

    @Test
    fun circle_containsInteriorPoints() {
        val circle = MapRegion(
            id = "ciudad",
            geometry = MapRegionGeometry.Circle(cx = 0.5f, cy = 0.5f, r = 0.25f)
        )
        assertTrue(circle.contains(0.5f, 0.5f))
        assertTrue(circle.contains(0.5f, 0.65f))
    }

    /** Radio y centro con potencias de dos para que la igualdad sea exacta. */
    @Test
    fun circle_containsItsOwnEdge() {
        val circle = MapRegion(
            id = "ciudad",
            geometry = MapRegionGeometry.Circle(cx = 0.5f, cy = 0.5f, r = 0.25f)
        )
        assertTrue(circle.contains(0.75f, 0.5f))
    }

    @Test
    fun circle_rejectsOutsidePoints() {
        val circle = MapRegion(
            id = "ciudad",
            geometry = MapRegionGeometry.Circle(cx = 0.5f, cy = 0.5f, r = 0.25f)
        )
        assertFalse(circle.contains(0.75f + T, 0.5f))
        assertFalse(circle.contains(0.0f, 0.0f))
    }

    @Test
    fun circle_zeroRadius_neverContains() {
        val circle = MapRegion(
            id = "punto",
            geometry = MapRegionGeometry.Circle(cx = 0.5f, cy = 0.5f, r = 0f)
        )
        assertFalse(circle.contains(0.5f, 0.5f))
    }

    // ───────────────────────── Rectángulo ─────────────────────────

    @Test
    fun envelope_containsInteriorAndEdges() {
        val envelope = MapRegion(
            id = "lab",
            geometry = MapRegionGeometry.Envelope(0.1f, 0.8f, 0.3f, 0.9f)
        )
        assertTrue(envelope.contains(0.2f, 0.85f))
        assertTrue(envelope.contains(0.1f, 0.8f))
        assertTrue(envelope.contains(0.3f, 0.9f))
    }

    @Test
    fun envelope_normalizesSwappedCorners() {
        val envelope = MapRegion(
            id = "lab",
            geometry = MapRegionGeometry.Envelope(0.3f, 0.9f, 0.1f, 0.8f)
        )
        assertTrue(envelope.contains(0.2f, 0.85f))
        assertFalse(envelope.contains(0.1f - T, 0.85f))
    }

    @Test
    fun envelope_rejectsOutsidePoints() {
        val envelope = MapRegion(
            id = "lab",
            geometry = MapRegionGeometry.Envelope(0.1f, 0.8f, 0.3f, 0.9f)
        )
        assertFalse(envelope.contains(0.3f + T, 0.85f))
        assertFalse(envelope.contains(0.2f, 0.8f - T))
    }

    // ───────────────────────── Polígono ─────────────────────────

    /**
     * Polígono en forma de "L" (cóncavo): el vértice interior queda fuera del
     * relleno y el ray casting tiene que atravesarlo bien.
     */
    private val lShape = MapRegionGeometry.Polygon(
        listOf(
            UvPoint(0.0f, 0.0f),
            UvPoint(1.0f, 0.0f),
            UvPoint(1.0f, 1.0f),
            UvPoint(0.5f, 1.0f),
            UvPoint(0.5f, 0.5f),
            UvPoint(0.0f, 0.5f)
        )
    )

    @Test
    fun polygon_containsInteriorOfTheBody() {
        val route = MapRegion(id = "L", geometry = lShape)
        assertTrue(route.contains(0.25f, 0.25f))
    }

    @Test
    fun polygon_containsWingOnlyOnItsSide() {
        val route = MapRegion(id = "L", geometry = lShape)
        assertTrue(route.contains(0.8f, 0.8f))
        // La muesca cóncava (la "L" sin cuerpo) es exterior.
        assertFalse(route.contains(0.3f, 0.7f))
    }

    @Test
    fun polygon_rejectsOutsidePoints() {
        val route = MapRegion(id = "L", geometry = lShape)
        // La muesca cóncava de la "L" no es interior.
        assertFalse(route.contains(0.2f, 0.8f))
        // ... ni tampoco fuera de todos los bordes.
        assertFalse(route.contains(1.1f, 0.5f))
    }

    @Test
    fun polygon_shortList_neverContains() {
        val open = MapRegion(
            id = "abierta",
            geometry = MapRegionGeometry.Polygon(
                listOf(UvPoint(0f, 0f), UvPoint(1f, 0f))
            )
        )
        assertFalse(open.contains(0.5f, 0.5f))
    }

    // ────────────────────── Prioridad y solape ──────────────────────

    /** Misma zona para tres regiones de distinto tipo: ganará la ciudad. */
    @Test
    fun regionAt_prefersHighestPriorityOnOverlap() {
        val map = KantoRegionMap(
            version = 1,
            regions = listOf(
                MapRegion(
                    id = "ruta-1",
                    geometry = MapRegionGeometry.Envelope(0f, 0f, 1f, 1f),
                    priority = MapRegionType.ROUTE.defaultPriority
                ),
                MapRegion(
                    id = "especial",
                    geometry = MapRegionGeometry.Envelope(0f, 0f, 1f, 1f),
                    priority = MapRegionType.SPECIAL.defaultPriority
                ),
                MapRegion(
                    id = "ciudad",
                    geometry = MapRegionGeometry.Envelope(0f, 0f, 1f, 1f),
                    priority = MapRegionType.CITY.defaultPriority
                )
            )
        )
        assertEquals("ciudad", map.regionAt(0.5f, 0.5f)!!.id)
    }

    /** Empate de prioridad: gana la primera de la lista, de modo estable. */
    @Test
    fun regionAt_tieBreaksByListOrder() {
        val tie = MapRegionGeometry.Envelope(0f, 0f, 1f, 1f)
        val first = MapRegion(id = "primera", geometry = tie, priority = 5)
        val second = MapRegion(id = "segunda", geometry = tie, priority = 5)
        assertEquals(first.id, KantoRegionMap(1, listOf(first, second)).regionAt(0.4f, 0.4f)!!.id)
        assertEquals(second.id, KantoRegionMap(1, listOf(second, first)).regionAt(0.4f, 0.4f)!!.id)
    }

    @Test
    fun regionAt_emptyMap_isNull() {
        assertNull(KantoRegionMap(1, emptyList()).regionAt(0.5f, 0.5f))
    }

    @Test
    fun regionAt_middleOfNowhere_isNull() {
        val map = KantoRegionMap(
            version = 1,
            regions = listOf(
                MapRegion(
                    id = "isla",
                    geometry = MapRegionGeometry.Circle(0.2f, 0.2f, 0.1f)
                )
            )
        )
        assertNull(map.regionAt(0.8f, 0.8f))
    }

    // ────────────────────── Nombres resolvidos ──────────────────────

    private val catalog = listOf(
        MapLocation(1, "Ruta 1", MapRegionType.ROUTE),
        MapLocation(26, "Pueblo Paleta", MapRegionType.CITY)
    )

    @Test
    fun resolvedName_usesCatalogLookup() {
        val linked = MapRegion(
            id = "paleta",
            geometry = MapRegionGeometry.Circle(0.5f, 0.5f, 0.1f),
            catalogId = 26
        )
        assertEquals("Pueblo Paleta", linked.resolvedName(catalog))
        assertEquals(MapRegionType.CITY, linked.resolvedType(catalog))
    }

    @Test
    fun resolvedName_fallsBackToEmbeddedWhenUnlinked() {
        val standalone = MapRegion(
            id = "cueva",
            geometry = MapRegionGeometry.Circle(0.5f, 0.5f, 0.1f),
            name = "Cueva Misteriosa",
            type = MapRegionType.SPECIAL
        )
        assertEquals("Cueva Misteriosa", standalone.resolvedName(catalog))
        assertEquals(MapRegionType.SPECIAL, standalone.resolvedType(catalog))
    }

    @Test
    fun resolvedName_unknownCatalogLink_fallsBackToEmbedded() {
        val linked = MapRegion(
            id = "rota",
            geometry = MapRegionGeometry.Circle(0.5f, 0.5f, 0.1f),
            catalogId = 999
        )
        assertNull(linked.resolvedName(catalog))
        // Sin tipo propio ni catálogo: prioridad por defecto de ruta.
        assertEquals(MapRegionType.ROUTE.defaultPriority, linked.effectivePriority(catalog))
    }

    @Test
    fun effectivePriority_explicitPriorityWinsOverResolvedType() {
        val linked = MapRegion(
            id = "paleta",
            geometry = MapRegionGeometry.Circle(0.5f, 0.5f, 0.1f),
            catalogId = 26,
            priority = 9
        )
        assertEquals(9, linked.effectivePriority(catalog))
    }

    @Test
    fun effectivePriority_deduce_del_tipo_resuelto() {
        val linked = MapRegion(
            id = "ruta-1",
            geometry = MapRegionGeometry.Envelope(0f, 0f, 1f, 1f),
            catalogId = 1
        )
        assertEquals(MapRegionType.ROUTE.defaultPriority, linked.effectivePriority(catalog))
    }

    // ────────────────────── Referencias cruzadas ──────────────────────

    @Test
    fun validateReferences_acceptsConsistentLink() {
        val map = KantoRegionMap(
            version = 1,
            regions = listOf(
                MapRegion(
                    id = "paleta",
                    geometry = MapRegionGeometry.Circle(0.5f, 0.5f, 0.1f),
                    priority = 3,
                    catalogId = 26
                )
            )
        )
        map.validateReferences(catalog)
    }

    @Test
    fun validateReferences_rejectsBrokenLink() {
        val map = KantoRegionMap(
            version = 1,
            regions = listOf(
                MapRegion(
                    id = "rota",
                    geometry = MapRegionGeometry.Circle(0.5f, 0.5f, 0.1f),
                    priority = 3,
                    catalogId = 999
                )
            )
        )
        try {
            map.validateReferences(catalog)
            throw AssertionError("Debía fallar con catalogId inexistente")
        } catch (expected: MapRegionsDataException) {
            assertTrue("999" in expected.message!!)
        }
    }

    // ─────────────────── Selección múltiple (selectAt) ───────────────────

    /** Catálogo minúsculo: enlace de catálogo 1 (ruta) y 26 (ciudad). */
    private val selectCatalog = listOf(
        MapLocation(1, "Ruta 1", MapRegionType.ROUTE),
        MapLocation(26, "Pueblo Paleta", MapRegionType.CITY)
    )

    private fun catalogRegion(
        catalogId: Int,
        geometry: MapRegionGeometry
    ): MapRegion = MapRegion(
        id = "ubic-$catalogId",
        geometry = geometry,
        catalogId = catalogId
    )

    private fun freeRegion(
        name: String,
        type: MapRegionType,
        geometry: MapRegionGeometry
    ): MapRegion = MapRegion(
        id = "libre-$name",
        geometry = geometry,
        name = name,
        type = type
    )

    private fun mapOf(regions: List<MapRegion>): KantoRegionMap =
        KantoRegionMap(version = 1, regions = regions)

    @Test
    fun selectAt_cityAlone_primaryOnly() {
        val map = mapOf(
            listOf(
                catalogRegion(26, MapRegionGeometry.Circle(0.3f, 0.4f, 0.15f))
            )
        )
        val selection = map.selectAt(0.3f, 0.4f, selectCatalog)!!
        assertEquals(26, selection.primary.catalogId)
        assertTrue(selection.secondary.isEmpty())
    }

    @Test
    fun selectAt_routeAlone_primaryOnly() {
        val map = mapOf(
            listOf(
                catalogRegion(1, MapRegionGeometry.Envelope(0.5f, 0.5f, 0.9f, 0.7f))
            )
        )
        val selection = map.selectAt(0.7f, 0.6f, selectCatalog)!!
        assertEquals(1, selection.primary.catalogId)
        assertTrue(selection.secondary.isEmpty())
    }

    @Test
    fun selectAt_specialAlone_primaryOnly() {
        val map = mapOf(
            listOf(
                freeRegion(
                    "Cueva Rara",
                    MapRegionType.SPECIAL,
                    MapRegionGeometry.Circle(0.2f, 0.8f, 0.1f)
                )
            )
        )
        val selection = map.selectAt(0.2f, 0.8f, selectCatalog)!!
        assertEquals("Cueva Rara", selection.primary.name)
        assertEquals(MapRegionType.SPECIAL, selection.primary.type)
        assertTrue(selection.secondary.isEmpty())
    }

    @Test
    fun selectAt_cityAndSpecial_cityFirst() {
        val map = mapOf(
            listOf(
                freeRegion(
                    "Especial",
                    MapRegionType.SPECIAL,
                    MapRegionGeometry.Envelope(0.25f, 0.35f, 0.35f, 0.45f)
                ),
                catalogRegion(26, MapRegionGeometry.Circle(0.3f, 0.4f, 0.15f))
            )
        )
        val selection = map.selectAt(0.3f, 0.4f, selectCatalog)!!
        assertEquals(26, selection.primary.catalogId)
        assertEquals(1, selection.secondary.size)
        assertEquals(MapRegionType.SPECIAL, selection.secondary.single().type)
    }

    @Test
    fun selectAt_cityAndRoute_cityFirst() {
        val map = mapOf(
            listOf(
                catalogRegion(1, MapRegionGeometry.Envelope(0.2f, 0.2f, 0.4f, 0.4f)),
                catalogRegion(26, MapRegionGeometry.Circle(0.3f, 0.3f, 0.15f))
            )
        )
        val selection = map.selectAt(0.3f, 0.3f, selectCatalog)!!
        assertEquals(26, selection.primary.catalogId)
        assertEquals(1, selection.secondary.single().catalogId)
    }

    @Test
    fun selectAt_routeAndSeveralSpecials_routeFirstThenSpecialsInJsonOrder() {
        val map = mapOf(
            listOf(
                // Especial B delante en el JSON: el orden de lista gana el empate.
                freeRegion(
                    "Especial B",
                    MapRegionType.SPECIAL,
                    MapRegionGeometry.Circle(0.5f, 0.6f, 0.2f)
                ),
                catalogRegion(1, MapRegionGeometry.Polygon(listOf(
                    UvPoint(0.0f, 0.5f),
                    UvPoint(1.0f, 0.5f),
                    UvPoint(1.0f, 0.7f),
                    UvPoint(0.0f, 0.7f)
                ))),
                freeRegion(
                    "Especial A",
                    MapRegionType.SPECIAL,
                    MapRegionGeometry.Envelope(0.45f, 0.55f, 0.55f, 0.65f)
                )
            )
        )
        val selection = map.selectAt(0.5f, 0.6f, selectCatalog)!!
        assertEquals(1, selection.primary.catalogId)
        assertEquals(
            listOf("Especial B", "Especial A"),
            selection.secondary.map { it.name }
        )
    }

    @Test
    fun selectAt_severalSpecials_allShownInJsonOrder() {
        val map = mapOf(
            listOf(
                freeRegion(
                    "Torre",
                    MapRegionType.SPECIAL,
                    MapRegionGeometry.Circle(0.5f, 0.45f, 0.15f)
                ),
                freeRegion(
                    "Central",
                    MapRegionType.SPECIAL,
                    MapRegionGeometry.Circle(0.5f, 0.55f, 0.15f)
                )
            )
        )
        val selection = map.selectAt(0.5f, 0.5f, selectCatalog)!!
        // Ambas se muestran: la primera del JSON como destacada, la otra debajo.
        assertEquals("Torre", selection.primary.name)
        assertEquals(listOf("Central"), selection.secondary.map { it.name })
    }

    @Test
    fun selectAt_tieSameCategory_firstInJsonListWins() {
        val map = mapOf(
            listOf(
                catalogRegion(26, MapRegionGeometry.Circle(0.5f, 0.5f, 0.2f)),
                MapRegion(
                    id = "paleta-2",
                    geometry = MapRegionGeometry.Circle(0.5f, 0.5f, 0.2f),
                    name = "Segunda Ciudad",
                    type = MapRegionType.CITY
                )
            )
        )
        val selection = map.selectAt(0.5f, 0.5f, selectCatalog)!!
        assertEquals(26, selection.primary.catalogId)
        assertEquals("Segunda Ciudad", selection.secondary.single().name)
    }

    @Test
    fun selectAt_allThreeCategories_cityRouteSpecial() {
        val map = mapOf(
            listOf(
                freeRegion(
                    "Especial",
                    MapRegionType.SPECIAL,
                    MapRegionGeometry.Envelope(0.3f, 0.3f, 0.4f, 0.4f)
                ),
                catalogRegion(1, MapRegionGeometry.Envelope(0.25f, 0.25f, 0.45f, 0.45f)),
                catalogRegion(26, MapRegionGeometry.Circle(0.35f, 0.35f, 0.15f))
            )
        )
        val selection = map.selectAt(0.35f, 0.35f, selectCatalog)!!
        assertEquals(26, selection.primary.catalogId)
        assertEquals(
            listOf(MapRegionType.ROUTE, MapRegionType.SPECIAL),
            selection.secondary.map { it.resolvedType(selectCatalog) }
        )
    }

    @Test
    fun selectAt_outsideAllRegions_null() {
        val map = mapOf(
            listOf(
                catalogRegion(26, MapRegionGeometry.Circle(0.3f, 0.3f, 0.1f)),
                catalogRegion(1, MapRegionGeometry.Envelope(0.6f, 0.6f, 0.9f, 0.8f))
            )
        )
        assertNull(map.selectAt(0.05f, 0.05f, selectCatalog))
        assertNull(map.selectAt(0.5f, 0.5f, selectCatalog))
    }

    @Test
    fun selectAt_emptyMap_null() {
        assertNull(KantoRegionMap(1, emptyList()).selectAt(0.5f, 0.5f, selectCatalog))
    }

    @Test
    fun selectAt_geometryBoundaries_inclusive() {
        val map = mapOf(
            listOf(
                // Círculo: el borde exacto entra (radio potencia de dos, como regionAt).
                catalogRegion(26, MapRegionGeometry.Circle(0.5f, 0.5f, 0.25f)),
                // Rectángulo: la esquina exacta entra.
                catalogRegion(1, MapRegionGeometry.Envelope(0.1f, 0.1f, 0.3f, 0.3f))
            )
        )
        val atCircleEdge = map.selectAt(0.75f, 0.5f, selectCatalog)!!
        assertTrue(atCircleEdge.primary.catalogId == 26 || 26 in atCircleEdge.secondary.map { it.catalogId })

        val atEnvelopeCorner = map.selectAt(0.3f, 0.3f, selectCatalog)!!
        assertTrue(1 in atEnvelopeCorner.primary.let { listOfNotNull(it.catalogId) } +
            atEnvelopeCorner.secondary.mapNotNull { it.catalogId })
    }
}
