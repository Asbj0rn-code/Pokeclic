/**
 * Pantalla principal (clicker) y su HUD, con estética diegética de consola:
 * chasis biutils biselado con sombra dura, marcas estilo Pokédex, paneles LCD
 * con líneas de barrido y barras cuadradas de EXP/HP.
 *
 * La pantalla es **sin estado**: recibe `GameUiState` y lambdas. El HUD calcula
 * solo presentación (fracciones ya derivadas por el ViewModel). Los únicos
 * estados extra son **efímeros de UI**: pops flotantes y fases de pulsación
 * declarados dentro de los composables.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.PokemonType
import com.dsrs.pokeclic.viewmodel.GameUiState
import kotlin.math.min
import kotlin.random.Random

/**
 * Botón táctil con hundimiento físico: al pulsar, la cara del botón se
 * desplaza hasta cubrir la sombra dura, como si se clavara en el chasis.
 * El desplazamiento se anima con un muelle rígido y se lee en la fase de
 * dibujo, así que la pulsación no recompone el resto del árbol.
 */
@Composable
private fun RetroButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    base: Color = RetroColors.ButtonBlue,
    edge: Color = RetroColors.ButtonBlueEdge,
    fontSize: TextUnit = 13.sp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sink by animateDpAsState(
        targetValue = if (pressed) 3.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "buttonSink"
    )

    Box(
        modifier = modifier
            .padding(bottom = 3.dp, end = 3.dp)
            .drawBehind {
                val off = 3.dp.toPx()
                drawRect(color = RetroColors.HardShadow, topLeft = Offset(off, off), size = Size(size.width, size.height))
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        // Cara del botón: bisel oscuro + núcleo plano + brillo superior plano.
        Box(
            modifier = Modifier
                .offset(x = sink, y = sink)
                .clip(ButtonShape)
                .background(edge)
        ) {
            Box(modifier = Modifier.padding(2.dp).clip(ButtonShape).background(base)) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = fontSize,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp,
                            maxLines = 1,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = RetroColors.HardShadow,
                                    offset = Offset(0f, 1.4f),
                                    blurRadius = 0f
                                )
                            )
                        )
                    }
                    // Filete de luz superior 2.dp: cara "plástica" del botón.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Color(0x3BFFFFFF))
                    )
                }
            }
        }
    }
}

/**
 * Barra cuadrada estilo consola: marco negro grueso, riel oscuro, relleno
 * plano y brillo de cristal de 2.dp a lo ancho. **Nunca**
 * `LinearProgressIndicator`; esquinas rectas, sin circular pastillas.
 *
 * El progreso se anima dentro (suma fracción) y se lee en el `Canvas`, de
 * modo que los cambios de fracción solo invalidan el dibujo.
 */
@Composable
private fun RetroBar(
    label: String,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    barHeight: Dp = 16.dp,
    tint: Color = RetroColors.ExpBlue,
    overlay: (@Composable BoxScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // La animación vive a nivel de composable; el `Canvas` solo la lee en la
        // fase de dibujo, de modo que el progreso no recompone el resto del HUD.
        val animatedFraction by animateFloatAsState(
            targetValue = progress(),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "barFill"
        )
        Text(
            text = label,
            color = RetroColors.HpYellow,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            style = TextStyle(
                shadow = Shadow(
                    color = RetroColors.HardShadow,
                    offset = Offset(0f, 1.4f),
                    blurRadius = 0f
                )
            ),
            modifier = Modifier.padding(end = 2.dp)
        )
        Box(modifier = Modifier.weight(1f).height(barHeight)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 2.dp.toPx()

                // Marco exterior negro grueso.
                drawRect(
                    color = RetroColors.HardShadow,
                    size = Size(size.width, size.height)
                )
                // Riel oscuro interior.
                drawRect(
                    color = RetroColors.BarTrack,
                    topLeft = Offset(stroke, stroke),
                    size = Size(
                        size.width - stroke * 2f,
                        size.height - stroke * 2f
                    )
                )
                val innerWidth = size.width - stroke * 2f - 2f
                val innerHeight = size.height - stroke * 2f - 2f
                if (innerWidth > 0f && innerHeight > 0f) {
                    val fillWidth = innerWidth * animatedFraction.coerceIn(0f, 1f)
                    if (fillWidth > 0f) {
                        drawRect(
                            color = tint,
                            topLeft = Offset(stroke + 1f, stroke + 1f),
                            size = Size(fillWidth, innerHeight)
                        )
                    }
                    // Brillo de "tubo de cristal" a lo ancho de la barra.
                    drawRect(
                        color = Color(0x59FFFFFF),
                        topLeft = Offset(stroke + 1f, stroke + 1f),
                        size = Size(innerWidth, min(2.dp.toPx(), innerHeight / 3f))
                    )
                }
            }
            overlay?.let { overlayFn -> Box(modifier = Modifier.matchParentSize(), content = overlayFn) }
        }
    }
}

