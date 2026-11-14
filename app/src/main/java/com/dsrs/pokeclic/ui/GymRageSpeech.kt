/**
 * Discurso de furia del líder, a mitad del combate.
 *
 * Cuando la vida del líder cruza el umbral de furia, el combate queda **en pausa**
 * (lo gobierna `GymState.rageSpeech`) y se muestra esta capa: el retrato enfadado
 * y dos líneas de diálogo. La segunda es el grito a su Pokémon, en mayúsculas y
 * con el retrato bamboleándose de lado a lado. Al cerrarse, el combate retoma ya
 * en fase de furia.
 *
 * Reutiliza el mismo lenguaje que la intro y la entrada al gimnasio: [DialogueBox],
 * máquina de escribir ([TYPE_CHAR_MS]) y toque para completar y avanzar
 * ([introTap]).
 */
package com.dsrs.pokeclic.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dsrs.pokeclic.R
import kotlinx.coroutines.delay

/** Amplitud del bamboleo lateral del retrato durante el grito, a cada lado. */
private val RAGE_SWAY_AMPLITUDE = 5.dp

/** Duración de cada medio bamboleo, en ms: lento, para que se lea como amenaza. */
private const val RAGE_SWAY_MS = 420

/** Una línea del discurso: [shout] la marca como grito (mayúsculas y bamboleo). */
private data class RageLine(@param:StringRes val textRes: Int, val shout: Boolean)

private val rageLines = listOf(
    RageLine(R.string.gym_rage_speech_warning, shout = false),
    RageLine(R.string.gym_rage_speech_shout, shout = true)
)

/**
 * Capa del discurso de furia. Sustituye la escena entera: el ViewModel tiene el
 * combate detenido, así que aquí no se pinta HUD, sprites ni barras.
 *
 * @param onFinished se invoca al cerrar la última línea; el combate retoma solo.
 */
@Composable
internal fun GymRageSpeech(
    leaderName: String?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val line = rageLines[stepIndex.coerceIn(rageLines.indices)]
    val fullText = stringResource(line.textRes)

    // Máquina de escribir: se reinicia en cada línea; un toque la completa.
    var revealed by remember { mutableIntStateOf(0) }
    LaunchedEffect(stepIndex, fullText) {
        revealed = 0
        while (revealed < fullText.length) {
            delay(TYPE_CHAR_MS)
            revealed++
        }
    }
    val lineComplete = revealed >= fullText.length
    val shown = fullText.take(revealed)
    val isLast = stepIndex == rageLines.lastIndex

    // Bamboleo de lado a lado, solo mientras le grita a su Pokémon: el retrato se
    // corre a izquierda y derecha, sin girarse.
    val sway = rememberInfiniteTransition(label = "rageSway")
    val swayFactor by sway.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = RAGE_SWAY_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rageSwayFactor"
    )
    val swayPx = with(LocalDensity.current) { RAGE_SWAY_AMPLITUDE.toPx() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val painter = painterResource(R.drawable.brock_angry)
        val intrinsic = painter.intrinsicSize
        val aspect = if (intrinsic.height > 0f) intrinsic.width / intrinsic.height else 1f
        val portraitWidth = leaderPortraitSize(maxWidth, maxHeight, aspect)
        val portraitHeight = portraitWidth / aspect

        Image(
            painter = painter,
            contentDescription = stringResource(R.string.gym_leader_desc, leaderName ?: "?"),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = PORTRAIT_TOP_PADDING)
                .size(portraitWidth, portraitHeight)
                .graphicsLayer { translationX = if (line.shout) swayFactor * swayPx else 0f }
        )

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
                        IntroTap.REVEAL_LINE -> revealed = fullText.length
                        IntroTap.ADVANCE -> if (isLast) onFinished() else stepIndex++
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
