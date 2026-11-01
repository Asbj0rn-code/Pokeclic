/**
 * Pruebas de la lógica pura de la subetapa 4B del editor: creación de
 * círculos, rectángulos y polígonos, validación de geometrías, objetivos del
 * catálogo (sin duplicados) y zonas libres.
 */
package com.dsrs.pokeclic.dbg

import com.dsrs.pokeclic.model.MapLocation
import com.dsrs.pokeclic.model.MapRegion
import com.dsrs.pokeclic.model.MapRegionGeometry
import com.dsrs.pokeclic.model.MapRegionType
import com.dsrs.pokeclic.model.UvPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val CATALOG = listOf(
    MapLocation(id = 1, name = "Pueblo Paleta", type = MapRegionType.CITY),
    MapLocation(id = 2, name = "Ruta 1", type = MapRegionType.ROUTE),
    MapLocation(id = 3, name = "Laboratorio Oak", type = MapRegionType.SPECIAL)
)

private val EMPTY = MapEditorState(catalog = CATALOG)

private fun uv(u: Float, v: Float) = UvPoint(u, v)

class MapEditorSessionTest {

    // ─────────────────────────── Objetivos ───────────────────────────

    @Test
    fun selectTarget_routePreselectsPolygonTool() {
        val result = EMPTY.selectTarget(MapEditorTarget.Catalog(catalogId = 2))
        assertEquals(MapEditorTool.POLYGON, result.state.tool)
        assertEquals(2, (result.state.target as MapEditorTarget.Catalog).catalogId)
    }

    @Test
    fun selectTarget_cityPreselectsCircleTool() {
        val result = EMPTY.selectTarget(MapEditorTarget.Catalog(catalogId = 1))
        assertEquals(MapEditorTool.CIRCLE, result.state.tool)
    }

    @Test
    fun selectTarget_duplicatedCatalogLocationIsRejected() {
        val first = EMPTY
            .selectTarget(MapEditorTarget.Catalog(1)).state
            .tap(uv(0.5f, 0.5f)).state
            .tap(uv(0.6f, 0.6f)).state
        assertEquals(1, first.regions.size)

        val second = first.selectTarget(MapEditorTarget.Catalog(1))
        assertEquals(MapEditorError.DUPLICATE_TARGET, second.error)
    }

    @Test
    fun selectTarget_freeZoneWithBlankNameIsRejected() {
        val result = EMPTY.selectTarget(
            MapEditorTarget.FreeZone(name = "   ", type = MapRegionType.SPECIAL)
        )
        assertEquals(MapEditorError.FREE_NAME_REQUIRED, result.error)
        // El estado no cambia.
        assertEquals(null, result.state.target)
    }

    @Test
    fun tap_withoutTarget_isRejected() {
        val result = EMPTY
            .withTool(MapEditorTool.CIRCLE).state
            .tap(uv(0.5f, 0.5f))
        assertEquals(MapEditorError.NO_TARGET, result.error)
    }

    @Test
    fun tap_inNavigateTool_doesNothing() {
        val result = EMPTY.tap(uv(0.5f, 0.5f))
        assertTrue(result.ok)
        assertTrue(result.state.draftPoints.isEmpty())
    }

    // ─────────────────────────── Círculos ───────────────────────────

    @Test
    fun circle_twoTaps_createLinkedRegionResolvedFromCatalog() {
        val state = EMPTY
            .selectTarget(MapEditorTarget.Catalog(1)).state
            .tap(uv(0.3f, 0.4f)).state
        assertEquals(1, state.draftPoints.size)

        val result = state.tap(uv(0.4f, 0.4f))
        assertTrue(result.ok)
        assertEquals(1, result.state.regions.size)

        val region = result.state.regions.single()
        assertEquals("ubic-1", region.id)
        assertEquals(1, region.catalogId)
        assertEquals("Pueblo Paleta", region.resolvedName(CATALOG))
        assertEquals(MapRegionType.CITY, region.resolvedType(CATALOG))
        assertEquals(3, region.effectivePriority(CATALOG))

        val geometry = region.geometry as MapRegionGeometry.Circle
        assertEquals(0.3f, geometry.cx, 1e-6f)
        assertEquals(0.4f, geometry.cy, 1e-6f)
        assertEquals(0.1f, geometry.r, 1e-6f)
        assertEquals(0, result.state.draftPoints.size)
    }