/**
 * Pausas flotantes de recompensa ("+N"): suben 48.dp y se desvanecen en
 * 640 ms. Estado efímero puro de UI, no toca ningún `UiState`.
 */
private data class RetroPop(
    val id: Long,
    val text: String,
    val tint: Color,
    val xJitter: Dp,
    val yJitter: Dp
)

/**
 * Capa superior de pops flotantes: cada "+N" se autoelimina al terminar su
 * subida (`onRemove`), así la lista no crece entre clics.
 */
@Composable
private fun PopLayer(
    pops: List<RetroPop>,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
    baseAlignment: Alignment = Alignment.Center
) {
    Box(modifier = modifier) {
        pops.forEach { pop ->
            key(pop.id) {
                RetroFloatingPop(
                    pop = pop,
                    onRemove = onRemove,
                    modifier = Modifier.align(baseAlignment)
                )
            }
        }
    }
}

@Composable
private fun RetroFloatingPop(
    pop: RetroPop,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(pop.id) {
        rise.snapTo(0f)
        rise.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 640, easing = FastOutSlowInEasing)
        )
        onRemove(pop.id)
    }
    Text(
        text = pop.text,
        color = pop.tint,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 15.sp,
        style = TextStyle(
            shadow = Shadow(
                color = RetroColors.HardShadow,
                offset = Offset(0f, 2f),
                blurRadius = 0f
            )
        ),
        modifier = modifier
            .offset(x = pop.xJitter, y = pop.yJitter)
            .graphicsLayer {
                translationY = -rise.value * 48.dp.toPx()
                alpha = 1f - rise.value
                val scalePop = 1.12f - 0.12f * rise.value
                scaleX = scalePop
                scaleY = scalePop
            }
    )
}

/**
 * Panel biselado pequeño dentro del HUD (entrenador, coins y similares).
 */
@Composable
private fun PlateCell(
    modifier: Modifier = Modifier,
    backgroundColor: Color = RetroColors.DialogCream,
    fontSize: TextUnit = 11.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    contentPadding: PaddingValues = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
    content: @Composable () -> Unit
) {
    RetroPanel(
        modifier = modifier,
        backgroundColor = backgroundColor,
        shape = PlateShape,
        borderWidth = 2.dp,
        innerGap = 1.dp,
        shadowOffset = 2.dp,
        contentPadding = contentPadding,
        content = { content() }
    )
}

/**
 * Placa de monedas: pide pulso al cambiar el número, clásico idle juice.
 */
