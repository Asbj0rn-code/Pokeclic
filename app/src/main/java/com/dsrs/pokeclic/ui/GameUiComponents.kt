/**
 * Componentes de interacción compartidos por la UI: la zona táctil con rebote
 * elástico y la moneda dibujada en Compose. El resto del "skinning" vive en
 * [RetroTheme].
 *
 * Son piezas 100 % de presentación: no conocen `GameUiState` ni el ViewModel.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Paleta compartida: dorado para las monedas y trazo oscuro de bordes.
 */
object GameColors {
    /** Dorado de monedas y contadores de recompensa. */
    val GoldTop = Color(0xFFFFE38A)
    val GoldBottom = Color(0xFFFFA726)

    /** Trazo oscuro de bordes y sombras. */
    val EdgeDark = Color(0xFF071426)
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

/**
 * Botón táctil con hundimiento físico: al pulsar, la cara del botón se
 * desplaza hasta cubrir la sombra dura, como si se clavara en el chasis.
 * El desplazamiento se anima con un muelle rígido y se lee en la fase de
 * dibujo, así que la pulsación no recompone el resto del árbol.
 *
 * Componente compartido de la UI retro: vive acá (junto al resto del skin) y
 * no en `MainScreen.kt`, para que cualquier pantalla lo reutilice sin
 * duplicarlo. El HUD conserva idéntica apariencia y comportamiento.
 */
@Composable
internal fun RetroButton(
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
