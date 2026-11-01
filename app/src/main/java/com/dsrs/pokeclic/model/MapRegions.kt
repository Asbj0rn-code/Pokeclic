/**
 * Modelo de datos del sistema de mapeo informativo de Kanto.
 *
 * Es **puro Kotlin** (sin Compose ni API de Android): aquí viven las zonas
 * interactivas del mapa — ciudad, ruta o localización especial — y el
 * hit-testing por coordenadas normalizadas `(u, v)` respecto a `mapa_kanto.png`
 * (el punto `(0, 0)` es la esquina superior izquierda del PNG y el `(1, 1)` la
 * inferior derecha, usando los **píxeles del recurso**, no los de pantalla).
 *
 * Hay dos ficheros relacionados, separando a propósito **nombres** de
 * **geografía**:
 *
 * - El **catálogo** (`assets/kanto_locations.json`, copia derivada de
 *   `ubicaciones.json`) es la única fuente de identificadores, nombres y tipos
 *   de las 46 ubicaciones de Kanto. Sus ids numéricos son **estables**: son la
 *   referencia que el resto del sistema usa para enlazar sin duplicar textos.
 * - El **mapeo** (`assets/kanto_regions.json`) solo guarda geografía: qué
 *   región (círculo, rectángulo o polígono) ocupa cada ubicación ya dibujada.
 *   Una región enlazada al catálogo referencia por `catalogId` y no repite
 *   nombre ni tipo; solo las ubicaciones **nuevas** que no están en el catálogo
 *   incrustan su propio `name`/`type`.
 *
 * De este modo renombrar o reclasificar en el catálogo actualiza el juego sin
 * tocar las geometrías, y el editor (fase de desarrollo) puede presentar el
 * catálogo completo marcando las ubicaciones ya mapeadas.
 */
package com.dsrs.pokeclic.model

/**
 * Clase de la ubicación. Su [defaultPriority] resuelve los solapes: pulsar en
 * una zona compartida por varias regiones selecciona la de prioridad mayor
 * (ciudad sobre especial, especial sobre ruta).
 */
enum class MapRegionType {
    CITY,
    ROUTE,
    SPECIAL;

    /** Prioridad por defecto: mayor gana en caso de solape. */
    val defaultPriority: Int
        get() = when (this) {
            CITY -> 3
            SPECIAL -> 2
            ROUTE -> 1
        }
}

/**
 * Entrada del catálogo de ubicaciones (la parte **nominal** del sistema).
 *
 * @param id identificador numérico **estable** del catálogo; es el enlace que
 *   usan las zonas mapeadas ([MapRegion.catalogId]).
 * @param nombre nombre visible de la ubicación, en el idioma de la app.
 * @param tipo clase de la ubicación, mapeada desde el catálogo.
 */
data class MapLocation(
    val id: Int,
    val name: String,
    val type: MapRegionType
)

/** Punto de la imagen original, en coordenadas normalizadas `(u, v)`. */
data class UvPoint(val u: Float, val v: Float)

/**
 * Geometría interactiva de una zona, sobre la imagen normalizada.
 */
sealed interface MapRegionGeometry {

    /**
     * Círculo de centro `(cx, cy)` y radio `r`. Usado para ciudades y para
     * localizaciones especiales de área compacta.
     */
    data class Circle(val cx: Float, val cy: Float, val r: Float) : MapRegionGeometry

    /**
     * Rectángulo alineado a ejes con esquinas `(x1, y1)` y `(x2, y2)` — si
     * llega desordenado del editor, se normaliza al vuelo.
     */
    data class Envelope(val x1: Float, val y1: Float, val x2: Float, val y2: Float) :
        MapRegionGeometry

    /**
     * Polígono por vértices en orden; el cierre es implícito (el último vértice
     * enlaza con el primero). Usado para rutas y para formas no compactas.
     */
    data class Polygon(val points: List<UvPoint>) : MapRegionGeometry
}

/**
 * Zona interactiva ya dibujada sobre el mapa: la parte **geográfica**.
 *
 * Una región es o bien **enlazada al catálogo** (`catalogId != null`, sin
 * `name`/`type` propios: se resuelven en runtime contra el catálogo) o bien
 * **libre** (`catalogId == null` con `name` y `type` embebidos) para ubicaciones
 * nuevas fuera del catálogo inicial.
 *
 * @param id identificador único de la zona, estable en el mapeo (independiente
 *   del catálogo, el editor lo valida).
 * @param geometry contorno normalizado de la zona.
 * @param priority prioridad en solapes; por defecto se toma
 *   [MapRegionType.defaultPriority] del tipo resuelto. Debe venir explícita
 *   cuando el tipo depende del catálogo (que en ese punto del JSON no se lee).
 * @param catalogId enlace al catálogo, o `null` para regiones libres.
 * @param name nombre embebido, solo regiones libres.
 * @param type clase embebida, solo regiones libres.
 */
