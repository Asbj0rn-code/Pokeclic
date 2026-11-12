/**
 * Shell de la aplicación: `Scaffold`, selector de pestaña, bottom menu y todas
 * las capas modales (intro, captura, principal y opciones).
 *
 * Es **sin estado**: recibe `GameUiState` ya derivado y emite eventos por lambdas;
 * no conoce el ViewModel ni calcula nada de negocio.
 */
package com.dsrs.pokeclic.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes
import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.AppTab
import com.dsrs.pokeclic.model.Direction
import com.dsrs.pokeclic.model.Encounter
import com.dsrs.pokeclic.model.CatchingDefaults
import com.dsrs.pokeclic.model.CatchingGame
import com.dsrs.pokeclic.model.CatchingState
import com.dsrs.pokeclic.model.GymIntroPhase
import com.dsrs.pokeclic.model.GymMechanic
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
 * 1. En escena de gimnasio (combate **o** capa de entrada: [GymIntroLayer]), el
 *    fondo del gimnasio a pantalla completa (cueva o piscina); si no, en zonas con fauna el
 *    [RouteScrollingBackground] (bucle vertical u horizontal, según la zona,
 *    movido por las pulsaciones de la Pokéball) y, si la pestaña de contenido es
 *    el mapa, el [MapOceanBackdrop]
 *    (color del océano del mapa). Encima, el contenido de la pestaña
 *    ([AppTab.Home] o [AppTab.Map]).
 *
 *    Ese mismo movimiento decide **hacia dónde camina el entrenador**: cada clic
 *    se anima en la dirección del eje que le toca (en vertical, recto → de
 *    espaldas y lateral → derecha o izquierda; en horizontal, recto → derecha).
 *    La Pokébola ya no elige dirección según el lado que se toca; tras un segundo
 *    sin pulsar, el sprite vuelve a `front` quieto.
 * 2. Sobre el contenido: el [GymOverlay] y el [CatchingOverlay] (partida de
 *    captura del encuentro actual).
 * 3. Sobre todo: la introducción (onboarding), el selector de **entrada al
 *    gimnasio** ([GymIntroLayer]), el [LeadOverlay] y la [OptionsSheet].
 *
 * Mientras hay combate ([GameUiState.gym] no es nulo) o partida de captura
 * ([GameUiState.catching] no es nulo) se ocultan la pantalla de contenido **y el
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
 * @param onPokeBallTouch evento de pulsación sobre la Pokébola: recibe la
 *   dirección en la que debe animarse el entrenador, que la marca el eje por el
 *   que se mueve el fondo.
 * @param onTallGrassToggle entra o sale del modo hierba alta.
 * @param onReset reinicia la run conservando la meta.
 * @param onWipeGame borra toda la partida (provisional, para testear la intro).
 * @param onDebugJumpToZone salta a una zona concreta (provisional, para testear
 *   contenido sin recorrer el mapa; ver `MainViewModel.onDebugJumpToZone`).
 * @param onEnterGym / @param onLeaveGym entran o abandonan el gimnasio.
 * @param onWatchGymTutorial "Ver tutorial" de la entrada al gimnasio.
 * @param onGymIntroSkip "Omitir" de la entrada al gimnasio.
 * @param onGymIntroAccepted "Entendido" (tutorial del gimnasio).
 * @param onAdvanceZone pulsa el botón Avanzar al llenarse la barra de la zona.
 * @param onGymRetry reintenta el combate; @param onGymProjectileHit rompe un
 *   proyectil; @param onGymRingTap intenta acertar el anillo más cercano;
 *   @param onGymVoltorbTap apaga el Voltorb de una celda (o sufre su descarga);
 *   @param onGymEnemyHit golpea al líder expuesto.
 * @param onCatchingHold notifica si el jugador mantiene pulsada la pantalla
 *   durante la partida de captura (true: pulsado; false: soltado).
 * @param onCatchingResultContinue cierra el resultado de la partida de captura.
 * @param onFinishIntro cierra la introducción con el nombre y el starter elegidos.
 * @param onSelectLead equipa un principal.
 * @param onSelectTab cambia de pantalla de contenido.
 * @param onOpenOptions / @param onCloseOptions gestionan la hoja de opciones.
 * @param onOpenLead / @param onCloseLead gestionan el selector de principal.
 * @param onOpenDex abre la Pokédex.
 * @param onCloseDex cierra la Pokédex y vuelve al juego.
 * @param onPokeBallExpanded avisa del resultado del gesto de arrastre.
 */
