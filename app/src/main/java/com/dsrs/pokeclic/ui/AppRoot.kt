/**
 * Shell de la aplicación: `Scaffold`, selector de pestaña, bottom menu y todas
 * las capas modales (intro, captura, principal y opciones).
 *
 * Es **sin estado**: recibe `GameUiState` ya derivado y emite eventos por lambdas;
 * no conoce el ViewModel ni calcula nada de negocio.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.AppTab
import com.dsrs.pokeclic.model.GymState
import com.dsrs.pokeclic.model.LeadOption
import com.dsrs.pokeclic.model.PokemonType
import com.dsrs.pokeclic.model.StarterOption
import com.dsrs.pokeclic.viewmodel.GameUiState

/**
 * Shell de la aplicación: contenedor de pantallas + bottom menu siempre visible.
 *
 * Es un composable **sin estado**: recibe todo el [GameUiState] (juego y
 * navegación) y reenvía cada interacción como una lambda. No conoce el ViewModel
 * ni gestiona `FragmentManager`.
 *
 * Capas, de abajo a arriba:
 * 1. En combate, el fondo del gimnasio a pantalla completa; si no, el contenido
 *    de la pestaña ([AppTab.Home] o [AppTab.Map]).
 * 2. Sobre el contenido: el [GymOverlay] y el [CaptureOverlay].
 * 3. Sobre todo: la introducción (onboarding), el [LeadOverlay] y la
 *    [OptionsSheet].
 *
 * Mientras hay combate ([GameUiState.gym] no es nulo) se ocultan la pantalla de
 * contenido **y el bottom menu al completo** (barra y Pokéball): todo el espacio
 * es para la escena, que ya trae sus propios controles.
 *
 * @param state estado completo: juego, pokéball y navegación.
 * @param onPokeBallTouch evento de toque sobre la pokéball (x, y y diámetro en px).
 * @param onTallGrassToggle entra o sale del modo hierba alta.
 * @param onReset reinicia la run conservando la meta.
 * @param onWipeGame borra toda la partida (provisional, para testear la intro).
 * @param onEnterGym / @param onLeaveGym entran o abandonan el gimnasio.
 * @param onGymRetry reintenta el combate; @param onGymProjectileHit rompe un
 *   proyectil; @param onGymEnemyHit golpea al líder expuesto.
 * @param onFinishIntro cierra la introducción con el nombre y el starter elegidos.
 * @param onSelectLead equipa un principal.
 * @param onSelectTab cambia de pantalla de contenido.
 * @param onOpenOptions / @param onCloseOptions gestionan la hoja de opciones.
 * @param onOpenLead / @param onCloseLead gestionan el selector de principal.
 * @param onPokeBallExpanded avisa del resultado del gesto de arrastre.
 */
