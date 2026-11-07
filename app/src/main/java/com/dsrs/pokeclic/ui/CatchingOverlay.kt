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
 * La piel imita la pantalla de captura de los juegos de Pokémon: campo de
 * batalla plano, columna de energía como riel, Pokébola como barra, medidor
 * clásico y cajas de diálogo GBA con borde doble y sombra dura.
 */
package com.dsrs.pokeclic.ui

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
import kotlin.math.abs
import kotlin.math.min as minFloat
import kotlin.random.Random

// ─────────────────────── Paleta de la escena ───────────────────────

private val SkyTop = Color(0xFF2B4C7E)
private val SkyBottom = Color(0xFF6A9FD4)
private val SunColor = Color(0xFFFFF0B8)
private val HillFar = Color(0xFF3C6E4A)
private val HorizonLine = Color(0xFF26405C)
private val GrassDark = Color(0xFF2F5E38)
private val GrassLight = Color(0xFF3E7644)
private val PlatformEdge = Color(0xFF24482A)
private val PlatformFill = Color(0xFF5C9A50)
private val GrassBlade = Color(0xFF6FB35C)

private val RailFill = Color(0x4422C8F0)
private val RailEdge = Color(0xFF8FE8FF)
private val RailTick = Color(0x88BFF4FF)
private val HaloGold = Color(0x44FFD75A)

private val PokeRed = Color(0xFFE8382F)
private val PokeWhite = Color(0xFFF8F8F8)
private val PokeInk = Color(0xFF202020)

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

    Box(
        modifier = modifier
            .fillMaxSize()
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
        CatchingBackdrop(Modifier.fillMaxSize())

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val railHeight = min(
                maxHeight * CatchingSceneDefaults.RAIL_HEIGHT_FRACTION,
                CatchingSceneDefaults.RAIL_MAX_HEIGHT
            )

            CatchingHud(
                encounter = encounter,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp, start = 16.dp, end = 16.dp)
            )

            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CaptureRail(
                    catching = catching,
                    encounter = encounter,
                    railHeight = railHeight,
                    modifier = Modifier
                        .width(CatchingSceneDefaults.RAIL_WIDTH)
                        .height(railHeight)
                )
                Spacer(Modifier.width(CatchingSceneDefaults.GAUGE_GAP))
                CaptureGauge(
                    progress = catching.progress,
                    modifier = Modifier
                        .width(CatchingSceneDefaults.GAUGE_WIDTH)
                        .height(railHeight)
                )
            }

            CatchingHint(
                encounter = encounter,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 22.dp, start = 16.dp, end = 16.dp)
            )

            if (finished) {
                CatchingResultPanel(
                    encounter = encounter,
                    captured = catching.phase == CatchingPhase.CAPTURED,
                    onContinue = onContinue,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

// ─────────────────────── Fondo de batalla ───────────────────────

/**
 * Campo de batalla plano: cielo en dos bandas, colinas lejanas, suelo y la
 * plataforma elíptica bajo el salvaje. Sin degradados difusos ni sombras
 * suaves: todo rectángulos y óvalos de color plano.
 */
@Composable
private fun CatchingBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.58f

        drawRect(color = SkyTop, size = Size(w, horizon * 0.55f))
        drawRect(
            color = SkyBottom,
            topLeft = Offset(0f, horizon * 0.55f),
            size = Size(w, horizon * 0.45f)
        )

        drawCircle(
            color = SunColor,
            radius = w * 0.055f,
            center = Offset(w * 0.80f, h * 0.15f)
        )

        val hills = Path().apply {
            moveTo(0f, horizon)
            lineTo(0f, horizon - h * 0.06f)
            lineTo(w * 0.22f, horizon - h * 0.13f)
            lineTo(w * 0.48f, horizon - h * 0.05f)
            lineTo(w * 0.72f, horizon - h * 0.12f)
            lineTo(w, horizon - h * 0.04f)
            lineTo(w, horizon)
            close()
        }
        drawPath(hills, color = HillFar)

        drawRect(
            color = HorizonLine,
            topLeft = Offset(0f, horizon),
            size = Size(w, 2.dp.toPx())
        )

        drawRect(color = GrassDark, topLeft = Offset(0f, horizon), size = Size(w, h - horizon))
        drawRect(
            color = GrassLight,
            topLeft = Offset(0f, horizon + (h - horizon) * 0.10f),
            size = Size(w, (h - horizon) * 0.90f)
        )

        val platformW = w * 0.66f
        val platformH = h * 0.085f
        val platformCenter = Offset(w * 0.5f, h * 0.80f)
        drawOval(
            color = PlatformEdge,
            topLeft = Offset(platformCenter.x - platformW / 2f, platformCenter.y - platformH / 2f),
            size = Size(platformW, platformH)
        )
        drawOval(
            color = PlatformFill,
            topLeft = Offset(platformCenter.x - platformW * 0.46f, platformCenter.y - platformH * 0.34f),
            size = Size(platformW * 0.92f, platformH * 0.68f)
        )

        // Matas de hierba en el borde inferior: rompen la banda plana.
        val blade = 10.dp.toPx()
        var x = w * 0.06f
        while (x < w * 0.94f) {
            val tall = if ((x / blade).toInt() % 2 == 0) blade else blade * 0.6f
            drawLine(
                color = GrassBlade,
                start = Offset(x, h - 6.dp.toPx()),
                end = Offset(x, h - 6.dp.toPx() - tall),
                strokeWidth = 2.dp.toPx()
            )
            x += blade * 2.2f
        }
    }
}

