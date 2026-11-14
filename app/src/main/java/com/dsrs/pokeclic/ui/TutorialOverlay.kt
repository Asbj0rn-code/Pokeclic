/**
 * Capa de diálogos del tutorial explicativo (issue #60).
 *
 * Reproduce el estilo de la caja de diálogo de la introducción (`Intro.kt`)
 * **sin retrato**: caja blanca de borde negro, efecto máquina de escribir,
 * flecha «▼» al completarse y botones de respuesta en las preguntas. La
 * introducción original no se toca: sus piezas privadas se replican aquí.
 *
 * Paginación: el texto se divide en **páginas** por párrafo («\n\n», ver
 * [tutorialPages]). El primer toque completa la página, el siguiente avanza y,
 * en la última, cierra el mensaje vía `onMessageTap`. Avanzar páginas **no**
 * completa el paso: mientras queden páginas todo el juego sigue bloqueado.
 *
 * Montaje único (QA): **todos** los mensajes usan scrim + `modalBlocking` con
 * la caja anclada al fondo sobre el menú; al cerrar la última página la capa
 * se retira y solo las interacciones autorizadas por la fase vuelven a operar.
 *
 * El nombre del jugador se sustituye dinámicamente (`%1$s`), con apelativo de
 * reserva si viniera vacío; escritura normal, sin mayúsculas forzadas.
 *
 * Solo presentación: la fase, el nombre y el momento del mensaje llegan ya
 * resueltos; las decisiones son eventos en forma de lambdas.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.TutorialPhase
import com.dsrs.pokeclic.model.isMessageVisible
import kotlinx.coroutines.delay

/** Milisegundos entre cada letra del efecto "máquina de escribir" (como la intro). */
private const val TYPE_CHAR_MS = 35L

/**
 * Scrim de la capa modal del tutorial: mismo tono que la capa de entrada al
 * gimnasio, suficiente para aislar el diálogo del juego de debajo.
 */
private val QuestionScrim = Color(0xFF04070C).copy(alpha = 0.70f)

/** Texto por fase. `null` en las fases sin diálogo. */
internal fun tutorialMessageRes(phase: TutorialPhase): Int? = when (phase) {
    TutorialPhase.OFFER -> R.string.tutorial_offer_body
    TutorialPhase.CONFIRM -> R.string.tutorial_confirm_body
    TutorialPhase.DEPLOY_POKEBALL -> R.string.tutorial_deploy_body
    TutorialPhase.OPEN_MAP -> R.string.tutorial_open_map_body
    TutorialPhase.RETURN_MAP -> R.string.tutorial_return_map_body
    TutorialPhase.OPEN_POKEDEX -> R.string.tutorial_open_pokedex_body
    TutorialPhase.RETURN_POKEDEX -> R.string.tutorial_return_pokedex_body
    TutorialPhase.FAREWELL -> R.string.tutorial_farewell_body
    TutorialPhase.EXP_NOTICE -> R.string.tutorial_exp_notice_body
    TutorialPhase.PENDING,
    TutorialPhase.EXP_WAIT,
    TutorialPhase.ADVANCE_WAIT,
    TutorialPhase.SKIPPED,
    TutorialPhase.COMPLETED -> null
}

/** ¿La fase es una pregunta con botones? Su respuesta es explícita, sin toques. */
internal fun tutorialIsQuestion(phase: TutorialPhase): Boolean =
    phase == TutorialPhase.OFFER || phase == TutorialPhase.CONFIRM

/** ¿El texto de esta fase lleva el nombre del jugador? */
internal fun tutorialUsesPlayerName(phase: TutorialPhase): Boolean =
    phase == TutorialPhase.OFFER || phase == TutorialPhase.RETURN_POKEDEX

/**
 * Divide el texto del mensaje en **páginas** por párrafo (separador «\n\n»).
 * Cada frase o pequeño grupo de frases vive en su propia página, como en los
 * diálogos de la introducción; los textos originales no se alteran.
 */
internal fun tutorialPages(fullText: String): List<String> =
    fullText.split("\n\n").filter { it.isNotEmpty() }

/**
 * Página que sigue al toque sobre la caja: la siguiente, o `null` si ya se
 * está en la última (el toque cierra el mensaje y solo entonces dispara
 * `onMessageTap`).
 */
internal fun tutorialPageAfterTap(pageIndex: Int, pageCount: Int): Int? =
    if (pageIndex < pageCount - 1) pageIndex + 1 else null

/**
 * Capa de diálogos del tutorial. No compone nada si la fase no muestra
 * mensaje ([TutorialPhase.isMessageVisible]).
 *
 * @param phase fase actual de la máquina del tutorial.
 * @param playerName nombre del entrenador para los textos que lo usan.
 * @param messageClosed `true` cuando el mensaje del paso guiado ya se cerró.
 * @param bottomReserved alto reservado al menú inferior: la caja se ancla
 *   justo encima, sin tapar la Pokéball ni los botones del menú.
 * @param onMessageTap cierre del mensaje por pulsación (solo en la última
 *   página; el ViewModel enruta cada caso).
 */