@Composable
fun AppRoot(
    state: GameUiState,
    isSplashShowing: Boolean = false,
    onPokeBallTouch: (direction: Direction) -> Unit,
    onTallGrassToggle: () -> Unit,
    onReset: () -> Unit,
    onWipeGame: () -> Unit,
    onDebugJumpToZone: (String) -> Unit,
    onEnterGym: () -> Unit,
    onLeaveGym: () -> Unit,
    onWatchGymTutorial: () -> Unit,
    onGymIntroSkip: () -> Unit,
    onGymIntroAccepted: () -> Unit,
    onAdvanceZone: () -> Unit,
    onGymRetry: () -> Unit,
    onGymProjectileHit: (Long) -> Unit,
    onGymRingTap: () -> Unit,
    onGymVoltorbTap: (Int) -> Unit,
    onGymEnemyHit: () -> Unit,
    onCatchingHold: (Boolean) -> Unit,
    onCatchingResultContinue: () -> Unit,
    onFinishIntro: (playerName: String, starterSpeciesId: String) -> Unit,
    onSelectLead: (String) -> Unit,
    onSelectTab: (AppTab) -> Unit,
    onOpenOptions: () -> Unit,
    onCloseOptions: () -> Unit,
    onOpenLead: () -> Unit,
    onCloseLead: () -> Unit,
    onOpenDex: () -> Unit,
    onCloseDex: () -> Unit,
    onPokeBallExpanded: (Boolean) -> Unit
) {
    // Contador de pulsaciones sobre la Pokébola: es lo único que mueve el fondo
    // de ruta y, con él, la dirección en la que camina el entrenador. Estado
    // efímero de UI, sin relación con la lógica de negocio.
    //
    // La clave es `state.zoneId`: la fase del recorrido es **por zona**. Al entrar
    // en otra zona el contador vuelve a cero y su trayectoria arranca por el primer
    // clic, en vez de heredar el punto en el que se quedó la zona anterior (que
    // hacía, por ejemplo, empezar con los últimos clics de una pierna lateral).
    val pokePulses = remember(state.zoneId) { mutableLongStateOf(0L) }

    // Pokédex: carcasa a pantalla (cabecera, marco NinePatch y base roja) con
    // el menú real compuesto encima de la base. Los destinos del menú cierran
    // la Dex y navegan; la Pokébola expandida no mueve al entrenador desde la
    // Pokédex y el botón Pokédex ya no abre la hoja de opciones en este
    // contexto. La vuelta también está con el botón físico (BackHandler
    // interno de la pantalla).
    if (state.isPokedexOpen) {
        val density = LocalDensity.current
        // Espacio que el menú ocupa abajo: su contenedor derivado
        // (`BottomMenuDefaults.CONTAINER_HEIGHT`) más la franja de navegación
        // del sistema que el propio menú rellena de acero. La base roja de la
        // Dex se dibuja exactamente detrás de esa zona.
        val menuReserved = BottomMenuDefaults.CONTAINER_HEIGHT +
            with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
        Box(modifier = Modifier.fillMaxSize()) {
            DexScreen(
                state = state.toDexUiState(),
                bottomReserved = menuReserved,
                onBack = onCloseDex
            )
            PokemonBottomMenu(
                modifier = Modifier.align(Alignment.BottomCenter),
                isPokedexSelected = true,
                // Sin juego bajo la bola: el arrastre no la estira y cuenta
                // como toque, que devuelve a la aventura.
                expansionEnabled = false,
                onMapClick = {
                    onCloseDex()
                    onSelectTab(AppTab.Map)
                },
                onPokedexClick = {},
                onHomeClick = {
                    onCloseDex()
                    onSelectTab(AppTab.Home)
                },
                onPokeBallGameClick = {},
                onExpandedChange = onPokeBallExpanded
            )
        }
        return
    }

    // Fondo de la zona actual: arte + trayectoria. La **misma** instancia mueve el
    // dibujo y decide la dirección del entrenador, así que no pueden desincronizarse.
    val routeBackground = RouteBackgrounds.forZone(state.zoneId)

    val gym = state.gym
    val gymActive = gym != null
    // Escena de gimnasio: combate **o** capa de entrada (bienvenida, tutorial y
    // cartel de arranque). Con la entrada activa aún no hay `GymState`, pero el
    // escenario (fondo de la cueva) debe verse ya detrás de esa capa.
    val gymScene = gymActive || state.gymIntro != null
    val catching = state.catching
    val catchingActive = catching != null && state.encounter != null && !gymActive
    // Takeover de escena: gimnasio (combate o entrada) y captura ocultan HUD y
    // menú por completo.
    val sceneActive = gymScene || catchingActive
    // Fondo de ruta animado: solo en la pantalla de juego, en zonas con fauna
    // (rutas, bosque y cueva) o con arte propio (Pueblo Paleta, Ciudad Verde). Las
    // ciudades sin fondo propio y la pestaña Mapa conservan el fondo liso.
    //
    // La captura es la excepción: la escena del minijuego es transparente y
    // quiere la ruta congelada detrás, así que el fondo se compone también con
    // `catchingActive`. El gimnasio sigue apagándolo porque tiene su propio arte.
    val showRouteBackground = state.contentTab == AppTab.Home &&
        (state.hasWilds || RouteBackgrounds.hasOwnArtwork(state.zoneId)) &&
        (!sceneActive || catchingActive) && !state.needsStarter
    // Fondo de océano del mapa: misma técnica que el fondo de ruta (capa detrás
    // del Scaffold transparente) para que el océano llegue hasta la zona flotante
    // de la Pokéball sin franja blanca. Solo con la pestaña Mapa visible.
    val showMapOceanBackdrop = state.contentTab == AppTab.Map &&
        !sceneActive && !state.needsStarter
    // Aislamiento de capas modales (issue #21): con la entrada al gimnasio (ya
    // cubierta por `sceneActive`), la intro, el selector de principal o la
    // cortinilla de arranque activos, el menú no se compone: su Pokéball ignora
    // el consumo de toques y solo su ausencia la aísla. El contenido del juego
    // solo desaparece bajo capas opacas (escena e intro). La Pokédex compone
    // su propio menú en su rama, así que aquí no hace falta ocultarlo.
    val menuHidden = sceneActive || state.needsStarter ||
        state.isLeadOpen || isSplashShowing

    Box(modifier = Modifier.fillMaxSize()) {
        // Fondo del gimnasio: combate y capa de entrada comparten la escena, de
        // modo que al caducar el cartel el combate arranque sin salto de fondo.
        // Cada mecánica tiene su arte: cueva de roca para Brock, piscina para
        // Misty y sala eléctrica para Lt. Surge. Está detrás de todo y el
        // `Scaffold` se vuelve transparente.
        if (gymScene) {
            when (state.gymMechanic) {
                GymMechanic.PROJECTILES -> GymBackground(R.drawable.rock_gym)
                GymMechanic.TIMING_RINGS -> GymBackground(R.drawable.water_gym)
                GymMechanic.VOLTORB_GRID -> GymBackground(R.drawable.electric_gym)
            }
        } else if (showRouteBackground) {
            // Fondo de ruta: bucle con deriva lateral por tramos (o de lado, si la
            // zona es horizontal), movido con las pulsaciones de la Pokéball. Va
            // detrás del `Scaffold`, que
            // también se vuelve transparente para que se vea en el hueco de la
            // barra.
            RouteScrollingBackground(
                pulseCount = pokePulses.longValue,
                modifier = Modifier
                    .fillMaxSize()
                    .routeCaptureDepth(catchingActive),
                motion = routeBackground.motion,
                imageRes = routeBackground.imageRes,
                seamCutRow = routeBackground.seamCutRow,
                startAnchor = routeBackground.startAnchor,
                startOffset = routeBackground.startOffset
            )
        } else if (showMapOceanBackdrop) {
            // Océano del mapa: rellena también el hueco inferior del menú (la
            // zona flotante de la Pokéball), como hiciera el fondo de ruta.
            MapOceanBackdrop(modifier = Modifier.fillMaxSize())
        }

        Scaffold(
            // El fondo propio (gimnasio, ruta u océano) se compone detrás, así que
            // el Scaffold tiene que dejar verlo.
            containerColor = if (gymScene || showRouteBackground || showMapOceanBackdrop) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.background
            },
            bottomBar = {
                // En combate o captura no se dibuja el menú: la escena ocupa toda
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
                        onPokeBallGameClick = {
                            // Modo captura (hierba alta o encuentro en curso): el
                            // mundo se congela; el clic no mueve el fondo ni
                            // cuenta para el avance de la zona.
                            if (advancesRouteWorld(state.isTallGrass, state.encounter != null)) {
                                pokePulses.longValue++
                            }
                            // El sprite sigue el eje por el que se acaba de mover el
                            // fondo: en vertical, recto → back, lateral derecho →
                            // right, izquierdo → left; en horizontal, recto → right.
                            // La Pokébola ya no elige dirección por el lado que se
                            // toca.
                            onPokeBallTouch(
                                routeBackground.motion.directionForStep(pokePulses.longValue)
                            )
                        },
                        onExpandedChange = onPokeBallExpanded
                    )
                }
            }
        ) { innerPadding ->
            // En la pestaña Mapa el contenido **no** se desplaza por la altura del
            // bottom menu: `MapScreen` compone su fondo y su dibujo hasta el fondo
            // de pantalla (así el mapa ampliado se ve tras las partes transparentes
            // del menú) y recibe como `bottomReserved` la altura reservada, que
            // descuenta solo de su área de gestos y de referencia. El resto de
            // pestañas conservan el padding exacto de siempre.
            val layoutDirection = LocalLayoutDirection.current
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(
                        when (state.contentTab) {
                            AppTab.Map -> PaddingValues(
                                top = innerPadding.calculateTopPadding(),
                                start = innerPadding.calculateStartPadding(layoutDirection),
                                end = innerPadding.calculateEndPadding(layoutDirection)
                            )
                            else -> innerPadding
                        }
                    )
            ) {
                // En gimnasio (combate o capa de entrada) o captura no se dibuja
                // la pantalla de fondo (entrenador, hierba, HUD...); queda solo
                // la escena encima. La intro es opaca, así que debajo tampoco
                // se compone nada.
                if (!sceneActive && !state.needsStarter) {
                    when (state.contentTab) {
                        AppTab.Home -> MainScreen(
                            state = state,
                            onTallGrassToggle = onTallGrassToggle,
                            onEnterGym = onEnterGym,
                            onAdvanceZone = onAdvanceZone
                        )
                        AppTab.Map -> MapScreen(
                            bottomReserved = innerPadding.calculateBottomPadding()
                        )
                    }
                }

                gym?.let { activeGym ->
                    GymOverlay(
                        state = activeGym,
                        onProjectileHit = onGymProjectileHit,
                        onRingTap = onGymRingTap,
                        onVoltorbTap = onGymVoltorbTap,
                        onEnemyHit = onGymEnemyHit,
                        onRetry = onGymRetry,
                        onLeave = onLeaveGym,
                        // Con una capa de entrada encima (al reintentar), el panel
                        // de derrota se superponía al cartel de arranque.
                        showDefeat = state.gymIntro == null
                    )
                }

                catching?.let { activeCatching ->
                    state.encounter?.let { encounter ->
                        if (!gymActive) {
                            CatchingOverlay(
                                encounter = encounter,
                                catching = activeCatching,
                                onHoldChanged = onCatchingHold,
                                onContinue = onCatchingResultContinue
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
            mechanic = state.gymMechanic,
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
            // La hoja se cierra antes de ejecutar la acción: el reset deja ver el
            // juego ya reiniciado y el selector de principal (`LeadOverlay`)
            // necesita la hoja fuera de en medio.
            onReset = {
                onCloseOptions()
                onReset()
            },
            onOpenLead = {
                onCloseOptions()
                onOpenLead()
            },
            onOpenPokedex = onOpenDex,
            onWipeGame = onWipeGame,
            onDebugJumpToZone = { zoneId ->
                onCloseOptions()
                onDebugJumpToZone(zoneId)
            }
        )
    }
}

/**
 * ¿Debe este clic de la Pokébola mover el fondo de ruta?
 *
 * En modo captura — hierba alta abierta o encuentro en curso — el mundo se
 * congela: el clic no desplaza el fondo ni cuenta para el avance de la zona
 * (eso ya lo evita `MainViewModel.onPokeBallTouch`, que no suma `zoneClicks`
 * en esos dos estados). Fuera de él, cada pulsación avanza el recorrido.
 */
internal fun advancesRouteWorld(isTallGrass: Boolean, encounterActive: Boolean): Boolean =
    !isTallGrass && !encounterActive

/** Radio del desenfoque del fondo de ruta mientras dura la captura. */
private val RouteCaptureBlurRadius = 8.dp

/** Velo sobre la ruta difuminada: la oscurece para que la UI gane foco. */
private val RouteCaptureScrim = Color(0x59000000)

/** Velo de respaldo donde no hay desenfoque (Android 11 o anterior). */
private val RouteCaptureFallbackScrim = Color(0x66000000)

/**
 * Hunde el fondo de ruta durante la captura para que la escena gane foco.
 *
 * `Modifier.blur` solo dibuja en Android 12+ (antes es un no-op), así que en
 * versiones previas se aplica un velo oscuro con la misma intención: separar el
 * fondo de la interfaz sin quitarlo de la vista.
 */
private fun Modifier.routeCaptureDepth(active: Boolean): Modifier = when {
    !active -> this
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        blur(RouteCaptureBlurRadius, BlurredEdgeTreatment.Rectangle)
            .drawWithContent {
                drawContent()
                drawRect(RouteCaptureScrim)
            }
    else -> drawWithContent {
        drawContent()
        drawRect(RouteCaptureFallbackScrim)
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
    catching: CatchingState? = null,
    isPokedexOpen: Boolean = false
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
    catching = catching,
    isPokedexOpen = isPokedexOpen,
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
 * Fondo del gimnasio: arte de cada mecánica a pantalla completa.
 */
@Composable
private fun GymBackground(@DrawableRes resId: Int) {
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Montaje completo de la app para los previews ([AppRoot] con sus overlays). El
 * shell ya no conoce ViewModels ni Fragments, así que reproduce la composición
 * real.
 */
@Composable
private fun AppRootStage(state: GameUiState) {
    AppRoot(
        state = state,
        onPokeBallTouch = { _ -> },
        onTallGrassToggle = {},
        onReset = {},
        onWipeGame = {},
        onDebugJumpToZone = {},
        onEnterGym = {},
        onLeaveGym = {},
        onWatchGymTutorial = {},
        onGymIntroSkip = {},
        onGymIntroAccepted = {},
        onAdvanceZone = {},
        onGymRetry = {},
        onGymProjectileHit = {},
        onGymRingTap = {},
        onGymVoltorbTap = {},
        onGymEnemyHit = {},
        onCatchingHold = {},
        onCatchingResultContinue = {},
        onFinishIntro = { _, _ -> },
        onSelectLead = {},
        onSelectTab = {},
        onOpenOptions = {},
        onCloseOptions = {},
        onOpenLead = {},
        onCloseLead = {},
        onOpenDex = {},
        onCloseDex = {},
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
                posture = 8,
                postureMax = 14,
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

/** Partida de captura (Etapa 3): escena a pantalla completa sobre el encuentro. */
@Preview(
    name = "5 · Captura",
    device = PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun AppRootCatchingPreview() = MaterialTheme {
    val config = CatchingDefaults.configFor(captureDifficulty = 0.15f)
    val encounter = Encounter(
        speciesId = "rattata",
        speciesName = "Rattata",
        speciesType = PokemonType.NORMAL,
        speciesDexNumber = 19
    )
    AppRootStage(
        state = previewGameState(
            encounter = encounter,
            catching = CatchingGame.initialState(config)
        )
    )
}

/** Pokédex: carcasa completa (cabecera, marco y base) con el menú real. */
@Preview(
    name = "6 · Pokédex",
    device = PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun AppRootDexPreview() = MaterialTheme {
    AppRootStage(state = previewGameState(isPokedexOpen = true))
}
