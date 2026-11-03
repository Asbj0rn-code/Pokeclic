/**
 * Pantalla del editor visual de zonas de Kanto — subetapa 4B.
 *
 * Muestra el mismo PNG de Kanto que el juego (`mapa_kanto`) respetando su
 * proporción y con el fondo oceánico detrás ([MapOceanColor]). Sobre él:
 *
 * - **Dibujo**: contornos de las zonas de la sesión y del borrador en curso,
 *   siempre en coordenadas de imagen: cada frame se pinta con `MapTransform`
 *   (`uvToScreen` con el zoom y el desplazamiento del momento), de modo que
 *   las zonas permanecen pegadas al mapa al ampliar o mover la vista.
 * - **Modos separados**: en Navegar la vista entera reacciona (pellizco,
 *   desplazamiento, doble toque, restauración). En las herramientas de dibujo
 *   el toque simple dibuja **y el arrastre sigue moviendo el mapa**: los
 *   detectores conviven — un toque limpio no genera slop y llega al detector
 *   de dibujo, mientras que todo movimiento lo consume antes el de navegación.
 *   El doble toque se desactiva al dibujar para no robar toques; el zoom a
 *   dos dedos sigue disponible.
 * - **Herramientas**: círculo (centro + punto del borde), rectángulo (dos
 *   esquinas) y polígono (vértices + Confirmar). Deshacer vértice y cancelar
 *   dibujo completan lo pedido; círculo y rectángulo se confirman solos al
 *   completarse los dos toques.
 * - **Objetivos**: una ubicación del catálogo (enlazada, sin duplicados) o una
 *   zona libre con nombre y tipo. Toda la lógica vive en [MapEditorSession]
 *   (pura, testada en la JVM): aquí solo se convierten toques a `(u, v)` con
 *   `MapTransform.screenToUv`.
 *
 * Persistencia (subetapa 4D, solo debug):
 * - **Autoguardado**: cada lista confirmada distinta de la última guardada
 *   se escribe en el espacio privado de la app con [MapEditorStore]
 *   (escritura atómica). Borradores a medias y ediciones sin aplicar nunca
 *   llegan al disco porque jamás modifican `regions`.
 * - **Recuperación**: al abrir se pregunta en un diálogo si se retoma el
 *   borrador anterior (y cuántas zonas tiene); un borrador dañado se
 *   informa sin cerrar el editor y solo se descarta con confirmación.
 * - **Exportar / Importar / Copiar** con SAF (`CreateDocument` /
 *   `OpenDocument`) y portapapeles; siempre se valida antes de exportar y
 *   tras importar, conservando el trabajo si el fichero es inválido.
 * - Los assets del juego **no se tocan**: la integración sigue siendo el
 *   pegado manual del JSON exportado en `kanto_regions.json`.
 */
package com.dsrs.pokeclic.dbg

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt
import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.data.loadKantoMapData
import com.dsrs.pokeclic.model.MapLocation
import com.dsrs.pokeclic.model.MapRegion
import com.dsrs.pokeclic.model.MapRegionGeometry
import com.dsrs.pokeclic.model.MapRegionType
import com.dsrs.pokeclic.model.MapRegionsDataException
import com.dsrs.pokeclic.model.UvPoint
import com.dsrs.pokeclic.model.parseRegions
import com.dsrs.pokeclic.ui.MapDoubleTapZoom
import com.dsrs.pokeclic.ui.MapFitZoom
import com.dsrs.pokeclic.ui.MapMaxZoom
import com.dsrs.pokeclic.ui.MapOceanColor
import com.dsrs.pokeclic.ui.clampMapOffset
import com.dsrs.pokeclic.ui.computeMapFitScale
import com.dsrs.pokeclic.ui.screenToUv
import com.dsrs.pokeclic.ui.uvToScreen

/**
 * Pantalla completa del editor: barra de desarrollo, visor con dibujo, barra
 * de herramientas y diálogos de persistencia. El estado es un
 * [MapEditorState] local (memoria de la sesión); las reducciones son puras
 * y testadas, y el borrador se autoguarda con [MapEditorStore].
 */