@Composable
fun TutorialOverlay(
    phase: TutorialPhase,
    playerName: String?,
    messageClosed: Boolean,
    bottomReserved: Dp = 0.dp,
    onMessageTap: () -> Unit,
    onAcceptOffer: () -> Unit,
    onDeclineOffer: () -> Unit,
    onBeginTutorial: () -> Unit,
    onSkipTutorial: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textRes = tutorialMessageRes(phase) ?: return
    if (!phase.isMessageVisible(messageClosed)) return

    val isQuestion = tutorialIsQuestion(phase)
    val displayName = playerName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.tutorial_player_fallback)
    val fullText = if (tutorialUsesPlayerName(phase)) {
        stringResource(textRes, displayName)
    } else {
        stringResource(textRes)
    }

    // Paginación: el índice se reinicia con cada mensaje y sobrevive a
    // rotaciones; al reabrir la app la fase y el momento llegan por la
    // persistencia y el mensaje arranca en su primera página.
    val pages = remember(fullText) { tutorialPages(fullText) }
    var pageIndex by rememberSaveable(inputs = arrayOf(fullText)) { mutableIntStateOf(0) }
    val pageText = pages[pageIndex.coerceIn(pages.indices)]

    // Efecto máquina de escribir, reiniciado en cada página. Un toque puede
    // adelantar el texto de la página completa.
    var revealed by remember(pageText) { mutableIntStateOf(0) }
    LaunchedEffect(pageText) {
        revealed = 0
        while (revealed < pageText.length) {
            delay(TYPE_CHAR_MS)
            revealed++
        }
    }
    val pageComplete = revealed >= pageText.length
    val shown = pageText.take(revealed)
    val lastPage = pageIndex == pages.lastIndex

    fun onBoxTap() {
        // Primer toque: completar el texto de la página si se está escribiendo.
        if (!pageComplete) {
            revealed = pageText.length
            return
        }
        // Con la página completa: avanzar a la siguiente, o cerrar el mensaje
        // en la última. Las preguntas nunca se cierran por toque: exigen la
        // respuesta explícita de sus botones.
        val nextPage = tutorialPageAfterTap(pageIndex, pages.size)
        if (nextPage != null) {
            pageIndex = nextPage
        } else if (!isQuestion) {
            onMessageTap()
        }
    }

    val dialogueBox: @Composable () -> Unit = {
        TutorialDialogueBox(
            text = shown,
            complete = pageComplete,
            onClick = ::onBoxTap,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Capa modal única para todos los mensajes: al cerrar la última página
    // `isMessageVisible` pasa a `false` y la capa se retira al acto.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(QuestionScrim)
            .modalBlocking()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomReserved)
        ) {
            dialogueBox()

            if (isQuestion && lastPage && pageComplete) {
                TutorialQuestionButtons(
                    phase = phase,
                    onAcceptOffer = onAcceptOffer,
                    onDeclineOffer = onDeclineOffer,
                    onBeginTutorial = onBeginTutorial,
                    onSkipTutorial = onSkipTutorial
                )
            }
        }
    }
}

/**
 * Fila de botones de las preguntas, solo en la última página con el texto
 * completo (como los widgets de la introducción).
 */
@Composable
private fun TutorialQuestionButtons(
    phase: TutorialPhase,
    onAcceptOffer: () -> Unit,
    onDeclineOffer: () -> Unit,
    onBeginTutorial: () -> Unit,
    onSkipTutorial: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(
            10.dp,
            alignment = Alignment.CenterHorizontally
        )
    ) {
        when (phase) {
            TutorialPhase.OFFER -> {
                Button(onClick = onAcceptOffer) {
                    Text(text = stringResource(R.string.intro_confirm_yes))
                }
                OutlinedButton(onClick = onDeclineOffer) {
                    Text(text = stringResource(R.string.intro_confirm_no))
                }
            }
            TutorialPhase.CONFIRM -> {
                Button(onClick = onBeginTutorial) {
                    Text(text = stringResource(R.string.tutorial_confirm_begin))
                }
                OutlinedButton(onClick = onSkipTutorial) {
                    Text(text = stringResource(R.string.tutorial_confirm_skip))
                }
            }
            else -> Unit
        }
    }
}

/**
 * Caja de diálogo estilo Pokémon, idéntica a la de la introducción: rectángulo
 * blanco con borde negro grueso, esquinas redondeadas y flecha «▼» al
 * completarse el texto de la página.
 */
@Composable
private fun TutorialDialogueBox(
    text: String,
    complete: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(3.dp, Color.Black, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = text,
            color = Color.Black,
            textAlign = TextAlign.Start,
            style = MaterialTheme.typography.titleMedium
        )
        if (complete) {
            Text(
                text = "▼",
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

// ─────────────────────────── Previews ───────────────────────────

/** Especificación de los previews, igual que en `AppRoot` y `MainScreen`. */
private const val SPEC = "spec:width=411dp,height=914dp,dpi=420"

@Preview(name = "1 · Oferta (pregunta modal)", device = SPEC, showSystemUi = true)
@Composable
private fun TutorialOfferPreview() = MaterialTheme {
    TutorialOverlay(
        phase = TutorialPhase.OFFER,
        playerName = "Sergio",
        messageClosed = false,
        onMessageTap = {},
        onAcceptOffer = {},
        onDeclineOffer = {},
        onBeginTutorial = {},
        onSkipTutorial = {}
    )
}

@Preview(name = "2 · Paso de la Pokéball", device = SPEC, showSystemUi = true)
@Composable
private fun TutorialDeployPreview() = MaterialTheme {
    TutorialOverlay(
        phase = TutorialPhase.DEPLOY_POKEBALL,
        playerName = "Sergio",
        messageClosed = false,
        onMessageTap = {},
        onAcceptOffer = {},
        onDeclineOffer = {},
        onBeginTutorial = {},
        onSkipTutorial = {}
    )
}

@Preview(name = "3 · Aviso de avance", device = SPEC, showSystemUi = true)
@Composable
private fun TutorialExpNoticePreview() = MaterialTheme {
    TutorialOverlay(
        phase = TutorialPhase.EXP_NOTICE,
        playerName = "Sergio",
        messageClosed = false,
        onMessageTap = {},
        onAcceptOffer = {},
        onDeclineOffer = {},
        onBeginTutorial = {},
        onSkipTutorial = {}
    )
}