@Composable
private fun CoinPlate(coins: Long, modifier: Modifier = Modifier) {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(coins) {
        pulse.snapTo(1.16f)
        pulse.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
    PlateCell(
        modifier = modifier.graphicsLayer {
            scaleX = pulse.value
            scaleY = pulse.value
        },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            CoinDisc(size = 15.dp)
            Text(
                text = stringResource(R.string.hud_coins, coins),
                color = RetroColors.InkBlack,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
    }
}

// ─────────────────────────── Pantalla ───────────────────────────

/**
 * Ajustes de posición de la pantalla. Todos los valores `bias` van de 0f (arriba)
 * a 1f (abajo) dentro de la zona de contenido, que desde la remodelación del HUD
 * abarca **toda** el área de la pantalla principal —el HUD flota encima—: así el
 * mundo no cambia de sitio cuando el panel crece o encoge.
 *
 * La Pokéball **no** se posiciona aquí: vive en el bottom menu.
 */
object MainLayout {
    val TRAINER_SIZE = 80.dp

    /**
     * Trainer algo por debajo del centro de la zona. Reproduce la altura a la
     * que caminaba con el HUD antiguo (mucho más alto): antes anclaba a 0f del
     * hueco que quedaba bajo el panel, y al compactarlo ese hueco creció y el
     * sprite subió casi hasta la barra de estado, desajustado del fondo de ruta.
     * Anclar a toda el área lo deja inmune a la altura del HUD.
     */
    const val TRAINER_BIAS = 0.54f

    /**
     * Botón de hierba alta: esquina inferior derecha de la zona, sobre la zona
     * flotante de la Pokébola.
     */
    val TALL_GRASS_SIZE = 80.dp
    const val TALL_GRASS_BIAS = 0.95f
    const val TALL_GRASS_H_BIAS = 0.9f

    /**
     * Capas de hierba alta que se superponen al trainer.
     * Ambas se centran sobre la misma posición que el trainer.
     */
    val TALL_GRASS_FULL_SIZE = 80.dp
    val TALL_GRASS_MID_SIZE = 80.dp

    /**
     * Desplazamiento de las capas de hierba respecto al trainer.
     * Positivo lo mueve a la derecha (X) o hacia abajo (Y); negativo, al revés.
     * Afecta a las dos capas a la vez para que no se separen entre sí.
     */
    val TALL_GRASS_X_OFFSET = 2.dp
    val TALL_GRASS_Y_OFFSET = 30.dp

    /** Margen del HUD respecto al borde superior de la zona de contenido. */
    val HUD_PADDING = 16.dp
}

/**
 * Pantalla principal: el mundo (entrenador y hierba alta) ancla a **toda** el
 * área de contenido y el HUD flota encima como capa superior.
 *
 * Antes el mundo se anclaba al hueco que dejaba el HUD debajo suyo, así que
 * cualquier cambio de altura del panel (como la remodelación compacta)
 * desplazaba al entrenador y lo desajustaba del camino del fondo de ruta. Con
 * el HUD superpuesto, el mundo es inmune a su altura.
 *
 * A cada clic de zona (toque de la Pokéball) responde con números de ganancia
 * flotantes (+potencia) que suben y se desvanecen. Es el **único** feedback: el
 * flash blanco de pantalla y la sacudida del sprite se retiraron porque el
 * parpadeo resultaba molesto.
 *
 * Es un composable **sin estado propio**: recibe el [GameUiState] ya calculado y
 * reenvía las interacciones como lambdas. No conoce el ViewModel.
 */
@Composable
fun MainScreen(
    state: GameUiState,
    onTallGrassToggle: () -> Unit,
    onEnterGym: () -> Unit,
    onAdvanceZone: () -> Unit,
    modifier: Modifier = Modifier
) {
    // "Juice" efímero: cada incremento real de zoneClicks dispara pop flotante.
    var prevClicks by remember { mutableLongStateOf(state.zoneClicks) }
    var lcdPops by remember { mutableStateOf(emptyList<RetroPop>()) }
    var nextPopId by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.zoneClicks) {
        val delta = state.zoneClicks - prevClicks
        prevClicks = state.zoneClicks
        if (delta > 0L) {
            lcdPops = lcdPops + RetroPop(
                id = nextPopId++,
                text = "+$delta",
                tint = RetroColors.DialogCream,
                xJitter = Random.nextInt(-64, 64).dp,
                yJitter = Random.nextInt(-14, 44).dp
            )
        }
    }
    val removeLcdPop: (Long) -> Unit = { id -> lcdPops = lcdPops.filterNot { it.id == id } }

    // El mundo ancla a toda el área de contenido (no al hueco bajo el HUD) y el
    // HUD flota encima: cambiar la altura del panel ya no mueve al entrenador.
    Box(modifier = modifier.fillMaxSize()) {
        PercentageLayout(
            modifier = Modifier.matchParentSize(),
            applyInsets = false
        ) {
            TrainerGrassStack(
                trainerFrameRes = state.trainerFrameRes,
                showTallGrass = state.isTallGrass,
                modifier = Modifier.anchorVertically(MainLayout.TRAINER_BIAS)
            )

            // Solo las zonas con hierba alta (rutas, bosque, cueva) pueden
            // entrar: las ciudades no tienen fauna y la hierba se oculta.
            if (state.hasWilds) {
                TallGrassButton(
                    onClick = onTallGrassToggle,
                    modifier = Modifier
                        .size(MainLayout.TALL_GRASS_SIZE)
                        .anchor(
                            verticalBias = MainLayout.TALL_GRASS_BIAS,
                            horizontalBias = MainLayout.TALL_GRASS_H_BIAS
                        )
                )
            }
        }

        PopLayer(
            pops = lcdPops,
            onRemove = removeLcdPop,
            modifier = Modifier.matchParentSize(),
            // Reproduce la altura de aparición que tenían con el HUD antiguo
            // (~70% de la zona): debajo del entrenador, no sobre él.
            baseAlignment = BiasAlignment(verticalBias = 0.42f, horizontalBias = 0f)
        )

        // Capa superior: HUD y, pegado debajo de su barra de clics, el CTA de
        // progresión. Ambos flotan sobre el mundo sin moverlo (el mundo ancla a
        // toda el área de contenido).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MainLayout.HUD_PADDING)
        ) {
            GameHud(
                state = state,
                modifier = Modifier.fillMaxWidth()
            )

            // Fuera del panel para que aparecer o desaparecer no cambie su
            // altura; el pequeño hueco de arriba lo separa del cartel de zona.
            ProgressionAction(
                isGymReady = state.isGymReady,
                canAdvanceZone = state.canAdvanceZone,
                gymLeaderName = state.gymLeaderName.orEmpty(),
                onEnterGym = onEnterGym,
                onAdvanceZone = onAdvanceZone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}

/**
 * CTA de progresión de la run: entrar al gimnasio o avanzar de zona.
 *
 * Es **un solo hueco** porque las dos acciones son excluyentes: el avance exige,
 * en zonas con gimnasio, haberlo ganado en la run. Se compone fuera del HUD para
 * que aparecer o desaparecer no cambie la altura del panel: flota justo debajo
 * de su barra de clics, sobre el fondo de la zona.
 */
@Composable
private fun ProgressionAction(
    isGymReady: Boolean,
    canAdvanceZone: Boolean,
    gymLeaderName: String,
    onEnterGym: () -> Unit,
    onAdvanceZone: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        isGymReady -> RetroButton(
            label = stringResource(R.string.action_gym, gymLeaderName),
            base = RetroColors.PokedexRed,
            edge = RetroColors.PokedexDarkRed,
            onClick = onEnterGym,
            modifier = modifier
        )

        canAdvanceZone -> RetroButton(
            label = stringResource(R.string.action_advance),
            onClick = onAdvanceZone,
            modifier = modifier
        )
    }
}

/**
 * Grupo trainer + hierba alta.
 *
 * Las tres imágenes se apilan en un mismo [Box], así que comparten **exactamente**
 * la misma posición: basta con cambiar el sesgo del grupo para moverlas todas.
 * El orden de dibujo en un `Box` es de fondo a frente. Todos los pasajes pasan
 * por `PixelImage` (`FilterQuality.None`) para preservar el pixel art.
 */
@Composable
private fun TrainerGrassStack(
    trainerFrameRes: Int,
    showTallGrass: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Capa 1 · fondo: hierba alta completa
        if (showTallGrass) {
            TallGrassLayer(
                drawableRes = R.drawable.tall_grass_full,
                size = MainLayout.TALL_GRASS_FULL_SIZE,
                offsetX = MainLayout.TALL_GRASS_X_OFFSET,
                offsetY = MainLayout.TALL_GRASS_Y_OFFSET
            )
        }

        // Capa 2 · medio: trainer
        PixelImage(
            res = trainerFrameRes,
            contentDescription = stringResource(R.string.trainer_desc),
            modifier = Modifier.size(MainLayout.TRAINER_SIZE)
        )

        // Capa 3 · frente: hierba alta parcial
        if (showTallGrass) {
            TallGrassLayer(
                drawableRes = R.drawable.tall_grass_mid,
                size = MainLayout.TALL_GRASS_MID_SIZE,
                offsetX = MainLayout.TALL_GRASS_X_OFFSET,
                offsetY = MainLayout.TALL_GRASS_Y_OFFSET
            )
        }
    }
}

@Composable
private fun TallGrassLayer(drawableRes: Int, size: Dp, offsetX: Dp = 0.dp, offsetY: Dp = 0.dp) {
    PixelImage(
        res = drawableRes,
        contentDescription = stringResource(R.string.tall_grass_desc),
        modifier = Modifier
            .size(size)
            .offset(x = offsetX, y = offsetY)
    )
}

/**
 * Botón de hierba alta, situado en la esquina inferior derecha de la zona, sobre
 * la zona flotante de la Pokébola.
 * Entra o sale del modo hierba alta (que también muestra/oculta las capas).
 *
 * Usa `anchor(...)` con los dos sesgos en una sola llamada: encadenar
 * `anchorVertically` + `anchorHorizontally` crearía dos datos de anclaje y solo
 * se aplicaría el último.
 *
 * El toque responde con **squash & stretch** asimétrico a 45° de muelle
 * (sin ripple) para dar game feel al área de interacción.
 */
@Composable
private fun TallGrassButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squashX by animateFloatAsState(
        targetValue = if (pressed) 1.14f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "grassSquashX"
    )
    val squashY by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "grassSquashY"
    )
    PixelImage(
        res = R.drawable.tall_grass_button,
        contentDescription = stringResource(R.string.tall_grass_desc),
        modifier = modifier
            .graphicsLayer {
                scaleX = squashX
                scaleY = squashY
                transformOrigin = TransformOrigin(0.5f, 0.7f)
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    )
}

