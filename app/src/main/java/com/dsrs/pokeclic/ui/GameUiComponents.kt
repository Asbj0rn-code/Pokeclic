/**
 * Componentes "gaming" reutilizables de la UI: panel de cristal, barra de
 * progreso estilo HP, botones con rebote táctil (sin ripple) y chips de
 * estadística, con la paleta de colores en [GameColors].
 *
 * Son piezas 100 % de presentación: no conocen `GameUiState` ni el ViewModel;
 * los eventos salen como lambdas y los valores que cambian a menudo (progreso,
 * escala de pulsación) se leen de forma diferida en la fase de dibujo.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Paleta del "skinning" gaming: navy profundo para paneles, dorado para las
 * monedas, azul eléctrico para títulos y rojo vivo para acciones de riesgo.
 */
object GameColors {
    /** Superficie de los paneles (con alfa aplicado al usarlos). */
    val PanelTop = Color(0xFF163A61)
    val PanelBottom = Color(0xFF0A1E38)

    /** Bordes y filetes sutiles. */
    val PanelBorder = Color(0xFF2A5A97)
    val EdgeDark = Color(0xFF071426)

    /** Fondo base translúcido para chips y pastillas. */
    val NavyDeep = Color(0xFF08182E)

    /** Dorado de monedas y contadores de recompensa. */
    val GoldTop = Color(0xFFFFE38A)
    val GoldBottom = Color(0xFFFFA726)

    /** Azul eléctrico de titulares de zona. */
    val ElectricTop = Color(0xFF7EE6FF)
    val ElectricBottom = Color(0xFF1E88E5)

    /** Degradados de botones (azul, navy y rojo de gimnasio). */
    val ButtonBlue = listOf(Color(0xFF3D9BE9), Color(0xFF1565C0))
    val ButtonNavy = listOf(Color(0xFF29527E), Color(0xFF142A4A))
    val ButtonRed = listOf(Color(0xFFFF5A5F), Color(0xFFB31217))

    /** Vía y borde de las barras de progreso. */
    val Track = Color(0xFF0A1B2E)
    val BarBorder = Color(0xFF06121F)
}

/**
 * Zona táctil con rebote elástico y **sin ripple** de Material.
 *
 * Al pulsar encoge el contenido con un muelle rápido y, al soltar, rebota a su
 * tamaño con `DampingRatioMediumBouncy`. La escala se anima con un `Animatable`
 * que solo se lee dentro de `graphicsLayer`: la pulsación **no recompone**
 * nada, solo invalida la capa de dibujo (64/120 FPS seguros en clics masivos).
 *
 * @param scaleDown factor de escala al estar pulsado (`1f` desactiva el efecto).
 */
fun Modifier.bouncyClickable(
    scaleDown: Float = 0.9f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> scope.launch {
                    scale.animateTo(
                        targetValue = scaleDown,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    )
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> scope.launch {
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
            }
        }
    }

    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }.clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}

/**
 * Panel inmersivo de HUD: tarjeta navy con degradado vertical, alfa sutil,
 * borde azul eléctrico y sombra suave. Agrupa el contenido en [Column].
 */
@Composable
fun GamePanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .shadow(elevation = 5.dp, shape = shape, clip = false)
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GameColors.PanelTop.copy(alpha = 0.86f),
                        GameColors.PanelBottom.copy(alpha = 0.94f)
                    )
                )
            )
            .border(width = 1.5.dp, color = GameColors.PanelBorder, shape = shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/**
 * Colores de fondo de la barra según la fracción de vida: verde >50 %,
 * amarillo 20–50 % y rojo <20 %, como la barra HP de los juegos originales.
 */
private fun hpFillColors(fraction: Float): List<Color> = when {
    fraction > 0.5f -> listOf(Color(0xFFA8E063), Color(0xFF388E3C))
    fraction > 0.2f -> listOf(Color(0xFFFFE16D), Color(0xFFF5A623))
    else -> listOf(Color(0xFFFF6E6E), Color(0xFFD32F2F))
}

/**
 * Barra de progreso estilo HP: pista navy oscura con borde marcado, pastilla
 * redondeada y relleno de degradado con brillo superior.
 *
 * El progreso se pasa como lambda para permitir la **lectura diferida** en la
 * fase de dibujo: si se apoya en un `State<Float>`, la animación no recomponen
 * ni relanzan el layout, solo pero redibujan el `Canvas`.
 *
 * @param progress proveedor del progreso, de `0f` a `1f`; se evalúa al dibujar.
 */
@Composable
fun GameProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    barHeight: Dp = 20.dp
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .height(barHeight)
            .clip(shape)
            .background(color = GameColors.Track)
            .border(width = 1.5.dp, color = GameColors.BarBorder, shape = shape)
            .padding(2.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val fraction = progress().coerceIn(0f, 1f)
            if (fraction <= 0f) return@Canvas

            val fillSize = Size(width = size.width * fraction, height = size.height)
            val fillRadius = CornerRadius(size.height / 2f, size.height / 2f)

            drawRoundRect(
                brush = Brush.horizontalGradient(colors = hpFillColors(fraction)),
                size = fillSize,
                cornerRadius = fillRadius
            )

            // Brillo superior tipo "cristal" sobre el relleno.
            val shineWidth = fillSize.width - size.height * 0.5f
            if (shineWidth > 0f) {
                val shineHeight = size.height * 0.3f
                drawRoundRect(
                    color = Color(0x4DFFFFFF),
                    topLeft = Offset(x = size.height * 0.25f, y = size.height * 0.16f),
                    size = Size(width = shineWidth, height = shineHeight),
                    cornerRadius = CornerRadius(shineHeight / 2f, shineHeight / 2f)
                )
            }
        }
    }
}