data class MapRegion(
    val id: String,
    val geometry: MapRegionGeometry,
    val priority: Int? = null,
    val catalogId: Int? = null,
    val name: String? = null,
    val type: MapRegionType? = null
) {

    /**
     * ¿Contiene el punto `(u, v)` la geometría? Inclusivo en todos los bordes
     * hasta donde lo permite la aritmética de números de coma flotante.
     */
    fun contains(u: Float, v: Float): Boolean = when (val g = geometry) {
        is MapRegionGeometry.Circle ->
            if (g.r <= 0f) false
            else (u - g.cx) * (u - g.cx) + (v - g.cy) * (v - g.cy) <= g.r * g.r

        is MapRegionGeometry.Envelope ->
            u >= minOf(g.x1, g.x2) && u <= maxOf(g.x1, g.x2) &&
                v >= minOf(g.y1, g.y2) && v <= maxOf(g.y1, g.y2)

        is MapRegionGeometry.Polygon -> pointInRing(u, v, g.points)
    }

    /** Nombre visible de la zona: catálogo si hay enlace, si no el embebido. */
    fun resolvedName(locations: List<MapLocation>): String? =
        catalogId?.let { cid -> locations.firstOrNull { it.id == cid }?.name } ?: name

    /** Clase de la zona: catálogo si hay enlace, si no la embebida. */
    fun resolvedType(locations: List<MapLocation>): MapRegionType? =
        catalogId?.let { cid -> locations.firstOrNull { it.id == cid }?.type } ?: type

    /**
     * Prioridad efectiva. Se calcula aquí y no en el parseo para que el esquema
     * del JSON no dependa de leer los dos ficheros a la vez: el valor de
     * [priority] es explícito si el JSON lo trae, y si no se deriva del tipo
     * resuelto.
     */
    fun effectivePriority(locations: List<MapLocation>): Int =
        priority ?: resolvedType(locations)?.defaultPriority ?: MapRegionType.ROUTE.defaultPriority
}

/**
 * Mapeo completo de zonas interactivas de Kanto, la parte leída de
 * `assets/kanto_regions.json`.
 */
data class KantoRegionMap(
    val version: Int,
    val regions: List<MapRegion> = emptyList()
)

/**
 * Excepción de datos: esquema JSON inválido, catálogo corrupto o referencias
 * rotas ([KantoRegionMap.validateReferences]). Quien la capture la convierte en
 * estado vacío o mensajeria del editor, según el contexto.
 */
class MapRegionsDataException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

/**
 * ¿Qué zona está bajo el punto `(u, v)` de la imagen? Devuelve la de
 * [MapRegion.effectivePriority] máxima; en empate gana la que antes aparece en
 * la lista (orden estable del JSON), de modo que el resultado depende solo del
 * fichero y no de ningún criterio mutable.
 */
fun KantoRegionMap.regionAt(
    u: Float,
    v: Float,
    locations: List<MapLocation> = emptyList()
): MapRegion? =
    regions.sortedByDescending { it.effectivePriority(locations) }
        .asIterable()
        .firstOrNull { it.contains(u, v) }

/**
 * Referencias cruzadas: comprueba que [locations] cubre los enlaces de este
 * mapeo. Lanza [MapRegionsDataException] si un `catalogId` no existe en el
 * catálogo — detecta catálogos y mapeos desincronizados en cuanto se cargan,
 * sin esperar a un fallo en pantalla.
 */
fun KantoRegionMap.validateReferences(locations: List<MapLocation>) {
    val catalog = locations.map { it.id }.toSet()
    val broken = regions.mapNotNull { r -> r.catalogId }.filter { it !in catalog }
    if (broken.isNotEmpty()) {
        throw MapRegionsDataException(
            "El mapeo referencia catalogId inexistentes en el catálogo: $broken"
        )
    }
}

/**
 * ¿Está `(u, v)` dentro del anillo [points] (cierre implícito)? Ray casting
 * **inclusivo-odd**: un rayo horizontal desde el punto cruza la frontera una
 * vez por cada tramo que lo separa de "dentro", así que el total impar indica
 * interior, también en polígonos cóncavos. Los toques exactos de borde son lo
 * mejor posible con coma flotante.
 */
private fun pointInRing(u: Float, v: Float, points: List<UvPoint>): Boolean {
    if (points.size < 3) return false

    var inside = false
    var previousIndex = points.lastIndex
    for (index in points.indices) {
        val current = points[index]
        val previous = points[previousIndex]

        // Tramo que cruza la horizontal del punto (uno de los extremos por
        // encima y el otro por debajo).
        if ((current.v > v) != (previous.v > v)) {
            val ratio = (v - current.v) / (previous.v - current.v)
            val crossing = current.u + ratio * (previous.u - current.u)
            if (crossing > u) inside = !inside
        }

        previousIndex = index
    }
    return inside
}
