/**
 * Serialización JSON del sistema de mapeo informativo de Kanto.
 *
 * Usa `org.json`, que ya está en la plataforma Android, así que no hay
 * dependencias ni plugins de build; los tests de JVM dependen puntualmente del
 * artefacto `org.json:json` vía `testImplementation`.
 *
 * Dos documentos relacionados, nunca mezclados:
 *
 * 1. **Catálogo** (`assets/kanto_locations.json`): copia derivada del fichero
 *    `ubicaciones.json` de pruebas, con sus claves originales en español
 *    (`ubicaciones` / `id` / `nombre` / `tipo`). Es la única fuente de nombres
 *    e identificadores; los `id` numéricos son estables y no se regeneran.
 * 2. **Mapeo** (`assets/kanto_regions.json`): solo geografía de las zonas ya
 *    dibujadas. Una región puede enlazarse al catálogo por `catalogId` — en
 *    cuyo caso no repite `name`/`type` — o ser libre (ambos embebidos).
 *
 * Flujo de actualización del mapeo (sin integración automática en el
 * proyecto): el editor de desarrollo guarda en borrador y exporta JSON por el
 * selector de documentos del sistema o al portapapeles; el fichero resultante
 * se pega o sobrescribe a mano en `app/src/main/assets/kanto_regions.json`, y
 * el juego lo lee al arrancar. El catálogo se edita en el repo, a mano.
 */
package com.dsrs.pokeclic.model

import org.json.JSONArray
import org.json.JSONObject

/** Versión de esquema que este código sabe leer y escribir. */
const val KANTO_REGIONS_SCHEMA_VERSION = 1

/** Nombre del recurso de imagen al que pertenecen las coordenadas. */
const val KANTO_MAP_IMAGE = "mapa_kanto.png"

/** Claves del catálogo, en las del original para copiarlo sin reescribirlo. */
private const val CATALOG_ROOT = "ubicaciones"
private const val CATALOG_ID = "id"
private const val CATALOG_NAME = "nombre"
private const val CATALOG_TYPE = "tipo"

private const val CATALOG_ROUTE = "ruta"
private const val CATALOG_CITY = "ciudad_pueblo"
private const val CATALOG_SPECIAL = "ubicacion_especial"

/** Tipos del catálogo → enum. Debe seguir la copia del original. */
private fun typeFromCatalog(raw: String): MapRegionType = when (raw) {
    CATALOG_ROUTE -> MapRegionType.ROUTE
    CATALOG_CITY -> MapRegionType.CITY
    CATALOG_SPECIAL -> MapRegionType.SPECIAL
    else -> throw MapRegionsDataException("Tipo de ubicación desconocido: \"$raw\"")
}

private fun typeFromJson(raw: String): MapRegionType = when (raw) {
    "CITY" -> MapRegionType.CITY
    "ROUTE" -> MapRegionType.ROUTE
    "SPECIAL" -> MapRegionType.SPECIAL
    else -> throw MapRegionsDataException("Tipo de región desconocido: \"$raw\"")
}

private fun typeName(type: MapRegionType): String = when (type) {
    MapRegionType.CITY -> "CITY"
    MapRegionType.ROUTE -> "ROUTE"
    MapRegionType.SPECIAL -> "SPECIAL"
}

/** Mensaje de error de campo con la entrada que lo falla (1-based para humanos). */
private fun catalogFieldError(entry: Int, field: String): Nothing =
    throw MapRegionsDataException(
        "La entrada $entry del catálogo no aporta \"$field\" válido"
    )

/**
 * Lee el catálogo de ubicaciones y valida su esquema mínimo: cada entrada con
 * `id` numérico **único**, `nombre` no vacía y `tipo` conocida. Devuelve la
 * lista en el orden del documento.
 */
fun parseCatalog(text: String): List<MapLocation> {
    val root = try {
        JSONObject(text)
    } catch (error: Exception) {
        throw MapRegionsDataException("El catálogo no es un objeto JSON válido", error)
    }

    val entries = root.optJSONArray(CATALOG_ROOT)
        ?: throw MapRegionsDataException("El catálogo no aporta \"$CATALOG_ROOT\" como array")

    val catalog = mutableListOf<MapLocation>()
    val seenIds = mutableSetOf<Int>()

    for (index in 0 until entries.length()) {
        val entry = index + 1
        val element = entries.optJSONObject(entry - 1)
            ?: throw MapRegionsDataException("La entrada $entry del catálogo no es un objeto")

        val id = element.opt(CATALOG_ID)
            ?.let { it as? Number ?: catalogFieldError(entry, CATALOG_ID) }
            ?.toInt()
            ?: catalogFieldError(entry, CATALOG_ID)

        val name = (element.opt(CATALOG_NAME) as? String)?.takeIf { it.isNotBlank() }
            ?: catalogFieldError(entry, CATALOG_NAME)

        val typeRaw = (element.opt(CATALOG_TYPE) as? String)?.takeIf { it.isNotBlank() }
            ?: catalogFieldError(entry, CATALOG_TYPE)

        if (!seenIds.add(id)) {
            throw MapRegionsDataException("id duplicado en el catálogo: $id")
        }

        catalog += MapLocation(id = id, name = name, type = typeFromCatalog(typeRaw))
    }
    return catalog
}

