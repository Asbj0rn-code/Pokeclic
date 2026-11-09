/**
 * Escena del minijuego de captura (Etapa 3): el salvaje se mueve por un riel
 * vertical, el jugador mantiene pulsado para subir su Pokébola y el medidor
 * lateral mide el progreso de la captura.
 *
 * Es full-screen takeover como el gimnasio: cuando hay partida, la escena
 * sustituye al contenido y al bottom menu. **Sin lógica de juego**: pinta el
 * `CatchingState` ya calculado por el ViewModel y le notifica la pulsación
 * (mantener/soltar) y la salida del panel de resultado.
 *
 * La piel imita la pantalla de captura de los juegos de Pokémon: la ruta actual
 * queda de fondo (la escena es transparente y `AppRoot` compone el fondo de la
 * zona detrás), columna de energía como riel, Pokébola como barra, medidor
 * clásico y cajas de diálogo GBA con borde doble y sombra dura.
 */
package com.dsrs.pokeclic.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.CatchingDefaults
import com.dsrs.pokeclic.model.CatchingGame
import com.dsrs.pokeclic.model.CatchingPhase
import com.dsrs.pokeclic.model.CatchingState
import com.dsrs.pokeclic.model.Encounter
import com.dsrs.pokeclic.model.PokemonType
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// ─────────────────────── Paleta de la escena ───────────────────────

private val RailBase = Color(0x99101A24)
private val RailFill = Color(0x4422C8F0)
private val RailEdge = Color(0xFF8FE8FF)
private val RailTick = Color(0x88BFF4FF)
private val HaloGold = Color(0x44FFD75A)

/**
 * Escena completa de la partida de captura.
 *
 * El gesto de pulsación se captura en **todo** el fondo (cualquier parte de la
 * pantalla sirve); el botón del panel de resultado convive con él sin problema
 * porque el ViewModel ignora `onHoldChanged` cuando la partida ya terminó.
 *
 * @param encounter especie en liza (nombre, tipo y número de Pokédex).
 * @param catching estado del minijuego (barra, sprite, progreso y fase).
 * @param onHoldChanged avisa al ViewModel de que la pantalla está pulsada.
 * @param onContinue cierra el panel de resultado.
 */