    @Test
    fun circle_tooSmallRadius_isRejected() {
        val state = EMPTY
            .selectTarget(MapEditorTarget.Catalog(1)).state
            .tap(uv(0.5f, 0.5f)).state
        val result = state.tap(uv(0.505f, 0.5f))
        assertEquals(MapEditorError.SMALL_RADIUS, result.error)
        assertTrue(result.state.regions.isEmpty())
    }

    @Test
    fun circle_protrudingOutsideImage_isRejected() {
        // El centro está dentro, pero el radio alcanza u = 1.01.
        val state = EMPTY
            .selectTarget(MapEditorTarget.Catalog(3)).state
            .tap(uv(0.99f, 0.5f)).state
            .tap(uv(0.4f, 0.5f))
        assertEquals(MapEditorError.OUT_OF_IMAGE, state.error)
    }

    @Test
    fun circle_centerTouchOutsideImage_isRejected() {
        val state = EMPTY
            .selectTarget(MapEditorTarget.Catalog(1)).state
            .tap(uv(1.2f, 0.5f))
        assertEquals(MapEditorError.OUT_OF_IMAGE, state.error)
        assertTrue(state.state.draftPoints.isEmpty())
    }

    @Test
    fun circle_afterSecondTap_freeZoneHasUniqueSequentialId() {
        val free = MapEditorTarget.FreeZone("Zona nueva", MapRegionType.SPECIAL)
        val state = EMPTY
            .selectTarget(free).state
            .tap(uv(0.2f, 0.2f)).state
            .tap(uv(0.3f, 0.2f)).state
        assertEquals("libre-1", state.regions.single().id)
        assertEquals("Zona nueva", state.regions.single().name)
        assertEquals(MapRegionType.SPECIAL, state.regions.single().type)
        assertEquals(2, state.regions.single().effectivePriority(CATALOG))
    }

    // ─────────────────────────── Rectángulos ───────────────────────────

    @Test
    fun rectangle_twoTaps_createsEnvelopeNormalizingCorners() {
        val free = MapEditorTarget.FreeZone("Sala especial", MapRegionType.SPECIAL)
        val result = EMPTY
            .selectTarget(free).state
            .withTool(MapEditorTool.RECTANGLE).state
            .tap(uv(0.8f, 0.6f)).state   // esquina inferior derecha primero
            .tap(uv(0.6f, 0.4f))
        assertTrue(result.ok)
        val geometry = result.state.regions.single().geometry as MapRegionGeometry.Envelope
        assertEquals(0.6f, minOf(geometry.x1, geometry.x2), 1e-6f)
        assertEquals(0.8f, maxOf(geometry.x1, geometry.x2), 1e-6f)
    }

    @Test
    fun rectangle_tooSmallInOneAxis_isRejected() {
        val free = MapEditorTarget.FreeZone("Fina", MapRegionType.SPECIAL)
        val result = EMPTY
            .selectTarget(free).state
            .withTool(MapEditorTool.RECTANGLE).state
            .tap(uv(0.5f, 0.5f)).state
            // 0.004 de alto: por debajo del mínimo.
            .tap(uv(0.7f, 0.504f))
        assertEquals(MapEditorError.SMALL_SHAPE, result.error)
    }

