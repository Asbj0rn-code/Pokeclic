/**
 * Carga y validación conjunta del mapeo de Kanto: catálogo de ubicaciones
 * (`assets/kanto_locations.json`) y zonas interactivas
 * (`assets/kanto_regions.json`).
 *
 * La parte **pura** ([parseKantoMapData]) es testeable en JVM; la parte de
 * Android ([loadKantoMapData]) solo lee assets y registra el fallo en el log
 * — nunca lanza: el juego debe seguir funcionando con el mapa sin zonas si los
 * ficheros faltan, están vacíos o corruptos (el indicador simplemente no
 * aparece y el diagnóstico queda en `Logcat` con la etiqueta [KANTO_MAP_LOG_TAG]).
 */
package com.dsrs.pokeclic.data

import android.content.Context
import android.util.Log
import com.dsrs.pokeclic.model.KantoRegionMap
import com.dsrs.pokeclic.model.MapLocation
import com.dsrs.pokeclic.model.MapRegion
import com.dsrs.pokeclic.model.parseCatalog
import com.dsrs.pokeclic.model.parseRegions
import com.dsrs.pokeclic.model.validateReferences

/** Nombre del asset del catálogo nominal (nombres, ids y tipos). */
private const val LOCATIONS_ASSET = "kanto_locations.json"

/** Nombre del asset del mapeo de zonas (geometrías). */
private const val REGIONS_ASSET = "kanto_regions.json"

/** Etiqueta de «log»: todos los fallos de estos ficheros salen con ella. */
const val KANTO_MAP_LOG_TAG = "KantoMap"

/**
 * Sistema de mapeo ya cargado y validado, listo para que la UI consulte.
 *
 * [MapScreen] recuerda una instancia por sesión de la pantalla; [onTap] usa [
 * KantoRegionMap.regionAt] sobre [regions] y resuelve el nombre visible con
 * [MapRegion.resolvedName] contra [locations].
 */
data class KantoMapData(
    val locations: List<MapLocation>,
    val regions: KantoRegionMap
)

/**
 * Parsea y **cruza** los dos documentos: catálogo correcto, mapeo correcto y
 * todo `catalogId` presente en el catálogo
 * ([com.dsrs.pokeclic.model.KantoRegionMap.validateReferences]). Cualquier
 * problema de esquema llega como `Failure` con una
 * [com.dsrs.pokeclic.model.MapRegionsDataException] descriptiva.
 */
fun parseKantoMapData(locationsText: String, regionsText: String): Result<KantoMapData> =
    runCatching {
        val locations = parseCatalog(locationsText)
        val regions = parseRegions(regionsText)
        regions.validateReferences(locations)
        KantoMapData(locations = locations, regions = regions)
    }

/**
 * Lee los dos assets y los valida con [parseKantoMapData]. Nunca lanza: los
 * errores (fichero ausente, JSON ínsaluble, enlaces rotos) devuelven
 * `Failure` y quedan registrados en el log para su diagnóstico.
 */
fun loadKantoMapData(context: Context): Result<KantoMapData> =
    runCatching {
        val locationsText = context.assets.open(LOCATIONS_ASSET).bufferedReader().use { it.readText() }
        val regionsText = context.assets.open(REGIONS_ASSET).bufferedReader().use { it.readText() }
        parseKantoMapData(locationsText, regionsText).getOrThrow()
    }.onFailure { error ->
        Log.w(KANTO_MAP_LOG_TAG, "El mapeo de Kanto no está disponible: ${error.message}", error)
    }
