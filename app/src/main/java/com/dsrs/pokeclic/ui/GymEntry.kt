/**
 * Entrada al gimnasio: cortinilla de Pokéball y discurso del líder.
 *
 * Reutiliza la cortinilla de arranque ([R.drawable.pokeball_splash_top] y
 * [R.drawable.pokeball_splash_bottom]) al revés: las dos mitades entran desde
 * fuera, se quedan cerradas un segundo y se abren para descubrir el gimnasio.
 * Detrás ya está dibujado el fondo del gimnasio sin sprites: durante la entrada
 * `gym == null`, así que `AppRoot` solo pinta el escenario.
 *
 * El discurso usa el marco compartido [LeaderDialogue], el mismo de la intro del
 * Profesor Oak: retrato arriba, caja de diálogo, máquina de escribir y toque para
 * completar la línea y luego avanzar. Solo presentación: el estado del juego lo
 * sigue poseyendo el ViewModel y las decisiones salen como lambdas.
 */
package com.dsrs.pokeclic.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dsrs.pokeclic.R
import kotlinx.coroutines.delay

/** Milisegundos que tarda cada mitad de la Pokéball en cerrar o abrir. */
private const val CURTAIN_SLIDE_MS = 700

/** Tiempo que la cortinilla se queda cerrada, como pidió el diseño. */
private const val CURTAIN_HOLD_MS = 1000L

/**
 * Una línea del discurso: qué dice y con qué retrato se dice.
 *
 * El retrato va por línea porque el líder cambia de cara a mitad de la
 * conversación (Brock pasa a sorprendido mientras escucha lo del Team Rocket).
 */
internal data class GymLeaderLine(
    /** Texto de la línea (`R.string.*`). */
    @param:StringRes val textRes: Int,
    /** Retrato que se muestra mientras se escribe la línea (`R.drawable.*`). */
    @param:DrawableRes val portraitRes: Int,
    /**
     * Segundo retrato que se turna con [portraitRes] mientras la línea se escribe:
     * la boca abierta y cerrada de un mismo dibujo. `null` deja el retrato quieto.
     */
    @param:DrawableRes val altPortraitRes: Int? = null
)

/**
 * Discurso de un líder, línea a línea.
 *
 * Una entrada por gimnasio. Los que aún no tienen retrato ni guion no aparecen
 * aquí y conservan la bienvenida genérica.
 */
internal data class GymLeaderScript(val lines: List<GymLeaderLine>)

/** Cara de siempre de Brock, y la de sorpresa mientras escucha al jugador. */
private val brockCalm = R.drawable.brock
private val brockSurprised = R.drawable.brock_surprised

/**
 * Misty: la boca abierta y cerrada de su cara de siempre. Se turnan mientras
 * habla, así que el retrato se mueve sin necesidad de más dibujos.
 */
private val mistyHappyOpen = R.drawable.misty_happy
private val mistyHappyClosed = R.drawable.misty_happy2
private val mistySurprised = R.drawable.misty_surprised
private val mistySmiling = R.drawable.misty_smiling

/** Brock: bienvenida, la sequía de retadores, el Team Rocket y la medalla. */
private val brockScript = GymLeaderScript(
    lines = listOf(
        GymLeaderLine(R.string.gym_leader_line_welcome, brockCalm),
        GymLeaderLine(R.string.gym_leader_line_quiet, brockCalm),
        // El jugador le cuenta algo: Brock escucha todavía con su cara de siempre.
        GymLeaderLine(R.string.gym_leader_line_listen, brockCalm),
        GymLeaderLine(R.string.gym_leader_line_listen, brockCalm),
        // La cara de sorpresa aparece junto con la mención al Team Rocket.
        GymLeaderLine(R.string.gym_leader_line_rocket_hint, brockSurprised),
        // Vuelve a escuchar antes del estallido, ya sorprendido.
        GymLeaderLine(R.string.gym_leader_line_listen, brockSurprised),
        GymLeaderLine(R.string.gym_leader_line_listen, brockSurprised),
        GymLeaderLine(R.string.gym_leader_line_rocket, brockSurprised),
        // Recuperada la calma hasta el final del discurso.
        GymLeaderLine(R.string.gym_leader_line_badge, brockCalm),
        GymLeaderLine(R.string.gym_leader_line_duel, brockCalm),
        GymLeaderLine(R.string.gym_leader_line_tutorial, brockCalm)
    )
)

/**
 * Misty: la bienvenida aburrida, la sorpresa al oír lo del Team Rocket, la
 * negativa a regalar la medalla y la pregunta del tutorial.
 *
 * Las líneas de su cara de siempre llevan [GymLeaderLine.altPortraitRes]: la boca
 * se abre y se cierra sola mientras escribe, que es todo lo que hace falta para
 * que parezca que habla.
 */
