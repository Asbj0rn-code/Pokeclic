/**
 * Discurso de furia del líder, a mitad del combate.
 *
 * Cuando la vida del líder cruza el umbral de furia, el combate queda **en pausa**
 * (lo gobierna `GymState.rageSpeech`) y se muestra esta capa: el retrato enfadado
 * y dos líneas de diálogo. La segunda es el grito a su Pokémon, en mayúsculas y
 * con el retrato bamboleándose de lado a lado. Al cerrarse, el combate retoma ya
 * en fase de furia.
 *
 * El marco (retrato, máquina de escribir y toque) lo aporta [LeaderDialogue].
 */
package com.dsrs.pokeclic.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dsrs.pokeclic.R

/** Amplitud del bamboleo lateral del retrato durante el grito, a cada lado. */
private val RAGE_SWAY_AMPLITUDE = 5.dp

/** Duración de cada medio bamboleo, en ms: lento, para que se lea como amenaza. */
private const val RAGE_SWAY_MS = 420

/** Una línea del discurso: [shout] la marca como grito (mayúsculas y bamboleo). */
private data class RageLine(@param:StringRes val textRes: Int, val shout: Boolean)

/**
 * Lo que un líder dice y con qué cara lo dice cuando se enfurece.
 *
 * @param portraitRes su cara de furia.
 * @param warningRes el aviso de que el combate se pone serio.
 * @param shoutRes el grito a su propio Pokémon.
 */
internal data class RageScript(
    @param:DrawableRes val portraitRes: Int,
    @param:StringRes val warningRes: Int,
    @param:StringRes val shoutRes: Int
)

private val rageScripts: Map<String, RageScript> = mapOf(
    "brock" to RageScript(
        portraitRes = R.drawable.brock_angry,
        warningRes = R.string.gym_rage_speech_warning,
        shoutRes = R.string.gym_rage_speech_shout
    ),
    "misty" to RageScript(
        portraitRes = R.drawable.misty_angry,
        warningRes = R.string.gym_rage_speech_misty_warning,
        shoutRes = R.string.gym_rage_speech_misty_shout
    )
)

/**
 * Furia de [gymId]. Los que aún no tienen retrato ni frases propias conservan los
 * de Brock: es lo que hacía este archivo antes de separarlo por gimnasio.
 */
internal fun rageScriptFor(gymId: String?): RageScript =
    gymId?.let { rageScripts[it] } ?: rageScripts.getValue("brock")

/**
 * Capa del discurso de furia. Sustituye la escena entera: el ViewModel tiene el
 * combate detenido, así que aquí no se pinta HUD, sprites ni barras.
 *
 * @param onFinished se invoca al cerrar la última línea; el combate retoma solo.
 */
@Composable
internal fun GymRageSpeech(
    gymId: String?,
    leaderName: String?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val script = rageScriptFor(gymId)
    val lines = listOf(
        RageLine(script.warningRes, shout = false),
        RageLine(script.shoutRes, shout = true)
    )
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val line = lines[stepIndex.coerceIn(lines.indices)]
    val isLast = stepIndex == lines.lastIndex

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

    LeaderDialogue(
        portraitRes = script.portraitRes,
        leaderName = leaderName,
        step = stepIndex,
        text = stringResource(line.textRes),
        onAdvance = { if (isLast) onFinished() else stepIndex++ },
        modifier = modifier,
        portraitModifier = Modifier.graphicsLayer {
            translationX = if (line.shout) swayFactor * swayPx else 0f
        }
    )
}
