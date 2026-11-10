/**
 * Capas modales y pantallas auxiliares del shell: selector de Pokémon
 * principal, hoja de opciones, pantalla del mapa y su fondo de océano.
 *
 * Todas son **sin estado**: reciben datos ya resueltos por el ViewModel y
 * notifican acciones con lambdas. En particular, el selector de principal recibe
 * `LeadOption` con id y nombre, así que no necesita conocer el catálogo.
 */
package com.dsrs.pokeclic.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dsrs.pokeclic.R
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import com.dsrs.pokeclic.data.loadKantoMapData
import com.dsrs.pokeclic.dbg.isDebugBuild
import com.dsrs.pokeclic.dbg.isMapEditorAvailable
import com.dsrs.pokeclic.dbg.openMapEditor
import com.dsrs.pokeclic.model.LeadOption
import com.dsrs.pokeclic.model.MapRegionSelection
import com.dsrs.pokeclic.model.selectAt
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Build
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch



/**
 * Selector de Pokémon principal. Ofrece las opciones ya resueltas ([options])
 * y marca la actual.
 *
 * Es **sin estado**: recibe los candidatos y la selección como datos, y avisa por
 * lambda.
 *
 * El nodo raíz aplica `Modifier.modalBlocking()` (issue #21): los toques fuera
 * de sus controles se consumen en la capa y no llegan al juego dimado de debajo
 * (la Pokéball del menú, además, no se compone mientras esta capa esté abierta,
 * ver `AppRoot`).
 */
@Composable
fun LeadOverlay(
    options: List<LeadOption>,
    currentId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .modalBlocking(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
            modifier = Modifier.padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.lead_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.lead_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(options, key = { it.id }) { option ->
                        LeadRow(
                            option = option,
                            selected = option.id == currentId,
                            onClick = { onSelect(option.id) }
                        )
                    }
                }

                OutlinedButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.lead_close))
                }
            }
        }
    }
}

@Composable
private fun LeadRow(option: LeadOption, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = {}, enabled = false) { Text(text = option.name) }
    } else {
        OutlinedButton(onClick = onClick) { Text(text = option.name) }
    }
}



/**
 * Menú de opciones como `ModalBottomSheet` de Material 3.
 *
 * Sustituye al antiguo `OptionsBottomSheetFragment`: al ser ya Compose puro, el
 * sheet se dibuja dentro del árbol de `AppRoot` y no necesita diálogo ni
 * `FragmentManager`. Su visibilidad la manda `GameUiState.isOptionsOpen`.
 *
 * @param onDismiss se invoca al cerrar (arrastre hacia abajo, toque fuera o
 *   botón atrás). El estado lo actualiza el `MainViewModel`.
 * @param onReset reinicia la run conservando la meta: el botón que antes
 *   vivía en el HUD se retiró aquí al compactarlo.
 * @param onOpenLead abre el selector de Pokémon principal: también provenía
 *   del HUD antes de la remodelación compacta.
 * @param onOpenPokedex abre la pantalla de Pokédex (TEST).
 * @param onWipeGame borra toda la partida (provisional: fila de test que vive
 *   aquí de momento, ver `MainViewModel.onWipeGame`).
 * @param onDebugJumpToZone salta a una zona concreta (provisional: fila de test,
 *   ver `MainViewModel.onDebugJumpToZone`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsSheet(
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onOpenLead: () -> Unit,
    onOpenPokedex: () -> Unit = {},
    onWipeGame: () -> Unit,
    onDebugJumpToZone: (String) -> Unit
) {
    // Sin estado intermedio: se abre directamente al contenido, como hacía el
    // fragment con STATE_EXPANDED.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        ImmersiveModalWindow()
        OptionsContent(
            onReset = onReset,
            onOpenLead = onOpenLead,
            onOpenPokedex = onOpenPokedex,
            onWipeGame = onWipeGame,
            onDebugJumpToZone = onDebugJumpToZone
        )
    }
}

/**
 * Reproduce el modo inmersivo del tema (`windowFullscreen`) dentro de las
 * ventanas modales de Compose (`ModalBottomSheet`, `AlertDialog`).
 *
 * Esas capas abren una **ventana propia** que no hereda el flag del Activity,
 * así que al mostrar la hoja de opciones reaparecía la barra de estado del
 * teléfono (no el contenido: la propia ventana modal). Este composable busca la
 * ventana dueña de la composición y la vuelve a poner a pantalla completa.
 * El usuario siempre puede revelar la barra con un deslizamiento.
 */