@Composable
fun CatchingOverlay(
    encounter: Encounter,
    catching: CatchingState,
    onHoldChanged: (Boolean) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val finished = catching.phase != CatchingPhase.PLAYING

    // El panel de resultado espera a que el efecto de pixelfx termine: si
    // apareciera en el mismo frame, taparía el burst justo cuando se ve.
    var showResult by remember { mutableStateOf(false) }
    LaunchedEffect(catching.phase) {
        showResult = false
        if (finished) {
            delay(CatchFx.RESULT_DELAY_MS)
            showResult = true
        }
    }

    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var railCenter by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInRoot() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onHoldChanged(true)
                        tryAwaitRelease()
                        onHoldChanged(false)
                    }
                )
            }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val railHeight = min(
                maxHeight * CatchingSceneDefaults.RAIL_HEIGHT_FRACTION,
                CatchingSceneDefaults.RAIL_MAX_HEIGHT
            )
            // El riel mide lo que mide el sprite de esta especie: primero se
            // recorta su padding transparente y se escala, y ese ancho manda.
            val spriteBitmap = pokedexSprite(encounter.speciesDexNumber)
                ?.let { ImageBitmap.imageResource(it) }
            val spriteBounds = remember(spriteBitmap) {
                spriteBitmap?.let { opaqueBounds(it) } ?: IntRect.Zero
            }
            val spriteScaleDp = railHeight.value *
                CatchingSceneDefaults.SPRITE_REFERENCE_FRACTION /
                CatchingSceneDefaults.SPRITE_REFERENCE_PX
            val railWidth = if (spriteBounds.width > 0) {
                (spriteBounds.width * spriteScaleDp).dp
            } else {
                railHeight * CatchingSceneDefaults.SPRITE_REFERENCE_FRACTION
            }
            val spriteSize = railWidth
            // La Pokébola es más ancha que el riel: el marco reserva a cada lado
            // lo que sobresale (más un margen) para que entre entera.
            val barDiameter = railHeight * catching.config.barSize
            val ballOverhang = ((barDiameter - railWidth) / 2f).coerceAtLeast(0.dp)
            val frameInset = maxOf(
                CatchingSceneDefaults.FRAME_MIN_INSET,
                ballOverhang + CatchingSceneDefaults.FRAME_BALL_MARGIN
            )

            // Lanzamiento: la Pokébola grande del menú vuela hasta el centro del
            // riel y se encoge. El ViewModel no mueve el riel hasta que termina.
            val throwProgress = remember(encounter.speciesId) { Animatable(0f) }
            LaunchedEffect(encounter.speciesId, railCenter) {
                if (railCenter == Offset.Zero) return@LaunchedEffect
                throwProgress.snapTo(0f)
                throwProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = CatchingDefaults.THROW_MS.toInt(),
                        easing = LinearEasing
                    )
                )
            }
            val throwing by remember(encounter.speciesId) {
                derivedStateOf { throwProgress.value < 1f }
            }
            val menuBall = ImageBitmap.imageResource(R.drawable.pokeball_menu)
            val density = LocalDensity.current
            val navBarPx = WindowInsets.navigationBars.getBottom(density)
            val throwStartBottom = BottomMenuDefaults.POKEBALL_EXPANDED_BOTTOM +
                BottomMenuDefaults.POKEBALL_EXPANDED_SIZE / 2f

            CatchingHud(
                encounter = encounter,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp, start = 16.dp, end = 16.dp)
            )

            // Riel y medidor viven dentro de un marco LCD, con el mismo doble
            // borde, esquinas cortadas y sombra dura que el resto de los menús.
            RetroPanel(
                modifier = Modifier.align(Alignment.Center),
                backgroundColor = RetroColors.LcdDark,
                outerBorder = RetroColors.DialogBorderOuter,
                innerBorder = Color(0xFF4E5A66),
                shadowOffset = 3.dp,
                shape = PanelShape,
                contentPadding = PaddingValues(horizontal = frameInset, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CaptureRail(
                        catching = catching,
                        encounter = encounter,
                        railHeight = railHeight,
                        spriteSize = spriteSize,
                        barDiameter = barDiameter,
                        spriteBitmap = spriteBitmap,
                        spriteBounds = spriteBounds,
                        showBall = !throwing,
                        modifier = Modifier
                            .width(railWidth)
                            .height(railHeight)
                            .onGloballyPositioned {
                                railCenter = it.boundsInRoot().center - rootOrigin
                            }
                    )
                    Spacer(Modifier.width(CatchingSceneDefaults.GAUGE_GAP))
                    CaptureGauge(
                        progress = catching.progress,
                        modifier = Modifier
                            .width(CatchingSceneDefaults.GAUGE_WIDTH)
                            .height(railHeight)
                    )
                }
            }

            CatchingHint(
                encounter = encounter,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 22.dp, start = 16.dp, end = 16.dp)
            )

            if (showResult) {
                CatchingResultPanel(
                    encounter = encounter,
                    captured = catching.phase == CatchingPhase.CAPTURED,
                    onContinue = onContinue,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // La bola del lanzamiento va por encima de todo: arranca donde está
            // la Pokébola expandida del menú y aterriza en el centro del riel.
            if (throwing) {
                Canvas(Modifier.fillMaxSize()) {
                    val t = throwProgress.value
                    val startX = size.width / 2f
                    val startY = size.height - throwStartBottom.toPx() + navBarPx
                    val endX = railCenter.x
                    val endY = railCenter.y
                    val dy = endY - startY
                    // Parábola: sale hacia arriba por encima del destino y cae en
                    // él. `rise` es el pico del arco sobre la recta de llegada.
                    val rise = -dy * 0.75f
                    val cx = startX + (endX - startX) * t
                    val cy = startY + dy * t - rise * sin(PI.toFloat() * t)
                    val startSize = BottomMenuDefaults.POKEBALL_EXPANDED_SIZE.toPx()
                    val endSize = barDiameter.toPx()
                    // Se empequeñece de a poco a lo largo de todo el vuelo.
                    val diameter = startSize + (endSize - startSize) * t
                    drawImage(
                        image = menuBall,
                        dstOffset = IntOffset(
                            (cx - diameter / 2f).toInt(),
                            (cy - diameter / 2f).toInt()
                        ),
                        dstSize = IntSize(diameter.toInt(), diameter.toInt()),
                        filterQuality = FilterQuality.None
                    )
                }
            }
        }
    }
}