    @Test
    fun rectangle_bothCornersInside_isAccepted_equalToBoundary() {
        val free = MapEditorTarget.FreeZone("Bordes", MapRegionType.SPECIAL)
        val result = EMPTY
            .selectTarget(free).state
            .withTool(MapEditorTool.RECTANGLE).state
            .tap(uv(0f, 0f)).state
            .tap(uv(0.2f, 0.2f))
        assertTrue(result.ok)
    }

    // ─────────────────────────── Polígonos ───────────────────────────

    private fun polygonFlow(vararg vertices: UvPoint): MapEditorResult {
        var state = EMPTY.selectTarget(MapEditorTarget.Catalog(2)).state
        for (point in vertices) {
            state = state.tap(point).state
        }
        return state.confirmPolygon()
    }

    @Test
    fun polygon_confirmWithThreeVertices_createsLinkedRoute() {
        val result = polygonFlow(uv(0.1f, 0.1f), uv(0.3f, 0.1f), uv(0.2f, 0.4f))
        assertTrue(result.ok)
        val region = result.state.regions.single()
        assertEquals("ubic-2", region.id)
        assertEquals("Ruta 1", region.resolvedName(CATALOG))
        assertEquals(1, region.effectivePriority(CATALOG))
        val geometry = region.geometry as MapRegionGeometry.Polygon
        assertEquals(3, geometry.points.size)
    }

    @Test
    fun polygon_confirmWithTwoVertices_isRejected() {
        val result = polygonFlow(uv(0.1f, 0.1f), uv(0.3f, 0.2f))
        assertEquals(MapEditorError.FEW_VERTICES, result.error)
        assertTrue(result.state.regions.isEmpty())
    }

    @Test
    fun polygon_consecutiveRepeatedVertex_isDeduplicatedOnConfirm() {
        // Toque repetido: el dedo "vibra" y marca el mismo punto dos veces;
        // la duplicación consecutiva no debe contar como vértice real.
        val result = polygonFlow(uv(0.1f, 0.1f), uv(0.2f, 0.2f), uv(0.2f, 0.2f), uv(0.3f, 0.1f))
        assertTrue(result.ok)
        assertEquals(3, (result.state.regions.single().geometry as MapRegionGeometry.Polygon).points.size)
    }

    @Test
    fun polygon_vertexOutsideImage_isRejectedOnTap() {
        var state = EMPTY.selectTarget(MapEditorTarget.Catalog(2)).state
        state = state.tap(uv(0.5f, 0.5f)).state
        val rejected = state.tap(uv(0.7f, -0.1f))
        assertEquals(MapEditorError.OUT_OF_IMAGE, rejected.error)
        assertEquals(1, rejected.state.draftPoints.size)
    }

    @Test
    fun polygon_undoVertex_dropsTheLastOne() {
        var state = EMPTY.selectTarget(MapEditorTarget.Catalog(2)).state
        state = state.tap(uv(0.1f, 0.1f)).state
        state = state.tap(uv(0.2f, 0.2f)).state
        state = state.undoVertex().state
        assertEquals(1, state.draftPoints.size)
        state = state.undoVertex().state
        assertEquals(0, state.draftPoints.size)
        // Deshacer con el borrador vacío no rompe nada.
        state = state.undoVertex().state
        assertEquals(0, state.draftPoints.size)
        assertTrue(state.regions.isEmpty())
    }

    @Test
    fun polygon_tooManyVertices_isBlocked() {
        var state = EMPTY.selectTarget(MapEditorTarget.Catalog(2)).state
        repeat(MapEditorMaxPolygonVertices) { index ->
            state = state.tap(uv(index / MapEditorMaxPolygonVertices.toFloat(), 0.5f)).state
        }
        // El toque 65 (punto distinto y dentro de la imagen) debe rechazarse.
        val rejected = state.tap(uv(0.005f, 0.4f))
        assertEquals(MapEditorError.MANY_VERTICES, rejected.error)
    }