@Composable
private fun ImmersiveModalWindow() {
    val view = LocalView.current
    val window = remember(view) {
        var current: Any? = view
        while (current is android.view.View) {
            val provider = current as? DialogWindowProvider
            if (provider != null) return@remember provider.window
            current = current.parent
        }
        null
    }

    DisposableEffect(window) {
        val window = window ?: return@DisposableEffect onDispose { }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { }
    }
}

/**
 * Atajo de desarrollo: salta a [zoneId] con la barra de la zona ya llena para
 * probar su gimnasio sin recorrer Kanto.
 */
private data class DebugZoneJump(val zoneId: String, @param:StringRes val labelRes: Int)

/**
 * Zonas de los atajos de desarrollo, en orden de progresión: los tres gimnasios
 * implementados (Brock, Misty y Lt. Surge).
 */
private val DEBUG_ZONE_JUMPS: List<DebugZoneJump> = listOf(
    DebugZoneJump("ciudad_plateada", R.string.options_debug_plateada),
    DebugZoneJump("ciudad_celeste", R.string.options_debug_celeste),
    DebugZoneJump("ciudad_carmin", R.string.options_debug_carmin)
)

/**
 * Contenido de la hoja de opciones: título, separador, dos filas decorativas
 * sin acción asociada, cambio de Pokémon principal y reset de run (retirados
 * del HUD al compactarlo), la fila provisional de borrado de partida (test)
 * —que pide confirmación explícita antes de destruir la partida— y, solo en
 * compilaciones de desarrollo, los saltos directos a las zonas de los
 * gimnasios implementados.
 */
@Composable
fun OptionsContent(
    onReset: () -> Unit = {},
    onOpenLead: () -> Unit = {},
    onOpenPokedex: () -> Unit = {},
    onWipeGame: () -> Unit = {},
    onDebugJumpToZone: (String) -> Unit = {}
) {
    // Confirmación del borrado: un toque accidental no debe poder destruir la
    // partida directamente.
    var showWipeConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Text(
            text = stringResource(R.string.options_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        HorizontalDivider()

        ListItem(
            headlineContent = { Text(stringResource(R.string.options_settings)) },
            leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.options_about)) },
            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )

        // Cambio de Pokémon principal y reset de run: antes eran botones del
        // HUD; al compactarlo se movieron a esta hoja (ver `GameHud`).
        ListItem(
            headlineContent = { Text(stringResource(R.string.lead_change)) },
            leadingContent = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenLead)
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.action_reset)) },
            leadingContent = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().clickable(onClick = onReset)
        )

        // Pokédex (TEST): fila de prueba con la pantalla propia de la dex; el
        // ViewModel ya cierra la hoja y valida que no haya un minijuego en curso.
        ListItem(
            headlineContent = { Text(stringResource(R.string.options_pokedex_test)) },
            leadingContent = { Icon(Icons.Filled.Book, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPokedex)
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.action_wipe)) },
            leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().clickable(onClick = { showWipeConfirm = true })
        )

        // Filas de desarrollo: dejan la partida en la zona del gimnasio elegido
        // con la barra llena, para probarlo sin recorrer Kanto. Solo se componen
        // en compilaciones depurables, igual que el botón del editor del mapa.
        if (context.isDebugBuild()) {
            DEBUG_ZONE_JUMPS.forEach { jump ->
                ListItem(
                    headlineContent = { Text(stringResource(jump.labelRes)) },
                    leadingContent = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable {
                        onDebugJumpToZone(jump.zoneId)
                    }
                )
            }
        }
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = {
                ImmersiveModalWindow()
                Text(text = stringResource(R.string.wipe_confirm_title))
            },
            text = { Text(text = stringResource(R.string.wipe_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showWipeConfirm = false
                    onWipeGame()
                }) {
                    Text(text = stringResource(R.string.wipe_confirm_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) {
                    Text(text = stringResource(R.string.wipe_confirm_cancel))
                }
            }
        )
    }
}



