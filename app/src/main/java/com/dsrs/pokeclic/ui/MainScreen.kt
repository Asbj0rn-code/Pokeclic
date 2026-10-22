/**
 * Pantalla principal y su HUD: trainer animado, hierba alta, zona, monedas y
 * acciones de reset/principal.
 *
 * La pantalla es **sin estado**: recibe `GameUiState` y lambdas. El HUD calcula
 * solo presentación (fracciones ya derivadas por el ViewModel).
 */
package com.dsrs.pokeclic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.PokemonType
import com.dsrs.pokeclic.viewmodel.GameUiState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text

/**
 * Pantalla principal y su HUD.
 *
 * `MainScreen` coloca al entrenador, la hierba alta y el botón de modo; `GameHud`
 * pinta los datos derivados del estado (monedas, zona, principal, medallas y
 * accesos a gimnasio/reset/cambio de principal). Ambos son **sin estado**: no
 * conocen el ViewModel y solo reenvían eventos como lambdas.
 */



/**
 * Ajustes de posición de la pantalla. Todos los valores `bias` van de 0f (arriba)
 * a 1f (abajo) dentro de la zona de contenido.
 *
 * La Pokéball **no** se posiciona aquí: vive en el bottom menu.
 */
object MainLayout {
    val TRAINER_SIZE = 180.dp

    /** Trainer pegado a la parte alta de la zona de contenido. */
    const val TRAINER_BIAS = -0.55f

    /** Botón de hierba alta: junto al trainer, arriba a la derecha. */
    val TALL_GRASS_SIZE = 80.dp
    const val TALL_GRASS_BIAS = 0.8f
    const val TALL_GRASS_H_BIAS = 0.9f

    /**
     * Capas de hierba alta que se superponen al trainer.
     * Ambas se centran sobre la misma posición que el trainer.
     */
    val TALL_GRASS_FULL_SIZE = 120.dp
    val TALL_GRASS_MID_SIZE = 120.dp

    /**
     * Desplazamiento de las capas de hierba respecto al trainer.
     * Positivo lo mueve a la derecha (X) o hacia abajo (Y); negativo, al revés.
     * Afecta a las dos capas a la vez para que no se separen entre sí.
     */
    val TALL_GRASS_X_OFFSET = 3.dp
    val TALL_GRASS_Y_OFFSET = 38.dp

    /** Margen del HUD respecto al borde superior de la zona de contenido. */
    val HUD_PADDING = 16.dp
}

/**
 * Pantalla principal: HUD arriba, entrenador y hierba alta debajo.
 *
 * El HUD **no se superpone** al contenido: forma la primera fila de un [Column]
 * y el entrenador/la hierba viven en el espacio restante (`weight(1f)`). Así, por
 * mucho que crezca el HUD, nunca tapa al entrenador.
 *
 * Es un composable **sin estado propio**: recibe el [GameUiState] ya calculado y
 * reenvía las interacciones como lambdas. No conoce el ViewModel.
 */
@Composable
fun MainScreen(
    state: GameUiState,
    onTallGrassToggle: () -> Unit,
    onReset: () -> Unit,
    onEnterGym: () -> Unit,
    onOpenLead: () -> Unit,
    onWipeGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        GameHud(
            state = state,
            onReset = onReset,
            onEnterGym = onEnterGym,
            onOpenLead = onOpenLead,
            onWipeGame = onWipeGame,
            modifier = Modifier
                .fillMaxWidth()
                .padding(MainLayout.HUD_PADDING)
        )

        // Espacio restante (por debajo del HUD): aquí se ancla el entrenador con
        // la hierba alta. `PercentageLayout` reparte sus hijos en porcentajes de
        // esta caja, no de la pantalla completa.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            PercentageLayout(modifier = Modifier.fillMaxSize()) {
                TrainerGrassStack(
                    trainerFrameRes = state.trainerFrameRes,
                    showTallGrass = state.isTallGrass,
                    modifier = Modifier.anchorVertically(MainLayout.TRAINER_BIAS)
                )

                TallGrassButton(
                    onClick = onTallGrassToggle,
                    modifier = Modifier
                        .size(MainLayout.TALL_GRASS_SIZE)
                        .anchor(
                            verticalBias = MainLayout.TALL_GRASS_BIAS,
                            horizontalBias = MainLayout.TALL_GRASS_H_BIAS
                        )
                )
            }
        }
    }
}