    @Test
    fun polygon_cancelDraft_clearsPointsWithoutTouchingRegions() {
        var state = EMPTY.selectTarget(MapEditorTarget.Catalog(2)).state
        state = state.tap(uv(0.1f, 0.1f)).state
        state = state.tap(uv(0.2f, 0.2f)).state
        val cancelled = state.cancelDraft()
        assertTrue(cancelled.ok)
        assertEquals(0, cancelled.state.draftPoints.size)
        // El objetivo sigue vivo: se puede redibujar de inmediato.
        assertEquals(2, (cancelled.state.target as MapEditorTarget.Catalog).catalogId)
    }

    // ─────────────────── Regresión del fallo de dos toques ───────────────────

    /**
     * Regresión del bug reportado en la 4B: después del centro, el segundo
     * toque debe confirmar la zona. La causa real era la captura **obsoleta**
     * del estado en el detector de toques de la UI (el segundo toque se
     * aplicaba al snapshot con el borrador vacío). Este test congela el
     * protocolo exacto que la UI mantiene contra el estado más reciente:
     * cada evento se entrega al resultado del anterior, nunca al inicial.
     */
    @Test
    fun regression_circleTwoTapSequence_eachEventSeesTheLatestState() {
        val initial = EMPTY.selectTarget(MapEditorTarget.Catalog(1)).state

        // Toque 1: centro del borrador, sin error y sin zona.
        val afterCenter = initial.tap(uv(0.25f, 0.55f))
        assertTrue(afterCenter.ok)
        assertEquals(1, afterCenter.state.draftPoints.size)
        assertTrue(afterCenter.state.regions.isEmpty())

        // Toque 2 aplicado al ESTADO DEVUELTO por el toque 1 (no al inicial).
        val afterEdge = afterCenter.state.tap(uv(0.35f, 0.55f))
        assertTrue(afterEdge.ok)
        assertEquals(1, afterEdge.state.regions.size)
        assertEquals(0, afterEdge.state.draftPoints.size)
        val geometry = afterEdge.state.regions.single().geometry as MapRegionGeometry.Circle
        assertEquals(0.1f, geometry.r, 1e-6f)
    }

    /**
     * Regresión del mismo patrón para el polígono: tres toques seguidos cada
     * uno sobre el resultado del anterior, y la confirmación al estado final.
     */
    @Test
    fun regression_polygonVertexSequence_eachEventSeesTheLatestState() {
        var state = EMPTY.selectTarget(MapEditorTarget.Catalog(2)).state
        for (point in listOf(uv(0.1f, 0.2f), uv(0.3f, 0.2f), uv(0.2f, 0.5f))) {
            val result = state.tap(point)
            assertTrue(result.ok)
            state = result.state
        }
        assertEquals(3, state.draftPoints.size)
        val confirmed = state.confirmPolygon()
        assertTrue(confirmed.ok)
        assertEquals(1, confirmed.state.regions.size)
    }

    /**
     * Los gestos de navegación no se rompen: en Navegar los toques no dibujan
     * y el borrador a medias no cruza a Navegar (con el objetivo reservado,
     * que solo se libera en el panel); al volver a dibujar, el borrador
     * empieza limpio y una secuencia completa de dos toques confirma.
     */
    @Test
    fun regression_navigateTapsDoNotDraw_andDrawingResumesClean() {
        var state = EMPTY
            .selectTarget(MapEditorTarget.Catalog(1)).state
            .tap(uv(0.3f, 0.5f)).state

        // Vuelta a navegar con el borrador a medias: el borrador no cruza de
        // herramienta; el objetivo sigue reservado para cuando vuelva a
        // dibujar (ese es el diseño de withTool).
        state = state.withTool(MapEditorTool.NAVIGATE).state
        assertTrue(state.target is MapEditorTarget.Catalog)
        assertEquals(0, state.draftPoints.size)
        val navTap = state.tap(uv(0.5f, 0.5f))
        assertTrue(navTap.ok)
        assertEquals(0, navTap.state.draftPoints.size)
        assertTrue(navTap.state.regions.isEmpty())

        // Regreso al dibujo con el objetivo intacto: secuencia completa OK.
        val resumed = state
            .withTool(MapEditorTool.CIRCLE).state
            .tap(uv(0.4f, 0.3f)).state
            .tap(uv(0.6f, 0.3f))
        assertTrue(resumed.ok)
        assertEquals(0, resumed.state.draftPoints.size)
        assertEquals(1, resumed.state.regions.size)
    }