// ─────────────────────────── Previews ───────────────────────────

/**
 * Especificación de dispositivo de los previews. Coincide **a propósito** con el
 * AVD de pruebas (`Medium_Phone`: 1080×2400 px @ 420 dpi ≈ 411×914 dp), para que
 * lo que se ve en el preview y en el emulador sea lo mismo.
 */
private const val PREVIEW_DEVICE = "spec:width=411dp,height=914dp,dpi=420"

/**
 * Estado de ejemplo realista para los previews. **No** vale el estado por defecto
 * (`GameUiState()`): con zona vacía, sin monedas ni principal, el HUD sale corto y
 * el preview engaña (parece que no se solapa cuando en el dispositivo sí).
 *
 * Los valores imitan una partida de principios de Ruta 1.
 */
private fun previewGameState(isTallGrass: Boolean = false) = GameUiState(
    playerName = "Sergio",
    coins = 40L,
    seenSpecies = setOf("bulbasaur", "rattata", "pidgey"),
    rewardedSpecies = setOf("bulbasaur"),
    medals = emptySet(),
    starterSpeciesId = "charmander",
    starterBond = 1,
    ownedSpecies = setOf("rattata", "pidgey"),
    leadSpeciesId = "bulbasaur",
    zoneIndex = 1,
    zoneClicks = 4L,
    isTallGrass = isTallGrass,
    trainerFrameRes = R.drawable.front0,
    zoneName = "Ruta 1",
    zoneRequirement = 20L,
    ownedCount = 3,
    clickPower = 3L,
    maxHp = 5,
    leadName = "Bulbasaur",
    leadType = PokemonType.GRASS,
    isGymReady = false,
    canAdvanceZone = false,
    needsStarter = false
)

