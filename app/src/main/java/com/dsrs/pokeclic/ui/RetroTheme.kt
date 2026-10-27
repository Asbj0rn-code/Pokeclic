/**
 * Tokens visuales y primitivas retro compartidas entre pantallas: chasis de
 * Pokédex, cajas de diálogo GBA y LCDs. Colores planos saturados y bordes
 * duros: nada de degradados diagonales ni sombras difusas.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tokens del "skinning" retro: chasis Pokédex, cajas de diálogo GBA/NDS,
 * pantallas LCD y barras de estado clásicas.
 */
internal object RetroColors {
    // Chasis / hardware
    val PokedexRed = Color(0xFFDC0A2D)
    val PokedexDarkRed = Color(0xFF8B001D)
    val SteelPanel = Color(0xFF303840)
    val SteelEdge = Color(0xFF14181D)
    val SteelBevel = Color(0xFF55636F)

    // Cajas de diálogo GBA/NDS
    val DialogCream = Color(0xFFF8F8F8)
    val DialogBorderInner = Color(0xFFD0D0C8)
    val DialogBorderOuter = Color(0xFF383838)
    val HardShadow = Color(0xFF181820)
    val InkBlack = Color(0xFF202020)

    // Pantallas LCD
    val LcdDark = Color(0xFF182830)
    val LcdText = Color(0xFFD8F0C8)

    // Barras de estado clásicas
    val BarTrack = Color(0xFF0B141C)
    val HpGreen = Color(0xFF58D080)
    val HpYellow = Color(0xFFF8E038)
    val HpRed = Color(0xFFF85838)
    val ExpBlue = Color(0xFF40C8F8)

    // Acciones
    val ButtonBlue = Color(0xFF2A66C8)
    val ButtonBlueEdge = Color(0xFF173059)
    val ResetBase = Color(0xFF39516B)
    val ResetEdge = Color(0xFF1B2A3A)

    // Destello de recompensa
    val GoldInk = Color(0xFFFFC94A)
}

/**
 * Esquinas 45° de los marcos: cortes rectos, tal como exige el manifiesto.
 */
internal val PanelShape = CutCornerShape(10.dp)
internal val PlateShape = CutCornerShape(6.dp)
internal val CellShape = CutCornerShape(4.dp)
internal val ButtonShape = CutCornerShape(6.dp)

/**
 * Marco biselado reutilizable: doble borde (exterior oscuro + filete claro),
 * sombra dura desplazada y esquinas cortadas a 45°. Reemplaza a `Card`.
 *
 * [fill] pinta el núcleo al tamaño completo del marco (paneles LCD); si es
 * `false`, el núcleo envuelve su contenido (cajas de texto y placas).
 */
@Composable
internal fun RetroPanel(
    modifier: Modifier = Modifier,
    backgroundColor: Color = RetroColors.DialogCream,
    outerBorder: Color = RetroColors.DialogBorderOuter,
    innerBorder: Color = RetroColors.DialogBorderInner,
    shadowColor: Color = RetroColors.HardShadow,
    shape: Shape = PlateShape,
    borderWidth: Dp = 2.dp,
    innerGap: Dp = 1.dp,
    shadowOffset: Dp = 2.dp,
    fill: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .padding(bottom = shadowOffset, end = shadowOffset)
            .drawBehind {
                // Sombra dura: rectángulo sólido desplazado, sin difuminar.
                val off = shadowOffset.toPx()
                if (off > 0f) {
                    drawRect(
                        color = shadowColor,
                        topLeft = Offset(off, off),
                        size = Size(size.width, size.height)
                    )
                }
            }
    ) {
        Box(modifier = Modifier.clip(shape).background(outerBorder)) {
            Box(modifier = Modifier.padding(borderWidth).clip(shape).background(innerBorder)) {
                Box(modifier = Modifier.padding(innerGap).clip(shape).background(backgroundColor)) {
                    Box(
                        modifier = Modifier
                            .padding(contentPadding)
                            .then(if (fill) Modifier.fillMaxSize() else Modifier),
                        content = content
                    )
                }
            }
        }
    }
}

/**
 * Pantalla LCD compartida por HUD y vitrinas del menú: fondo profundo (o la
 * cinta roja del chasis si [selected]), doble borde hondo y líneas de barrido
 * por encima del contenido. Con [fill] la pantalla cubre todo el marco.
 */
@Composable
internal fun RetroLcdPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    fill: Boolean = false,
    shadowOffset: Dp = 2.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
    content: @Composable BoxScope.() -> Unit
) {
    RetroPanel(
        modifier = modifier,
        backgroundColor = if (selected) RetroColors.PokedexRed else RetroColors.LcdDark,
        outerBorder = if (selected) RetroColors.PokedexDarkRed else RetroColors.DialogBorderOuter,
        innerBorder = if (selected) Color(0xFFE8ACB2) else Color(0xFF4E5A66),
        shape = CellShape,
        borderWidth = 2.dp,
        innerGap = 1.dp,
        shadowOffset = shadowOffset,
        fill = fill,
        contentPadding = contentPadding,
        content = {
            content()
            Box(modifier = Modifier.matchParentSize().lcdScanlines())
        }
    )
}

/**
 * Rejilla de barrido sutil sobre paneles LCD: líneas horizontales cada
 * [step], dibujadas en `Canvas`, sin filtros.
 */
internal fun Modifier.lcdScanlines(step: Dp = 3.dp, color: Color = Color(0x14000000)): Modifier =
    this.drawBehind {
        val gap = step.toPx()
        val line = 1.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawRect(color = color, topLeft = Offset(0f, y), size = Size(size.width, line))
            y += gap
        }
    }

/**
 * Sprite pixel art sin reescalado bilineal: obligatorio `FilterQuality.None`.
 */
@Composable
internal fun PixelImage(
    res: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val bitmap = ImageBitmap.imageResource(res)
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        filterQuality = FilterQuality.None
    )
}
