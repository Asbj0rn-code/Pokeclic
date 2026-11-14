/**
 * Despedida del líder al ganar el gimnasio.
 *
 * Cuando la vida del Pokémon del líder llega a cero el combate se detiene en
 * `GymOutcome.WON`: primero su Pokémon se hunde y se apaga (lo hace `GymOverlay`)
 * y después se muestra esta capa, con el retrato de la cara que toque en cada
 * línea, la despedida, y la medalla apareciendo en el centro cuando la entrega.
 *
 * Mismo lenguaje que el resto de diálogos: [DialogueBox], máquina de escribir
 * ([TYPE_CHAR_MS]) y toque para completar y avanzar ([introTap]).
 */
package com.dsrs.pokeclic.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dsrs.pokeclic.R
import kotlinx.coroutines.delay

/** Milisegundos que tarda el Pokémon del líder en hundirse y apagarse. */
internal const val VICTORY_FADE_MS = 650

/** Cuánto se hunde el Pokémon del líder al caer, en la escena. */
internal val VICTORY_ENEMY_SINK = 48.dp

/** Tamaño de la medalla cuando aparece en el centro de la pantalla. */
private val VICTORY_BADGE_SIZE = 150.dp

/**
 * Una línea de la despedida.
 *
 * @param badge `true` si mientras se dice ya se está mostrando la medalla.
 */
internal data class GymVictoryLine(
    /** Texto de la línea (`R.string.*`). */
    @param:StringRes val textRes: Int,
    /** Retrato que se muestra mientras se escribe la línea (`R.drawable.*`). */
    @param:DrawableRes val portraitRes: Int,
    val badge: Boolean = false
)

/** Despedida de un líder: sus líneas y la medalla que entrega. */
internal data class GymVictoryScript(
    val lines: List<GymVictoryLine>,
    @param:DrawableRes val badgeRes: Int
)

/** Brock: rabia por el resultado, alegría por el combate y la Medalla Roca. */
private val brockVictory = GymVictoryScript(
    badgeRes = R.drawable.medalla_roca,
    lines = listOf(
        GymVictoryLine(R.string.gym_victory_line_almost, R.drawable.brock_angry),
        GymVictoryLine(R.string.gym_victory_line_enjoyed, R.drawable.brock_happy),
        GymVictoryLine(R.string.gym_victory_line_badge, R.drawable.brock_happy, badge = true),
        GymVictoryLine(R.string.gym_victory_line_farewell, R.drawable.brock_happy, badge = true)
    )
)

private val victoryScripts: Map<String, GymVictoryScript> = mapOf("brock" to brockVictory)

/** Gimnasios con despedida propia; el resto cierra el combate en el acto. */
internal val victorySpeechGymIds: Set<String> = victoryScripts.keys

/** Despedida de [gymId]; `null` si el líder aún no tiene guion de victoria. */
internal fun victoryScriptFor(gymId: String?): GymVictoryScript? = gymId?.let { victoryScripts[it] }

/**
 * Capa de despedida. Sustituye la escena: cuando aparece, el Pokémon del líder ya
 * se ha hundido y el combate está cerrado en `WON`.
 *
 * @param onFinished se invoca al cerrar la última línea; ahí se cobra la medalla.
 */
@Composable
internal fun GymVictory(
    script: GymVictoryScript,
    leaderName: String?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val line = script.lines[stepIndex.coerceIn(script.lines.indices)]
    val fullText = stringResource(line.textRes)

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
    val isLast = stepIndex == script.lines.lastIndex

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val painter = painterResource(line.portraitRes)
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
        )

        // La medalla aparece en el centro de la pantalla cuando la entrega y se
        // queda hasta el final de la despedida.
        AnimatedVisibility(
            visible = line.badge,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.6f, animationSpec = tween(260)),
            exit = fadeOut(tween(120))
        ) {
            Image(
                painter = painterResource(script.badgeRes),
                contentDescription = stringResource(R.string.gym_victory_badge_desc),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(VICTORY_BADGE_SIZE)
            )
        }

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