/**
 * Grupo trainer + hierba alta.
 *
 * Las tres imágenes se apilan en un mismo [Box], así que comparten **exactamente**
 * la misma posición: basta con cambiar el sesgo del grupo para moverlas todas.
 * El orden de dibujo en un `Box` es de fondo a frente.
 */
@Composable
private fun TrainerGrassStack(
    trainerFrameRes: Int,
    showTallGrass: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Capa 1 · fondo: hierba alta completa
        if (showTallGrass) {
            TallGrassLayer(
                drawableRes = R.drawable.tall_grass_full,
                size = MainLayout.TALL_GRASS_FULL_SIZE,
                offsetX = MainLayout.TALL_GRASS_X_OFFSET,
                offsetY = MainLayout.TALL_GRASS_Y_OFFSET
            )
        }

        // Capa 2 · medio: trainer
        Image(
            painter = painterResource(trainerFrameRes),
            contentDescription = stringResource(R.string.trainer_desc),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(MainLayout.TRAINER_SIZE)
        )

        // Capa 3 · frente: hierba alta parcial
        if (showTallGrass) {
            TallGrassLayer(
                drawableRes = R.drawable.tall_grass_mid,
                size = MainLayout.TALL_GRASS_MID_SIZE,
                offsetX = MainLayout.TALL_GRASS_X_OFFSET,
                offsetY = MainLayout.TALL_GRASS_Y_OFFSET
            )
        }
    }
}

@Composable
private fun TallGrassLayer(drawableRes: Int, size: Dp, offsetX: Dp = 0.dp, offsetY: Dp = 0.dp) {
    Image(
        painter = painterResource(drawableRes),
        contentDescription = stringResource(R.string.tall_grass_desc),
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(size)
            .offset(x = offsetX, y = offsetY)
    )
}

/**
 * Botón de hierba alta, situado arriba a la derecha del trainer.
 * Entra o sale del modo hierba alta (que también muestra/oculta las capas).
 *
 * Usa `anchor(...)` con los dos sesgos en una sola llamada: encadenar
 * `anchorVertically` + `anchorHorizontally` crearía dos datos de anclaje y solo
 * se aplicaría el último.
 */
@Composable
private fun TallGrassButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.tall_grass_button),
        contentDescription = stringResource(R.string.tall_grass_desc),
        contentScale = ContentScale.Fit,
        modifier = modifier.clickable(onClick = onClick)
    )
}

// ─────────────────────────── Previews ───────────────────────────

/**
 * Especificación de dispositivo de los previews. Coincide **a propósito** con el
 * AVD de pruebas (`Medium_Phone`: 1080×2400 px @ 420 dpi ≈ 411×914 dp), para que
 * lo que se ve en el preview y en el emulador sea lo mismo.
 */
private const val PREVIEW_DEVICE = "spec:width=411dp,height=914dp,dpi=420"

/**
 * Estado de ejemplo realista para los previews. **No** vale el estado por defecto
 * (`GameUiState()`): con zona vacía, sin monedas ni principal, el HUD sale corto y
 * el preview engaña (parece que no se solapa cuando en el dispositivo sí).
 *
 * Los valores imitan una partida de principios de Ruta 1.
 */
private fun previewGameState(isTallGrass: Boolean = false) = GameUiState(
    playerName = "Sergio",
    coins = 40L,
    seenSpecies = setOf("bulbasaur", "rattata", "pidgey"),
    rewardedSpecies = setOf("bulbasaur"),
    medals = emptySet(),
    starterSpeciesId = "charmander",
    starterBond = 1,
    ownedSpecies = setOf("rattata", "pidgey"),
    leadSpeciesId = "bulbasaur",
    zoneIndex = 0,
    zoneClicks = 4L,
    isTallGrass = isTallGrass,
    walkProgress = if (isTallGrass) 0.4f else 0f,
    trainerFrameRes = R.drawable.front0,
    zoneName = "Ruta 1",
    zoneRequirement = 10L,
    ownedCount = 3,
    clickPower = 3L,
    maxHp = 5,
    leadName = "Bulbasaur",
    leadType = PokemonType.GRASS,
    isGymReady = false,
    needsStarter = false
)

