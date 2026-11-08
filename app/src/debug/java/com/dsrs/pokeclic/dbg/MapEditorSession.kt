/**
 * Lógica pura del editor de zonas de Kanto: estado y reducción de eventos,
 * **sin Compose ni Android**, para poder testearla en la JVM. Cubre la
 * creación (4B), la edición/eliminación (4C) y la validación de datos para
 * la persistencia/importación (4D); el acceso físico a ficheros vive en
 * [com.dsrs.pokeclic.dbg.MapEditorStore] y la UI en [MapEditorScreen]:
 * aquí solo se valida y se transforma el estado.
 *
 * - El editor carga el **catálogo** (47 ubicaciones) y mantiene en memoria las
 *   zonas confirmadas de la sesión; la persistencia en fichero la hace la
 *   capa de I/O (autoguardado, recuperación, exportar/importar), nunca aquí.
 * - Una zona se dibuja siempre **sobre un objetivo** ([MapEditorTarget]): o
 *   bien una ubicación del catálogo (enlazada por `catalogId`, sin duplicar
 *   nombre ni tipo) o bien una **zona libre** con nombre y tipo propios.
 * - Herramientas: círculo (centro + punto de radio), rectángulo (dos
 *   esquinas) y polígono (vértices por toques, confirmado con botón).
 * - Todas las coordenadas son `(u, v)` normalizadas al PNG de Kanto, ya
 *   convertidas por el llamante con `MapTransform.screenToUv` — aquí no hay
 *   matemática de pantalla.
 * - Cada zona confirmada es validada **antes** de entrar en el mapa: dentro de
 *   la imagen `[0, 1]` en ambos ejes, radio positivo, dimensiones mínimas del
 *   rectángulo y al menos tres vértices. El objetivo del catálogo no puede
 *   repetirse: una ubicación, una zona.
 * - Sin importar Compose: el estado es un data class y cada evento devuelve un
 *   [MapEditorResult] (estado nuevo + posible motivo de rechazo) — la UI nunca
 *   inventa reglas.
 */
package com.dsrs.pokeclic.dbg

import com.dsrs.pokeclic.model.MapRegion
import com.dsrs.pokeclic.model.MapRegionGeometry
import com.dsrs.pokeclic.model.MapRegionType
import com.dsrs.pokeclic.model.UvPoint
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Herramienta activa del editor. [NAVIGATE] conserva los gestos del mapa
 * (zoom a dos dedos, desplazamiento, doble toque y restauración); las tres
 * herramientas de dibujo reemplazan el toque simple por pasos de dibujo.
 * [EDIT] aísla el arrastre de puntos de control del arrastre del mapa (el
 * pellizco vuelve a estar disponible en las demás herramientas).
 */
enum class MapEditorTool {
    NAVIGATE,
    CIRCLE,
    RECTANGLE,
    POLYGON,
    EDIT
}

/**
 * Clase geométrica de la zona en edición: determina el significado de los
 * puntos de control del borrador de edición.
 */
enum class MapEditorEditKind { CIRCLE, ENVELOPE, POLYGON }

/**
 * Confirma el resultado de un evento afortunado, con el nombre visible de la
 * zona afectada ya resuelto contra el catálogo (la lógica calcula etiquetas
 * pero nunca las pinta).
 */
data class MapEditorFeedback(
    /** Qué ocurrió (creada, actualizada o eliminada). */
    val kind: MapEditorFeedbackKind,

    /** Nombre visible de la zona: del catálogo si está enlazada, si no. */
    val zoneLabel: String
)

/** Variantes de [MapEditorFeedback]. */
enum class MapEditorFeedbackKind { ZONE_CREATED, ZONE_UPDATED, ZONE_DELETED, ZONES_IMPORTED }

/**
 * Objetivo de la zona que se va a dibujar. Enlazada al catálogo (sin nombre
 * ni tipo: los resuelve el runtime) o libre (nombre y tipo embebidos).
 */
sealed interface MapEditorTarget {
    data class Catalog(val catalogId: Int) : MapEditorTarget
    data class FreeZone(val name: String, val type: MapRegionType) : MapEditorTarget
}