    // ─────────────────────────── Sesión completa ───────────────────────────

    /** Sesión con una ciudad circular confirmada (Paleta, centrada en .25/.5). */
    private fun circleSession(): MapEditorState = EMPTY
        .selectTarget(MapEditorTarget.Catalog(1)).state
        .tap(uv(0.25f, 0.5f)).state
        .tap(uv(0.35f, 0.5f)).state

    /** Sesión con la ruta poligonal confirmada (3 vértices). */
    private fun polygonSession(): MapEditorState =
        polygonFlow(uv(0.1f, 0.2f), uv(0.3f, 0.2f), uv(0.2f, 0.5f)).state

    @Test
    fun fullSession_mixedZones_liveOnlyInMemory() {
        var state = EMPTY
            .selectTarget(MapEditorTarget.Catalog(1)).state
            // Círculo con radio y_bounds en la imagen: centro lejos del borde.
            .tap(uv(0.3f, 0.3f)).state
            .tap(uv(0.45f, 0.3f)).state
        assertTrue(state.regions.size == 1)
        state = state.clearTarget().state
        state = state.selectTarget(MapEditorTarget.FreeZone("Club", MapRegionType.SPECIAL)).state
        state = state.tap(uv(0.7f, 0.7f)).state
        state = state.tap(uv(0.9f, 0.9f)).state
        assertEquals(2, state.regions.size)
        assertEquals(listOf("ubic-1", "libre-1"), state.regions.map { it.id })
        // El siguiente id libre salta los ya usados, como pedía el anti-choque.
        assertEquals("libre-2", nextFreeRegionId(state.regions))
    }

    // ──────────────────── Edición y eliminación (4C) ────────────────────

    @Test
    fun editSelect_entersEditModeWithControlPoints_withoutTouchingGeometries() {
        val result = circleSession().selectRegionForEdit("ubic-1")
        assertTrue(result.ok)
        val state = result.state
        assertEquals(MapEditorTool.EDIT, state.tool)
        assertEquals("ubic-1", state.editingRegionId)
        // centro y punto-este del perímetro, en ese orden
        assertEquals(listOf(uv(0.25f, 0.5f), uv(0.35f, 0.5f)), state.draftPoints)
        // la zona confirmada conserva su geometría original
        val region = state.regions.single()
        val original = region.geometry as MapRegionGeometry.Circle
        assertEquals(0.1f, original.r, 1e-6f)
    }

    @Test
    fun editSelect_unknownRegion_isRejected() {
        val result = circleSession().selectRegionForEdit("no-existe")
        assertEquals(MapEditorError.NOTHING_EDITABLE, result.error)
    }

    @Test
    fun editMoveHandle_updatesPreviewOnly_andIgnoresBadIndex() {
        var state = circleSession().selectRegionForEdit("ubic-1").state

        // Arrastre en vivo: el borrador cambia, la geometría confirmada no.
        state = state.editMoveHandle(0, uv(0.25f, 0.4f)).state
        assertEquals(uv(0.25f, 0.4f), state.draftPoints[0])
        assertEquals(0.1f, (state.regions.single().geometry as MapRegionGeometry.Circle).r, 1e-6f)

        // Índice fuera de rango: sin efecto (ni error: no es acción del usuario).
        val untouched = state.editMoveHandle(7, uv(0.9f, 0.9f)).state
        assertEquals(state.draftPoints, untouched.draftPoints)
    }