// ─────────────────────── Riel y Pokébola ───────────────────────

/**
 * Riel vertical: columna de energía translúcida con marcas, la Pokébola del
 * jugador (que desborda el riel) y el salvaje, que ocupa el ancho del riel y se
 * pinta **delante** para que siempre se vea; la bola asoma por los costados.
 */
@Composable
private fun CaptureRail(
    catching: CatchingState,
    encounter: Encounter,
    railHeight: Dp,
    spriteSize: Dp,
    barDiameter: Dp,
    spriteBitmap: ImageBitmap?,
    spriteBounds: IntRect,
    showBall: Boolean,
    modifier: Modifier = Modifier
) {
    // La Pokébola no se recorta al ancho del riel: sobresale por los bordes.
    val barCenterY = railHeight * (1f - catching.barPos)
    val spriteCenterY = railHeight * (1f - catching.spritePos)
    val overlapping = abs(catching.spritePos - catching.barPos) <= catching.config.barSize / 2f

    // Rebote al tocar los extremos: un empujón visual corto hacia adentro con
    // una compresión leve. No toca la física, así que no hace vibrar la barra
    // cuando el jugador la sostiene contra el tope.
    val halfBar = catching.config.barSize / 2f
    val atTop = catching.barPos >= 1f - halfBar - 0.001f
    val atBottom = catching.barPos <= halfBar + 0.001f
    val atEdge = atTop || atBottom
    val recoil = remember { Animatable(0f) }
    LaunchedEffect(atEdge) {
        if (!atEdge) return@LaunchedEffect
        recoil.snapTo(1f)
        recoil.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = 220,
                easing = FastOutLinearInEasing
            )
        )
    }
    val recoilDirection = if (atTop) 1f else -1f

    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            // Esquinas proporcionales: el riel cambia de ancho con cada especie.
            val radius = CornerRadius(size.width * 0.22f)
            // Base oscura: el riel tiene que leerse sobre cualquier ruta.
            drawRoundRect(color = RailBase, cornerRadius = radius)
            drawRoundRect(color = RailFill, cornerRadius = radius)
            drawRoundRect(
                color = RailEdge,
                cornerRadius = radius,
                style = Stroke(2.dp.toPx())
            )
            val step = 14.dp.toPx()
            var y = step
            while (y < size.height - 4.dp.toPx()) {
                drawLine(
                    color = RailTick,
                    start = Offset(5.dp.toPx(), y),
                    end = Offset(size.width - 5.dp.toPx(), y),
                    strokeWidth = 1.dp.toPx()
                )
                y += step
            }
        }

        if (overlapping && showBall) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = spriteCenterY - spriteSize * 0.75f)
                    .requiredSize(spriteSize * 1.5f)
            ) {
                drawCircle(color = HaloGold, radius = size.minDimension / 2f)
            }
        }

        // Misma Pokébola que el menú; `requiredSize` la deja desbordar el riel.
        if (showBall) {
            PixelImage(
                res = R.drawable.pokeball_menu,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = barCenterY - barDiameter / 2)
                    .requiredSize(barDiameter)
                    .graphicsLayer {
                        val k = recoil.value
                        translationY = CatchingSceneDefaults.EDGE_RECOIL.toPx() * k * recoilDirection
                        scaleY = 1f - 0.10f * k
                        scaleX = 1f + 0.06f * k
                    }
            )
        }

        WildSpriteBox(
            bitmap = spriteBitmap,
            bounds = spriteBounds,
            name = encounter.speciesName,
            type = encounter.speciesType,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = spriteCenterY - spriteSize / 2)
                .size(spriteSize)
        )

        // Efecto de pixelfx al cerrar la partida: estrellas al capturar, humo
        // al escapar. Se dibuja sobre el sprite y desborda el riel a propósito.
        if (catching.phase != CatchingPhase.PLAYING) {
            val captured = catching.phase == CatchingPhase.CAPTURED
            val effectSize = if (captured) CatchFx.SUCCESS_SIZE else CatchFx.BREAK_SIZE
            SpriteSheetEffect(
                sheet = if (captured) R.drawable.fx_catch_success else R.drawable.fx_catch_break,
                frameCount = if (captured) CatchFx.SUCCESS_FRAMES else CatchFx.BREAK_FRAMES,
                frameSizePx = if (captured) CatchFx.SUCCESS_FRAME_PX else CatchFx.BREAK_FRAME_PX,
                columns = CatchFx.COLUMNS,
                fps = CatchFx.FPS,
                playKey = catching.phase,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = spriteCenterY - effectSize / 2)
                    .requiredSize(effectSize)
            )
        }
    }
}

