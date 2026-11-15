/**
 * Despedida del líder al ganar el gimnasio.
 *
 * Cuando la vida del Pokémon del líder llega a cero el combate se detiene en
 * `GymOutcome.WON`: primero su Pokémon se hunde y se apaga (lo hace `GymOverlay`)
 * y después se muestra esta capa, con el retrato de la cara que toque en cada
 * línea, la despedida, y la medalla apareciendo en el centro cuando la entrega.
 *
 * El marco (retrato, máquina de escribir y toque) lo aporta [LeaderDialogue].
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dsrs.pokeclic.R

/** Milisegundos que tarda el Pokémon del líder en hundirse y apagarse. */
internal const val VICTORY_FADE_MS = 650

/** Cuánto se hunde el Pokémon del líder al caer, en la escena. */
internal val VICTORY_ENEMY_SINK = 48.dp

/** Tamaño ideal de la medalla cuando aparece en el centro de la pantalla. */
private val VICTORY_BADGE_SIZE = 150.dp

/**
 * Cuánto se sube la medalla respecto al centro de la pantalla. Centrada caía muy
 * abajo y se metía bajo el borde de la caja de diálogo; subida queda delante del
 * retrato, que es donde se entrega.
 */
private val VICTORY_BADGE_RISE = 60.dp

/**
 * Tope de alto de la medalla como fracción de la pantalla: en pantallas cortas
 * manda esto, para que no se coma la caja de diálogo.
 */
private const val VICTORY_BADGE_HEIGHT_FRACTION = 0.18f

/** Animación de entrada y salida de la medalla. */
private const val BADGE_FADE_IN_MS = 220
private const val BADGE_SCALE_IN_MS = 260
private const val BADGE_START_SCALE = 0.6f
private const val BADGE_FADE_OUT_MS = 120

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
    val badge: Boolean = false,
    /**
     * Segundo retrato que se turna con [portraitRes] mientras la línea se escribe.
     * `null` deja el retrato quieto.
     */
    @param:DrawableRes val altPortraitRes: Int? = null
)

/** Despedida de un líder: sus líneas y la medalla que entrega. */
internal data class GymVictoryScript(
    val lines: List<GymVictoryLine>,
    @param:DrawableRes val badgeRes: Int,
    /** Descripción de la medalla para lectores de pantalla. */
    @param:StringRes val badgeDescRes: Int = R.string.gym_victory_badge_desc
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

/**
 * Misty: la tristeza de perder, el ánimo y la Medalla Cascada. La boca se mueve
 * en las líneas de su cara de siempre, igual que en el discurso de entrada.
 */
private val mistyVictory = GymVictoryScript(
    badgeRes = R.drawable.medalla_cascada,
    badgeDescRes = R.string.gym_victory_badge_cascada_desc,
    lines = listOf(
        GymVictoryLine(R.string.gym_victory_line_misty_almost, R.drawable.misty_sad),
        GymVictoryLine(R.string.gym_victory_line_misty_enjoyed, R.drawable.misty_sad),
        GymVictoryLine(
            R.string.gym_victory_line_misty_badge,
            R.drawable.misty_happy,
            badge = true,
            altPortraitRes = R.drawable.misty_happy2
        ),
        GymVictoryLine(
            R.string.gym_victory_line_misty_farewell,
            R.drawable.misty_happy,
            badge = true,
            altPortraitRes = R.drawable.misty_happy2
        )
    )
)

private val victoryScripts: Map<String, GymVictoryScript> =
    mapOf("brock" to brockVictory, "misty" to mistyVictory)

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
    val isLast = stepIndex == script.lines.lastIndex

    LeaderDialogue(
        portraitRes = line.portraitRes,
        altPortraitRes = line.altPortraitRes,
        leaderName = leaderName,
        step = stepIndex,
        text = stringResource(line.textRes),
        onAdvance = { if (isLast) onFinished() else stepIndex++ },
        modifier = modifier,
        overlay = {
            // La medalla aparece cuando la entrega y se queda hasta el final de la
            // despedida. Va centrada pero subida, delante del retrato.
            BoxWithConstraints(modifier = Modifier.matchParentSize()) {
                val badgeSize = minOf(
                    VICTORY_BADGE_SIZE,
                    maxHeight * VICTORY_BADGE_HEIGHT_FRACTION
                )
                AnimatedVisibility(
                    visible = line.badge,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = -VICTORY_BADGE_RISE),
                    enter = fadeIn(tween(BADGE_FADE_IN_MS)) +
                        scaleIn(
                            initialScale = BADGE_START_SCALE,
                            animationSpec = tween(BADGE_SCALE_IN_MS)
                        ),
                    exit = fadeOut(tween(BADGE_FADE_OUT_MS))
                ) {
                    Image(
                        painter = painterResource(script.badgeRes),
                        contentDescription = stringResource(script.badgeDescRes),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(badgeSize)
                    )
                }
            }
        }
    )
}