@Composable
fun MapEditorScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // Catálogo (47) y mapeo base de los assets: se cargan una vez. Las zonas
    // de la sesión viven en el MapEditorState; el borrador en disco decide
    // si se retoman al abrir (diálogo de recuperación).
    val loaded = remember { loadKantoMapData(context).getOrNull() }
    var session by remember {
        mutableStateOf(
            MapEditorState(
                catalog = loaded?.locations.orEmpty(),
                regions = loaded?.regions?.regions.orEmpty()
            )
        )
    }
    var lastError by remember { mutableStateOf<MapEditorError?>(null) }
    var errorDetail by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<MapEditorFeedback?>(null) }
    var zonesPanelOpen by remember { mutableStateOf(false) }
    var deleteConfirmOpen by remember { mutableStateOf(false) }

    // ─────────────── Persistencia del borrador (4D) ───────────────
    val store = remember { MapEditorStore(MapEditorStore.defaultDirectory(context)) }

    /**
     * Armado del autoguardado: **desarrollo en falso hasta decidir la
     * recuperación** — si se guardara desde el primer frame, el estado base
     * de los assets pisaría el borrador sin preguntar nada.
     */
    var persistArmed by remember { mutableStateOf(false) }

    /** Contenido ya en disco (`null` = nada guardado todavía). */
    var savedRegions by remember { mutableStateOf<List<MapRegion>?>(null) }

    /** Motivo del último fallo de guardado (`null` = sin errores). */
    var persistError by remember { mutableStateOf<String?>(null) }

    /** Hora legible del último guardado (para la barra de estado). */
    var savedAt by remember { mutableStateOf<String?>(null) }

    /** Mensaje puntual de exportar/importar/copiar; se auto-anula. */
    var ioMessage by remember { mutableStateOf<MapEditorIoMessage?>(null) }

    /** Diálogo de recuperación pendiente de resolver. */
    var recovery by remember { mutableStateOf<MapEditorDraftLoad>(MapEditorDraftLoad.None) }
    var recoveryResolved by remember { mutableStateOf(false) }

    /** Importación pendiente de confirmar (con trabajo actual en pantalla). */
    var pendingImport by remember { mutableStateOf<List<MapRegion>?>(null) }
    var importConfirmOpen by remember { mutableStateOf(false) }

    fun showIo(isError: Boolean, text: String) {
        ioMessage = if (isError) MapEditorIoMessage(text, isError = true) else MapEditorIoMessage(text)
    }

    fun showIoError(reason: String) = showIo(isError = true, text = reason)

    // Punto único de paso de los eventos: adopta el estado nuevo, registra
    // el último rechazo y delata el feedback de éxito (creada/actualizada/
    // eliminada/importada), que se oculta a los pocos segundos.
    fun onEvent(result: MapEditorResult) {
        session = result.state
        lastError = result.error
        errorDetail = result.message
        feedback = result.feedback
    }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(4000)
            feedback = null
        }
    }
    LaunchedEffect(ioMessage) {
        if (ioMessage != null) {
            delay(4000)
            ioMessage = null
        }
    }

    // Recuperación: leer el borrador una vez, en I/O fuera del hilo UI. Si
    // no hay borrador, el autoguardado se arma directamente; con borrador
    // (válido o dañado) decide el usuario en el diálogo.
    LaunchedEffect(Unit) {
        val outcome = withContext(Dispatchers.IO) { store.load() }
        recovery = outcome
        if (outcome == MapEditorDraftLoad.None) {
            savedRegions = session.regions
            persistArmed = true
            recoveryResolved = true
        }
    }

    // Autoguardado: SÓLO cuando cambia la lista CONFIRMADA y difiere de la
    // ya en disco. Validar antes de escribir; jamás durante la decisión de
    // recuperación (persistArmed en falso).
    LaunchedEffect(session.regions, persistArmed, session.catalog) {
        if (!persistArmed || savedRegions == session.regions) return@LaunchedEffect
        val reason = validateRegionsForEditor(session.regions, session.catalog)
        if (reason != null) {
            persistError = reason
            return@LaunchedEffect
        }
        val error = withContext(Dispatchers.IO) {
            store.save(session.regions, session.catalog)
        }
        if (error == null) {
            savedRegions = session.regions
            persistError = null
            savedAt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        } else {
            persistError = error
        }
    }

    // Cierre con cambios en vuelo: si un confirmado acaba de aterrizar y su
    // escritura no llegó, el dispose escribe una última vez (escritura de
    // pocos KB; solo compilaciones debug).
    val latestSession by rememberUpdatedState(session)
    val latestSaved by rememberUpdatedState(savedRegions)
    val latestArmed by rememberUpdatedState(persistArmed)
    DisposableEffect(store) {
        onDispose {
            val current = latestSession
            if (latestArmed && latestSaved != current.regions) {
                runCatching { store.save(current.regions, current.catalog) }
            }
        }
    }

    // ─────────────── Exportar / Importar / Copiar (SAF) ───────────────
    var exportPayload by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val payload = exportPayload
        if (uri == null || payload == null) return@rememberLauncherForActivityResult
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(payload.toByteArray(Charsets.UTF_8))
                        output.flush()
                        null
                    } ?: "Android no entregó salida para el fichero elegido"
                } catch (ioFailure: Exception) {
                    ioFailure.message ?: ioFailure.javaClass.simpleName
                }
            }
            if (error == null) {
                showIo(isError = false, text = "JSON exportado")
            } else {
                showIoError(error)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().decodeToString()
                    }
                } catch (ioFailure: Exception) {
                    throw IllegalStateException(
                        ioFailure.message ?: ioFailure.javaClass.simpleName, ioFailure
                    )
                }
            }
                ?: run {
                    showIoError("No se pudo leer el fichero elegido")
                    return@launch
                }
            val parsed = try {
                parseRegions(text)
            } catch (dataFailure: MapRegionsDataException) {
                showIoError("Fichero inválido: ${dataFailure.message}")
                return@launch
            }
            val reason = validateRegionsForEditor(parsed.regions, session.catalog)
            if (reason != null) {
                // Fichero inválido: el trabajo actual queda EXACTO como
                // estaba (requisito 4).
                showIoError("Importación rechazada: $reason")
                return@launch
            }
            if (session.regions.isEmpty()) {
                onEvent(session.importRegions(parsed.regions))
            } else {
                // Trabajo previo presente: sustituir exige confirmación.
                pendingImport = parsed.regions
                importConfirmOpen = true
            }
        }
    }

    fun launchExport() {
        val reason = validateRegionsForEditor(session.regions, session.catalog)
        if (reason != null) {
            showIoError("Nada que exportar: $reason")
            return
        }
        exportPayload = store.exportPayload(session.regions, session.catalog)
        exportLauncher.launch("kanto_regions.json")
    }

    fun launchImport() {
        importLauncher.launch(arrayOf("*/*"))
    }

    fun launchCopy() {
        val reason = validateRegionsForEditor(session.regions, session.catalog)
        if (reason != null) {
            showIoError("Nada que copiar: $reason")
            return
        }
        clipboard.setText(AnnotatedString(store.exportPayload(session.regions, session.catalog)))
        showIo(isError = false, text = "JSON copiado al portapapeles")
    }

    // Estado del borrador para la barra superior: prioridad de mensajes
    // I/O puntual > error de autoguardado > pendiente > guardado.
    val pendingChanges = persistArmed && savedRegions != session.regions
    val currentPersistError = persistError
    val topStatusText = when {
        ioMessage != null -> ioMessage!!.text
        currentPersistError != null ->
            stringResource(R.string.map_editor_status_error, currentPersistError)
        pendingChanges -> stringResource(R.string.map_editor_status_pending)
        persistArmed ->
            stringResource(
                R.string.map_editor_status_saved,
                savedAt.orEmpty(),
                session.regions.size
            )
        else -> stringResource(R.string.map_editor_status_ready)
    }
    val topStatusIsError = ioMessage?.isError == true || currentPersistError != null

    Column(modifier = Modifier.fillMaxSize()) {
        MapEditorTopBar(
            statusText = topStatusText,
            statusIsError = topStatusIsError,
            onClose = onClose
        )
        MapEditorViewport(
            session = session,
            onEvent = ::onEvent,
            modifier = Modifier.weight(1f)
        )
        MapEditorToolsBar(
            session = session,
            lastError = lastError,
            lastErrorMessage = errorDetail,
            feedback = feedback,
            onEvent = ::onEvent,
            onOpenZonesPanel = { zonesPanelOpen = true },
            onRequestDeleteZone = { deleteConfirmOpen = true },
            onExportRequest = ::launchExport,
            onImportRequest = ::launchImport,
            onCopyRequest = ::launchCopy
        )
    }

    if (zonesPanelOpen) {
        MapEditorZonesPanel(
            session = session,
            onEvent = ::onEvent,
            onDismiss = { zonesPanelOpen = false }
        )
    }

    // ─────────────── Recuperación del borrador (4D) ───────────────
    val recoverableDraft = recovery as? MapEditorDraftLoad.Loaded
    if (!recoveryResolved && recoverableDraft != null) {
        // No se puede cerrar pellizcando fuera: elegir entre retomar o empezar
        // de cero. Empezar de cero no pisa nada todavía: el borrador se
        // sobrescribe con el PRIMER guardado confirmado (elección consciente).
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.map_editor_recover_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.map_editor_recover_text,
                        recoverableDraft.regions.size
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        session = session.copy(
                            regions = recoverableDraft.regions,
                            tool = MapEditorTool.NAVIGATE,
                            target = null,
                            draftPoints = emptyList(),
                            editingRegionId = null,
                            editingKind = null
                        )
                        savedRegions = recoverableDraft.regions
                        persistError = null
                        persistArmed = true
                        recoveryResolved = true
                    }
                ) { Text(stringResource(R.string.map_editor_recover_yes)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // Partir de cero conserva el fichero hasta el primer
                        // guardado (que lo sustituirá íntegro).
                        savedRegions = session.regions
                        persistArmed = true
                        recoveryResolved = true
                    }
                ) { Text(stringResource(R.string.map_editor_recover_no)) }
            }
        )
    }

    val damagedDraft = recovery as? MapEditorDraftLoad.Corrupted
    if (!recoveryResolved && damagedDraft != null) {
        // No se cierra el editor y NO se borra nada solo: informar y decidir.
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.map_editor_corrupt_title)) },
            text = {
                Text(stringResource(R.string.map_editor_corrupt_text, damagedDraft.reason))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { store.delete() }
                        }
                        persistArmed = true
                        recoveryResolved = true
                    }
                ) { Text(stringResource(R.string.map_editor_discard_damaged)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        savedRegions = session.regions
                        persistArmed = true
                        recoveryResolved = true
                    }
                ) { Text(stringResource(R.string.map_editor_keep_damaged)) }
            }
        )
    }

    // Confirmación de importación: hay trabajo actual, mostrar recuentos.
    if (importConfirmOpen && pendingImport != null) {
        val incoming = pendingImport.orEmpty()
        AlertDialog(
            onDismissRequest = { importConfirmOpen = false },
            title = { Text(stringResource(R.string.map_editor_import_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.map_editor_import_confirm_text,
                        session.regions.size,
                        incoming.size
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        importConfirmOpen = false
                        onEvent(session.importRegions(pendingImport.orEmpty()))
                        pendingImport = null
                    }
                ) { Text(stringResource(R.string.map_editor_import_confirm_yes)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        importConfirmOpen = false
                        pendingImport = null
                    }
                ) { Text(stringResource(R.string.map_editor_import_confirm_no)) }
            }
        )
    }

    // Borrado con confirmación previa (requisito 7): la acción real queda en
    // manos del usuario; cancelar no toca nada.
    if (deleteConfirmOpen) {
        val deleteTarget = session.editingRegionId?.let { editingId ->
            session.regions.firstOrNull { it.id == editingId }
        }
        AlertDialog(
            onDismissRequest = { deleteConfirmOpen = false },
            title = { Text(stringResource(R.string.map_editor_edit_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.map_editor_edit_delete_confirm_text,
                        deleteTarget?.resolvedName(session.catalog)
                            ?: deleteTarget?.name ?: deleteTarget?.id.orEmpty()
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirmOpen = false
                        deleteTarget?.let { onEvent(session.removeRegion(it.id)) }
                    }
                ) { Text(stringResource(R.string.map_editor_edit_delete_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmOpen = false }) {
                    Text(stringResource(R.string.map_editor_edit_delete_confirm_no))
                }
            }
        )
    }
}