// ─────────────────────── Riel y Pokébola ───────────────────────

/**
 * Riel vertical: columna de energía translúcida con marcas, la Pokébola del
 * jugador (la barra de captura) y el salvaje. La Pokébola se pinta **detrás**
 * del sprite para que el Pokémon siga viéndose cuando ambos se solapan.
 */
@Composable
private fun CaptureRail(
    catching: CatchingState,
    encounter: Encounter,
    railHeight: Dp,
    modifier: Modifier = Modifier
) {
    val barDiameter = min(
        railHeight * catching.config.barSize,
        CatchingSceneDefaults.RAIL_WIDTH - 8.dp
    )
    val barCenterY = railHeight * (1f - catching.barPos)
    val spriteSize = CatchingSceneDefaults.SPRITE_SIZE
    val spriteCenterY = railHeight * (1f - catching.spritePos)
    val overlapping = abs(catching.spritePos - catching.barPos) <= catching.config.barSize / 2f

    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val radius = CornerRadius(18.dp.toPx())
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

        if (overlapping) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = spriteCenterY - spriteSize * 0.75f)
                    .size(spriteSize * 1.5f)
            ) {
                drawCircle(color = HaloGold, radius = size.minDimension / 2f)
            }
        }

        PokeBall(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = barCenterY - barDiameter / 2)
                .size(barDiameter)
        )

        WildSpriteBox(
            dexNumber = encounter.speciesDexNumber,
            name = encounter.speciesName,
            type = encounter.speciesType,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = spriteCenterY - spriteSize / 2)
                .size(spriteSize)
        )
    }
}

/** Pokébola pixel art: dos mitades, banda negra y botón central. */
@Composable
private fun PokeBall(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val radius = minFloat(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(color = Color(0x33FFD75A), radius = radius * 1.12f, center = center)

        val circle = Path().apply { addOval(Rect(center = center, radius = radius)) }
        clipPath(circle) {
            drawRect(
                color = PokeWhite,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f)
            )
            drawRect(
                color = PokeRed,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius)
            )
            drawRect(
                color = PokeInk,
                topLeft = Offset(center.x - radius, center.y - radius * 0.14f),
                size = Size(radius * 2f, radius * 0.28f)
            )
        }

        drawCircle(
            color = PokeInk,
            radius = radius,
            center = center,
            style = Stroke(2.dp.toPx())
        )
        drawCircle(color = PokeWhite, radius = radius * 0.30f, center = center)
        drawCircle(
            color = PokeInk,
            radius = radius * 0.30f,
            center = center,
            style = Stroke(2.dp.toPx())
        )
        drawCircle(color = PokeInk, radius = radius * 0.12f, center = center)
    }
}

/**
 * Sprite del salvaje: el PNG de `Pokedex/` que corresponde a su número de la
 * Pokédex nacional. Si el número no tiene sprite, dibuja el mismo placeholder
 * que el gimnasio.
 */
@Composable
private fun WildSpriteBox(
    dexNumber: Int,
    name: String,
    type: PokemonType,
    modifier: Modifier = Modifier
) {
    val sprite = pokedexSprite(dexNumber)
    if (sprite != null) {
        PixelImage(
            res = sprite,
            contentDescription = name,
            modifier = modifier
        )
    } else {
        GymSpritePlaceholder(name = name, type = type, modifier = modifier)
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
            color = RetroColors.LcdText,
            fontSize = 7.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false
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
            PokeBall(modifier = Modifier.size(40.dp))
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
    val RAIL_WIDTH = 104.dp

    /** Alto del riel como fracción de la pantalla, con tope para tablets. */
    val RAIL_HEIGHT_FRACTION = 0.52f
    val RAIL_MAX_HEIGHT = 420.dp

    /** Tamaño visual del sprite dentro del riel. */
    val SPRITE_SIZE = 96.dp

    /** Grosor y hueco del medidor de progreso. */
    val GAUGE_WIDTH = 34.dp
    val GAUGE_GAP = 16.dp
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
    return CatchingGame.initialState(config, Random(42)).copy(progress = progress, phase = phase)
}

private val previewEncounter = Encounter(
    speciesId = "rattata",
    speciesName = "Rattata",
    speciesType = PokemonType.NORMAL,
    speciesDexNumber = 19
)

@Preview(
    name = "Catching · Partida en curso",
    device = CATCHING_PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun CatchingOverlayPreview() {
    MaterialTheme {
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
    MaterialTheme {
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
    MaterialTheme {
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