private val mistyScript = GymLeaderScript(
    lines = listOf(
        GymLeaderLine(R.string.gym_leader_line_misty_welcome, mistyHappyOpen, mistyHappyClosed),
        GymLeaderLine(R.string.gym_leader_line_misty_bored, mistyHappyOpen, mistyHappyClosed),
        // El jugador le cuenta lo del Team Rocket: ella escucha, ya sorprendida.
        GymLeaderLine(R.string.gym_leader_line_listen, mistySurprised),
        GymLeaderLine(R.string.gym_leader_line_listen, mistySurprised),
        GymLeaderLine(R.string.gym_leader_line_misty_rocket, mistySurprised),
        GymLeaderLine(R.string.gym_leader_line_listen, mistySurprised),
        // La medalla no se regala: media sonrisa y el reto.
        GymLeaderLine(R.string.gym_leader_line_misty_badge, mistySmiling),
        GymLeaderLine(R.string.gym_leader_line_misty_duel, mistySmiling),
        // Vuelve a su cara de siempre para ofrecer el tutorial.
        GymLeaderLine(R.string.gym_leader_line_misty_tutorial, mistyHappyOpen, mistyHappyClosed)
    )
)

private val leaderScripts: Map<String, GymLeaderScript> =
    mapOf("brock" to brockScript, "misty" to mistyScript)

/** Gimnasios con discurso propio; el resto usa la bienvenida genérica. */
internal val scriptedLeaderGymIds: Set<String> = leaderScripts.keys

/** Guion de [gymId]; `null` si el líder aún no tiene discurso propio. */
internal fun leaderScriptFor(gymId: String?): GymLeaderScript? = gymId?.let { leaderScripts[it] }

/**
 * Cortinilla de entrada: las dos mitades de la Pokéball se cierran sobre la
 * pantalla, aguantan [CURTAIN_HOLD_MS] y se abren. Cubre todo el alto, también
 * la franja de la barra de navegación.
 *
 * @param onClosed se invoca cuando la Pokéball ya cubre la pantalla por completo:
 *   es el único momento en el que el llamante puede cambiar la escena sin que se
 *   vea el salto.
 * @param onOpened se invoca al terminar la apertura.
 */
@Composable
internal fun GymEntryCurtain(
    onClosed: () -> Unit,
    onOpened: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 0 = abierta (las dos mitades fuera), 1 = cerrada (cubren la pantalla).
    val closed = remember { Animatable(0f) }
    val latestOnClosed by rememberUpdatedState(onClosed)
    val latestOnOpened by rememberUpdatedState(onOpened)

    LaunchedEffect(Unit) {
        closed.animateTo(1f, tween(CURTAIN_SLIDE_MS, easing = FastOutSlowInEasing))
        // Cerrada: el fondo del gimnasio puede entrar ahora sin que se note.
        latestOnClosed()
        delay(CURTAIN_HOLD_MS)
        closed.animateTo(0f, tween(CURTAIN_SLIDE_MS, easing = FastOutSlowInEasing))
        latestOnOpened()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().modalBlocking()) {
        // Recorrido en px: la altura de la pantalla basta para sacar cada mitad,
        // que ocupa toda la pantalla, por completo.
        val travel = constraints.maxHeight.toFloat()
        val open = 1f - closed.value

        Image(
            painter = painterResource(R.drawable.pokeball_splash_top),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = -travel * open }
        )

        Image(
            painter = painterResource(R.drawable.pokeball_splash_bottom),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = travel * open }
        )
    }
}

/**
 * Discurso del líder sobre el escenario. La última línea no avanza con el toque:
 * la cierran los dos botones del pie.
 *
 * @param onWatchTutorial "Ver tutorial" de la última línea.
 * @param onSkip "Omitir" de la última línea.
 */
@Composable
internal fun GymEntryDialogue(
    script: GymLeaderScript,
    leaderName: String?,
    onWatchTutorial: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val line = script.lines[stepIndex.coerceIn(script.lines.indices)]
    val isLastLine = stepIndex == script.lines.lastIndex

    LeaderDialogue(
        portraitRes = line.portraitRes,
        altPortraitRes = line.altPortraitRes,
        leaderName = leaderName,
        step = stepIndex,
        text = stringResource(line.textRes),
        onAdvance = { if (!isLastLine) stepIndex++ },
        modifier = modifier,
        footer = { lineComplete ->
            if (isLastLine && lineComplete) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RetroButton(
                        label = stringResource(R.string.gym_intro_watch),
                        onClick = onWatchTutorial,
                        modifier = Modifier.weight(1f)
                    )
                    RetroButton(
                        label = stringResource(R.string.gym_intro_skip),
                        base = RetroColors.ResetBase,
                        edge = RetroColors.ResetEdge,
                        onClick = onSkip,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    )
}