/**
 * Mensaje puntual de una operación de I/O visible (exportar, importar,
 * copiar): texto y si ha salido mal (para colorear el estado).
 */
private data class MapEditorIoMessage(
    val text: String,
    val isError: Boolean = false
)

/**
 * Barra superior: identifica la herramienta de desarrollo, muestra el estado
 * real de la persistencia del borrador (guardado/pendiente/error o el
 * resultado de exportar/importar) y ofrece el cierre visible.
 */
@Composable
private fun MapEditorTopBar(
    statusText: String,
    statusIsError: Boolean,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 16.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.map_editor_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (statusIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.map_editor_close)
            )
        }
    }
}

// ───────────────────────────── Visor y dibujo ─────────────────────────────

/**
 * Visor del mapa con superposición de zonas. Los gestos dependen del modo:
 * navegar mantiene el doble toque; las herramientas de dibujo usan el toque
 * simple para dibujar (el arrastre sigue siendo navegación).
 */
@Composable
private fun MapEditorViewport(
    session: MapEditorState,
    onEvent: (MapEditorResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val mapPainter = painterResource(id = R.drawable.mapa_kanto)
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(MapFitZoom) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val fitScale = remember(mapPainter.intrinsicSize, containerSize, density) {
        computeMapFitScale(mapPainter.intrinsicSize, containerSize, density)
    }
    if (fitScale == null) {
        Box(modifier = modifier.fillMaxSize().onSizeChanged { containerSize = it })
        return
    }
    val fitWidthPx = with(density) { mapPainter.intrinsicSize.width.dp.toPx() } * fitScale
    val fitHeightPx = with(density) { mapPainter.intrinsicSize.height.dp.toPx() } * fitScale
    val canReset = zoom > MapFitZoom || offset != Offset.Zero
    val containerCenter = Offset(
        containerSize.width / 2f, containerSize.height / 2f
    )

    /**
     * Estado **más reciente** visible desde los detectores. `pointerInput` no
     * se reinstala en cada recomposición (solo cuando cambia su clave), y el
     * cierre capturaría el parámetro del momento de la instalación: el segundo
     * toque del círculo se aplicaría al snapshot con el borrador vacío y nunca
     * confirmaría la zona. `rememberUpdatedState` mantiene el cierre pegado al
     * estado de la recomposición en curso. (Los vars `zoom`/`offset` ya se
     * leen frescos por ser delegados Compose.)
     */
    val latestSession by rememberUpdatedState(session)

    // Clave y rama del detector de toques: fija al instalar (la clave garantiza
    // que la rama coincide con la herramienta del momento).
    val editorTool = session.tool
    // Desactivar el pellizco/tap de navegación en el modo Editar (4C)
    val editingMode = editorTool == MapEditorTool.EDIT

    fun clampOffset(candidate: Offset): Offset = clampMapOffset(
        candidate = candidate,
        containerWidth = containerSize.width.toFloat(),
        containerHeight = containerSize.height.toFloat(),
        fitWidthPx = fitWidthPx,
        fitHeightPx = fitHeightPx,
        zoom = zoom
    )

    /** Pantalla → imagen con la vista del momento (fuente única). */
    fun uvOf(tap: Offset): UvPoint = screenToUv(
        tap.x, tap.y,
        containerCenter.x, containerCenter.y,
        offset.x, offset.y, zoom, fitWidthPx, fitHeightPx
    )

    /** Imagen → pantalla con la vista del momento: para pintar las zonas. */
    fun offsetOf(u: Float, v: Float): Offset = uvToScreen(
        u, v, containerCenter.x, containerCenter.y,
        offset.x, offset.y, zoom, fitWidthPx, fitHeightPx
    )

    /**
     * ¿Qué punto de control hay bajo `(x, y)` de pantalla en el estado actual?
     * Radio de agarre píxel-basado y generoso (48 dp), con el ganador el más
     * cercano: fácil de pulsar con el ratón del emulador.
     */
    fun editingControlNear(current: MapEditorState, touch: Offset): Int {
        val grabRadius = with(density) { 48.dp.toPx() }
        var bestIndex = -1
        var bestDistance = grabRadius
        current.draftPoints.forEachIndexed { index, point ->
            val screen = offsetOf(point.u, point.v)
            val distance = sqrt(
                (screen.x - touch.x) * (screen.x - touch.x) +
                    (screen.y - touch.y) * (screen.y - touch.y)
            )
            if (distance <= bestDistance) {
                bestIndex = index
                bestDistance = distance
            }
        }
        return bestIndex
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MapOceanColor)
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            .pointerInput(editingMode, fitWidthPx, fitHeightPx) {
                // En Editar el pellizco se apaga: el arrastre de ese modo
                // pertenece a los puntos de control (y al desplazamiento
                // manual), sin pelear por el mismo gesto. El zoom está a un
                // chip de distancia (Navegar).
                if (!editingMode) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val newZoom = (zoom * gestureZoom).coerceIn(MapFitZoom, MapMaxZoom)
                        var newOffset = offset
                        if (newZoom != zoom) {
                            newOffset = centroid - containerCenter -
                                (centroid - containerCenter - offset) * (newZoom / zoom)
                        }
                        newOffset += pan
                        zoom = newZoom
                        offset = clampOffset(newOffset)
                    }
                }
            }
            .pointerInput(editorTool, fitWidthPx, fitHeightPx) {
                if (editorTool == MapEditorTool.NAVIGATE) {
                    // Navegación: doble toque de zoom, como en el mapa normal.
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            val targetZoom =
                                if (zoom > MapFitZoom) MapFitZoom else MapDoubleTapZoom
                            offset = if (targetZoom == MapFitZoom) {
                                Offset.Zero
                            } else {
                                clampOffset(
                                    tap - containerCenter -
                                        (tap - containerCenter - offset) * (targetZoom / zoom)
                                )
                            }
                            zoom = targetZoom
                        }
                    )
                } else {
                    // Dibujo/Editar: cada toque limpio es un paso (dibujo) o
                    // una selección de zona (Editar), aplicado al estado MÁS
                    // RECIENTE (no al snapshot de instalación). Sin doble toque
                    // (precisión): el zoom sigue a dos dedos fuera de Editar.
                    detectTapGestures(
                        onTap = { tap -> onEvent(latestSession.tap(uvOf(tap))) }
                    )
                }
            }
            .pointerInput(editingMode, session.editingRegionId, fitWidthPx, fitHeightPx) {
                if (!editingMode) return@pointerInput
                // Editar: el único arrastre que no mueve el mapa es el de un
                // punto de control (agarrado al empezar el gesto, con radio
                // generoso para el ratón del emulador). Arrastrar el vacío
                // desplaza la vista manualmente, clamped igual que el pellizco.
                var grabbed = -1
                detectDragGestures(
                    onDragStart = { start ->
                        grabbed = editingControlNear(latestSession, start)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (grabbed >= 0) {
                            onEvent(latestSession.editMoveHandle(grabbed, uvOf(change.position)))
                        } else {
                            offset = clampOffset(offset + dragAmount)
                        }
                    },
                    onDragEnd = { grabbed = -1 },
                    onDragCancel = { grabbed = -1 }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = mapPainter,
            contentDescription = stringResource(R.string.map_kanto_desc),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(
                    with(density) { mapPainter.intrinsicSize.width.dp } * fitScale,
                    with(density) { mapPainter.intrinsicSize.height.dp } * fitScale
                )
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = offset.x
                    translationY = offset.y
                }
        )

        // Paleta resuelta del tema (dentro de Canvas no hay composición).
        val colors = MapEditorZoneColors(
            city = MaterialTheme.colorScheme.primary,
            route = MaterialTheme.colorScheme.tertiary,
            special = MaterialTheme.colorScheme.secondary,
            draft = MaterialTheme.colorScheme.onSurface
        )
        Canvas(modifier = Modifier.matchParentSize()) {
            for (region in session.regions) {
                drawRegionOutline(
                    region,
                    session.catalog,
                    colors,
                    emphasized = region.id == session.editingRegionId,
                    ::offsetOf
                )
            }
            if (session.tool == MapEditorTool.EDIT && session.editingRegionId != null) {
                // Edición: vista previa discontinua de la forma provisional y
                // puntos de control grandes (fáciles de AGARRAR con el ratón).
                drawEditPreview(session.editingKind, session.draftPoints, colors.draft, ::offsetOf)
                drawEditHandles(session.draftPoints, colors.draft, ::offsetOf)
            } else {
                drawDraftOutline(session.draftPoints, session.tool, colors.draft, ::offsetOf)
            }
        }
        if (canReset) {
            Icon(
                imageVector = Icons.Filled.Replay,
                contentDescription = stringResource(R.string.map_editor_reset_view),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .clickable {
                        zoom = MapFitZoom
                        offset = Offset.Zero
                    }
                    .padding(8.dp)
            )
        }
    }
}