/**
 * Motivos por los que un evento de dibujo puede ser rechazado. La UI los
 * traduce a mensajes; la lógica nunca muestra texto.
 */
enum class MapEditorError {
    /** Dibujo sin objetivo elegido. */
    NO_TARGET,

    /** La ubicación del catálogo ya tiene una zona (duplicados prohibidos). */
    DUPLICATE_TARGET,

    /** Zona libre sin nombre (el nombre es obligatorio: identifica la zona). */
    FREE_NAME_REQUIRED,

    /** Un punto (o toda la forma) queda fuera de la imagen `[0, 1]`. */
    OUT_OF_IMAGE,

    /** Radio del círculo por debajo del mínimo Útil. */
    SMALL_RADIUS,

    /** Rectángulo demasiado pequeño en algún eje. */
    SMALL_SHAPE,

    /** Polígono confirmado con menos vértices que el mínimo. */
    FEW_VERTICES,

    /** Polígono con demasiados vértices (protección acumulativa). */
    MANY_VERTICES,

    /** Modo Editar tocado en un sitio sin ninguna zona debajo. */
    NOTHING_EDITABLE,

    /** La importación/persistencia trajo datos que no superan la validación. */
    IMPORT_REJECTED
}

/**
 * Resultado inmutable de un evento: el estado siguiente, el error si el
 * evento fue rechazado (`error != null`) y el feedback si creó/actualizó/
 * eliminó una zona con éxito. En caso de rechazo el estado no cambia: ni
 * media zona a medias entra en el mapa.
 */
data class MapEditorResult(
    val state: MapEditorState,
    val error: MapEditorError? = null,
    val feedback: MapEditorFeedback? = null,

    /**
     * Detalle humano del rechazo cuando el motivo no cabe en [error]
     * (p. ej. importación con varias reglas posibles fallidas). La UI lo
     * muestra en vez del mensaje genérico del error; nunca lo inventa.
     */
    val message: String? = null
) {
    val ok: Boolean get() = error == null
}

/**
 * Estado completo de una sesión del editor. Las zonas viven en [regions] **en
 * memoria**: la persistencia física (borrador autoguardado) es tarea del
 * almacén de la UI, que detecta cambios comparando la lista confirmada. Los
 * borradores a medias ([draftPoints]) y las ediciones sin aplicar **nunca**
 * alteran [regions], así que jamás se autoguardan solos.
 */
data class MapEditorState(
    /** Ubicaciones del catálogo (47 con el JSON actual). */
    val catalog: List<com.dsrs.pokeclic.model.MapLocation>,

    /** Zonas confirmadas en la sesión, en orden de creación. */
    val regions: List<MapRegion> = emptyList(),

    /** Herramienta activa. */
    val tool: MapEditorTool = MapEditorTool.NAVIGATE,

    /** Objetivo de la zona en curso; `null` hasta que se elige en el panel. */
    val target: MapEditorTarget? = null,

    /**
     * Puntos ya recogidos del dibujo en curso: centro del círculo, primera
     * esquina del rectángulo o vértices del polígono, según la herramienta.
     * En [MapEditorTool.EDIT] son los **puntos de control** vivos de la zona
     * en edición (la geometría confirmada no cambia hasta «Aplicar cambios»).
     */
    val draftPoints: List<UvPoint> = emptyList(),

    /**
     * Zona en edición ([MapEditorTool.EDIT]): su id en [regions]. `null` en
     * cualquier otra herramienta.
     */
    val editingRegionId: String? = null,

    /** Clase geométrica de la zona en edición, para reconstruir su forma. */
    val editingKind: MapEditorEditKind? = null
) {
    /** ¿Tiene el objetivo una zona ya creada (assets + sesión)? */
    fun catalogBusy(catalogId: Int): Boolean =
        regions.any { it.catalogId == catalogId }
}

// ─────────────────────────── Límites de validación ───────────────────────────

/**
 * Radio mínimo de un círculo en unidades de imagen (1 % del ancho del PNG):
 * por debajo, la zona es imprecisa y no útil para el hit-testing.
 */
const val MapEditorMinRadiusUv = 0.01f

