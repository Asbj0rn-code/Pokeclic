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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.AppTab
import com.dsrs.pokeclic.model.Encounter
import com.dsrs.pokeclic.model.FishingDefaults
import com.dsrs.pokeclic.model.FishingGame
import com.dsrs.pokeclic.model.FishingState
import com.dsrs.pokeclic.model.GymIntroPhase
import com.dsrs.pokeclic.model.GymState
import com.dsrs.pokeclic.model.LeadOption
import com.dsrs.pokeclic.model.PokemonType
import com.dsrs.pokeclic.model.StarterOption
import com.dsrs.pokeclic.viewmodel.GameUiState
import kotlin.random.Random

/**
 * Shell de la aplicación: contenedor de pantallas + bottom menu siempre visible.
 *
 * Es un composable **sin estado**: recibe todo el [GameUiState] (juego y
 * navegación) y reenvía cada interacción como una lambda. No conoce el ViewModel
 * ni gestiona `FragmentManager`.
 *
 * Capas, de abajo a arriba:
 * 1. En escena de gimnasio (combate **o** capa de entrada: [GymIntroLayer]), el
 *    fondo de la cueva a pantalla completa; si no, en zonas con fauna el
 *    [RouteScrollingBackground] (bucle vertical movido por las pulsaciones de
 *    la Pokéball) y, por encima, el contenido de la pestaña
 *    ([AppTab.Home] o [AppTab.Map]).
 * 2. Sobre el contenido: el [GymOverlay] y el [FishingOverlay] (partida de
 *    pesca del encuentro actual).
 * 3. Sobre todo: la introducción (onboarding), el selector de **entrada al
 *    gimnasio** ([GymIntroLayer]), el [LeadOverlay] y la [OptionsSheet].
 *
 * Mientras hay combate ([GameUiState.gym] no es nulo) o partida de pesca
 * ([GameUiState.fishing] no es nulo) se ocultan la pantalla de contenido **y el
 * bottom menu al completo** (barra y Pokéball): todo el espacio es para la
 * escena, que ya trae sus propios controles.
 *
 * Aislamiento de capas modales (issue #21): con la capa de entrada al gimnasio
 * o la intro ([GameUiState.needsStarter], opaca) activas no se compone tampoco
 * el contenido del juego; con el selector de principal ([GameUiState.isLeadOpen])
 * o la cortinilla de arranque ([isSplashShowing]) el juego queda visible pero
 * sin menú. En todos los casos el menú no se compone porque la Pokéball usa un
 * detector que **ignora el consumo de toques** (`requireUnconsumed = false`), así
 * que la única forma de aislarla es no dibujarla; el resto de controles del
 * juego (clicables normales) los bloquea el `Modifier.modalBlocking()` de la
 * propia capa.
 *
 * @param state estado completo: juego, pokéball y navegación.
 * @param isSplashShowing si la cortinilla de arranque ([PokeBallSplash], compuesta
 *   por la Activity) está cubriendo la pantalla; mientras tanto no se compone el
 *   menú.
 * @param onPokeBallTouch evento de toque sobre la pokéball (x, y y diámetro en px).
 * @param onTallGrassToggle entra o sale del modo hierba alta.
 * @param onReset reinicia la run conservando la meta.
 * @param onWipeGame borra toda la partida (provisional, para testear la intro).
 * @param onEnterGym / @param onLeaveGym entran o abandonan el gimnasio.
 * @param onWatchGymTutorial "Ver tutorial" de la entrada al gimnasio.
 * @param onGymIntroSkip "Omitir" de la entrada al gimnasio.
 * @param onGymIntroAccepted "Entendido" (tutorial del gimnasio).
 * @param onAdvanceZone pulsa el botón Avanzar al llenarse la barra de la zona.
 * @param onGymRetry reintenta el combate; @param onGymProjectileHit rompe un
 *   proyectil; @param onGymEnemyHit golpea al líder expuesto.
 * @param onFishingHold notifica si el jugador mantiene pulsada la pantalla
 *   durante la partida de pesca (true: pulsado; false: soltado).
 * @param onFishingResultContinue cierra el resultado de la partida de pesca.
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
    isSplashShowing: Boolean = false,
    onPokeBallTouch: (touchX: Float, touchY: Float, sizePx: Float) -> Unit,
    onTallGrassToggle: () -> Unit,
    onReset: () -> Unit,
    onWipeGame: () -> Unit,
    onEnterGym: () -> Unit,
    onLeaveGym: () -> Unit,
    onWatchGymTutorial: () -> Unit,
    onGymIntroSkip: () -> Unit,
    onGymIntroAccepted: () -> Unit,
    onAdvanceZone: () -> Unit,
    onGymRetry: () -> Unit,
    onGymProjectileHit: (Long) -> Unit,
    onGymEnemyHit: () -> Unit,
    onFishingHold: (Boolean) -> Unit,
    onFishingResultContinue: () -> Unit,
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

    // Contador de pulsaciones sobre la Pokéball: es lo único que mueve el fondo
    // de ruta. Estado efímero de UI, sin relación con la lógica de negocio.
    val pokePulses = remember { mutableLongStateOf(0L) }

    val gym = state.gym
    val gymActive = gym != null
    // Escena de gimnasio: combate **o** capa de entrada (bienvenida, tutorial y
    // cartel de arranque). Con la entrada activa aún no hay `GymState`, pero el
    // escenario (fondo de la cueva) debe verse ya detrás de esa capa.
    val gymScene = gymActive || state.gymIntro != null
    val fishing = state.fishing
    val fishingActive = fishing != null && state.encounter != null && !gymActive
    // Takeover de escena: gimnasio (combate o entrada) y pesca ocultan HUD y
    // menú por completo.
    val sceneActive = gymScene || fishingActive
    // Fondo de ruta animado: solo en la pantalla de juego y solo en zonas con
    // fauna (rutas, bosque y cueva). Las ciudades y la pestaña Mapa conservan
    // el fondo liso de siempre.
    val showRouteBackground = state.contentTab == AppTab.Home &&
        state.hasWilds && !sceneActive && !state.needsStarter
    // Aislamiento de capas modales (issue #21): con la entrada al gimnasio (ya
    // cubierta por `sceneActive`), la intro, el selector de principal o la
    // cortinilla de arranque activos, el menú no se compone: su Pokéball ignora
    // el consumo de toques y solo su ausencia la aísla. El contenido del juego
    // solo desaparece bajo capas opacas (escena e intro).
    val menuHidden = sceneActive || state.needsStarter ||
        state.isLeadOpen || isSplashShowing

    Box(modifier = Modifier.fillMaxSize()) {
        // Fondo del gimnasio: combate y capa de entrada comparten la cueva, de
        // modo que al caducar el cartel el combate arranque sin salto de fondo.
        // Está detrás de todo y el `Scaffold` se vuelve transparente.
        if (gymScene) {
            Image(
                painter = painterResource(R.drawable.rock_gym),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (showRouteBackground) {
            // Fondo de ruta: bucle vertical que se mueve con las pulsaciones de
            // la Pokéball. Va detrás del `Scaffold`, que también se vuelve
            // transparente para que se vea en el hueco de la barra.
            RouteScrollingBackground(
                pulseCount = pokePulses.longValue,
                modifier = Modifier.fillMaxSize()
            )
        }

        Scaffold(
            containerColor = when {
                gymScene -> Color.Transparent
                showRouteBackground -> Color.Transparent
                else -> MaterialTheme.colorScheme.background
            },
            bottomBar = {
                // En combate o pesca no se dibuja el menú: la escena ocupa toda
                // la altura y ya trae sus propios controles. Tampoco bajo las
                // capas modales (intro, principal, splash): issue #21.
                if (!menuHidden) {
                    PokemonBottomMenu(
                        isMapSelected = !state.isOptionsOpen && state.contentTab == AppTab.Map,
                        isPokedexSelected = state.isOptionsOpen,
                        isHomeSelected = !state.isOptionsOpen && state.contentTab == AppTab.Home,
                        onMapClick = { onSelectTab(AppTab.Map) },
                        onPokedexClick = onOpenOptions,
                        onHomeClick = { onSelectTab(AppTab.Home) },
                        onPokeBallGameClick = { offset ->
                            pokePulses.longValue++
                            onPokeBallTouch(offset.x, offset.y, pokeBallSizePx)
                        },
                        onExpandedChange = onPokeBallExpanded
                    )
                }
            }
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                // En gimnasio (combate o capa de entrada) o pesca no se dibuja
                // la pantalla de fondo (entrenador, hierba, HUD...); queda solo
                // la escena encima. La intro es opaca, así que debajo tampoco
                // se compone nada.
                if (!sceneActive && !state.needsStarter) {
                    when (state.contentTab) {
                        AppTab.Home -> MainScreen(
                            state = state,
                            onTallGrassToggle = onTallGrassToggle,
                            onReset = onReset,
                            onEnterGym = onEnterGym,
                            onAdvanceZone = onAdvanceZone,
                            onOpenLead = onOpenLead
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

                fishing?.let { activeFishing ->
                    state.encounter?.let { encounter ->
                        if (!gymActive) {
                            FishingOverlay(
                                encounter = encounter,
                                fishing = activeFishing,
                                onHoldChanged = onFishingHold,
                                onContinue = onFishingResultContinue
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.gymIntro != null) {
        GymIntroLayer(
            phase = state.gymIntro,
            leaderName = state.gymLeaderName,
            onWatchTutorial = onWatchGymTutorial,
            onSkip = onGymIntroSkip,
            onAccepted = onGymIntroAccepted
        )
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
        OptionsSheet(
            onDismiss = onCloseOptions,
            onWipeGame = onWipeGame
        )
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
    gymIntro: GymIntroPhase? = null,
    needsStarter: Boolean = false,
    encounter: Encounter? = null,
    fishing: FishingState? = null
) = GameUiState(
    playerName = if (needsStarter) null else "Sergio",
    coins = if (needsStarter) 0L else 40L,
    seenSpecies = setOf("bulbasaur", "rattata", "pidgey"),
    rewardedSpecies = setOf("bulbasaur"),
    starterSpeciesId = if (needsStarter) null else "charmander",
    starterBond = if (needsStarter) 0 else 1,
    ownedSpecies = setOf("rattata", "pidgey"),
    leadSpeciesId = if (needsStarter) null else "bulbasaur",
    zoneIndex = 1,
    zoneClicks = 4L,
    isTallGrass = isTallGrass,
    gym = gym,
    gymIntro = gymIntro,
    encounter = encounter,
    fishing = fishing,
    trainerFrameRes = R.drawable.front0,
    zoneName = "Ruta 1",
    zoneRequirement = 20L,
    gymLeaderName = "Brock",
    ownedCount = if (needsStarter) 0 else 3,
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
        onWatchGymTutorial = {},
        onGymIntroSkip = {},
        onGymIntroAccepted = {},
        onAdvanceZone = {},
        onGymRetry = {},
        onGymProjectileHit = {},
        onGymEnemyHit = {},
        onFishingHold = {},
        onFishingResultContinue = {},
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

/** Bienvenida al gimnasio: capa modal sobre el juego normal. */
@Preview(
    name = "3b · Bienvenida gimnasio",
    device = PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun AppRootGymIntroPreview() = MaterialTheme {
    AppRootStage(
        state = previewGameState(
            gymIntro = GymIntroPhase.WELCOME
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

/** Partida de pesca (Etapa 3): escena a pantalla completa sobre el encuentro. */
@Preview(
    name = "5 · Pesca",
    device = PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun AppRootFishingPreview() = MaterialTheme {
    val config = FishingDefaults.configFor(captureDifficulty = 0.15f)
    val encounter = Encounter(
        speciesId = "rattata",
        speciesName = "Rattata",
        speciesType = PokemonType.NORMAL
    )
    AppRootStage(
        state = previewGameState(
            encounter = encounter,
            fishing = FishingGame.initialState(config, Random(42))
        )
    )
}