/**
 * Montaje real de la pantalla principal para los previews: el [Scaffold] con el
 * [PokemonBottomMenu] reserva el mismo hueco que en la app (`CONTAINER_HEIGHT`),
 * que es justo lo que faltaba en los previews antiguos y hacía que el entrenador
 * saliera en otro sitio. Fondo de chasis oscuro para encajar con el diseño.
 */
@Composable
private fun MainScreenStage(state: GameUiState) {
    Scaffold(
        containerColor = Color(0xFF14181D),
        bottomBar = { PokemonBottomMenu(isHomeSelected = true) }
    ) { innerPadding ->
        MainScreen(
            state = state,
            onTallGrassToggle = {},
            onEnterGym = {},
            onAdvanceZone = {},
            modifier = Modifier.padding(innerPadding)
        )
    }
}

/** Juego en reposo: HUD completo, entrenador y botón de hierba. */
@Preview(
    name = "1 · Juego",
    device = PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun MainScreenPreview() = MaterialTheme {
    MainScreenStage(state = previewGameState())
}

/** Modo hierba alta: capas de hierba sobre el entrenador y barra de búsqueda. */
@Preview(
    name = "2 · Hierba alta",
    device = PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun MainScreenTallGrassPreview() = MaterialTheme {
    MainScreenStage(state = previewGameState(isTallGrass = true))
}

// ─────────────────────────── HUD ───────────────────────────

/**
 * HUD compacto de la pantalla principal con estética de consola portátil:
 * chasis de acero biselado con sombra dura y solo dos líneas de contenido —
 * cartel de zona y monedas en una fila, barra EXP cuadrada con el contador de
 * clics superpuesto debajo. Entrenador, principal, estadísticas y los botones
 * de reset y cambio de principal se retiraron a la hoja desplegable de la
 * Pokédex (ver `OptionsSheet`); los CTA de gimnasio y avance **no** viven aquí
 * (ver `ProgressionAction`), para que su aparición no cambie la altura del
 * panel.
 *
 * Cada vez que ganan monedas (p. ej. tras una captura), un número dorado
 * flotante aparece junto a la placa de monedas y se desvanece.
 *
 * Es **sin estado**: recibe el [GameUiState] ya calculado.
 */
@Composable
fun GameHud(
    state: GameUiState,
    modifier: Modifier = Modifier
) {
    var coinPops by remember { mutableStateOf(emptyList<RetroPop>()) }
    var nextPopId by remember { mutableLongStateOf(0L) }
    var prevCoins by remember { mutableLongStateOf(state.coins) }

    LaunchedEffect(state.coins) {
        val delta = state.coins - prevCoins
        prevCoins = state.coins
        if (delta > 0L) {
            coinPops = coinPops + RetroPop(
                id = nextPopId++,
                text = "+$delta",
                tint = RetroColors.GoldInk,
                // Ancla TopEnd: negativo hacia la izquierda, positivo hacia abajo.
                xJitter = -Random.nextInt(16, 104).dp,
                yJitter = Random.nextInt(2, 36).dp
            )
        }
    }
    val removeCoinPop: (Long) -> Unit = { id -> coinPops = coinPops.filterNot { it.id == id } }

    Box(modifier = modifier.fillMaxWidth()) {
        RetroPanel(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = RetroColors.SteelPanel,
            outerBorder = RetroColors.SteelEdge,
            innerBorder = RetroColors.SteelBevel,
            shape = PanelShape,
            borderWidth = 3.dp,
            innerGap = 1.dp,
            shadowOffset = 4.dp,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                // Cartel de zona a la izquierda y monedas a la derecha: la única
                // fila de datos del HUD compacto.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Placa crema con texto negro, estilo letrero de ruta.
                    PlateCell { Text(
                        text = state.zoneName,
                        color = RetroColors.InkBlack,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        maxLines = 1
                    ) }

                    CoinPlate(coins = state.coins)
                }

                // Progreso de la zona: barra EXP cuadrada con el contador superpuesto.
                RetroBar(
                    label = "EXP",
                    progress = { state.zoneProgress },
                    tint = RetroColors.ExpBlue,
                    modifier = Modifier.fillMaxWidth(),
                    overlay = {
                        Text(
                            text = stringResource(
                                R.string.hud_zone_progress,
                                state.zoneClicks,
                                state.zoneRequirement
                            ),
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.align(Alignment.Center),
                            style = TextStyle(
                                shadow = Shadow(
                                    color = RetroColors.HardShadow,
                                    offset = Offset(0f, 1.2f),
                                    blurRadius = 0f
                                )
                            )
                        )
                    }
                )

                // El CTA de progresión (gimnasio / avanzar) **no** vive aquí: ver
                // `ProgressionAction`. Si estuviera en esta columna, su aparición
                // cambiaría la altura del HUD y empujaría al entrenador.
            }
        }

        // Recompensas flotantes junto a la placa de monedas (esquina superior derecha).
        PopLayer(
            pops = coinPops,
            onRemove = removeCoinPop,
            modifier = Modifier.matchParentSize(),
            baseAlignment = Alignment.TopEnd
        )
    }
}