/**
 * Dimensión mínima del rectángulo en cada eje, por el mismo criterio.
 */
const val MapEditorMinDimensionUv = 0.01f

/** Vértices mínimo y máximo del polígono: menos no delimita, más no es UI. */
const val MapEditorMinPolygonVertices = 3
const val MapEditorMaxPolygonVertices = 64

/** Extensión de la imagen en coordenadas normalizadas: nada puede salirse. */
private val UV_IMAGE_RANGE = 0f..1f

// ─────────────────────────────── Reducciones ───────────────────────────────

/**
 * Cambia la herramienta activa. El dibujo en curso se pierde a medias
 * («intencional»: empezar la herramienta nueva desde cero evita zonas
 * absurdas), pero el objetivo no se toca. Salir del modo Editar descarta la
 * edición en curso (equivalente a cancelarla: nunca altera zonas existentes).
 */
fun MapEditorState.withTool(tool: MapEditorTool): MapEditorResult {
    val dropEdit = tool != MapEditorTool.EDIT
    return MapEditorResult(
        copy(
            tool = tool,
            draftPoints = emptyList(),
            editingRegionId = if (dropEdit) null else editingRegionId,
            editingKind = if (dropEdit) null else editingKind
        )
    )
}

/**
 * Cambia el objetivo del dibujo y preselecciona la herramienta lógica para su
 * clase: rutas → polígono; ciudades y especiales → círculo (siempre manual
 * ajustable). Una ubicación del catálogo no puede repetirse, y la zona libre
 * exige nombre.
 */
fun MapEditorState.selectTarget(target: MapEditorTarget): MapEditorResult = when (target) {
    is MapEditorTarget.Catalog ->
        if (catalogBusy(target.catalogId)) {
            MapEditorResult(this, MapEditorError.DUPLICATE_TARGET)
        } else {
            val type = catalog.firstOrNull { it.id == target.catalogId }?.type
                ?: MapRegionType.SPECIAL
            MapEditorResult(
                copy(
                    target = target,
                    tool = defaultToolFor(type),
                    draftPoints = emptyList()
                )
            )
        }

    is MapEditorTarget.FreeZone ->
        if (target.name.isBlank()) {
            MapEditorResult(this, MapEditorError.FREE_NAME_REQUIRED)
        } else {
            MapEditorResult(
                copy(
                    target = target,
                    tool = defaultToolFor(target.type),
                    draftPoints = emptyList()
                )
            )
        }
}

/**
 * Descarta el objetivo y vuelve a navegar: fin del dibujo, sin tocar zonas ya
 * confirmadas (y dejando cualquier edición a medias fuera de estado).
 */
fun MapEditorState.clearTarget(): MapEditorResult =
    MapEditorResult(
        copy(
            target = null,
            tool = MapEditorTool.NAVIGATE,
            draftPoints = emptyList(),
            editingRegionId = null,
            editingKind = null
        )
    )

/**
 * Descarta el dibujo en curso conservando la herramienta y el objetivo: para
 * reintentar el trazo sin volver a abrir el panel.
 */
fun MapEditorState.cancelDraft(): MapEditorResult =
    MapEditorResult(copy(draftPoints = emptyList()))

/**
 * Deshace el último vértice del polígono en curso. Con un solo punto o sin
 * dibujo, el estado queda con el dibujo vacío (la UI lo interpreta como
 * "borrador en blanco"), sin borrar zonas confirmadas.
 */
fun MapEditorState.undoVertex(): MapEditorResult =
    MapEditorResult(copy(draftPoints = draftPoints.dropLast(1)))

/**
 * Toque de dibujo en `(u, v)` de la imagen, ya convertido por la UI con
 * `MapTransform.screenToUv`. El comportamiento depende de la herramienta:
 *
 * - **Círculo / Rectángulo**: dos toques; con el segundo se valida y confirma
 *   la zona directamente (no hace falta botón) y el editor vuelve a Navegar
 *   con el mensaje de confirmación correspondiente.
 * - **Polígono**: cada toque añade un vértice; confirmar es explícito
 *   ([confirmPolygon]) porque el final de una ruta lo decide el usuario.
 * - **Editar**: con ninguna zona en edición, el toque selecciona la zona de
 *   mayor prioridad bajo el dedo y pasa a editarla; si no hay ninguna
 *   debajo, rechaza con [MapEditorError.NOTHING_EDITABLE]. Con una zona ya
 *   en edición, los toques no hacen nada (el trabajo se hace arrastrando
 *   puntos de control y con los botones «Aplicar cambios»/«Cancelar»).
 * - En [MapEditorTool.NAVIGATE] el toque no dibuja (y no se informa error:
 *   navegar no es un fallo).
 */