    @Test
    fun editCommit_validGeometryReplacesOnlyTheGeometry_andNavigatesBack() {
        var state = circleSession().selectRegionForEdit("ubic-1").state
        // Mover el punto-este alarga el radio: .25→.40 = r 0.15
        // (y el círculo sigue cabiendo entero en la imagen: 0.10…0.40).
        state = state.editMoveHandle(1, uv(0.4f, 0.5f)).state

        val result = state.commitEdit()
        assertTrue(result.ok)
        val region = result.state.regions.single()
        assertEquals("ubic-1", region.id)
        assertEquals(0.15f, (region.geometry as MapRegionGeometry.Circle).r, 1e-6f)
        assertEquals(0, result.state.draftPoints.size)
        assertEquals(null, result.state.editingRegionId)
        assertEquals(MapEditorTool.NAVIGATE, result.state.tool)
        assertEquals(MapEditorFeedbackKind.ZONE_UPDATED, result.feedback?.kind)
        assertEquals("Pueblo Paleta", result.feedback?.zoneLabel)
    }

    @Test
    fun editCommit_invalidEdit_keepsTheOriginalAndStaysInEditMode() {
        var state = circleSession().selectRegionForEdit("ubic-1").state
        // Centro arrastrado al borde: el círculo reconstruido no cabe.
        state = state.editMoveHandle(0, uv(0.9f, 0.5f)).state

        val result = state.commitEdit()
        assertEquals(MapEditorError.OUT_OF_IMAGE, result.error)
        // La zona sigue exactamente como estaba y la edición queda abierta.
        assertEquals(0.1f, (result.state.regions.single().geometry as MapRegionGeometry.Circle).r, 1e-6f)
        assertEquals("ubic-1", result.state.editingRegionId)
        assertEquals(MapEditorTool.EDIT, result.state.tool)
    }

    @Test
    fun editCancel_restoresTheOriginalGeometry() {
        var state = circleSession().selectRegionForEdit("ubic-1").state
        state = state.editMoveHandle(0, uv(0.6f, 0.6f)).state
        val cancelled = state.cancelEdit()
        assertTrue(cancelled.ok)
        assertEquals(0.1f, (cancelled.state.regions.single().geometry as MapRegionGeometry.Circle).r, 1e-6f)
        assertEquals(0, cancelled.state.draftPoints.size)
        assertEquals(null, cancelled.state.editingRegionId)
        assertEquals(MapEditorTool.NAVIGATE, cancelled.state.tool)
    }

    @Test
    fun editEnvelope_movesMaxCorner_andNormalizes() {
        val target = MapEditorTarget.FreeZone("Sala", MapRegionType.SPECIAL)
        var state = EMPTY.selectTarget(target).state
            .withTool(MapEditorTool.RECTANGLE).state
            .tap(uv(0.2f, 0.2f)).state
            .tap(uv(0.4f, 0.4f)).state
        val id = state.regions.single().id

        state = state.selectRegionForEdit(id).state
        state = state.editMoveHandle(1, uv(0.6f, 0.5f)).state
        val result = state.commitEdit()
        assertTrue(result.ok)
        val envelope = result.state.regions.single().geometry as MapRegionGeometry.Envelope
        assertEquals(0.2f, minOf(envelope.x1, envelope.x2), 1e-6f)
        assertEquals(0.6f, maxOf(envelope.x1, envelope.x2), 1e-6f)
        assertEquals(0.5f, maxOf(envelope.y1, envelope.y2), 1e-6f)
    }

    @Test
    fun editPolygon_movesOneVertexAndKeepsTheOthers() {
        var state = polygonSession().selectRegionForEdit("ubic-2").state
        state = state.editMoveHandle(1, uv(0.35f, 0.25f)).state
        val result = state.commitEdit()
        assertTrue(result.ok)
        val points = (result.state.regions.single().geometry as MapRegionGeometry.Polygon).points
        assertEquals(uv(0.1f, 0.2f), points[0])
        assertEquals(uv(0.35f, 0.25f), points[1])
        assertEquals(uv(0.2f, 0.5f), points[2])
    }