/**
 * Fondo de océano a pantalla completa para la pestaña Mapa.
 *
 * Reproduce el patrón de composición del fondo de ruta de `AppRoot`: se dibuja
 * **detrás del `Scaffold`** (que pasa a transparente mientras se use), de modo
 * que el color rellena también el hueco inferior reservado al menú — incluida
 * la zona flotante por encima del chasis donde asoma la Poké Ball — sin franja
 * blanca del `containerColor` del tema. Carece de medidas fijas: cubre siempre
 * todo el contenedor que reciba.
 */
@Composable
internal fun MapOceanBackdrop(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(MapOceanColor))
}

/**
 * Pantalla de mapa. Se compone en un contenedor de **doble área**:
 *
 * - **Área de renderizado** (contenedor exterior): ocupa toda la pantalla, hasta
 *   por detrás del bottom menu (`bottomReserved` solo descuenta la franja del
 *   layout interior, no del dibujo). Lleva el fondo [MapOceanColor] y el
 *   `clipToBounds`: cuando el mapa se amplía o desplaza, la imagen puede pintar
 *   en la parte transparente del menú (hendidura y Pokéball) sin desbordarse
 *   fuera de pantalla.
 * - **Área de interacción y de referencia** (contenedor interior): descuenta
 *   [bottomReserved] del layout, de modo que sus límites son exactamente los del
 *   área visible de siempre. Aquí viven los gestos — el hit-testing de Compose
 *   se limita a los bounds de layout del nodo, no a su dibujo — y aquí mide su
 *   centro y tamaño `clampOffset`, el anclaje de los zooms y la fórmula de
 *   coordenadas `(u, v)` para las futuras zonas interactivas.
 *
 * Fase 1: muestra `mapa_kanto.png` completo y centrado (vista fit), con zoom por
 * pellizco anclado al gesto, desplazamiento con un dedo limitado a los bordes del
 * mapa, doble toque que alterna entre vista completa y ampliar a
 * [MapDoubleTapZoom] anclado a la pulsación, y un botón discreto que restaura la
 * vista completa. La rotación del gesto se ignora a propósito: el mapa nunca debe
 * girar.
 *
 * Las zonas libres del área visible (cuando el aspecto de la pantalla no coincide
 * con el del mapa) se pintan con [MapOceanColor], que coincide con el borde del
 * PNG, por lo que las uniones quedan invisibles sin ningún tratamiento adicional;
 * detrás del menú solo queda el color de reserva donde no llega la imagen.
 *
 * Orden de detectores: el doble toque se registra **antes** del pellizco porque
 * `detectTransformGestures` consume el down y todo movimiento con slop; el
 * detector de toques debe ver los eventos antes de que se consuman. Así un
 * arrastre/pellizco (que consume cambios) cancela el toque, y un toque limpio no
 * dispara transformaciones. Las pulsaciones simples de zonas interactivas van
 * en el mismo `detectTapGestures`: `onDoubleTap` ya evita que un doble toque
 * dispare `onTap` (Compose espera `doubleTapTimeoutMillis`).
 *
 * El zoom y el desplazamiento son estado local de Compose (transformaciones
 * puramente visuales, no forman parte de [com.dsrs.pokeclic.viewmodel.GameUiState]).
 *
 * **Zonas interactivas**: al arrancar la pantalla se cargan una vez el catálogo
 * y el mapeo de Kanto ([loadKantoMapData], con validación cruzada de los
 * `catalogId`). Un toque simple se convierte a `(u, v)` con [screenToUv] y
 * [com.dsrs.pokeclic.model.KantoRegionMap.selectAt] devuelve **todas** las
 * zonas que contienen realmente el punto (hit-testing geométrico), ordenadas
 * por categoría CITY > ROUTE > SPECIAL; sus nombres aparecen en
 * [MapRegionBadge], anclado a la esquina superior derecha del área visible
 * (fijo, aunque la imagen se mueva, con márgenes de zona segura). Si el toque
 * cae fuera de toda zona el indicador **desaparece** hasta la siguiente
 * pulsación dentro de una zona; al abrir la pantalla no existe. Con assets
 * ausentes, vacíos o con errores, la pantalla vive sin indicador y el fallo
 * queda en `Logcat`.
 *
 * La transformación usada aquí es la referencia compartida del sistema
 * (`MapTransform`): un punto normalizado `(u, v)` de la imagen original se
 * pinta en pantalla en `centroContenedor + offset + zoom * ((u - 0.5) *
 * fitWidthPx, (v - 0.5) * fitHeightPx)`, donde los tamaños `fit` ya incluyen
 * el escalado inicial (fit) de la imagen y `centroContenedor` es el centro del
 * área visible (contenedor interior, con [bottomReserved] descontado).
 *
 * @param bottomReserved altura reservada al bottom menu que el contenedor
 *   interior debe descartar de su layout (y por la que el contenedor exterior
 *   baja detrás del menú). No la mide el propio mapa: la cede el `Scaffold` vía
 *   `AppRoot`, así que la medición es derivada y no hay dimensiones fijas de
 *   dispositivo.
 */
