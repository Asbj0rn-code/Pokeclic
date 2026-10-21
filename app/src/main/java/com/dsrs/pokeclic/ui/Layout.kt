/**
 * Layout de anclaje porcentual usado por la pantalla principal.
 *
 * Reproduce con `Layout` de Compose las guías y el `verticalBias` de
 * ConstraintLayout (sin añadir dependencias), corrigiendo las guías con los
 * insets del sistema. Es infraestructura de presentación: no contiene lógica de
 * juego.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt



/**
 * Límites de la "zona de contenido", en porcentaje de la altura.
 * Equivalen a `app:layout_constraintGuide_percent` del XML.
 */
object ContentZoneDefaults {
    const val TOP = 0.10f
    const val BOTTOM = 0.90f

    /** La zona nunca sube más del 30% ni baja del 70%, pase lo que pase con los insets. */
    const val MAX_TOP = 0.30f
    const val MIN_BOTTOM = 0.70f
}

/**
 * Scope de [PercentageLayout].
 *
 * `bias` reproduce `app:layout_constraintVertical_bias` del XML:
 * 0f = pegado a la guía superior, 0.5f = centrado, 1f = pegado a la inferior.
 */
class PercentageLayoutScope internal constructor() {
    /** Coloca el elemento dentro de la zona de contenido con un sesgo vertical. */
    fun Modifier.anchorVertically(bias: Float = 0.5f): Modifier =
        this.then(AnchorData(verticalBias = bias))

    /** Coloca el elemento con un sesgo horizontal respecto a los bordes de la pantalla. */
    fun Modifier.anchorHorizontally(bias: Float = 0.5f): Modifier =
        this.then(AnchorData(horizontalBias = bias))

    /** Coloca el elemento en ambas dimensiones de una vez. */
    fun Modifier.anchor(verticalBias: Float = 0.5f, horizontalBias: Float = 0.5f): Modifier =
        this.then(AnchorData(verticalBias = verticalBias, horizontalBias = horizontalBias))
}

/**
 * Layout con anclaje porcentual, equivalente al uso de `Guideline` + `verticalBias`
 * de ConstraintLayout, pero sin depender de ninguna librería externa.
 *
 * Todos los hijos se colocan en porcentajes de la pantalla, así que la composición
 * es idéntica en cualquier tamaño de móvil. Las guías se corrigen con los insets del
 * sistema (barras y notch) para que el contenido nunca quede debajo de ellas.
 *
 * ```
 * PercentageLayout {
 *     Image(..., Modifier.size(180.dp).anchorVertically(bias = 0.05f))
 *     Text(..., Modifier.anchorVertically(bias = 0.55f))
 * }
 * ```
 *
 * @param topPercent guía superior inicial, en porcentaje de la altura.
 * @param bottomPercent guía inferior inicial, en porcentaje de la altura.
 * @param applyInsets corrige las guías con los insets del sistema.
 */
@Composable
fun PercentageLayout(
    modifier: Modifier = Modifier,
    topPercent: Float = ContentZoneDefaults.TOP,
    bottomPercent: Float = ContentZoneDefaults.BOTTOM,
    maxTopPercent: Float = ContentZoneDefaults.MAX_TOP,
    minBottomPercent: Float = ContentZoneDefaults.MIN_BOTTOM,
    applyInsets: Boolean = true,
    content: @Composable PercentageLayoutScope.() -> Unit
) {
    val density = LocalDensity.current
    val bars = WindowInsets.systemBars.asPaddingValues()
    val cutout = WindowInsets.displayCutout.asPaddingValues()

    val topInsetPx: Float
    val bottomInsetPx: Float
    if (applyInsets) {
        val topDp = maxOf(bars.calculateTopPadding(), cutout.calculateTopPadding())
        topInsetPx = with(density) { topDp.toPx() }
        bottomInsetPx = with(density) { bars.calculateBottomPadding().toPx() }
    } else {
        topInsetPx = 0f
        bottomInsetPx = 0f
    }

    Layout(
        content = { PercentageLayoutScope().content() },
        modifier = modifier,
        measurePolicy = PercentageMeasurePolicy(
            topPercent = topPercent,
            bottomPercent = bottomPercent,
            maxTopPercent = maxTopPercent,
            minBottomPercent = minBottomPercent,
            topInsetPx = topInsetPx,
            bottomInsetPx = bottomInsetPx
        )
    )
}

private class PercentageMeasurePolicy(
    private val topPercent: Float,
    private val bottomPercent: Float,
    private val maxTopPercent: Float,
    private val minBottomPercent: Float,
    private val topInsetPx: Float,
    private val bottomInsetPx: Float
) : MeasurePolicy {

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureResult {
        val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }

        val width = constraints.maxWidth
        val height = constraints.maxHeight

        // Réplica de la lógica de adjustGuidelines() del Activity original.
        val topLimit = (height * topPercent + topInsetPx).coerceAtMost(height * maxTopPercent)
        val bottomLimit = (height * bottomPercent - bottomInsetPx)
            .coerceAtLeast(height * minBottomPercent)
        val zoneHeight = (bottomLimit - topLimit).coerceAtLeast(0f)

        return layout(width, height) {
            placeables.forEach { placeable ->
                val anchor = placeable.parentData as? AnchorData ?: AnchorData()
                val x = ((width - placeable.width) * anchor.horizontalBias).roundToInt()
                val y = (topLimit + (zoneHeight - placeable.height) * anchor.verticalBias).roundToInt()
                placeable.place(x, y)
            }
        }
    }
}

private data class AnchorData(
    val verticalBias: Float = 0.5f,
    val horizontalBias: Float = 0.5f
) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@AnchorData
}