@Composable
fun AppRoot(
    state: GameUiState,
    onPokeBallTouch: (touchX: Float, touchY: Float, sizePx: Float) -> Unit,
    onTallGrassToggle: () -> Unit,
    onReset: () -> Unit,
    onWipeGame: () -> Unit,
    onEnterGym: () -> Unit,
    onLeaveGym: () -> Unit,
    onGymRetry: () -> Unit,
    onGymProjectileHit: (Long) -> Unit,
    onGymEnemyHit: () -> Unit,
    onFinishIntro: (playerName: String, starterSpeciesId: String) -> Unit,
    onSelectLead: (String) -> Unit,
    onSelectTab: (AppTab) -> Unit,
    onOpenOptions: () -> Unit,
    onCloseOptions: () -> Unit,
    onOpenLead: () -> Unit,
    onCloseLead: () -> Unit,
    onPokeBallExpanded: (Boolean) -> Unit
) {
    val pokeBallSizePx = with(LocalDensity.current) { BottomMenuDefaults.POKEBALL_SIZE.toPx() }

    val gym = state.gym
    val gymActive = gym != null

    Box(modifier = Modifier.fillMaxSize()) {
        // Fondo exclusivo del combate: se dibuja a pantalla completa detrás de
        // todo y el `Scaffold` se vuelve transparente para dejarlo ver.
        if (gymActive) {
            Image(
                painter = painterResource(R.drawable.rock_gym),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Scaffold(
            containerColor = if (gymActive) Color.Transparent else MaterialTheme.colorScheme.background,
            bottomBar = {
                // En combate no se dibuja el menú: la escena del gimnasio ocupa
                // toda la altura y ya tiene sus propios controles.
                if (!gymActive) {
                    PokemonBottomMenu(
                        isMapSelected = !state.isOptionsOpen && state.contentTab == AppTab.Map,
                        isPokedexSelected = state.isOptionsOpen,
                        isHomeSelected = !state.isOptionsOpen && state.contentTab == AppTab.Home,
                        onMapClick = { onSelectTab(AppTab.Map) },
                        onPokedexClick = onOpenOptions,
                        onHomeClick = { onSelectTab(AppTab.Home) },
                        onPokeBallGameClick = { offset ->
                            onPokeBallTouch(offset.x, offset.y, pokeBallSizePx)
                        },
                        onExpandedChange = onPokeBallExpanded
                    )
                }
            }
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                // En combate no se dibuja la pantalla de fondo (entrenador,
                // hierba, HUD...); queda solo la escena sobre el gimnasio.
                if (!gymActive) {
                    when (state.contentTab) {
                        AppTab.Home -> MainScreen(
                            state = state,
                            onTallGrassToggle = onTallGrassToggle,
                            onReset = onReset,
                            onEnterGym = onEnterGym,
                            onOpenLead = onOpenLead,
                            onWipeGame = onWipeGame
                        )
                        AppTab.Map -> MapScreen()
                    }
                }

                gym?.let { activeGym ->
                    GymOverlay(
                        state = activeGym,
                        onProjectileHit = onGymProjectileHit,
                        onEnemyHit = onGymEnemyHit,
                        onRetry = onGymRetry,
                        onLeave = onLeaveGym
                    )
                }

                if (!gymActive) {
                    state.encounter?.let { encounter ->
                        CaptureOverlay(encounter = encounter)
                    }
                }
            }
        }
    }

    if (state.needsStarter) {
        IntroOverlay(starters = state.starterOptions, onFinish = onFinishIntro)
    }

    if (state.isLeadOpen) {
        LeadOverlay(
            options = state.leadOptions,
            currentId = state.leadSpeciesId ?: state.starterSpeciesId,
            onSelect = onSelectLead,
            onDismiss = onCloseLead
        )
    }

    if (state.isOptionsOpen) {
        OptionsSheet(onDismiss = onCloseOptions)
    }
}

// ─────────────────────────── Previews ───────────────────────────

/**
 * Especificación de dispositivo de los previews. Coincide **a propósito** con el
 * AVD de pruebas (`Medium_Phone`: 1080×2400 px @ 420 dpi ≈ 411×914 dp) para que
 * preview y emulador muestren lo mismo.
 */
private const val PREVIEW_DEVICE = "spec:width=411dp,height=914dp,dpi=420"

/**
 * Estado de ejemplo realista. No sirve `GameUiState()` en blanco: con la zona
 * vacía y sin monedas el HUD sale corto y el preview engaña.
 */
private fun previewGameState(
    isTallGrass: Boolean = false,
    gym: GymState? = null,
    needsStarter: Boolean = false
) = GameUiState(
    playerName = if (needsStarter) null else "Sergio",
    coins = if (needsStarter) 0L else 40L,
    seenSpecies = setOf("bulbasaur", "rattata", "pidgey"),
    rewardedSpecies = setOf("bulbasaur"),
    starterSpeciesId = if (needsStarter) null else "charmander",
    starterBond = if (needsStarter) 0 else 1,
    ownedSpecies = setOf("rattata", "pidgey"),
    leadSpeciesId = if (needsStarter) null else "bulbasaur",
    zoneIndex = 0,
    zoneClicks = 4L,
    isTallGrass = isTallGrass,
    gym = gym,
    trainerFrameRes = R.drawable.front0,
    zoneName = "Ruta 1",
    zoneRequirement = 10L,
    clickPower = 3L,
    maxHp = 5,
    leadName = if (needsStarter) null else "Bulbasaur",
    leadType = if (needsStarter) null else PokemonType.GRASS,
    needsStarter = needsStarter,
    leadOptions = if (needsStarter) emptyList() else listOf(
        LeadOption("bulbasaur", "Bulbasaur"),
        LeadOption("rattata", "Rattata")
    ),
    starterOptions = listOf(
        StarterOption("bulbasaur", "Bulbasaur", PokemonType.GRASS),
        StarterOption("charmander", "Charmander", PokemonType.FIRE),
        StarterOption("squirtle", "Squirtle", PokemonType.WATER)
    )
)

/**
 * Montaje completo de la app para los previews ([AppRoot] con sus overlays). El
 * shell ya no conoce ViewModels ni Fragments, así que reproduce la composición
 * real.
 */
@Composable
private fun AppRootStage(state: GameUiState) {
    AppRoot(
        state = state,
        onPokeBallTouch = { _, _, _ -> },
        onTallGrassToggle = {},
        onReset = {},
        onWipeGame = {},
        onEnterGym = {},
        onLeaveGym = {},
        onGymRetry = {},
        onGymProjectileHit = {},
        onGymEnemyHit = {},
        onFinishIntro = { _, _ -> },
        onSelectLead = {},
        onSelectTab = {},
        onOpenOptions = {},
        onCloseOptions = {},
        onOpenLead = {},
        onCloseLead = {},
        onPokeBallExpanded = {}
    )
}

/** Juego normal: HUD arriba y bottom menu abajo. */
@Preview(
    name = "1 · Juego",
    device = PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun AppRootGamePreview() = MaterialTheme {
    AppRootStage(state = previewGameState())
}

/** Modo hierba alta. */
@Preview(
    name = "2 · Hierba alta",
    device = PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun AppRootTallGrassPreview() = MaterialTheme {
    AppRootStage(state = previewGameState(isTallGrass = true))
}

/** Combate de gimnasio: fondo a pantalla completa y HUD del combate. */
@Preview(
    name = "3 · Gimnasio",
    device = PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun AppRootGymPreview() = MaterialTheme {
    AppRootStage(
        state = previewGameState(
            gym = GymState(
                gymId = "brock",
                leaderName = "Brock",
                gymType = PokemonType.ROCK,
                leaderHp = 42,
                leaderMaxHp = 60,
                playerHp = 4,
                maxHp = 5,
                columns = 5,
                waveDurationMs = 10_000L,
                waveRemainingMs = 6_000L,
                exposureDurationMs = 2_500L,
                leadSpeciesId = "bulbasaur",
                leadName = "Bulbasaur",
                leadType = PokemonType.GRASS
            )
        )
    )
}

/** Primera partida: se muestra la introducción del Profesor Oak. */
@Preview(
    name = "4 · Introducción",
    device = PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun AppRootIntroPreview() = MaterialTheme {
    AppRootStage(state = previewGameState(needsStarter = true))
}
