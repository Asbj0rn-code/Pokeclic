/**
 * Escena del minijuego de captura (Etapa 3): el salvaje nada por un riel vertical,
 * el jugador mantiene pulsado para subir su barra de captura y el medidor
 * lateral mide el progreso de la misma.
 *
 * Es full-screen takeover como el gimnasio: cuando hay partida, la escena
 * sustituye al contenido y al bottom menu. **Sin lógica de juego**: pinta el
 * `CatchingState` ya calculado por el ViewModel y le notifica la pulsación
 * (mantener/soltar) y la salida del panel de resultado.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment as UiAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.Encounter
import com.dsrs.pokeclic.model.CatchingDefaults
import com.dsrs.pokeclic.model.CatchingGame
import com.dsrs.pokeclic.model.CatchingPhase
import com.dsrs.pokeclic.model.CatchingState
import com.dsrs.pokeclic.model.PokemonType
import kotlin.random.Random

/** Fondos y colores de la escena: agua oscura, barra dorada, progreso verde. */
private val BACKDROP_COLOR = Color(0xFF17251A)
private val RAIL_COLOR = Color(0xFF0E2340)
private val BAR_COLOR = Color(0xFFFFD75A)
private val GAUGE_COLOR = Color(0xFF334033)
private val GAUGE_FILL_COLOR = Color(0xFF66BB6A)

/**
 * Escena completa de la partida de captura.
 *
 * El gesto de pulsación se captura en **todo** el fondo (cualquier parte de la
 * pantalla sirve, como se acordó en el diseño); el botón del panel de resultado
 * convive con él sin problema porque el ViewModel ignora `onHoldChanged` cuando
 * la partida ya terminó.
 *
 * @param encounter especie en liza (nombre y tipo para HUD y resultado).
 * @param catching estado del minijuego (barra, sprite, progreso y fase).
 * @param onHoldChanged avisa al ViewModel de que la pantalla está pulsada.
 * @param onContinue cierra el panel de resultado.
 */
@Composable
fun CatchingOverlay(
    encounter: Encounter,
    catching: CatchingState,
    onHoldChanged: (Boolean) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BACKDROP_COLOR)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onHoldChanged(true)
                        tryAwaitRelease()
                        onHoldChanged(false)
                    }
                )
            }
    ) {
        CatchingHud(
            encounter = encounter,
            modifier = Modifier
                .align(UiAlignment.TopCenter)
                .padding(top = 24.dp)
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val railHeight = min(maxHeight * CatchingSceneDefaults.RAIL_HEIGHT_FRACTION, CatchingSceneDefaults.RAIL_MAX_HEIGHT)

            // Riel central del minijuego.
            Box(
                modifier = Modifier
                    .align(UiAlignment.Center)
                    .width(CatchingSceneDefaults.RAIL_WIDTH)
                    .height(railHeight)
                    .clip(RoundedCornerShape(20.dp))
                    .background(RAIL_COLOR)
            ) {
                // Barra de captura: posiciones normalizadas del modelo, donde
                // `barPos` es el centro y `0f` es la parte baja del riel.
                val halfBar = catching.config.barSize / 2f
                val barTop = railHeight * (1f - (catching.barPos + halfBar))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CatchingSceneDefaults.BAR_INSET)
                        .offset(y = barTop)
                        .height(railHeight * catching.config.barSize)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BAR_COLOR)
                )

                // Sprite del salvaje, animado o placeholder según el arte actual.
                val spriteTop = railHeight * (1f - catching.spritePos) - CatchingSceneDefaults.SPRITE_SIZE / 2
                WildSpriteBox(
                    speciesId = encounter.speciesId,
                    name = encounter.speciesName,
                    type = encounter.speciesType,
                    modifier = Modifier
                        .align(UiAlignment.TopCenter)
                        .offset(y = spriteTop)
                        .size(CatchingSceneDefaults.SPRITE_SIZE)
                )
            }

            // Medidor de progreso, a la derecha del riel. Se llena de abajo a arriba.
            Box(
                modifier = Modifier
                    .align(UiAlignment.Center)
                    .offset(x = CatchingSceneDefaults.RAIL_WIDTH / 2 + CatchingSceneDefaults.GAUGE_GAP)
                    .width(CatchingSceneDefaults.GAUGE_WIDTH)
                    .height(railHeight)
                    .clip(RoundedCornerShape(6.dp))
                    .background(GAUGE_COLOR)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(UiAlignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(catching.progress)
                        .clip(RoundedCornerShape(4.dp))
                        .background(GAUGE_FILL_COLOR)
                )
            }

            if (catching.phase == CatchingPhase.CAPTURED || catching.phase == CatchingPhase.ESCAPED) {
                CatchingResultPanel(
                    encounter = encounter,
                    captured = catching.phase == CatchingPhase.CAPTURED,
                    onContinue = onContinue,
                    modifier = Modifier.align(UiAlignment.Center)
                )
            }
        }
    }
}

