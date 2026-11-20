package com.dsrs.pokeclic.viewmodel

import androidx.compose.runtime.Immutable

/**
 * Estado de la pantalla del mapa, **ya resuelto** por [MapViewModel].
 *
 * La UI solo pinta lo que hay aquí: no conoce el catálogo de Kanto, ni las
 * geometrías de las zonas, ni el resultado del hit-testing.
 *
 * @param primaryName nombre visible de la zona destacada bajo la última
 *   pulsación; `null` cuando no hay selección (antes del primer toque, al tocar
 *   fuera de toda zona o al volver a abrir la pantalla). Si el enlace de
 *   catálogo está roto se usa el id técnico como último recurso.
 * @param otherNames resto de zonas que también contienen el punto, en el orden
 *   de presentación (ciudad > ruta > especial). Las que no resuelven nombre se
 *   omiten.
 */
@Immutable
data class MapUiState(
    val primaryName: String? = null,
    val otherNames: List<String> = emptyList()
)
