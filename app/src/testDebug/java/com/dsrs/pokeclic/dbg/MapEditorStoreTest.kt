/**
 * Pruebas del almacén del borrador del editor (subetapa 4D): escritura/lectura
 * atómica en ficheros reales (carpeta temporal de JUnit), esquema compatible
 * con `kanto_regions.json`, y políticas de fichero dañado o ausente.
 */
package com.dsrs.pokeclic.dbg

import com.dsrs.pokeclic.model.MapLocation
import com.dsrs.pokeclic.model.MapRegion
import com.dsrs.pokeclic.model.MapRegionGeometry
import com.dsrs.pokeclic.model.MapRegionType
import com.dsrs.pokeclic.model.UvPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private val CATALOG = listOf(
    MapLocation(id = 1, name = "Pueblo Paleta", type = MapRegionType.CITY),
    MapLocation(id = 2, name = "Ruta 1", type = MapRegionType.ROUTE),
    MapLocation(id = 3, name = "Laboratorio Oak", type = MapRegionType.SPECIAL)
)

private val LINKED_CIRCLE = MapRegion(
    id = "ubic-1",
    geometry = MapRegionGeometry.Circle(cx = 0.3f, cy = 0.4f, r = 0.1f),
    catalogId = 1
)

private val FREE_POLYGON = MapRegion(
    id = "libre-1",
    geometry = MapRegionGeometry.Polygon(
        listOf(UvPoint(0.1f, 0.1f), UvPoint(0.2f, 0.1f), UvPoint(0.15f, 0.2f))
    ),
    name = "Bosque Verde",
    type = MapRegionType.SPECIAL
)

private val LINKED_ENVELOPE = MapRegion(
    id = "ubic-2",
    geometry = MapRegionGeometry.Envelope(x1 = 0.5f, y1 = 0.5f, x2 = 0.7f, y2 = 0.8f),
    priority = 4,
    catalogId = 2
)

private fun storeIn(root: TemporaryFolder): MapEditorStore =
    MapEditorStore(root.newFolder("mapeo_kanto"))

class MapEditorStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun load_withoutDraft_returnsNone() {
        val load = storeIn(temp).load()
        assertEquals(MapEditorDraftLoad.None, load)
    }

    @Test
    fun save_andLoad_roundTripPreservesRegionsAndExplicitPriorities() {
        val store = storeIn(temp)
        val regions = listOf(LINKED_CIRCLE, FREE_POLYGON, LINKED_ENVELOPE)

        assertNull(store.save(regions, CATALOG))
        val load = store.load()
        assertTrue(load is MapEditorDraftLoad.Loaded)
        // El esquema serializa SIEMPRE la prioridad explícita (autocontenido:
        // comportamiento documentado de MapRegionsJson); al releer, deja de
        // ser implícita. Comparar con esa normalización aplicada.
        val expected = regions.map { region ->
            region.copy(priority = region.effectivePriority(CATALOG))
        }
        assertEquals(expected, (load as MapEditorDraftLoad.Loaded).regions)
    }

    @Test
    fun save_linkedRegionWithImplicitPriority_writesItResolvedFromCatalog() {
        val store = storeIn(temp)
        assertNull(store.save(listOf(LINKED_CIRCLE, FREE_POLYGON), CATALOG))
        val loaded = (store.load() as MapEditorDraftLoad.Loaded).regions

        // Autocontenido: la enlazada sin prioridad explícita se serializa con
        // su prioridad resuelta (ciudad → 3); al leerla ya no depende del catálogo.
        val linked = loaded.first { it.id == "ubic-1" }
        assertEquals(3, linked.priority)
        // La libre se serializa con el valor por defecto de su tipo (2).
        assertEquals(2, loaded.first { it.id == "libre-1" }.priority)
    }

    @Test
    fun save_thenSaveAgain_replacesTheWholeDraft_notAppendsNorCorrupts() {
        val store = storeIn(temp)
        assertNull(store.save(listOf(LINKED_CIRCLE), CATALOG))
        assertNull(store.save(listOf(LINKED_CIRCLE, FREE_POLYGON, LINKED_ENVELOPE), CATALOG))

        val loaded = (store.load() as MapEditorDraftLoad.Loaded).regions
        assertEquals(3, loaded.size)

        // Sin restos del temporal tras el guardado atómico.
        val leftovers = temp.root.resolve("mapeo_kanto")
            .listFiles()
            ?.filter { it.name.endsWith(".tmp") }
            .orEmpty()
        assertTrue(leftovers.isEmpty())
    }

    @Test
    fun load_withCorruptedJson_reportsReason_andKeepsTheFileOnDisk() {
        val store = storeIn(temp)
        assertNull(store.save(listOf(LINKED_CIRCLE), CATALOG))
        val file = temp.root.resolve("mapeo_kanto/kanto_regions_borrador.json")
        file.writeText("{ esto no es json")

        val load = store.load()
        assertTrue(load is MapEditorDraftLoad.Corrupted)
        assertNotNull((load as MapEditorDraftLoad.Corrupted).reason)
        // Nunca se borra solo: la decisión es del usuario en el diálogo.
        assertTrue(store.exists())
    }

    @Test
    fun load_withWrongSchemaVersion_isCorruptedNotCrash() {
        val store = storeIn(temp)
        val dir = temp.root.resolve("mapeo_kanto")
        dir.resolve("kanto_regions_borrador.json")
            .writeText("""{"version": 99, "regions": []}""")

        assertTrue(store.load() is MapEditorDraftLoad.Corrupted)
    }

    @Test
    fun delete_removesDraftAndTemp() {
        val store = storeIn(temp)
        assertNull(store.save(listOf(LINKED_CIRCLE), CATALOG))
        assertTrue(store.exists())

        assertTrue(store.delete())
        assertFalse(store.exists())
        assertEquals(MapEditorDraftLoad.None, store.load())
    }

    @Test
    fun exportPayload_matchesTheKantoRegionsSchema() {
        val store = storeIn(temp)
        val payload = store.exportPayload(
            listOf(LINKED_CIRCLE, FREE_POLYGON, LINKED_ENVELOPE),
            CATALOG
        )
        assertTrue(payload.contains("\"version\": 1"))
        assertTrue(payload.contains("\"image\""))

        // El fichero exportado lo releen los mismos parsers del juego.
        val reloaded = com.dsrs.pokeclic.model.parseRegions(payload)
        val expected = listOf(LINKED_CIRCLE, FREE_POLYGON, LINKED_ENVELOPE)
            .map { it.copy(priority = it.effectivePriority(CATALOG)) }
        assertEquals(expected, reloaded.regions)
    }
}
