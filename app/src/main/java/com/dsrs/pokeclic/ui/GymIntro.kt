/**
 * Capa modal de entrada al gimnasio: bienvenida, tutorial y cartel de arranque.
 *
 * Tres pantallas sobre un mismo scrim oscuro (ver [GymIntroPhase]):
 * - **Welcome**: texto de bienvenida y los botones "Ver tutorial" / "Omitir".
 * - **Tutorial**: explicación del minijuego en 3 pasos que se revelan solos;
 *   "Repetir tutorial" reinicia la revelación desde el paso 1 y "Entendido"
 *   cierra la capa dejando paso al cartel.
 * - **Countdown**: cartel "¡Que comience el combate!" que bloquea la pantalla
 *   durante el segundo de espera; el ViewModel arranca el combate al caducar.
 *
 * Solo presentación: el estado lo posee el ViewModel (que además cancela el
 * cartel al salir/resetear) y los eventos salen como lambdas.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.GymIntroPhase
import kotlinx.coroutines.delay

/**
 * scrim de la capa: semiobscuro para atenuar la cueva del gimnasio (dibujada
 * por `AppRoot` detrás) sin taparla, manteniendo la legibilidad del texto.
 */
private val IntroScrim = Color(0xFF04070C).copy(alpha = 0.70f)

/** color de tinta para los pasos dentro de la caja crema. */
private val StepInk = RetroColors.InkBlack

/** intervalo de la revelación de los pasos del tutorial. */
private const val STEP_REVEAL_MS = 700L

/** panel crema tipo GBA con relleno generoso, base de las tres pantallas. */
@Composable
private fun IntroPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    RetroPanel(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .heightIn(max = 480.dp),
        contentPadding = contentPadding
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

// ─────────────────────────── capa raíz ───────────────────────────

/**
 * Capa de entrada al gimnasio. Se compone desde `AppRoot` mientras
 * `state.gymIntro != null`; aislamiento (`modalBlocking`) en el nodo raíz
 * (issue #21) para que ningún toque llegue al juego de debajo.
 *
 * @param phase fase visible del flujo de entrada.
 * @param leaderName nombre del líder de la zona, para personalizar el saludo.
 * @param onWatchTutorial "Ver tutorial" (bienvenida).
 * @param onSkip "Omitir" (bienvenida).
 * @param onAccepted "Entendido" (tutorial).
 */
@Composable
fun GymIntroLayer(
    phase: GymIntroPhase,
    leaderName: String?,
    onWatchTutorial: () -> Unit,
    onSkip: () -> Unit,
    onAccepted: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IntroScrim)
            .modalBlocking()
            .navigationBarsPadding()
    ) {
        when (phase) {
            GymIntroPhase.WELCOME ->
                WelcomePanel(
                    leaderName = leaderName,
                    onWatchTutorial = onWatchTutorial,
                    onSkip = onSkip,
                    modifier = Modifier.align(Alignment.Center)
                )
            GymIntroPhase.TUTORIAL ->
                TutorialPanel(
                    onAccepted = onAccepted,
                    modifier = Modifier.align(Alignment.Center)
                )
            GymIntroPhase.COUNTDOWN ->
                CountdownBanner(modifier = Modifier.align(Alignment.Center))
        }
    }
}

// ─────────────────────────── bienvenida ───────────────────────────

/**
 * Texto de bienvenida al gimnasio con los dos botones iniciales: "Ver
 * tutorial" abre la explicación y "Omitir" pasa directo al cartel de arranque.
 */
@Composable
private fun WelcomePanel(
    leaderName: String?,
    onWatchTutorial: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    IntroPanel(modifier = modifier) {
        Text(
            text = stringResource(R.string.gym_welcome_title, leaderName ?: "?"),
            style = MaterialTheme.typography.titleLarge,
            color = StepInk,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.padding(top = 8.dp))
        Text(
            text = stringResource(R.string.gym_welcome_body),
            style = MaterialTheme.typography.bodyMedium,
            color = StepInk,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.padding(top = 14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onWatchTutorial) {
                Text(text = stringResource(R.string.gym_intro_watch))
            }
            OutlinedButton(onClick = onSkip) {
                Text(text = stringResource(R.string.gym_intro_skip))
            }
        }
    }
}

// ─────────────────────────── tutorial ───────────────────────────

/** Los 3 pasos del tutorial, en el orden en que se revelan. */
private data class TutorialStep(val titleRes: Int, val textRes: Int)

private val tutorialSteps = listOf(
    TutorialStep(R.string.gym_tutorial_step1_title, R.string.gym_tutorial_step1_text),
    TutorialStep(R.string.gym_tutorial_step2_title, R.string.gym_tutorial_step2_text),
    TutorialStep(R.string.gym_tutorial_step3_title, R.string.gym_tutorial_step3_text)
)

/**
 * Explicación del minijuego en 3 pasos que aparecen uno tras otro: sencillos
 * y con ritmo de juego para que cualquier jugador los entienda. "Repetir
 * tutorial" reinicia la revelación desde el paso 1; "Entendido" va al cartel
 * de arranque en cualquier momento.
 */
@Composable
private fun TutorialPanel(
    onAccepted: () -> Unit,
    modifier: Modifier = Modifier
) {
    // cada cambio de `replayNonce` reinicia la revelación (es "repetir").
    var replayNonce by remember { mutableIntStateOf(0) }
    var revealedAt by remember { mutableIntStateOf(0) }

    LaunchedEffect(replayNonce) {
        revealedAt = 0
        while (revealedAt < tutorialSteps.size) {
            delay(STEP_REVEAL_MS)
            revealedAt++
        }
    }

    IntroPanel(modifier = modifier) {
        Text(
            text = stringResource(R.string.gym_tutorial_title),
            style = MaterialTheme.typography.titleLarge,
            color = StepInk,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.padding(top = 10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            tutorialSteps.forEachIndexed { index, step ->
                AnimatedVisibility(
                    visible = index < revealedAt,
                    enter = fadeIn(tween(durationMillis = 250)),
                    exit = fadeOut()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = stringResource(
                                R.string.gym_tutorial_step_number,
                                index + 1,
                                stringResource(step.titleRes)
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = StepInk
                        )
                        Text(
                            text = stringResource(step.textRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = StepInk
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.padding(top = 14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { replayNonce++ }) {
                Text(text = stringResource(R.string.gym_tutorial_repeat))
            }
            Button(onClick = onAccepted) {
                Text(text = stringResource(R.string.gym_tutorial_accept))
            }
        }
    }
}

// ─────────────────────────── cartel de arranque ───────────────────────────

/**
 * Cartel de "que comience el combate": caja crema tipo GBA con el texto en
 * grande. Se muestra mientras corre el segundo de espera del ViewModel, y la
 * capa raíz ya bloquea los toques para que el jugador no entre por sorpresa.
 */
@Composable
private fun CountdownBanner(modifier: Modifier = Modifier) {
    RetroPanel(
        modifier = modifier.padding(horizontal = 24.dp),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.gym_countdown),
            style = MaterialTheme.typography.headlineSmall,
            color = StepInk,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 6.dp)
        )
    }
}