fun MapEditorState.tap(uv: UvPoint): MapEditorResult {
    if (tool == MapEditorTool.NAVIGATE) return MapEditorResult(this)
    if (tool == MapEditorTool.EDIT) {
        if (editingRegionId != null) return MapEditorResult(this)
        val picked = regions.topmostAt(uv, catalog)
            ?: return MapEditorResult(this, MapEditorError.NOTHING_EDITABLE)
        return selectRegionForEdit(picked.id)
    }
    if (target == null) return MapEditorResult(this, MapEditorError.NO_TARGET)
    if (uv.u !in UV_IMAGE_RANGE || uv.v !in UV_IMAGE_RANGE) {
        return MapEditorResult(this, MapEditorError.OUT_OF_IMAGE)
    }

    return when (tool) {
        MapEditorTool.CIRCLE -> tapCircle(uv)
        MapEditorTool.RECTANGLE -> tapRectangle(uv)
        MapEditorTool.POLYGON -> tapPolygonVertex(uv)
        MapEditorTool.NAVIGATE -> MapEditorResult(this)
        MapEditorTool.EDIT -> MapEditorResult(this) // tratado arriba; por exhaustividad
    }
}

/** Círculo: primer toque centro, segundo toque punto del perímetro → commit. */
private fun MapEditorState.tapCircle(uv: UvPoint): MapEditorResult {
    if (draftPoints.isEmpty()) {
        return MapEditorResult(copy(draftPoints = listOf(uv)))
    }
    val center = draftPoints.single()
    val radius = distanceOf(center, uv)
    if (radius < MapEditorMinRadiusUv) {
        return MapEditorResult(this, MapEditorError.SMALL_RADIUS)
    }
    val geometry = MapRegionGeometry.Circle(cx = center.u, cy = center.v, r = radius)
    return commit(geometry)
}

/** Rectángulo: esquinas opuestas; el orden de toques es indiferente. */
private fun MapEditorState.tapRectangle(uv: UvPoint): MapEditorResult {
    if (draftPoints.isEmpty()) {
        return MapEditorResult(copy(draftPoints = listOf(uv)))
    }
    val corner = draftPoints.single()
    val geometry = MapRegionGeometry.Envelope(
        x1 = corner.u, y1 = corner.v, x2 = uv.u, y2 = uv.v
    )
    val width = abs(uv.u - corner.u)
    val height = abs(uv.v - corner.v)
    if (width < MapEditorMinDimensionUv || height < MapEditorMinDimensionUv) {
        return MapEditorResult(this, MapEditorError.SMALL_SHAPE)
    }
    return commit(geometry)
}

/** Polígono: añade un vértice validado; la confirmación es un evento aparte. */
private fun MapEditorState.tapPolygonVertex(uv: UvPoint): MapEditorResult {
    if (draftPoints.size >= MapEditorMaxPolygonVertices) {
        return MapEditorResult(this, MapEditorError.MANY_VERTICES)
    }
    return MapEditorResult(copy(draftPoints = draftPoints + uv))
}

/**
 * Confirma el polígono en curso: mínimo de vértices, descarte de vértices
 * repetidos consecutivos (ruido diminuto del dedo) y validación completa de
 * la forma antes de crear la zona.
 */
fun MapEditorState.confirmPolygon(): MapEditorResult {
    if (tool != MapEditorTool.POLYGON) return MapEditorResult(this)
    if (target == null) return MapEditorResult(this, MapEditorError.NO_TARGET)

    val vertices = draftPoints
        .filterIndexed { index, point -> index == 0 || point != draftPoints[index - 1] }
    if (vertices.size < MapEditorMinPolygonVertices) {
        return MapEditorResult(this, MapEditorError.FEW_VERTICES)
    }
    return commit(MapRegionGeometry.Polygon(points = vertices))
}

