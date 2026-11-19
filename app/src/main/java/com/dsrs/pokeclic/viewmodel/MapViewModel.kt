package com.dsrs.pokeclic.viewmodel

import androidx.lifecycle.ViewModel
import com.dsrs.pokeclic.data.KantoMapData
import com.dsrs.pokeclic.model.MapRegionSelection
import com.dsrs.pokeclic.model.selectAt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel de la pantalla del mapa.
 *
 * Es el único que toca la capa de datos: carga el mapeo de Kanto y hace el
 * hit-testing de la zona tocada, entregando a la UI nombres ya resueltos en
 * [MapUiState]. La pantalla solo convierte el toque a coordenadas normalizadas
 * (transformación visual) y reenvía el evento.
 *
 * El mapeo se carga **una sola vez** en [onScreenShown], la primera vez que la
 * pantalla aparece, y un fallo de carga deja el estado vacío para siempre: el
 * mapa sigue funcionando sin indicador, igual que antes.
 *
 * @param loadMap acceso al mapeo ya validado. Se inyecta desde el borde de la
 *   app (`MainActivity`) para que el ViewModel no dependa de `Context` ni lea
 *   assets por su cuenta en los tests.
 */
class MapViewModel(
    private val loadMap: () -> Result<KantoMapData>
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    /** Mapeo cargado perezosamente; `null` mientras no se haya abierto o si falló. */
    private var map: KantoMapData? = null

    /**
     * La pantalla apareció. Carga el mapeo la primera vez y limpia el indicador
     * anterior: al abrir el mapa no hay ninguna zona destacada.
     */
    fun onScreenShown() {
        if (map == null) {
            map = loadMap().getOrNull()
        }
        _uiState.value = MapUiState()
    }

    /**
     * Toque simple en el mapa, en coordenadas **normalizadas** `(u, v)` de la
     * imagen original. Resuelve todas las zonas que contienen el punto; fuera
     * de toda zona el indicador queda limpio.
     */
    fun onTap(u: Float, v: Float) {
        val data = map ?: return clearSelection()
        val selection = data.regions.selectAt(u, v, data.locations)
        _uiState.value = selection?.toUiState(data) ?: MapUiState()
    }

    private fun clearSelection() {
        _uiState.value = MapUiState()
    }
}

private fun MapRegionSelection.toUiState(data: KantoMapData): MapUiState =
    MapUiState(
        primaryName = primary.resolvedName(data.locations) ?: primary.id,
        otherNames = secondary.mapNotNull { it.resolvedName(data.locations) }
    )