/**
 * Reproduce una sola vez un sprite sheet exportado por pixelfx. [playKey]
 * reinicia la animación; al cambiar la fase de la partida vuelve a empezar.
 * Se dibuja con `FilterQuality.None` para no emborronar el pixel art.
 */
@Composable
private fun SpriteSheetEffect(
    @DrawableRes sheet: Int,
    frameCount: Int,
    frameSizePx: Int,
    columns: Int,
    fps: Int,
    playKey: Any,
    modifier: Modifier = Modifier
) {
    val bitmap = ImageBitmap.imageResource(sheet)
    val frame = remember(playKey) { Animatable(0f) }

    LaunchedEffect(playKey) {
        frame.snapTo(0f)
        frame.animateTo(
            targetValue = (frameCount - 1).toFloat(),
            animationSpec = tween(
                durationMillis = frameCount * 1000 / fps,
                easing = LinearEasing
            )
        )
    }

    Canvas(modifier) {
        val index = frame.value.toInt().coerceIn(0, frameCount - 1)
        drawImage(
            image = bitmap,
            srcOffset = IntOffset((index % columns) * frameSizePx, (index / columns) * frameSizePx),
            srcSize = IntSize(frameSizePx, frameSizePx),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.None
        )
    }
}

/**
 * Sprite del salvaje ya recortado a su contenido opaco. La caja mide
 * exactamente su ancho, que es el mismo que el del riel, así que el Pokémon lo
 * llena de lado a lado; el alto sale de la proporción de la especie. Si no hay
 * sprite, dibuja el mismo placeholder que el gimnasio.
 */
@Composable
private fun WildSpriteBox(
    bitmap: ImageBitmap?,
    bounds: IntRect,
    name: String,
    type: PokemonType,
    modifier: Modifier = Modifier
) {
    if (bitmap == null || bounds.width <= 0 || bounds.height <= 0) {
        GymSpritePlaceholder(name = name, type = type, modifier = modifier)
        return
    }

    Canvas(
        modifier = modifier.semantics { contentDescription = name }
    ) {
        val dstW = size.width
        val dstH = bounds.height * (dstW / bounds.width)
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(bounds.left, bounds.top),
            srcSize = IntSize(bounds.width, bounds.height),
            dstOffset = IntOffset(0, ((size.height - dstH) / 2f).toInt()),
            dstSize = IntSize(dstW.toInt(), dstH.toInt()),
            filterQuality = FilterQuality.None
        )
    }
}

/** Caja opaca del sprite: recorta el padding transparente de los PNG. */
private fun opaqueBounds(bitmap: ImageBitmap): IntRect {
    val source = bitmap.asAndroidBitmap()
    val w = source.width
    val h = source.height
    if (w <= 0 || h <= 0) return IntRect.Zero

    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)

    var left = w
    var top = h
    var right = -1
    var bottom = -1
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            if (pixels[row + x] ushr 24 != 0) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
    }
    return if (right < left || bottom < top) {
        IntRect.Zero
    } else {
        IntRect(left, top, right + 1, bottom + 1)
    }
}

// ─────────────────────── Medidor de captura ───────────────────────

/**
 * Medidor clásico: marco oscuro, relleno de abajo a arriba y brillo blanco
 * arriba. El color pasa a verde, amarillo o rojo según cuánto falte para
 * capturar; las marcas cada 10 % vienen de las barras de estado de GBA.
 */