/**
 * Crea la región definitivamente y acumula el estado. Validación previa:
 *
 * 1. Debe haber objetivo y la geometría ha de caber íntegra en la imagen.
 * 2. Objetivo del catálogo: sin duplicados (una ubicación, una zona) y con
 *    identificador `ubic-X` derivado del `catalogId`.
 * 3. Zona libre: nombre y tipo embebidos e id único `libre-N`.
 *
 * Tras confirmar vuelven a Navegar automáticamente con el borrador vacío y el
 * feedback de creación (requisito de la 4C); una repetición inmediata del
 * dibujo con el mismo objetivo del catálogo la rechaza el anti-duplicado.
 */
private fun MapEditorState.commit(geometry: MapRegionGeometry): MapEditorResult {
    val currentTarget = target ?: return MapEditorResult(this, MapEditorError.NO_TARGET)
    if (!geometry.isWithinImage()) return MapEditorResult(this, MapEditorError.OUT_OF_IMAGE)

    val region = when (currentTarget) {
        is MapEditorTarget.Catalog -> {
            if (catalogBusy(currentTarget.catalogId)) {
                return MapEditorResult(this, MapEditorError.DUPLICATE_TARGET)
            }
            // Enlazada: sin nombre, tipo ni prioridad — se resuelven contra el
            // catálogo en runtime, y la prioridad por defecto sale del tipo.
            MapRegion(
                id = "ubic-${currentTarget.catalogId}",
                geometry = geometry,
                catalogId = currentTarget.catalogId
            )
        }

        is MapEditorTarget.FreeZone ->
            // Libre: nombre, tipo y prioridad por defecto del tipo, embebidos.
            MapRegion(
                id = nextFreeRegionId(regions),
                geometry = geometry,
                name = currentTarget.name,
                type = currentTarget.type
            )
    }

    return MapEditorResult(
        copy(
            regions = regions + region,
            draftPoints = emptyList(),
            tool = MapEditorTool.NAVIGATE
        ),
        feedback = MapEditorFeedback(
            kind = MapEditorFeedbackKind.ZONE_CREATED,
            zoneLabel = region.resolvedLabel(catalog)
        )
    )
}

/**
 * Identificador único para una zona libre: secuencia `libre-N` tomando la
 * primera `N` no usada.
 */
fun nextFreeRegionId(existing: List<MapRegion>): String {
    val used = existing.map { it.id }.toSet()
    var index = 1
    while ("libre-$index" in used) index++
    return "libre-$index"
}

// ────────────────────────── Edición de zonas (4C) ──────────────────────────

/**
 * Empieza a editar la zona [regionId]: la geometría confirmada **no cambia**
 * en absoluto; el borrador pasa a ser sus puntos de control y todo arrastre
 * es solo vista previa hasta «Aplicar cambios» ([commitEdit]) o «Cancelar»
 * ([cancelEdit]).
 *
 * Puntos de control por clase:
 * - Círculo: [centro, punto-al-este-del-perímetro] — mover el centro traslada
 *   el círculo entero y mover el punto este cambia el radio.
 * - Rectángulo: [esquina-min, esquina-max] (caja alineada a ejes).
 * - Polígono: cada vértice, en orden.
 */
fun MapEditorState.selectRegionForEdit(regionId: String): MapEditorResult {
    val region = regions.firstOrNull { it.id == regionId }
        ?: return MapEditorResult(this, MapEditorError.NOTHING_EDITABLE)
    val kind = when (region.geometry) {
        is MapRegionGeometry.Circle -> MapEditorEditKind.CIRCLE
        is MapRegionGeometry.Envelope -> MapEditorEditKind.ENVELOPE
        is MapRegionGeometry.Polygon -> MapEditorEditKind.POLYGON
    }
    return MapEditorResult(
        copy(
            tool = MapEditorTool.EDIT,
            editingRegionId = regionId,
            editingKind = kind,
            draftPoints = controlPointsOf(region.geometry)
        )
    )
}

