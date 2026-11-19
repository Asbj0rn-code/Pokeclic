package com.dsrs.pokeclic.viewmodel

import com.dsrs.pokeclic.data.KantoMapData
import com.dsrs.pokeclic.model.KantoRegionMap
import com.dsrs.pokeclic.model.MapLocation
import com.dsrs.pokeclic.model.MapRegion
import com.dsrs.pokeclic.model.MapRegionGeometry
import com.dsrs.pokeclic.model.MapRegionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato del ViewModel del mapa: carga el mapeo por su cuenta (nunca la UI),
 * resuelve el nombre visible de la zona tocada y limpia el indicador al volver
 * a abrir la pantalla.
 */
class MapViewModelTest {

    private val catalog = listOf(
        MapLocation(1, "Ruta 1", MapRegionType.ROUTE),
        MapLocation(26, "Pueblo Paleta", MapRegionType.CITY)
    )

    private fun catalogRegion(catalogId: Int, geometry: MapRegionGeometry): MapRegion =
        MapRegion(
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

    private fun viewModelFor(regions: List<MapRegion>): MapViewModel =
        MapViewModel(
            loadMap = {
                Result.success(
                    KantoMapData(
                        locations = catalog,
                        regions = KantoRegionMap(version = 1, regions = regions)
                    )
                )
            }
        )

    @Test
    fun tapInsideRegion_exposesCatalogName() {
        val viewModel = viewModelFor(
            listOf(catalogRegion(26, MapRegionGeometry.Circle(0.3f, 0.4f, 0.15f)))
        )

        viewModel.onScreenShown()
        viewModel.onTap(0.3f, 0.4f)

        assertEquals("Pueblo Paleta", viewModel.uiState.value.primaryName)
        assertTrue(viewModel.uiState.value.otherNames.isEmpty())
    }

    @Test
    fun tapOutsideEveryRegion_clearsSelection() {
        val viewModel = viewModelFor(
            listOf(catalogRegion(26, MapRegionGeometry.Circle(0.3f, 0.4f, 0.15f)))
        )

        viewModel.onScreenShown()
        viewModel.onTap(0.3f, 0.4f)
        viewModel.onTap(0.9f, 0.1f)

        assertNull(viewModel.uiState.value.primaryName)
        assertTrue(viewModel.uiState.value.otherNames.isEmpty())
    }

    @Test
    fun tapSeveralRegions_ordersCityFirstAndListsTheRest() {
        val viewModel = viewModelFor(
            listOf(
                freeRegion(
                    "Cueva Rara",
                    MapRegionType.SPECIAL,
                    MapRegionGeometry.Envelope(0.45f, 0.45f, 0.55f, 0.55f)
                ),
                catalogRegion(1, MapRegionGeometry.Envelope(0.4f, 0.4f, 0.6f, 0.6f)),
                catalogRegion(26, MapRegionGeometry.Circle(0.5f, 0.5f, 0.3f))
            )
        )

        viewModel.onScreenShown()
        viewModel.onTap(0.5f, 0.5f)

        assertEquals("Pueblo Paleta", viewModel.uiState.value.primaryName)
        assertEquals(listOf("Ruta 1", "Cueva Rara"), viewModel.uiState.value.otherNames)
    }

    @Test
    fun screenShownAgain_clearsPreviousSelection() {
        val viewModel = viewModelFor(
            listOf(catalogRegion(26, MapRegionGeometry.Circle(0.3f, 0.4f, 0.15f)))
        )

        viewModel.onScreenShown()
        viewModel.onTap(0.3f, 0.4f)
        viewModel.onScreenShown()

        assertNull(viewModel.uiState.value.primaryName)
    }

    @Test
    fun tapWithFailedLoad_leavesNoSelection() {
        val viewModel = MapViewModel(loadMap = { Result.failure(IllegalStateException("boom")) })

        viewModel.onScreenShown()
        viewModel.onTap(0.3f, 0.4f)

        assertNull(viewModel.uiState.value.primaryName)
        assertTrue(viewModel.uiState.value.otherNames.isEmpty())
    }

    @Test
    fun primaryWithBrokenCatalogLink_fallsBackToTechnicalId() {
        val orphan = MapRegion(
            id = "huerfana",
            geometry = MapRegionGeometry.Circle(0.5f, 0.5f, 0.2f),
            catalogId = 999
        )
        val viewModel = viewModelFor(listOf(orphan))

        viewModel.onScreenShown()
        viewModel.onTap(0.5f, 0.5f)

        assertEquals("huerfana", viewModel.uiState.value.primaryName)
    }
}