/** Paleta de contornos, resuelta desde el tema antes de dibujar. */
private data class MapEditorZoneColors(
    val city: Color,
    val route: Color,
    val special: Color,
    val draft: Color
)

private fun zoneColor(type: MapRegionType?, colors: MapEditorZoneColors): Color =
    when (type) {
        MapRegionType.CITY -> colors.city
        MapRegionType.ROUTE -> colors.route
        MapRegionType.SPECIAL, null -> colors.special
    }

/**
 * Contorno de una zona confirmada:
 *
 * - **Círculo** de radio `r` en unidades `(u, v)`: el óvalo exacto con
 *   semiejes `r·fitWidth` y `r·fitHeight` — es precisamente el lugar de
 *   puntos que la región considera dentro (el hittesting usa la norma
 *   euclídea en `(u, v)`, y la pantalla estira u y v de forma distinta).
 * - **Rectángulo** normalizado a esquinas min/max.
 * - **Polígono**: trazado cerrado, relleno tenue para distinguirlo del mapa.
 *
 * Visibilidad (requisito de la 4C): cada trazo lleva **halo blanco** de
 * haloWidth para destacar sobre cualquier fondo del PNG; la zona en edición
 * toma trazo más grueso ([emphasized]).
 */
private fun DrawScope.drawRegionOutline(
    region: MapRegion,
    catalog: List<MapLocation>,
    colors: MapEditorZoneColors,
    emphasized: Boolean,
    offsetOf: (u: Float, v: Float) -> Offset
) {
    val color = zoneColor(region.resolvedType(catalog), colors)
    val strokeWidth = if (emphasized) 3.dp.toPx() else 2.dp.toPx()
    val haloStroke = Stroke(width = 5.dp.toPx())
    val haloColor = Color.White.copy(alpha = 0.55f)
    val fillAlpha = if (emphasized) 0.22f else 0.14f

    when (val geometry = region.geometry) {
        is MapRegionGeometry.Circle -> {
            val radius = geometry.r
            val leftTop = offsetOf(geometry.cx - radius, geometry.cy - radius)
            val rightBottom = offsetOf(geometry.cx + radius, geometry.cy + radius)
            val ovalSize = Size(rightBottom.x - leftTop.x, rightBottom.y - leftTop.y)
            drawOval(
                color = color.copy(alpha = fillAlpha),
                topLeft = leftTop,
                size = ovalSize
            )
            drawOval(topLeft = leftTop, size = ovalSize, color = haloColor, style = haloStroke)
            drawOval(
                color = color,
                topLeft = leftTop,
                size = ovalSize,
                style = Stroke(width = strokeWidth)
            )
        }

        is MapRegionGeometry.Envelope -> {
            val left = minOf(geometry.x1, geometry.x2)
            val top = minOf(geometry.y1, geometry.y2)
            val right = maxOf(geometry.x1, geometry.x2)
            val bottom = maxOf(geometry.y1, geometry.y2)
            val rectTopLeft = offsetOf(left, top)
            val rectSize = Size(
                offsetOf(right, bottom).x - rectTopLeft.x,
                offsetOf(right, bottom).y - rectTopLeft.y
            )
            drawRect(color = color.copy(alpha = fillAlpha), topLeft = rectTopLeft, size = rectSize)
            drawRect(topLeft = rectTopLeft, size = rectSize, color = haloColor, style = haloStroke)
            drawRect(
                color = color,
                topLeft = rectTopLeft,
                size = rectSize,
                style = Stroke(width = strokeWidth)
            )
        }

        is MapRegionGeometry.Polygon -> {
            val path = Path()
            geometry.points.forEachIndexed { index, point ->
                val screen = offsetOf(point.u, point.v)
                if (index == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
            }
            path.close()
            drawPath(path, color = color.copy(alpha = fillAlpha))
            drawPath(path, color = haloColor, style = haloStroke)
            drawPath(path, color = color, style = Stroke(width = strokeWidth))
        }
    }
}

/**
 * Borrador del dibujo: los puntos ya recogidos marcados como círculos
 * rellenos; en polígono, además, una polilínea **abierta** discontinua (el
 * cierre ocurre al confirmar).
 */
private fun DrawScope.drawDraftOutline(
    draftPoints: List<UvPoint>,
    tool: MapEditorTool,
    color: Color,
    offsetOf: (u: Float, v: Float) -> Offset
) {
    if (draftPoints.isEmpty()) return
    val dotRadius = 4.dp.toPx()
    for (point in draftPoints) {
        drawCircle(
            color = color,
            radius = dotRadius,
            center = offsetOf(point.u, point.v)
        )
    }
    if (tool == MapEditorTool.POLYGON && draftPoints.size > 1) {
        val path = Path()
        draftPoints.forEachIndexed { index, point ->
            val screen = offsetOf(point.u, point.v)
            if (index == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
        }
        drawPath(
            path,
            color = color,
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
            )
        )
    }
}

/**
 * Vista previa discontinua de la **forma provisional en edición** (reconstruida
 * de los puntos de control, idéntico a como lo hace la lógica): círculo u
 * óvalo, rectángulo min/max o polilínea cerrada se rehace al aplicar; aquí se
 * pinta abierta para dejar claro que es un borrador.
 */
private fun DrawScope.drawEditPreview(
    editingKind: com.dsrs.pokeclic.dbg.MapEditorEditKind?,
    points: List<UvPoint>,
    color: Color,
    offsetOf: (u: Float, v: Float) -> Offset
) {
    if (points.isEmpty()) return
    val dash = Stroke(
        width = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
    )
    when (editingKind) {
        MapEditorEditKind.CIRCLE -> {
            val center = points[0]
            val edge = points.getOrNull(1) ?: return
            val radius = sqrt((edge.u - center.u).let { it * it } + (edge.v - center.v).let { it * it })
            if (radius <= 0f) return
            val leftTop = offsetOf(center.u - radius, center.v - radius)
            val rightBottom = offsetOf(center.u + radius, center.v + radius)
            drawOval(
                color = color,
                topLeft = leftTop,
                size = Size(rightBottom.x - leftTop.x, rightBottom.y - leftTop.y),
                style = dash
            )
        }

        MapEditorEditKind.ENVELOPE -> {
            val a = points[0]
            val b = points.getOrNull(1) ?: return
            val leftTop = offsetOf(minOf(a.u, b.u), minOf(a.v, b.v))
            val rightBottom = offsetOf(maxOf(a.u, b.u), maxOf(a.v, b.v))
            drawRect(
                color = color,
                topLeft = leftTop,
                size = Size(rightBottom.x - leftTop.x, rightBottom.y - leftTop.y),
                style = dash
            )
        }

        MapEditorEditKind.POLYGON, null -> {
            val path = Path()
            points.forEachIndexed { index, point ->
                val screen = offsetOf(point.u, point.v)
                if (index == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
            }
            path.close()
            drawPath(path, color = color, style = dash)
        }
    }
}

/**
 * Puntos de control en edición: discos grandes y contrastados para el agarre.
 */
private fun DrawScope.drawEditHandles(
    points: List<UvPoint>,
    color: Color,
    offsetOf: (u: Float, v: Float) -> Offset
) {
    points.forEach { point ->
        val center = offsetOf(point.u, point.v)
        drawCircle(color = Color.White, radius = 9.dp.toPx(), center = center)
        drawCircle(
            color = color,
            radius = 7.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

// ───────────────────────────── Barra de herramientas ─────────────────────────────

/**
 * Herramientas de la sesión: aviso temporal (error en rojo, feedback de éxito
 * en el color primario, si no la indicación del paso), objetivo actual (abre
 * el panel de ubicaciones), chips de herramientas, botones según la fase de
 * dibujo y, en el modo Editar, aplicar/cancelar/eliminar.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MapEditorToolsBar(
    session: MapEditorState,
    lastError: MapEditorError?,
    lastErrorMessage: String?,
    feedback: MapEditorFeedback?,
    onEvent: (MapEditorResult) -> Unit,
    onOpenZonesPanel: () -> Unit,
    onRequestDeleteZone: () -> Unit,
    onExportRequest: () -> Unit,
    onImportRequest: () -> Unit,
    onCopyRequest: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(
                text = when {
                    lastError != null && lastErrorMessage != null -> lastErrorMessage
                    lastError != null -> stringResource(lastError.stringRes())
                    feedback != null -> stringResource(feedback.kind.stringRes(), feedback.zoneLabel)
                    else -> stringResource(session.hintRes())
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    lastError != null -> MaterialTheme.colorScheme.error
                    feedback != null -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 2.dp)
            )

            // Herramientas principales: todos los chips en una única fila
            // repartiendo el ancho disponible (pesos), de modo que nunca sea
            // necesario desplazarse lateralmente: el gesto horizontal cerca
            // del borde en pantalla completa lo intercepta el sistema.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MapToolChip(
                    label = stringResource(R.string.map_editor_tool_navigate),
                    selected = session.tool == MapEditorTool.NAVIGATE,
                    modifier = Modifier.weight(1f),
                    onClick = { onEvent(session.withTool(MapEditorTool.NAVIGATE)) }
                )
                MapToolChip(
                    label = stringResource(R.string.map_editor_tool_circle),
                    selected = session.tool == MapEditorTool.CIRCLE,
                    modifier = Modifier.weight(1f),
                    onClick = { onEvent(session.withTool(MapEditorTool.CIRCLE)) }
                )
                MapToolChip(
                    label = stringResource(R.string.map_editor_tool_rectangle),
                    selected = session.tool == MapEditorTool.RECTANGLE,
                    modifier = Modifier.weight(1f),
                    onClick = { onEvent(session.withTool(MapEditorTool.RECTANGLE)) }
                )
                MapToolChip(
                    label = stringResource(R.string.map_editor_tool_polygon),
                    selected = session.tool == MapEditorTool.POLYGON,
                    modifier = Modifier.weight(1f),
                    onClick = { onEvent(session.withTool(MapEditorTool.POLYGON)) }
                )
                MapToolChip(
                    label = stringResource(R.string.map_editor_tool_edit),
                    selected = session.tool == MapEditorTool.EDIT,
                    modifier = Modifier.weight(1f),
                    onClick = { onEvent(session.withTool(MapEditorTool.EDIT)) }
                )
            }

            // Fase contextual: acceso al panel de ubicaciones y acciones del
            // paso en curso. Se agrupan en FlowRow para que, si no caben en
            // una línea, pasen a la siguiente en vez de recortarse.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                TextButton(onClick = onOpenZonesPanel) {
                    Text(
                        text = session.target?.let { target ->
                            "${targetLabel(target, session.catalog)} ·" +
                                stringResource(R.string.map_editor_zones_open)
                        } ?: stringResource(R.string.map_editor_zones_open),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Persistencia (4D): exportar/importar SIEMPRE disponibles
                // (con zonas; sin ellas, exportar/copiar no tienen sentido),
                // sin desplazamiento lateral gracias al FlowRow.
                TextButton(
                    onClick = onExportRequest,
                    enabled = session.regions.isNotEmpty()
                ) {
                    Text(stringResource(R.string.map_editor_action_export))
                }
                TextButton(onClick = onImportRequest) {
                    Text(stringResource(R.string.map_editor_action_import))
                }
                TextButton(
                    onClick = onCopyRequest,
                    enabled = session.regions.isNotEmpty()
                ) {
                    Text(stringResource(R.string.map_editor_action_copy))
                }

                if (session.tool == MapEditorTool.EDIT && session.editingRegionId != null) {
                    // Editar: la confirmación es explícita; cancelar descarta
                    // todo; eliminar pide confirmación en un diálogo aparte.
                    TextButton(onClick = { onEvent(session.commitEdit()) }) {
                        Text(stringResource(R.string.map_editor_edit_apply))
                    }
                    TextButton(onClick = { onEvent(session.cancelEdit()) }) {
                        Text(stringResource(R.string.map_editor_edit_cancel))
                    }
                    TextButton(onClick = onRequestDeleteZone) {
                        Text(stringResource(R.string.map_editor_edit_delete))
                    }
                } else if (session.tool == MapEditorTool.POLYGON && session.draftPoints.isNotEmpty()) {
                    TextButton(onClick = { onEvent(session.undoVertex()) }) {
                        Text(stringResource(R.string.map_editor_undo))
                    }
                    TextButton(onClick = { onEvent(session.cancelDraft()) }) {
                        Text(stringResource(R.string.map_editor_cancel))
                    }
                    TextButton(onClick = { onEvent(session.confirmPolygon()) }) {
                        Text(stringResource(R.string.map_editor_confirm))
                    }
                } else if (session.draftPoints.isNotEmpty()) {
                    // Fase a medias de círculo/rectángulo: descartar el toque
                    // aislado sin cambiar de modo ni de objetivo.
                    TextButton(onClick = { onEvent(session.cancelDraft()) }) {
                        Text(stringResource(R.string.map_editor_cancel))
                    }
                }
            }
        }
    }
}

/** Chip compacto de herramienta: fondo acentuado cuando es la activa. */
@Composable
private fun MapToolChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp)
    )
}

/** Etiqueta técnica del objetivo actual, para los indicaciones del paso. */
private fun targetLabel(target: MapEditorTarget, catalog: List<MapLocation>): String =
    when (target) {
        is MapEditorTarget.Catalog ->
            catalog.firstOrNull { it.id == target.catalogId }?.name ?: "catálogo ${target.catalogId}"

        is MapEditorTarget.FreeZone ->
            "Libre: ${target.name}"
    }

/** Indicación del paso en curso según la herramienta y el borrador. */
private fun MapEditorState.hintRes(): Int = when {
    tool == MapEditorTool.EDIT && editingRegionId == null ->
        R.string.map_editor_hint_edit_select
    tool == MapEditorTool.EDIT -> R.string.map_editor_hint_edit_move
    tool == MapEditorTool.NAVIGATE -> R.string.map_editor_hint_navigate
    target == null -> R.string.map_editor_hint_no_target
    tool == MapEditorTool.CIRCLE && draftPoints.isEmpty() ->
        R.string.map_editor_hint_circle_center
    tool == MapEditorTool.CIRCLE -> R.string.map_editor_hint_circle_radius
    tool == MapEditorTool.RECTANGLE && draftPoints.isEmpty() ->
        R.string.map_editor_hint_rect_first
    tool == MapEditorTool.RECTANGLE -> R.string.map_editor_hint_rect_second
    tool == MapEditorTool.POLYGON && draftPoints.size < MapEditorMinPolygonVertices ->
        R.string.map_editor_hint_polygon
    else -> R.string.map_editor_hint_polygon_confirm
}

/** Traducción del rechazo a cadena localizada (la lógica nunca escribe texto). */
private fun MapEditorError.stringRes(): Int = when (this) {
    MapEditorError.NO_TARGET -> R.string.map_editor_error_NO_TARGET
    MapEditorError.DUPLICATE_TARGET -> R.string.map_editor_error_DUPLICATE_TARGET
    MapEditorError.FREE_NAME_REQUIRED -> R.string.map_editor_error_FREE_NAME_REQUIRED
    MapEditorError.OUT_OF_IMAGE -> R.string.map_editor_error_OUT_OF_IMAGE
    MapEditorError.SMALL_RADIUS -> R.string.map_editor_error_SMALL_RADIUS
    MapEditorError.SMALL_SHAPE -> R.string.map_editor_error_SMALL_SHAPE
    MapEditorError.FEW_VERTICES -> R.string.map_editor_error_FEW_VERTICES
    MapEditorError.MANY_VERTICES -> R.string.map_editor_error_MANY_VERTICES
    MapEditorError.NOTHING_EDITABLE -> R.string.map_editor_error_NOTHING_EDITABLE
    MapEditorError.IMPORT_REJECTED -> R.string.map_editor_error_IMPORT_REJECTED
}

/** Traducción del feedback de éxito a cadena localizada (con el nombre %1$s). */
private fun MapEditorFeedbackKind.stringRes(): Int = when (this) {
    MapEditorFeedbackKind.ZONE_CREATED -> R.string.map_editor_feedback_created
    MapEditorFeedbackKind.ZONE_UPDATED -> R.string.map_editor_feedback_updated
    MapEditorFeedbackKind.ZONE_DELETED -> R.string.map_editor_feedback_deleted
    MapEditorFeedbackKind.ZONES_IMPORTED -> R.string.map_editor_feedback_imported
}

// ───────────────────────────── Panel de ubicaciones ─────────────────────────────

/**
 * Panel inferior con las 47 ubicaciones del catálogo agrupadas por clase y la
 * constructora de zonas libres. Una ubicación con zona en la sesión aparece
 * deshabilitada con sufijo "(ya dibujada)" — no se pueden crear duplicados.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MapEditorZonesPanel(
    session: MapEditorState,
    onEvent: (MapEditorResult) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, bottom = 24.dp
            )
        ) {
            item(key = "free") {
                MapEditorFreeZoneBuilder(
                    session = session,
                    onEvent = onEvent,
                    onDone = onDismiss
                )
            }

            val grouped = session.catalog.groupBy { it.type }
            val sections = listOf(
                MapRegionType.CITY to R.string.map_editor_sheet_groups_city,
                MapRegionType.ROUTE to R.string.map_editor_sheet_groups_route,
                MapRegionType.SPECIAL to R.string.map_editor_sheet_groups_special
            )
            for ((type, titleRes) in sections) {
                item(key = "header-$type") {
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                val locations = grouped[type].orEmpty()
                items(locations, key = { it.id }) { location ->
                    val busy = session.catalogBusy(location.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (busy) {
                                    // Con zona ya dibujada: seleccionarla para
                                    // editarla (requisito: elegir zona existente
                                    // desde el listado). Sin duplicados posibles.
                                    onEvent(session.selectRegionForEdit("ubic-${location.id}"))
                                } else {
                                    onEvent(session.selectTarget(MapEditorTarget.Catalog(location.id)))
                                }
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = location.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        if (busy) {
                            Text(
                                text = stringResource(R.string.map_editor_zones_edit_action),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Constructora de la zona libre: nombre (obligatorio, lo valida la lógica)
 * y clase. Al aceptar, el objetivo queda fijado y se preselecciona la
 * herramienta recomendada para la clase.
 */
@Composable
private fun MapEditorFreeZoneBuilder(
    session: MapEditorState,
    onEvent: (MapEditorResult) -> Unit,
    onDone: () -> Unit
) {
    var freeName by remember { mutableStateOf("") }
    var freeType by remember { mutableStateOf(MapRegionType.SPECIAL) }

    Column {
        Text(
            text = stringResource(R.string.map_editor_sheet_free_title),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = freeName,
            onValueChange = { freeName = it },
            label = { Text(stringResource(R.string.map_editor_free_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.map_editor_free_type),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = freeType == MapRegionType.CITY,
                onClick = { freeType = MapRegionType.CITY },
                label = { Text(stringResource(R.string.map_editor_free_type_city)) }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = freeType == MapRegionType.ROUTE,
                onClick = { freeType = MapRegionType.ROUTE },
                label = { Text(stringResource(R.string.map_editor_free_type_route)) }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = freeType == MapRegionType.SPECIAL,
                onClick = { freeType = MapRegionType.SPECIAL },
                label = { Text(stringResource(R.string.map_editor_free_type_special)) }
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = {
                onEvent(session.selectTarget(MapEditorTarget.FreeZone(freeName, freeType)))
                onDone()
            },
            enabled = freeName.isNotBlank()
        ) {
            Text(stringResource(R.string.map_editor_free_accept))
        }
    }
}