/**
 * Vista previa en vivo del arrastre de un punto de control: mueve el cameo
 * provisional al punto `(u, v)` dado **sin validar** (el arrastre es libre;
 * la validación ocurre al aplicar, para no impedir recorridos que cruzan
 * zonas inválidas antes de llegar a la posición deseada).
 */
fun MapEditorState.editMoveHandle(index: Int, to: UvPoint): MapEditorResult {
    if (editingRegionId == null || index !in draftPoints.indices) {
        return MapEditorResult(this)
    }
    return MapEditorResult(copy(draftPoints = draftPoints.mapIndexed { i, p -> if (i == index) to else p }))
}

/**
 * Valida y aplica la edición. Reconstruye la geometría de los puntos de
 * control con las mismas reglas que la creación (dentro de la imagen, radio
 * y dimensiones mínimas, ≥3 vértices) y sustituye **solo** la geometría de la
 * zona en edición (id, enlace al catálogo y datos libres intactos).
 *
 * Si la validación rechaza, el estado no cambia: la zona confirmada sigue
 * con su forma original y la edición queda abierta para corregir.
 */
fun MapEditorState.commitEdit(): MapEditorResult {
    val regionId = editingRegionId ?: return MapEditorResult(this)
    val kind = editingKind ?: return MapEditorResult(this)
    val index = regions.indexOfFirst { it.id == regionId }
    if (index < 0) return MapEditorResult(this)

    val rebuilt = when (kind) {
        MapEditorEditKind.CIRCLE -> {
            val center = draftPoints.getOrNull(0) ?: return MapEditorResult(this)
            val edge = draftPoints.getOrNull(1) ?: return MapEditorResult(this)
            val radius = distanceOf(center, edge)
            if (radius < MapEditorMinRadiusUv) {
                return MapEditorResult(this, MapEditorError.SMALL_RADIUS)
            }
            MapRegionGeometry.Circle(cx = center.u, cy = center.v, r = radius)
        }

        MapEditorEditKind.ENVELOPE -> {
            val cornerA = draftPoints.getOrNull(0) ?: return MapEditorResult(this)
            val cornerB = draftPoints.getOrNull(1) ?: return MapEditorResult(this)
            val width = abs(cornerA.u - cornerB.u)
            val height = abs(cornerA.v - cornerB.v)
            if (width < MapEditorMinDimensionUv || height < MapEditorMinDimensionUv) {
                return MapEditorResult(this, MapEditorError.SMALL_SHAPE)
            }
            MapRegionGeometry.Envelope(
                x1 = cornerA.u, y1 = cornerA.v, x2 = cornerB.u, y2 = cornerB.v
            )
        }

        MapEditorEditKind.POLYGON -> {
            val vertices = draftPoints
                .filterIndexed { i, p -> i == 0 || p != draftPoints[i - 1] }
            if (vertices.size < MapEditorMinPolygonVertices) {
                return MapEditorResult(this, MapEditorError.FEW_VERTICES)
            }
            if (vertices.size > MapEditorMaxPolygonVertices) {
                return MapEditorResult(this, MapEditorError.MANY_VERTICES)
            }
            MapRegionGeometry.Polygon(points = vertices)
        }
    }
    if (!rebuilt.isWithinImage()) return MapEditorResult(this, MapEditorError.OUT_OF_IMAGE)

    val updated = regions[index].copy(geometry = rebuilt)
    return MapEditorResult(
        copy(
            regions = regions.toMutableList().also { it[index] = updated },
            draftPoints = emptyList(),
            editingRegionId = null,
            editingKind = null,
            tool = MapEditorTool.NAVIGATE
        ),
        feedback = MapEditorFeedback(
            kind = MapEditorFeedbackKind.ZONE_UPDATED,
            zoneLabel = updated.resolvedLabel(catalog)
        )
    )
}

/**
 * Cancela la edición sin aplicar nada: la zona confirmada conserva su
 * geometría original y el editor vuelve a Navegar.
 */
