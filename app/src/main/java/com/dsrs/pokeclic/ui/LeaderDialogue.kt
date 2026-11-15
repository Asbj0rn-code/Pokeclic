/**
 * Piezas compartidas por los diálogos del líder.
 *
 * La entrada al gimnasio, el discurso de furia y la despedida tienen el mismo
 * esqueleto: retrato arriba, caja con máquina de escribir debajo y toque para
 * completar la línea y luego avanzar. Cada pantalla solo aporta sus líneas y, si
 * le hace falta, una capa extra (la medalla) o un pie (los botones).
 */
package com.dsrs.pokeclic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dsrs.pokeclic.R
import kotlinx.coroutines.delay

/** Fracción del ancho de pantalla que ocupa el retrato del líder. */
private const val PORTRAIT_WIDTH_FRACTION = 0.72f

/** Tope de ancho del retrato, para que no crezca sin límite en pantallas grandes. */
private val PORTRAIT_MAX_WIDTH = 320.dp

/** Tope de alto, como fracción de la pantalla, para que siempre quede sitio al texto. */
private const val PORTRAIT_HEIGHT_FRACTION = 0.55f

/** Margen superior del retrato. */
private val PORTRAIT_TOP_PADDING = 24.dp

/** Cada cuánto se turna el retrato con su segunda cara (la boca), en ms. */
private const val PORTRAIT_FLAP_MS = 140L

/**
 * Ancho del retrato de un líder dentro de un área: acotado por ancho **y** por
 * alto, para que el diálogo y los botones siempre entren aunque la pantalla sea
 * baja.
 *
 * @param aspect relación ancho/alto de la imagen.
 */
private fun leaderPortraitSize(maxWidth: Dp, maxHeight: Dp, aspect: Float): Dp = minOf(
    maxWidth * PORTRAIT_WIDTH_FRACTION,
    PORTRAIT_MAX_WIDTH,
    maxHeight * PORTRAIT_HEIGHT_FRACTION * aspect
)

/** Relación ancho/alto de un painter; `1f` si no la declara. */
private fun aspectOf(painter: Painter): Float {
    val size = painter.intrinsicSize
    return if (size.height > 0f) size.width / size.height else 1f
}

/**
 * Marco de diálogo del líder: retrato, caja y máquina de escribir.
 *
 * @param portraitRes retrato que se muestra mientras se escribe la línea.
 * @param altPortraitRes segunda cara que se turna con [portraitRes] mientras la
 *   línea se escribe (la boca abierta y cerrada). `null` deja el retrato quieto.
 * @param step índice de la línea; reinicia la máquina de escribir aunque dos
 *   líneas seguidas tengan el mismo texto (los «...» de Brock).
 * @param text texto completo de la línea.
 * @param onAdvance toque con la línea ya completa. El llamante decide si pasa a
 *   la siguiente, cierra el diálogo o no hace nada (la última de la entrada la
 *   cierran sus botones).
 * @param portraitModifier gancho para animar el retrato (el bamboleo del grito).
 * @param overlay capa sobre el retrato, alineada en el `Box` (la medalla).
 * @param footer pie bajo la caja, con la línea ya completa (los botones).
 */
@Composable
internal fun LeaderDialogue(
    portraitRes: Int,
    leaderName: String?,
    step: Int,
    text: String,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
    altPortraitRes: Int? = null,
    portraitModifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
    footer: @Composable ColumnScope.(lineComplete: Boolean) -> Unit = {}
) {
    // Máquina de escribir: se reinicia en cada línea. Un toque la completa.
    var revealed by remember { mutableIntStateOf(0) }
    LaunchedEffect(step, text) {
        revealed = 0
        while (revealed < text.length) {
            delay(TYPE_CHAR_MS)
            revealed++
        }
    }
    val lineComplete = revealed >= text.length
    val shown = text.take(revealed)

    // Boca: mientras la línea se está escribiendo, el retrato se turna con su
    // segunda cara. Al completarse se queda la de siempre, quieta.
    var flapTick by remember { mutableIntStateOf(0) }
    val flapping = altPortraitRes != null && !lineComplete
    LaunchedEffect(step, altPortraitRes, flapping) {
        flapTick = 0
        if (!flapping) return@LaunchedEffect
        while (true) {
            delay(PORTRAIT_FLAP_MS)
            flapTick++
        }
    }
    val shownPortraitRes =
        if (flapping && flapTick % 2 == 1) altPortraitRes!! else portraitRes

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // El tamaño sale del retrato base: las dos caras miden lo mismo y así el
        // parpadeo no puede mover el encuadre.
        val painter = painterResource(portraitRes)
        val aspect = aspectOf(painter)
        val portraitWidth = leaderPortraitSize(maxWidth, maxHeight, aspect)
        val portraitHeight = portraitWidth / aspect

        Image(
            painter = painterResource(shownPortraitRes),
            contentDescription = stringResource(R.string.gym_leader_desc, leaderName ?: "?"),
            contentScale = ContentScale.Fit,
            modifier = portraitModifier
                .align(Alignment.TopCenter)
                .padding(top = PORTRAIT_TOP_PADDING)
                .size(portraitWidth, portraitHeight)
        )

        overlay()

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(portraitHeight + PORTRAIT_TOP_PADDING))

            DialogueBox(
                text = shown,
                complete = lineComplete,
                onClick = {
                    when (introTap(lineComplete)) {
                        IntroTap.REVEAL_LINE -> revealed = text.length
                        IntroTap.ADVANCE -> onAdvance()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            footer(lineComplete)
        }
    }
}