    @Test
    fun editTap_picksTheTopmostZone_andRejectsEmptyTaps() {
        // Ciudad (prioridad 3) y libre especial (prioridad 2) solapadas.
        var state = EMPTY
            .selectTarget(MapEditorTarget.Catalog(1)).state
            .tap(uv(0.45f, 0.45f)).state
            .tap(uv(0.55f, 0.55f)).state
        state = state.clearTarget().state
        state = state.selectTarget(MapEditorTarget.FreeZone("Refugio", MapRegionType.SPECIAL)).state
        state = state.tap(uv(0.5f, 0.5f)).state
        state = state.tap(uv(0.6f, 0.5f)).state

        // Modo Editar: toque dentro del solape → gana la ciudad (prioridad).
        val editing = state.withTool(MapEditorTool.EDIT).state.tap(uv(0.5f, 0.47f))
        assertTrue(editing.ok)
        assertEquals("ubic-1", editing.state.editingRegionId)

        // Sitio sin zonas → rechazo explícito.
        val missed = state.withTool(MapEditorTool.EDIT).state.tap(uv(0.05f, 0.9f))
        assertEquals(MapEditorError.NOTHING_EDITABLE, missed.error)
    }

    @Test
    fun editRemove_deletesOnlyThatZone_andLetsTheRedraw() {
        val state = polygonSession() // ubic-2 confirmado

        val result = state.removeRegion("ubic-2")
        assertTrue(result.ok)
        assertEquals(0, result.state.regions.size)
        assertEquals(MapEditorFeedbackKind.ZONE_DELETED, result.feedback?.kind)
        assertEquals("Ruta 1", result.feedback?.zoneLabel)
        // La ubicación queda libre para redibujarse (requisito 7).
        assertEquals(false, result.state.catalogBusy(2))
        val redraw = result.state.selectTarget(MapEditorTarget.Catalog(2))
        assertTrue(redraw.ok)
    }

    @Test
    fun editRemove_whileEditing_cleanlyDropsEditing() {
        var state = circleSession().selectRegionForEdit("ubic-1").state
        val result = state.removeRegion("ubic-1")
        assertTrue(result.ok)
        assertEquals(0, result.state.regions.size)
        assertEquals(null, result.state.editingRegionId)
        assertEquals(0, result.state.draftPoints.size)
        assertEquals(MapEditorTool.NAVIGATE, result.state.tool)
    }

    @Test
    fun create_confirms_withFeedbackAndAutoNavigation() {
        val created = EMPTY
            .selectTarget(MapEditorTarget.Catalog(1)).state
            .tap(uv(0.25f, 0.5f)).state
            .tap(uv(0.35f, 0.5f))
        assertTrue(created.ok)
        assertEquals(MapEditorFeedbackKind.ZONE_CREATED, created.feedback?.kind)
        assertEquals("Pueblo Paleta", created.feedback?.zoneLabel)
        // Tras completarse la creación, el editor vuelve solo a Navegar.
        assertEquals(MapEditorTool.NAVIGATE, created.state.tool)
    }

    @Test
    fun create_polygonConfirm_alsoNavigatesBack() {
        val confirm = polygonFlow(uv(0.1f, 0.2f), uv(0.3f, 0.2f), uv(0.2f, 0.5f))
        assertTrue(confirm.ok)
        assertEquals(MapEditorFeedbackKind.ZONE_CREATED, confirm.feedback?.kind)
        assertEquals("Ruta 1", confirm.feedback?.zoneLabel)
        assertEquals(MapEditorTool.NAVIGATE, confirm.state.tool)
    }