fun MapEditorState.cancelEdit(): MapEditorResult =
    MapEditorResult(
        copy(
            draftPoints = emptyList(),
            editingRegionId = null,
            editingKind = null,
            tool = MapEditorTool.NAVIGATE
        )
    )

/**
 * Elimina la zona [regionId] (tras la confirmación que hace la UI):es la vía
 * para volver a dibujar una ubicación del catálogo — al desaparecer la zona,
 * [MapEditorState.catalogBusy] deja de bloquearla.
 */
fun MapEditorState.removeRegion(regionId: String): MapEditorResult {
    val index = regions.indexOfFirst { it.id == regionId }
    if (index < 0) return MapEditorResult(this)
    val removed = regions[index]
    val editingGone = editingRegionId == regionId
    return MapEditorResult(
        copy(
            regions = regions.filterIndexed { i, _ -> i != index },
            draftPoints = if (editingGone) emptyList() else draftPoints,
            editingRegionId = if (editingGone) null else editingRegionId,
            editingKind = if (editingGone) null else editingKind,
            tool = if (editingGone) MapEditorTool.NAVIGATE else tool
        ),
        feedback = MapEditorFeedback(
            kind = MapEditorFeedbackKind.ZONE_DELETED,
            zoneLabel = removed.resolvedLabel(catalog)
        )
    )
}

/** Puntos de control de una geometría, igual orden que los reconstruye. */
private fun controlPointsOf(geometry: MapRegionGeometry): List<UvPoint> = when (geometry) {
    is MapRegionGeometry.Circle -> listOf(
        UvPoint(geometry.cx, geometry.cy),
        UvPoint(geometry.cx + geometry.r, geometry.cy)
    )

    is MapRegionGeometry.Envelope -> listOf(
        UvPoint(minOf(geometry.x1, geometry.x2), minOf(geometry.y1, geometry.y2)),
        UvPoint(maxOf(geometry.x1, geometry.x2), maxOf(geometry.y1, geometry.y2))
    )

    is MapRegionGeometry.Polygon -> geometry.points
}

/** Nombre visible de la zona: catálogo si hay enlace, si no el embebido. */
private fun MapRegion.resolvedLabel(locations: List<com.dsrs.pokeclic.model.MapLocation>): String =
    resolvedName(locations) ?: id // Último recurso: el id técnico es informativo.

/**
 * Zona de mayor prioridad bajo el punto `(u, v)`: el mismo criterio del
 * mapa normal ([com.dsrs.pokeclic.model.KantoRegionMap.regionAt]) pero sobre
 * la lista viva de la sesión, donde las devoluciones de id/orden ya no son
 * las del fichero.
 */
private fun List<MapRegion>.topmostAt(uv: UvPoint, locations: List<com.dsrs.pokeclic.model.MapLocation>): MapRegion? =
    sortedByDescending { it.effectivePriority(locations) }
        .firstOrNull { it.contains(uv.u, uv.v) }

/**
 * Herramienta preseleccionada al elegir un objetivo, según su clase.
 */
private fun defaultToolFor(type: MapRegionType): MapEditorTool =
    if (type == MapRegionType.ROUTE) MapEditorTool.POLYGON else MapEditorTool.CIRCLE

/**
 * ¿Encaja la geometría **por completo** dentro de la imagen `[0, 1]`?
 *
 * - Círculo: todo el disco (centro ± radio) en ambos ejes.
 * - Rectángulo: sus dos esquinas dentro (la caja es alineada a ejes).
 * - Polígono: todos sus vértices dentro; los tramos entre vértices nunca
 *   sobresalen más que la envolvente convexa de puntos que ya están dentro.
 */
private fun MapRegionGeometry.isWithinImage(): Boolean = when (this) {
    is MapRegionGeometry.Circle ->
        cx - r >= 0f && cx + r <= 1f && cy - r >= 0f && cy + r <= 1f

    is MapRegionGeometry.Envelope ->
        x1 in UV_IMAGE_RANGE && y1 in UV_IMAGE_RANGE &&
            x2 in UV_IMAGE_RANGE && y2 in UV_IMAGE_RANGE

    is MapRegionGeometry.Polygon ->
        points.all { it.u in UV_IMAGE_RANGE && it.v in UV_IMAGE_RANGE }
}

