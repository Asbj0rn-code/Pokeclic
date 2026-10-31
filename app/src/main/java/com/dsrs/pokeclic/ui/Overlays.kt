/**
 * Capas modales y pantallas auxiliares del shell: selector de Pokémon
 * principal, hoja de opciones, pantalla del mapa y su fondo de océano.
 *
 * Todas son **sin estado**: reciben datos ya resueltos por el ViewModel y
 * notifican acciones con lambdas. En particular, el selector de principal recibe
 * `LeadOption` con id y nombre, así que no necesita conocer el catálogo.
 */
package com.dsrs.pokeclic.ui

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
import com.dsrs.pokeclic.model.LeadOption
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min



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
 * @param onWipeGame borra toda la partida (provisional: fila de test que vive
 *   aquí de momento, ver `MainViewModel.onWipeGame`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsSheet(
    onDismiss: () -> Unit,
    onWipeGame: () -> Unit
) {
    // Sin estado intermedio: se abre directamente al contenido, como hacía el
    // fragment con STATE_EXPANDED.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        ImmersiveModalWindow()
        OptionsContent(onWipeGame = onWipeGame)
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
 * Contenido de la hoja de opciones: título, separador y dos filas decorativas
 * sin acción asociada, más la fila provisional de borrado de partida (test),
 * que pide confirmación explícita antes de destruir la partida.
 */
@Composable
fun OptionsContent(onWipeGame: () -> Unit = {}) {
    // Confirmación del borrado: un toque accidental no debe poder destruir la
    // partida directamente.
    var showWipeConfirm by remember { mutableStateOf(false) }

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

        ListItem(
            headlineContent = { Text(stringResource(R.string.action_wipe)) },
            leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().clickable(onClick = { showWipeConfirm = true })
        )
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
 * Zoom que corresponde a la vista completa: el mapa entero encajado en pantalla.
 * Es el zoom mínimo y el estado al que se restaura la vista.
 */
private const val MapFitZoom = 1f

/**
 * Zoom máximo del mapa, relativo a la vista completa (el mapa entero encajado en
 * pantalla). El mínimo es la propia vista completa.
 */
private const val MapMaxZoom = 5f

/**
 * Zoom dado por el doble toque al partir de la vista completa.
 */
private const val MapDoubleTapZoom = 2f

/**
 * Duración de la animación al restaurar la vista completa del mapa.
 */
private const val MapResetAnimationMs = 300

/**
 * Duración de la animación del doble toque al ampliar a [MapDoubleTapZoom].
 */
private const val MapZoomToggleAnimationMs = 220

/**
 * Color de relleno detrás del mapa, muestreado del borde inferior del PNG
 * (océano de Kanto, RGB ≈ 1/133/219). Se emplea para las zonas libres del
 * contenedor cuando la relación de aspecto de la pantalla no coincide con la
 * del mapa, de modo que el océano parece continuar más allá de la imagen.
 */
private val MapOceanColor = Color(0xFF0185DB)

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
 * Escala (píxeles de pantalla por píxel de la imagen) con la que el mapa encaja
 * completo en el contenedor respetando su relación de aspecto. `imageSize` es el
 * tamaño intrínseco del pintor (nativo del recurso) y `containerSize` el tamaño
 * real del contenedor. Devuelve `null` si aún no se han podido medir.
 */
private fun computeMapFitScale(imageSize: Size, containerSize: IntSize, density: Density): Float? {
    if (containerSize.width <= 0 || containerSize.height <= 0) return null
    if (imageSize.width <= 0f || imageSize.height <= 0f) return null
    val imageWidthPx = with(density) { imageSize.width.dp.toPx() }
    val imageHeightPx = with(density) { imageSize.height.dp.toPx() }
    return min(
        containerSize.width / imageWidthPx,
        containerSize.height / imageHeightPx
    )
}

/**
 * Pantalla de mapa. Ocupa todo el espacio del contenedor (debajo del bottom menu).
 *
 * Fase 1: muestra `mapa_kanto.png` completo y centrado (vista fit), con zoom por
 * pellizco anclado al gesto, desplazamiento con un dedo limitado a los bordes del
 * mapa, doble toque que alterna entre vista completa y ampliar a
 * [MapDoubleTapZoom] anclado a la pulsación, y un botón discreto que restaura la
 * vista completa. La rotación del gesto se ignora a propósito: el mapa nunca debe
 * girar.
 *
 * Las zonas libres del contenedor (cuando el aspecto de la pantalla no coincide
 * con el del mapa) se pintan con [MapOceanColor], que coincide con el borde del
 * PNG, por lo que las uniones quedan invisibles sin ningún tratamiento adicional.
 *
 * Orden de detectores: el doble toque se registra **antes** del pellizco porque
 * `detectTransformGestures` consume el down y todo movimiento con slop; el
 * detector de toques debe ver los eventos antes de que se consuman. Así un
 * arrastre/pellizco (que consume cambios) cancela el toque, y un toque limpio no
 * dispara transformaciones. En una fase futura, las pulsaciones simples de zonas
 * interactivas irán en el mismo `detectTapGestures`: `onDoubleTap` ya evita que
 * un doble toque dispare `onTap` (Compose espera `doubleTapTimeoutMillis`).
 *
 * El zoom y el desplazamiento son estado local de Compose (transformaciones
 * puramente visuales, no forman parte de [com.dsrs.pokeclic.viewmodel.GameUiState]).
 *
 * La transformación usada aquí es la referencia para las futuras zonas
 * interactivas: un punto normalizado `(u, v)` de la imagen original se pinta en
 * pantalla en `centroContenedor + offset + zoom * ((u - 0.5) * fitWidthPx,
 * (v - 0.5) * fitHeightPx)`, donde los tamaños `fit` ya incluyen el escalado
 * inicial (fit) de la imagen.
 */
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val mapPainter = painterResource(id = R.drawable.mapa_kanto)
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

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
     * si en algún eje el mapa es más pequeño que la vista, ese eje queda centrado fijo.
     */
    fun clampOffset(candidate: Offset): Offset {
        val rangeX = max(0f, fitWidthPx * zoom / 2f - containerSize.width / 2f)
        val rangeY = max(0f, fitHeightPx * zoom / 2f - containerSize.height / 2f)
        return Offset(
            candidate.x.coerceIn(-rangeX, rangeX),
            candidate.y.coerceIn(-rangeY, rangeY)
        )
    }

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

    Box(
        modifier = modifier
            .fillMaxSize()
            // Fondo con el color del océano: rellena todas las zonas libres del
            // contenedor (recalculadas en cada medición, sin tamaños fijos) tanto
            // en la vista fit como al ampliar o desplazar.
            .background(MapOceanColor)
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            // El doble toque va antes del pellizco: detectTransformGestures consume
            // el down y los movimientos, y el detector de toques debe verlos antes.
            .pointerInput(fitWidthPx, fitHeightPx) {
                detectTapGestures(
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

        // Botón discreto para restaurar la vista completa, solo visible si la vista
        // se ha movido o ampliado respecto al estado inicial.
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
    }
}

@Preview(showBackground = true)
@Composable
private fun MapScreenPreview() {
    MaterialTheme { MapScreen() }
}