/**
 * Lee el mapeo de zonas. No puede tocar el catálogo, que vive en otro
 * fichero: los enlaces rotos se validan al cargar ambos documentos con
 * [KantoRegionMap.validateReferences].
 *
 * Reglas: `id` único; `geometry` con `kind` y sus campos completos; regiones
 * **libres** (sin `catalogId`) exigen `name` y `type`; regiones **enlazadas**
 * no pueden llevar `name`/`type` propios. La `priority` es opcional sólo
 * cuando el tipo se deduce sin catálogo; si una región enlazada no la lleva
 * explícita, se rechaza en el momento del parseo para no depender de leer los
 * dos ficheros a la vez.
 */
fun parseRegions(text: String): KantoRegionMap {
    val root = try {
        JSONObject(text)
    } catch (error: Exception) {
        throw MapRegionsDataException("El mapeo no es un objeto JSON válido", error)
    }

    val version = root.optInt("version", -1)
    if (version != KANTO_REGIONS_SCHEMA_VERSION) {
        throw MapRegionsDataException(
            "Versión de esquema no soportada: $version (esperada: $KANTO_REGIONS_SCHEMA_VERSION)"
        )
    }

    val regionsJson = root.optJSONArray("regions")
        ?: throw MapRegionsDataException("\"regions\" debe ser un array")

    val regions = mutableListOf<MapRegion>()
    val seenIds = mutableSetOf<String>()

    for (index in 0 until regionsJson.length()) {
        val element = regionsJson.optJSONObject(index)
            ?: throw MapRegionsDataException("La región ${index + 1} no es un objeto")

        val id = (element.opt("id") as? String)?.takeIf { it.isNotBlank() }
            ?: throw MapRegionsDataException("La región ${index + 1} no aporta \"id\"")

        if (!seenIds.add(id)) {
            throw MapRegionsDataException("id de región duplicado: \"$id\"")
        }

        val geometryJson = element.optJSONObject("geometry")
            ?: throw MapRegionsDataException("La región \"$id\" no aporta \"geometry\"")
        val geometry = parseGeometry(id, geometryJson)

        val catalogId: Int? = if (element.has("catalogId")) {
            val value = element.opt("catalogId") as? Number
                ?: throw MapRegionsDataException(
                    "La región \"$id\" deja \"catalogId\" sin valor entero"
                )
            value.toInt()
        } else {
            null
        }

        val name = (element.opt("name") as? String)?.takeIf { it.isNotBlank() }
        val type = (element.opt("type") as? String)
            ?.takeIf { it.isNotBlank() }
            ?.let { typeFromJson(it) }

        when {
            catalogId == null && (name == null || type == null) ->
                throw MapRegionsDataException(
                    "La región libre \"$id\" necesita \"name\" y \"type\" (no va enlazada al catálogo)"
                )
            catalogId != null && (name != null || type != null) ->
                throw MapRegionsDataException(
                    "La región \"$id\" mezcla enlace de catálogo con datos propios: " +
                        "si va enlazada, no lleva \"name\"/\"type\""
                )
        }

        val priority: Int? = when {
            element.has("priority") ->
                (element.opt("priority") as? Number)?.toInt()
                    ?: throw MapRegionsDataException(
                        "La región \"$id\" deja \"priority\" sin número entero"
                    )
            type != null -> type.defaultPriority
            else -> throw MapRegionsDataException(
                "La región \"$id\" no aporta \"priority\" y su tipo depende del catálogo, " +
                    "que este documento no puede leer: exige una prioridad explícita"
            )
        }

        regions += MapRegion(
            id = id,
            geometry = geometry,
            priority = priority,
            catalogId = catalogId,
            name = name,
            type = type
        )
    }

    return KantoRegionMap(version = version, regions = regions)
}

/**
 * Parsea la geometría por su discriminante `kind`. Los valores llegan tal
 * cual (sin normalizar orden); la consulta puede reordenarse en [MapRegion].
 */