/**
 * Botón de acción con degradado, borde oscuro marcado y rebote elástico al
 * pulsar (sin ripple). [bouncyClickable] encoge el degradado con el contenido
 * al pulsar.
 *
 * @param container colores del degradado vertical (arriba → abajo).
 */
@Composable
fun GameButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: List<Color> = GameColors.ButtonBlue,
    labelColor: Color = Color.White,
    fontSize: TextUnit = 14.sp,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(percent = 50)
    Text(
        text = label,
        color = labelColor,
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.8.sp,
        maxLines = 1,
        modifier = modifier
            .bouncyClickable(enabled = enabled, onClick = onClick)
            .shadow(elevation = 4.dp, shape = shape, clip = false)
            .clip(shape)
            .background(brush = Brush.verticalGradient(colors = container))
            .border(width = 2.dp, color = GameColors.EdgeDark, shape = shape)
            .padding(horizontal = 22.dp, vertical = 11.dp)
    )
}

/**
 * Botón secundario "fantasma": fondo translúcido del color de acento con borde
 * fino, para acciones secundarias como el borrado de prueba de la partida.
 */
@Composable
fun GameGhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFFB71C1C),
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(percent = 50)
    Text(
        text = label,
        color = accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        maxLines = 1,
        modifier = modifier
            .bouncyClickable(enabled = enabled, onClick = onClick)
            .clip(shape)
            .background(color = accent.copy(alpha = 0.12f))
            .border(width = 1.5.dp, color = accent.copy(alpha = 0.55f), shape = shape)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * Chip de dato rápido (entrenador, posesión, potencia…): pastilla navy
 * translúcida con texto claro.
 */
@Composable
fun StatChip(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(percent = 50)
    Text(
        text = text,
        color = Color(0xFFDCE7F5),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(color = GameColors.NavyDeep.copy(alpha = 0.55f))
            .border(width = 1.dp, color = GameColors.PanelBorder, shape = shape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/**
 * Moneda dorada dibujada en Compose (sin arte): disco con degradado radial,
 * borde oscuro y reflejo blanco. Se usa junto al contador de monedas.
 */
@Composable
fun CoinDisc(modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(GameColors.GoldTop, GameColors.GoldBottom)
                )
            )
            .border(width = 2.dp, color = GameColors.EdgeDark, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size * 0.3f)
                .clip(CircleShape)
                .background(color = Color(0x55FFFFFF))
        )
    }
}

// ─────────────────────────── Previews ───────────────────────────

/** Vista de los componentes en columna sobre un fondo saturado. */
@Preview(
    name = "Componentes gaming",
    device = "spec:width=360dp,height=800dp,dpi=420",
    showBackground = true,
    backgroundColor = 0xFF2E7D32
)
@Composable
private fun GameComponentsPreview() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GamePanel {
            Text(text = "Ruta 1", color = Color.White, fontSize = 20.sp)
            GameProgressBar(
                progress = { 0.42f },
                modifier = Modifier.fillMaxWidth()
            )
            GameButton(
                label = "Gimnasio: Brock",
                container = GameColors.ButtonRed,
                onClick = {}
            )
            GameGhostButton(
                label = "Borrar partida (test)",
                onClick = {}
            )
        }
        StatChip(text = "Entrenador: Sergio")
        CoinDisc()
    }
}