/**
 * Montaje real de la pantalla principal para los previews: el [Scaffold] con el
 * [PokemonBottomMenu] reserva el mismo hueco que en la app (`CONTAINER_HEIGHT`),
 * que es justo lo que faltaba en los previews antiguos y hacía que el entrenador
 * saliera en otro sitio.
 */
@Composable
private fun MainScreenStage(state: GameUiState) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { PokemonBottomMenu(isHomeSelected = true) }
    ) { innerPadding ->
        MainScreen(
            state = state,
            onTallGrassToggle = {},
            onReset = {},
            onEnterGym = {},
            onOpenLead = {},
            onWipeGame = {},
            modifier = Modifier.padding(innerPadding)
        )
    }
}

/** Juego en reposo: HUD completo, entrenador y botón de hierba. */
@Preview(
    name = "1 · Juego",
    device = PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun MainScreenPreview() = MaterialTheme {
    MainScreenStage(state = previewGameState())
}

/** Modo hierba alta: capas de hierba sobre el entrenador y barra de búsqueda. */
@Preview(
    name = "2 · Hierba alta",
    device = PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun MainScreenTallGrassPreview() = MaterialTheme {
    MainScreenStage(state = previewGameState(isTallGrass = true))
}



/**
 * HUD de juego de la pantalla principal: nombre, monedas, zona y progreso, estado
 * de la hierba alta, principal, medallas y los botones de gimnasio, reset y
 * cambio de principal.
 *
 * Las mejoras comprables (`Mejorar` / `Vida`) se retiraron por no tener UI ni
 * forma de adquirirse; volverán en su propio menú en una etapa futura.
 *
 * Es **sin estado**: recibe el [GameUiState] ya calculado y reenvía las
 * interacciones como lambdas.
 */
@Composable
fun GameHud(
    state: GameUiState,
    onReset: () -> Unit,
    onEnterGym: () -> Unit,
    onOpenLead: () -> Unit,
    onWipeGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        state.playerName?.let { name ->
            Text(
                text = stringResource(R.string.hud_trainer, name),
                style = MaterialTheme.typography.titleMedium
            )
        }

        Text(
            text = stringResource(R.string.hud_coins, state.coins),
            style = MaterialTheme.typography.titleMedium
        )

        Text(text = state.zoneName, style = MaterialTheme.typography.headlineSmall)

        LinearProgressIndicator(
            progress = { state.zoneProgress },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(
                R.string.hud_zone_progress,
                state.zoneClicks,
                state.zoneRequirement
            )
        )

        if (state.isTallGrass) {
            Text(
                text = stringResource(R.string.hud_searching),
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(
                progress = { state.walkProgress },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            text = stringResource(
                R.string.hud_lead,
                state.leadName ?: stringResource(R.string.hud_lead_none)
            )
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.hud_owned, state.ownedCount))
            Text(text = stringResource(R.string.hud_zone_power, state.clickPower))
            Text(text = stringResource(R.string.hud_medals, state.medals.size))
        }

        if (state.isGymReady) {
            Button(onClick = onEnterGym) {
                Text(text = stringResource(R.string.action_gym, state.gymLeaderName.orEmpty()))
            }
        }

        // Reset y cambio de principal comparten fila: encajan juntos y dejan la
        // mitad inferior del HUD más despejada.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onReset) {
                Text(text = stringResource(R.string.action_reset))
            }
            Button(onClick = onOpenLead) {
                Text(text = stringResource(R.string.lead_change))
            }
        }

        // Provisional: borra toda la partida (para testear la intro). Ver
        // `MainViewModel.onWipeGame`.
        OutlinedButton(onClick = onWipeGame) {
            Text(text = stringResource(R.string.action_wipe))
        }
    }
}