private fun parseGeometry(regionId: String, geometry: JSONObject): MapRegionGeometry =
    when (val kind = geometry.optString("kind").takeIf { it.isNotBlank() }
        ?: throw MapRegionsDataException(
            "La geometría de \"$regionId\" no aporta \"kind\""
        )) {
        "circle" -> MapRegionGeometry.Circle(
            cx = requireCoordinate(regionId, kind, geometry, "cx"),
            cy = requireCoordinate(regionId, kind, geometry, "cy"),
            r = requireCoordinate(regionId, kind, geometry, "r")
        )

        "envelope" -> MapRegionGeometry.Envelope(
            x1 = requireCoordinate(regionId, kind, geometry, "x1"),
            y1 = requireCoordinate(regionId, kind, geometry, "y1"),
            x2 = requireCoordinate(regionId, kind, geometry, "x2"),
            y2 = requireCoordinate(regionId, kind, geometry, "y2")
        )

        "polygon" -> {
            val pointsJson = geometry.optJSONArray("points")
                ?: throw MapRegionsDataException(
                    "La geometría \"$kind\" de \"$regionId\" no aporta \"points\""
                )
            val points = mutableListOf<UvPoint>()
            for (index in 0 until pointsJson.length()) {
                val point = pointsJson.optJSONArray(index)
                    ?: throw MapRegionsDataException(
                        "El vértice ${index + 1} de \"$regionId\" no es un par [u, v]"
                    )
                if (point.length() != 2) {
                    throw MapRegionsDataException(
                        "El vértice ${index + 1} de \"$regionId\" debe tener exactamente 2 valores"
                    )
                }
                val u = point.optDouble(0, Double.NaN)
                val v = point.optDouble(1, Double.NaN)
                if (u.isNaN() || v.isNaN()) {
                    throw MapRegionsDataException(
                        "El vértice ${index + 1} de \"$regionId\" no es numérico"
                    )
                }
                points += UvPoint(u = u.toFloat(), v = v.toFloat())
            }
            if (points.size < 3) {
                throw MapRegionsDataException(
                    "\"$regionId\": los polígonos necesitan al menos 3 vértices " +
                        "(recibidos ${points.size})"
                )
            }
            MapRegionGeometry.Polygon(points)
        }

        else -> throw MapRegionsDataException(
            "Kind de geometría desconocido en \"$regionId\": \"$kind\""
        )
    }

private fun requireCoordinate(
    regionId: String,
    kind: String,
    geometry: JSONObject,
    field: String
): Float {
    val raw = geometry.optDouble(field, Double.NaN)
    if (raw.isNaN()) {
        throw MapRegionsDataException(
            "La geometría \"$kind\" de \"$regionId\" no aporta \"$field\""
        )
    }
    return raw.toFloat()
}

/**
 * Escribe el mapeo con el esquema versionado. La `priority` se serializa
 * siempre explícita con su valor [MapRegion.effectivePriority] contra las
 * [locations] que se pasen (el catálogo, cuando esté cargado), de modo que el
 * fichero exportado es autocontenido: al volver a leerlo no hace falta
 * deducir nada por fuera.
 */
fun KantoRegionMap.writeRegions(locations: List<MapLocation> = emptyList()): String {
    val root = JSONObject()
        .put("version", version)
        .put("image", KANTO_MAP_IMAGE)

    val regionsJson = JSONArray()
    for (region in regions) {
        val element = JSONObject()
        element.put("id", region.id)
        region.catalogId?.let { element.put("catalogId", it) }
        region.name?.let { element.put("name", it) }
        region.type?.let { element.put("type", typeName(it)) }
        element.put("priority", region.effectivePriority(locations))

        val geometry = region.geometry
        val geometryJson = when (geometry) {
            is MapRegionGeometry.Circle -> JSONObject()
                .put("kind", "circle")
                .put("cx", doubles(geometry.cx))
                .put("cy", doubles(geometry.cy))
                .put("r", doubles(geometry.r))

            is MapRegionGeometry.Envelope -> JSONObject()
                .put("kind", "envelope")
                .put("x1", doubles(geometry.x1))
                .put("y1", doubles(geometry.y1))
                .put("x2", doubles(geometry.x2))
                .put("y2", doubles(geometry.y2))

            is MapRegionGeometry.Polygon -> {
                val points = JSONArray()
                for (point in geometry.points) {
                    points.put(
                        JSONArray()
                            .put(doubles(point.u))
                            .put(doubles(point.v))
                    )
                }
                JSONObject().put("kind", "polygon").put("points", points)
            }
        }
        element.put("geometry", geometryJson)
        regionsJson.put(element)
    }
    root.put("regions", regionsJson)
    return root.toString(2)
}

/** Sin conversión implícita ni recurrente: Float → Double explícito. */
private fun doubles(value: Float): Double = value.toDouble()