@Composable
private fun CaptureGauge(progress: Float, modifier: Modifier = Modifier) {
    val fill = progress.coerceIn(0f, 1f)
    val fillColor = when {
        fill >= 0.5f -> RetroColors.HpGreen
        fill >= 0.2f -> RetroColors.HpYellow
        else -> RetroColors.HpRed
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.catching_gauge),
            color = Color.White,
            fontSize = 7.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                shadow = Shadow(
                    color = Color(0xCC000000),
                    offset = Offset(0f, 1f),
                    blurRadius = 0f
                )
            )
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(PlateShape)
                .background(RetroColors.DialogBorderOuter)
                .padding(2.dp)
                .clip(PlateShape)
                .background(RetroColors.BarTrack)
                .padding(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(fill)
                    .background(fillColor)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0x99FFFFFF))
                )
            }

            Canvas(Modifier.matchParentSize()) {
                val step = size.height / 10f
                var y = step
                while (y < size.height) {
                    drawLine(
                        color = Color(0x33000000),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    y += step
                }
            }
        }
    }
}

// ─────────────────────── Cajas de diálogo ───────────────────────

/** HUD superior: nombre del salvaje, número de Pokédex y tipo. */
@Composable
private fun CatchingHud(encounter: Encounter, modifier: Modifier = Modifier) {
    RetroPanel(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = RetroColors.DialogCream,
        outerBorder = RetroColors.DialogBorderOuter,
        innerBorder = RetroColors.DialogBorderInner,
        shadowOffset = 3.dp,
        shape = PlateShape,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.catch_title, encounter.speciesName),
                    color = RetroColors.InkBlack,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                DexBadge(encounter.speciesDexNumber)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(encounter.speciesType)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.catching_wild),
                    color = RetroColors.DialogBorderOuter,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

/** Instrucción inferior, con la flecha de avance parpadeando. */
@Composable
private fun CatchingHint(encounter: Encounter, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "catchingHintBlink")
    val arrowAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "catchingHintArrow"
    )

    RetroPanel(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = RetroColors.DialogCream,
        outerBorder = RetroColors.DialogBorderOuter,
        innerBorder = RetroColors.DialogBorderInner,
        shadowOffset = 3.dp,
        shape = PlateShape,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.catching_hint, encounter.speciesName),
                color = RetroColors.InkBlack,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "▼",
                color = RetroColors.PokedexRed.copy(alpha = arrowAlpha),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/** Panel de resultado: mensaje y botón para volver a la hierba. */
@Composable
private fun CatchingResultPanel(
    encounter: Encounter,
    captured: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    RetroPanel(
        modifier = modifier.fillMaxWidth(0.86f),
        backgroundColor = RetroColors.DialogCream,
        outerBorder = RetroColors.DialogBorderOuter,
        innerBorder = RetroColors.DialogBorderInner,
        shadowOffset = 4.dp,
        shape = PanelShape,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PixelImage(
                res = R.drawable.pokeball_menu,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = stringResource(
                    if (captured) R.string.catching_captured else R.string.catching_escaped,
                    encounter.speciesName
                ),
                color = RetroColors.InkBlack,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            CatchingButton(
                label = stringResource(R.string.catching_continue),
                onClick = onContinue
            )
        }
    }
}

/** Chip con el número de la Pokédex nacional. */
@Composable
private fun DexBadge(dexNumber: Int) {
    Box(
        modifier = Modifier
            .clip(CellShape)
            .background(RetroColors.SteelPanel)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = stringResource(R.string.catching_dex, dexNumber),
            color = RetroColors.GoldInk,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black
        )
    }
}