@Composable
fun MapScreen(modifier: Modifier = Modifier, bottomReserved: Dp = 0.dp) {
    val mapPainter = painterResource(id = R.drawable.mapa_kanto)
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Mapeo de zonas y catálogo: una única lectura de assets por sesión de la
    // pantalla. Con ficheros ausentes, vacíos o con errores queda `null` y el
    // mapa sigue funcionando sin indicador; el diagnóstico está en Logcat
    // («KantoMap»).
    val context = LocalContext.current
    val kantoMap = remember { loadKantoMapData(context).getOrNull() }

    // Última selección en el punto tocado: todas las zonas que contienen el
    // punto, ya ordenadas para presentación (ciudad > ruta > especial).
    // Determinación puramente visual: se muestra tal cual hasta que otra
    // pulsación la sustituya. Antes de la primera zona o al tocar fuera vale
    // `null` (pulso sin ninguna zona: el indicador desaparece).
    var selection by remember { mutableStateOf<MapRegionSelection?>(null) }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(MapFitZoom) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewAnimationJob by remember { mutableStateOf<Job?>(null) }

    // Escalado inicial (fit): la imagen completa, centrada y sin recortar.
    val fitScale = remember(mapPainter.intrinsicSize, containerSize, density) {
        computeMapFitScale(mapPainter.intrinsicSize, containerSize, density)
    }
    val fitWidthPx = with(density) { mapPainter.intrinsicSize.width.dp.toPx() } * (fitScale ?: 1f)
    val fitHeightPx = with(density) { mapPainter.intrinsicSize.height.dp.toPx() } * (fitScale ?: 1f)

    /**
     * Limita el desplazamiento para que el mapa nunca abandone el contenedor:
     * si en algún eje el mapa es más pequeño que la vista, ese eje queda
     * centrado fijo. Delega en [clampMapOffset] con el estado actual de la
     * vista, que es la misma función que usará el editor de zonas.
     */
    fun clampOffset(candidate: Offset): Offset = clampMapOffset(
        candidate = candidate,
        containerWidth = containerSize.width.toFloat(),
        containerHeight = containerSize.height.toFloat(),
        fitWidthPx = fitWidthPx,
        fitHeightPx = fitHeightPx,
        zoom = zoom
    )

    /**
     * Cancela la animación de vista en curso, para que un gesto del usuario
     * (pellizco o arrastre) pueda interrumpirla sin saltos: el gesto parte del
     * estado interpolado en el que se interrumpió la animación.
     */
    fun cancelViewAnimation() {
        viewAnimationJob?.cancel()
        viewAnimationJob = null
    }

    /**
     * Anima (zoom, offset) hacia (targetZoom, targetOffset), interpolando línea a
     * línea y limitando el desplazamiento a cada frame con el zoom interpolado.
     */
    fun animateView(targetZoom: Float, targetOffset: Offset, durationMs: Int) {
        cancelViewAnimation()
        val startZoom = zoom
        val startOffset = offset
        if (startZoom == targetZoom && startOffset == targetOffset) return
        viewAnimationJob = scope.launch {
            Animatable(0f).animateTo(1f, tween(durationMs)) {
                val progress = value
                zoom = startZoom + (targetZoom - startZoom) * progress
                offset = clampOffset(startOffset + (targetOffset - startOffset) * progress)
            }
        }
    }

    /**
     * Restaura la vista completa (zoom fit, centrado) con animación suave.
     */
    fun resetView() = animateView(MapFitZoom, Offset.Zero, MapResetAnimationMs)

    /**
     * Doble toque: amplía a [MapDoubleTapZoom] anclado al punto pulsado si la
     * vista está completa, o restaura la vista completa en cualquier otro caso.
     * El anclaje usa la misma fórmula que el pellizco: el punto de la imagen bajo
     * el toque queda aproximadamente bajo el toque tras el zoom.
     */
    fun toggleZoomAt(tap: Offset, containerCenter: Offset) {
        if (zoom > MapFitZoom) {
            resetView()
            return
        }
        val targetZoom = MapDoubleTapZoom.coerceIn(MapFitZoom, MapMaxZoom)
        val targetOffset = tap - containerCenter -
            (tap - containerCenter - offset) * (targetZoom / zoom)
        animateView(targetZoom, targetOffset, MapZoomToggleAnimationMs)
    }

    if (fitScale == null) {
        // Contenedor aún sin medir (primer frame): nada que dibujar todavía,
        // pero hay que seguir midiéndolo para activarse en el siguiente.
        Box(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
        )
        return
    }

    val canReset = zoom > MapFitZoom || offset != Offset.Zero

    // Área de renderizado: fondo de océano de reserva (solo queda visible donde
    // no llega la imagen) y recorte al contenedor completo, para que el mapa
    // ampliado pueda pintar detrás del menú sin desbordarse fuera de pantalla.
    Box(modifier = modifier.fillMaxSize().background(MapOceanColor).clipToBounds()) {
        // Área de interacción y de referencia: el `bottomReserved` del menú solo
        // descuenta el layout, no el dibujo, así que sus bounds son los del área
        // visible de siempre (`size` de los detectores, `clampOffset`, anclajes y
        // coordenadas `(u, v)` intactos) mientras la imagen puede salir de ellos.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomReserved)
                .onSizeChanged { containerSize = it }
                // El doble toque va antes del pellizco: detectTransformGestures consume
                // el down y los movimientos, y el detector de toques debe verlos antes.
                .pointerInput(fitWidthPx, fitHeightPx) {
                    detectTapGestures(
                        // Toque simple: ¿hay una zona bajo el dedo? La conversión
                        // a (u, v) usa los mismos primitivos que la composición,
                        // ya con el zoom y el desplazamiento en curso, así que la
                        // selección siempre coincide con lo pintado. Fuera de toda
                        // zona la llamada devuelve `null` y el indicador conserva
                        // el último nombre (comportamiento pactado).
                        onTap = { tap ->
                            val uv = screenToUv(
                                tap.x, tap.y,
                                size.width / 2f, size.height / 2f,
                                offset.x, offset.y, zoom, fitWidthPx, fitHeightPx
                            )
                            // `selectAt` ordena CIUDAD > RUTA > ESPECIAL y solo
                            // incluye lo que realmente contiene el punto; fuera
                            // de toda zona devuelve `null` y limpia el estado
                            // (el indicador desaparece, comportamiento de la
                            // mejora del indicador).
                            selection = kantoMap?.regions
                                ?.selectAt(uv.u, uv.v, kantoMap.locations)
                        },
                        onDoubleTap = { tap ->
                            cancelViewAnimation()
                            toggleZoomAt(tap, Offset(size.width / 2f, size.height / 2f))
                        }
                    )
                }
                .pointerInput(fitWidthPx, fitHeightPx) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        // La rotación del gesto ("_") se descarta: el mapa no gira.
                        cancelViewAnimation()
                        val newZoom = (zoom * gestureZoom).coerceIn(MapFitZoom, MapMaxZoom)
                        val containerCenter = Offset(size.width / 2f, size.height / 2f)
                        var newOffset = offset
                        if (newZoom != zoom) {
                            // Zoom anclado al punto bajo el gesto, con el centro de la imagen
                            // como origen de la transformación (igual que en graphicsLayer).
                            newOffset = centroid - containerCenter -
                                (centroid - containerCenter - newOffset) * (newZoom / zoom)
                        }
                        newOffset += pan
                        zoom = newZoom
                        offset = clampOffset(newOffset)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = mapPainter,
                contentDescription = stringResource(R.string.map_kanto_desc),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(with(density) { DpSize(fitWidthPx.toDp(), fitHeightPx.toDp()) })
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = offset.x
                        translationY = offset.y
                    }
            )

            // Indicador de zona(s): fijo en la esquina superior derecha del
            // área visible, sin seguir al mapa, con márgenes de zona segura.
            // Solo se compone con una selección; el nombre resuelto (catálogo
            // o propio) puede ser `null` si el enlace de catálogo está roto,
            // y entonces esa línea enseña su id técnico (último recurso,
            // informativo, nunca rompe la pantalla).
            val locations = kantoMap?.locations.orEmpty()
            selection?.let { selected ->
                // Márgenes de zona segura: el `Scaffold` (`AppRoot`) ya
                // descuenta las barras del sistema de esta área, así que aquí
                // solo se compensa el recorte de pantalla (que aquel no
                // desuenta) y se añade el respiro fijo del diseño; sin
                // duplicar márgenes. La anchura queda limitada a una fracción
                // del contenedor, adaptativa a cualquier dispositivo, y la
                // altura crece con las líneas coexistentes sin desbordarse
                // (el cuadro envuelve su contenido).
                val cutout = WindowInsets.displayCutout.asPaddingValues()
                val layoutDirection = LocalLayoutDirection.current
                val badgeMaxWidth = with(density) {
                    containerSize.width.toDp() * MapBadgeMaxWidthFraction
                }
                MapRegionBadge(
                    primary = selected.primary.resolvedName(locations)
                        ?: selected.primary.id,
                    others = selected.secondary.mapNotNull { it.resolvedName(locations) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            top = cutout.calculateTopPadding() + MapBadgeEdgeMargin,
                            end = cutout.calculateEndPadding(layoutDirection) + MapBadgeEdgeMargin
                        )
                        .widthIn(max = badgeMaxWidth)
                )
            }

            // Botón discreto para restaurar la vista completa, solo visible si la vista
            // se ha movido o ampliado respecto al estado inicial. Vive en el área
            // visible: siempre queda justo encima de la franja del menú.
            if (canReset) {
                Icon(
                    imageVector = Icons.Filled.Replay,
                    contentDescription = stringResource(R.string.map_reset_view),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .clickable { resetView() }
                        .padding(8.dp)
                )
            }

            // Botón de desarrollo: abre el editor visual del mapa. Solo se
            // compone con la marca de depurado (`isMapEditorAvailable`); en
            // release no existe composición, la actividad que abrir no está
            // en el manifiesto y el clic no tendría a dónde ir. Va en la
            // esquina inferior izquierda para no pisar al de restaurar y,
            // siendo un hijo propio (consumidor), no alimenta los gestos del
            // mapa ni el indicador de zonas que hay debajo.
            if (context.isMapEditorAvailable()) {
                Icon(
                    imageVector = Icons.Filled.Build,
                    contentDescription = stringResource(R.string.map_open_dev_editor),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .clickable { context.openMapEditor() }
                        .padding(6.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MapScreenPreview() {
    MaterialTheme { MapScreen() }
}