    @Test
    fun switchTool_awayFromEdit_dropsTheOpenEdit() {
        var state = circleSession().selectRegionForEdit("ubic-1").state
        state = state.withTool(MapEditorTool.CIRCLE).state
        assertEquals(null, state.editingRegionId)
        assertEquals(null, state.editingKind)
        assertEquals(0, state.draftPoints.size)
        // La geometría confirmada nunca se vio afectada.
        assertEquals(0.1f, (state.regions.single().geometry as MapRegionGeometry.Circle).r, 1e-6f)
    }

    // ─────────────────── Persistencia e importación (4D) ───────────────────

    private val importedMixed = listOf(
        MapRegion(
            id = "ubic-2",
            geometry = MapRegionGeometry.Polygon(
                listOf(uv(0.1f, 0.6f), uv(0.9f, 0.6f), uv(0.5f, 0.9f))
            ),
            priority = 1,
            catalogId = 2
        ),
        MapRegion(
            id = "libre-7",
            geometry = MapRegionGeometry.Circle(cx = 0.5f, cy = 0.2f, r = 0.05f),
            name = "Torre Fuera del Catálogo",
            type = MapRegionType.SPECIAL
        )
    )

    @Test
    fun validateRegions_acceptsLinkedAndFreeRegions() {
        assertEquals(null, validateRegionsForEditor(importedMixed, CATALOG))
    }

    @Test
    fun validateRegions_rejectsDuplicatedIds() {
        val reason = validateRegionsForEditor(
            importedMixed + importedMixed.first().copy(geometry = MapRegionGeometry.Circle(0.7f, 0.3f, 0.05f)),
            CATALOG
        )
        assertNotNull(reason)
        assertTrue(reason!!.contains("ubic-2"))
    }

    @Test
    fun validateRegions_rejectsGeometryOutsideImage() {
        val reason = validateRegionsForEditor(
            listOf(
                importedMixed.first().copy(
                    geometry = MapRegionGeometry.Circle(cx = 0.98f, cy = 0.5f, r = 0.1f)
                )
            ),
            CATALOG
        )
        assertNotNull(reason)
    }

    @Test
    fun validateRegions_rejectsBrokenCatalogLink() {
        val reason = validateRegionsForEditor(
            listOf(
                MapRegion(
                    id = "ubic-999",
                    geometry = MapRegionGeometry.Circle(0.5f, 0.5f, 0.05f),
                    catalogId = 999
                )
            ),
            CATALOG
        )
        assertNotNull(reason)
        assertTrue(reason!!.contains("999"))
    }

    @Test
    fun importRegions_replacesTheWholeState_andReportsTheCount() {
        // Trabajo previo: zona del catálogo + edición abierta + objetivo vivo.
        var busy = circleSession().selectRegionForEdit("ubic-1").state
            .selectTarget(MapEditorTarget.FreeZone("Refugio", MapRegionType.SPECIAL)).state

        val result = busy.importRegions(importedMixed)
        assertTrue(result.ok)
        assertEquals(importedMixed, result.state.regions)
        // Reinicio limpio del modo de dibujo/edición: sin rastros colgantes.
        assertEquals(MapEditorTool.NAVIGATE, result.state.tool)
        assertEquals(null, result.state.target)
        assertEquals(null, result.state.editingRegionId)
        assertEquals(null, result.state.editingKind)
        assertEquals(0, result.state.draftPoints.size)
        assertEquals(MapEditorFeedbackKind.ZONES_IMPORTED, result.feedback?.kind)
        assertEquals("2", result.feedback?.zoneLabel)
    }

    @Test
    fun importRegions_invalidImport_leavesTheCurrentWorkUntouched() {
        val preserved = circleSession()
        val invalid = importedMixed + importedMixed.last().copy(id = "libre-7")

        val result = preserved.importRegions(invalid)
        assertEquals(MapEditorError.IMPORT_REJECTED, result.error)
        // El motivo va en claro para la UI (no se limita al enum).
        assertNotNull(result.message)
        assertEquals(preserved, result.state)
    }
}