/** Chip con el color y el nombre del tipo elemental. */
@Composable
private fun TypeBadge(type: PokemonType) {
    Box(
        modifier = Modifier
            .clip(CellShape)
            .background(typeColor(type))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = type.name,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}

/** Botón retro que se hunde contra la sombra dura al pulsarlo. */
@Composable
private fun CatchingButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sink by animateDpAsState(
        targetValue = if (pressed) 3.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "catchingButtonSink"
    )

    Box(
        modifier = modifier
            .padding(bottom = 3.dp, end = 3.dp)
            .drawBehind {
                val off = 3.dp.toPx()
                drawRect(
                    color = RetroColors.HardShadow,
                    topLeft = Offset(off, off),
                    size = Size(size.width, size.height)
                )
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .offset(x = sink, y = sink)
                .clip(ButtonShape)
                .background(RetroColors.ButtonBlueEdge)
        ) {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .clip(ButtonShape)
                    .background(RetroColors.ButtonBlue)
            ) {
                Box(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────── Medidas ───────────────────────

/** Tamaños y posiciones fijas de la escena de captura. */
private object CatchingSceneDefaults {
    /**
     * Ancho de referencia como fracción del alto del riel: el sprite más ancho
     * del catálogo (56 px) mide esto y el riel se ajusta al ancho de cada
     * especie. Es también el ancho de respaldo cuando no hay sprite.
     */
    const val SPRITE_REFERENCE_FRACTION = 0.19f

    /** Alto del riel como fracción de la pantalla, con tope para tablets. */
    const val RAIL_HEIGHT_FRACTION = 0.52f
    val RAIL_MAX_HEIGHT = 420.dp

    /**
     * Ancho de contenido (en px del PNG) que se dibuja a todo el ancho del riel.
     * Es el sprite más ancho del catálogo (Geodude, 56 px): los demás quedan
     * proporcionalmente más chicos, respetando su tamaño relativo.
     */
    const val SPRITE_REFERENCE_PX = 56f

    /** Grosor y hueco del medidor de progreso. */
    val GAUGE_WIDTH = 34.dp
    val GAUGE_GAP = 28.dp

    /** Aire mínimo entre el riel/medidor y el borde interior del marco. */
    val FRAME_MIN_INSET = 14.dp

    /** Aire extra entre la Pokébola y el borde interior del marco. */
    val FRAME_BALL_MARGIN = 4.dp

    /** Cuánto se mete la Pokébola al rebotar contra un extremo del riel. */
    val EDGE_RECOIL = 6.dp
}

/** Metadatos de los sprite sheets exportados por pixelfx. */
private object CatchFx {
    const val COLUMNS = 6
    const val FPS = 30

    /** Margen para que el efecto se vea antes de que entre el panel. */
    const val RESULT_DELAY_MS = 900L

    const val SUCCESS_FRAMES = 28
    const val SUCCESS_FRAME_PX = 64
    val SUCCESS_SIZE = 128.dp

    const val BREAK_FRAMES = 30
    const val BREAK_FRAME_PX = 56
    val BREAK_SIZE = 112.dp
}

// ─────────────────────────── Previews ───────────────────────────

private const val CATCHING_PREVIEW_DEVICE = "spec:width=411dp,height=914dp,dpi=420"

/**
 * Estado de captura de ejemplo, determinista (semilla fija) para que el preview
 * no cambie de una compilación a otra.
 */
private fun previewCatchingState(
    captureDifficulty: Float,
    progress: Float = 0f,
    phase: CatchingPhase = CatchingPhase.PLAYING
): CatchingState {
    val config = CatchingDefaults.configFor(captureDifficulty)
    return CatchingGame.initialState(config).copy(progress = progress, phase = phase)
}

private val previewEncounter = Encounter(
    speciesId = "rattata",
    speciesName = "Rattata",
    speciesType = PokemonType.NORMAL,
    speciesDexNumber = 19
)

/** La escena es transparente: el preview le pone el fondo de ruta real detrás. */
@Composable
private fun CatchingPreview(content: @Composable () -> Unit) {
    MaterialTheme {
        Box(Modifier.fillMaxSize()) {
            PixelImage(
                res = R.drawable.background_ruta,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            content()
        }
    }
}

@Preview(
    name = "Catching · Partida en curso",
    device = CATCHING_PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun CatchingOverlayPreview() {
    CatchingPreview {
        CatchingOverlay(
            encounter = previewEncounter,
            catching = previewCatchingState(captureDifficulty = 0.15f, progress = 0.45f),
            onHoldChanged = {},
            onContinue = {}
        )
    }
}

@Preview(
    name = "Catching · Capturado",
    device = CATCHING_PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun CatchingOverlayCapturedPreview() {
    CatchingPreview {
        CatchingOverlay(
            encounter = previewEncounter,
            catching = previewCatchingState(
                captureDifficulty = 0.15f,
                progress = 1f,
                phase = CatchingPhase.CAPTURED
            ),
            onHoldChanged = {},
            onContinue = {}
        )
    }
}

@Preview(
    name = "Catching · Escapado",
    device = CATCHING_PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun CatchingOverlayEscapedPreview() {
    CatchingPreview {
        CatchingOverlay(
            encounter = previewEncounter,
            catching = previewCatchingState(
                captureDifficulty = 0.15f,
                progress = 0f,
                phase = CatchingPhase.ESCAPED
            ),
            onHoldChanged = {},
            onContinue = {}
        )
    }
}