/** HUD superior: salvaje, tipo y la instrucción del minijuego. */
@Composable
private fun CatchingHud(
    encounter: Encounter,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.catch_title, encounter.speciesName),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(text = stringResource(R.string.catch_type, encounter.speciesType.name))
            Text(
                text = stringResource(R.string.catching_hint, encounter.speciesName),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Panel de resultado centrado: mensajen y botón para volver a la hierba. Igual
 * de sobrio que el panel de derrota del gimnasio.
 */
@Composable
private fun CatchingResultPanel(
    encounter: Encounter,
    captured: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 8.dp,
        modifier = modifier.padding(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = UiAlignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(
                    if (captured) R.string.catching_captured else R.string.catching_escaped,
                    encounter.speciesName
                ),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Button(onClick = onContinue) {
                Text(text = stringResource(R.string.catching_continue))
            }
        }
    }
}

/**
 * Sprite del salvaje con sustituto: si no hay GIF todavía (hoy solo lo tienen
 * los starters), dibuja el mismo placeholder que el gimnasio.
 */
@Composable
private fun WildSpriteBox(
    speciesId: String,
    name: String,
    type: PokemonType,
    modifier: Modifier = Modifier
) {
    val gifRes = wildGifFor(speciesId)
    if (gifRes != null) {
        AnimatedGifImage(
            resId = gifRes,
            contentDescription = name,
            modifier = modifier
        )
    } else {
        GymSpritePlaceholder(name = name, type = type, modifier = modifier)
    }
}

/**
 * Sprite del salvaje, o `null` si esa especie aún no tiene GIF. Único punto
 * donde se mapea la especie de captura a su recurso, como `enemyGifFor`.
 */
@DrawableRes
private fun wildGifFor(speciesId: String): Int? = when (speciesId) {
    "bulbasaur" -> R.drawable.bulbasaur
    "charmander" -> R.drawable.charmander
    "squirtle" -> R.drawable.squirtle
    else -> null
}

/** Tamaños y posiciones fijas de la escena de captura. */
private object CatchingSceneDefaults {
    val RAIL_WIDTH = 72.dp

    /** Alto del riel como fracción de la pantalla, con tope para tablets. */
    val RAIL_HEIGHT_FRACTION = 0.58f
    val RAIL_MAX_HEIGHT = 480.dp

    /** Tamaño visual del sprite dentro del riel. */
    val SPRITE_SIZE = 40.dp

    /** Grosor y hueco del medidor de progreso. */
    val GAUGE_WIDTH = 16.dp
    val GAUGE_GAP = 18.dp

    /** Tapas laterales de la barra dentro del riel. */
    val BAR_INSET = 4.dp
}

// ─────────────────────────── Previews ───────────────────────────

private const val CATCHING_PREVIEW_DEVICE = "spec:width=411dp,height=914dp,dpi=420"

/**
 * Estado de captura de ejemplo, determinista (semilla fija) para que el preview
 * no cambie de una compilación a otra.
 */
private fun previewCatchingState(
    captureDifficulty: Float,
    progress: Float = 0f
): CatchingState {
    val config = CatchingDefaults.configFor(captureDifficulty)
    val base = CatchingGame.initialState(config, Random(42))
    return base.copy(progress = progress)
}

private val previewEncounter = Encounter(
    speciesId = "rattata",
    speciesName = "Rattata",
    speciesType = PokemonType.NORMAL
)

@Preview(
    name = "Catching · Partida en curso",
    device = CATCHING_PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun CatchingOverlayPreview() {
    MaterialTheme {
        CatchingOverlay(
            encounter = previewEncounter,
            catching = previewCatchingState(captureDifficulty = 0.15f),
            onHoldChanged = {},
            onContinue = {}
        )
    }
}

@Preview(
    name = "Catching · Resultado",
    device = CATCHING_PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun CatchingOverlayCapturedPreview() {
    MaterialTheme {
        CatchingOverlay(
            encounter = previewEncounter,
            catching = previewCatchingState(captureDifficulty = 0.15f, progress = 1f),
            onHoldChanged = {},
            onContinue = {}
        )
    }
}