private fun distanceOf(a: UvPoint, b: UvPoint): Float = hypot(b.u - a.u, b.v - a.v)

// ───────────────────────── Persistencia e importación (4D) ─────────────────────────

/**
 * Valida las [regions] que van a **persistirse** (borrador o JSON exportado)
 * o **importarse** (fichero o copia): sin archivos incorrectos viajando.
 *
 * Reglas (más laxas que las del trazo en UI — ahí mandan los mínimos de
 * precisión del editor; aquí mandan la coherencia estructural, para no
 * bloquear la recuperación de datos antiguos o ajustados a mano):
 *
 * - `id` no vacío y único.
 * - Geometría íntegra dentro de la imagen `[0, 1]` (reusa [isWithinImage],
 *   que también descarta NaN/∞ puestos a mano en el JSON).
 * - Círculo con radio positivo; rectángulo con área; polígono con al menos
 *   [MapEditorMinPolygonVertices] vértices.
 * - Zona enlazada: el `catalogId` debe existir en el catálogo actual (la
 *   documentación de los 47 ids es la validación de referencias rotas).
 * - Zona libre: `name` no vacío y `type` presente (el parseo del esquema ya
 *   lo exige; se revalida por robustez al aceptar datos directos).
 *
 * Devuelve `null` si todo es válido; en otro caso el motivo en claro para
 * la capa de UI (que decide cómo presentarlo).
 */
fun validateRegionsForEditor(
    regions: List<MapRegion>,
    catalog: List<com.dsrs.pokeclic.model.MapLocation>
): String? {
    val seenIds = mutableSetOf<String>()
    for (region in regions) {
        if (region.id.isBlank() || !seenIds.add(region.id)) {
            return "id de región vacío o duplicado: \"${region.id}\""
        }
        if (!region.geometry.isWithinImage()) {
            return "la geometría de \"${region.id}\" se sale de la imagen"
        }
        when (val geometry = region.geometry) {
            is MapRegionGeometry.Circle ->
                if (geometry.r <= 0f) return "la zona \"${region.id}\" tiene radio no positivo"

            is MapRegionGeometry.Envelope ->
                if (geometry.x1 == geometry.x2 || geometry.y1 == geometry.y2) {
                    return "el rectángulo \"${region.id}\" no tiene área"
                }

            is MapRegionGeometry.Polygon ->
                if (geometry.points.size < MapEditorMinPolygonVertices) {
                    return "el polígono \"${region.id}\" necesita " +
                        "al menos $MapEditorMinPolygonVertices vértices"
                }
        }
        val link = region.catalogId
        if (link != null) {
            if (catalog.none { it.id == link }) {
                return "\"${region.id}\" enlaza al catalogId $link, que no existe en el catálogo"
            }
        } else if (region.name.isNullOrBlank() || region.type == null) {
            return "la zona libre \"${region.id}\" no aporta nombre y tipo"
        }
    }
    return null
}

/**
 * Importa [incoming] como nuevo mapeo de la sesión (sustitución completa
 * tras la confirmación previa que hace la UI): valida y, si hay rechazo,
 * el estado no cambia en absoluto (el trabajo actual queda intacto).
 *
 * El reemplazo limpia el modo de trazado/edición (nada de borradores a
 * medias referenciando zonas que ya no existen) y emite el feedback de
 * importación con el recuento (etiquetado [MapEditorFeedbackKind.ZONES_IMPORTED]).
 */
fun MapEditorState.importRegions(incoming: List<MapRegion>): MapEditorResult {
    val reason = validateRegionsForEditor(incoming, catalog)
    if (reason != null) {
        return MapEditorResult(this, MapEditorError.IMPORT_REJECTED, message = reason)
    }
    return MapEditorResult(
        copy(
            regions = incoming,
            tool = MapEditorTool.NAVIGATE,
            target = null,
            draftPoints = emptyList(),
            editingRegionId = null,
            editingKind = null
        ),
        feedback = MapEditorFeedback(
            kind = MapEditorFeedbackKind.ZONES_IMPORTED,
            zoneLabel = incoming.size.toString()
        )
    )
}
