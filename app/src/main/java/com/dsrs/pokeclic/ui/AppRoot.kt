/**
 * Shell de la aplicación: `Scaffold`, selector de pestaña, bottom menu y todas
 * las capas modales (intro, captura, principal y opciones).
 *
 * Es **sin estado**: recibe `GameUiState` ya derivado y emite eventos por lambdas;
 * no conoce el ViewModel ni calcula nada de negocio.
 */
package com.dsrs.pokeclic.ui

import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
import com.dsrs.pokeclic.model.TutorialAction
import com.dsrs.pokeclic.model.TutorialPhase
import com.dsrs.pokeclic.model.isFreePlay
import com.dsrs.pokeclic.model.isMessageVisible
import com.dsrs.pokeclic.model.isModal
import com.dsrs.pokeclic.viewmodel.GameUiState

/**
 * Duración del fundido con el que aparece y desaparece el bottom menu cuando una
 * capa modal lo oculta (cortinilla de arranque, selector de principal). No
 * afecta al layout: el hueco del menú está reservado siempre en la pantalla de
 * juego (ver el `padding` del contenido en [AppRoot]).
 */
private const val MENU_FADE_MS = 220

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
    onGymProjectileMiss: () -> Unit,
    onGymRingTap: () -> Unit,
    onGymVoltorbTap: (Int) -> Unit,
    onGymEnemyHit: () -> Unit,
    onGymRageSpeechDone: () -> Unit,
    onGymVictoryDone: () -> Unit,
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
    onPokeBallExpanded: (Boolean) -> Unit,
    onTutorialAction: (TutorialAction) -> Unit,
    onTutorialMessageTap: () -> Unit
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

    // Atrás y tutorial (QA): mientras **cualquier** mensaje del
    // tutorial esté visible (preguntas, pasos, despedida o aviso), el botón
    // Atrás del sistema no navega ni sale de la capa — la respuesta es
    // explícita en sus botones o en los toques del propio diálogo. Cerrado el
    // mensaje, el Atrás vuelve a comportarse como siempre (en la Pokédex, su
    // cierre queda limitado por los guards del ViewModel).
    BackHandler(
        enabled = tutorialDialogVisible(state.tutorialPhase, state.tutorialMessageClosed)
    ) { }

    // Espacio que el menú ocupa abajo: su contenedor derivado
    // (`BottomMenuDefaults.CONTAINER_HEIGHT`) más la franja de navegación del
    // sistema que el propio menú rellena de acero. Lo usan la Pokédex (para
    // dibujar su base roja exactamente detrás) y el contenido del `Scaffold`
    // como hueco reservado **estable**, se dibuje o no el menú.
    val density = LocalDensity.current
    val navInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val menuReserved = BottomMenuDefaults.CONTAINER_HEIGHT + navInset

    // Pokédex: carcasa a pantalla (cabecera, marco NinePatch y base roja) con
    // el menú real compuesto encima de la base. Los destinos del menú cierran
    // la Dex y navegan; la Pokébola expandida no mueve al entrenador desde la
    // Pokédex y el botón Pokédex ya no abre la hoja de opciones en este
    // contexto. La vuelta también está con el botón físico (BackHandler
    // interno de la pantalla).
    if (state.isPokedexOpen) {
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
                // Tutorial: la bola queda inerte con el mensaje del
                // regreso visible; cerrado, vuelve a servir para volver.
                pokeBallEnabled = tutorialPokeBallEnabled(
                    state.tutorialPhase,
                    state.tutorialMessageClosed
                ),
                // Tutorial: durante los pasos los destinos del menú quedan
                // inertes — el regreso de la Pokédex es exclusivo de la
                // Pokéball, que es a su vez el botón Home —; fuera del
                // tutorial, navegación normal.
                destinationsEnabled = tutorialDexDestinationsEnabled(state.tutorialPhase),
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

            // El mensaje del tutorial (regreso de la Pokédex) se compone aquí
            // también: su rama temprana devuelve antes de llegar al bloque de
            // capas del final, y el paso exige ver la Dex detrás del diálogo.
            TutorialLayer(
                state = state,
                bottomReserved = menuReserved,
                onTutorialAction = onTutorialAction,
                onTutorialMessageTap = onTutorialMessageTap
            )
        }
        return
    }

    // Fondo de la zona actual: arte + trayectoria. La **misma** instancia mueve el
    // dibujo y decide la dirección del entrenador, así que no pueden desincronizarse.
    val routeBackground = RouteBackgrounds.forZone(state.zoneId)

    val gym = state.gym
    val gymActive = gym != null
    // La cortinilla de entrada se cierra sobre el juego: mientras entra, el fondo
    // del gimnasio **no** se dibuja. El cambio de escena ocurre con la cortinilla
    // ya cerrada, así que lo que se descubre al abrirse es el gimnasio y no un
    // escenario vacío apareciendo antes de tiempo.
    //
    // La clave reinicia el estado al abrir y cerrar la entrada; con la fase en
    // curso (bienvenida → tutorial → cartel) se conserva.
    var gymRevealed by rememberSaveable(state.gymIntro != null) { mutableStateOf(false) }
    val gymScene = gymActive || (state.gymIntro != null && gymRevealed)
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
    // Aislamiento de capas modales (issue #21): con una capa modal activa
    // (escena de gimnasio/captura, intro, selector de principal, cortinilla o
    // fases modales del tutorial) el menú no se compone: su Pokéball ignora el
    // consumo de toques y solo su ausencia la aísla. En los pasos guiados del
    // tutorial el menú sigue compuesto — la capa del diálogo lo cubre — porque
    // la Pokéball y los botones del menú son la interacción del propio paso.
    val menuHidden = sceneActive || state.needsStarter ||
        state.isLeadOpen || isSplashShowing || tutorialHidesMenu(state.tutorialPhase)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Marco del mundo: una sola proyección para el fondo y para el entrenador.
        // Se deriva del tamaño de **este** Box, que es el mismo que ocupa el fondo,
        // así que no pueden separarse. La escala sale del ancho y la cámara se
        // centra en vertical: el encuadre no depende del alto ni de la densidad.
        val world = with(density) { worldFrame(maxWidth.toPx(), maxHeight.toPx()) }

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
                startOffset = routeBackground.startOffset,
                artScale = routeBackground.artScale
            )
        } else if (showMapOceanBackdrop) {
            // Océano del mapa: rellena también el hueco inferior del menú (la
            // zona flotante de la Pokéball), como hiciera el fondo de ruta.
            MapOceanBackdrop(modifier = Modifier.fillMaxSize())
        }

        // Mundo: entrenador y hierba, en el mismo `Box` y con la misma proyección
        // que el fondo (`world`), así que no pueden desincronizarse. Va aquí y no
        // dentro del `Scaffold` porque el `Scaffold` desplaza y recorta su contenido
        // según la altura del menú; el mundo se mide contra la pantalla.
        if (!sceneActive && !state.needsStarter && state.contentTab == AppTab.Home) {
            WorldTrainer(
                trainerFrameRes = state.trainerFrameRes,
                showTallGrass = state.isTallGrass,
                world = world
            )
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
                //
                // Entra y sale con un fundido corto. El hueco que ocupa ya está
                // reservado en el contenido (ver `contentBottom` más abajo), así
                // que la animación solo toca el alfa y no mueve la escena.
                AnimatedVisibility(
                    visible = !menuHidden,
                    enter = fadeIn(tween(MENU_FADE_MS)),
                    exit = fadeOut(tween(MENU_FADE_MS))
                ) {
                    PokemonBottomMenu(
                        isMapSelected = !state.isOptionsOpen && state.contentTab == AppTab.Map,
                        isPokedexSelected = state.isOptionsOpen,
                        // Sobre el mapa la bola no tiene juego: el arrastre no la
                        // desliza sobre la carcasa; un arrastre completado cuenta
                        // como toque y devuelve a la aventura (como en la Pokédex).
                        // En la pantalla principal conserva su juego: se expande.
                        expansionEnabled = state.contentTab != AppTab.Map,
                        // Tutorial: mientras haya mensaje visible o
                        // la bola no sea la interacción del paso, queda
                        // completamente inerte.
                        pokeBallEnabled = tutorialPokeBallEnabled(
                            state.tutorialPhase,
                            state.tutorialMessageClosed
                        ),
                        onMapClick = { onSelectTab(AppTab.Map) },
                        onPokedexClick = onOpenOptions,
                        onHomeClick = { onSelectTab(AppTab.Home) },
                        onPokeBallGameClick = {
                            // Tutorial (regla principal de QA): el
                            // clic de juego solo genera experiencia y
                            // movimiento en juego libre; con cualquier mensaje
                            // visible, en pasos guiados o en el aviso de
                            // avance, la bola no mueve el fondo (el ViewModel
                            // además ignora el toque).
                            if (tutorialPokeBallClickEnabled(state.tutorialPhase)) {
                                // Modo captura (hierba alta o encuentro en
                                // curso): el mundo se congela; el clic no mueve
                                // el fondo ni cuenta para el avance de la zona.
                                if (
                                    advancesRouteWorld(
                                        state.isTallGrass,
                                        state.encounter != null,
                                        state.alwaysWilds
                                    )
                                ) {
                                    pokePulses.longValue++
                                }
                                // El sprite sigue el eje por el que se acaba de
                                // mover el fondo: en vertical, recto → back,
                                // lateral derecho → right, izquierdo → left; en
                                // horizontal, recto → right. La Pokébola ya no
                                // elige dirección por el lado que se toca.
                                onPokeBallTouch(
                                    routeBackground.motion.directionForStep(pokePulses.longValue)
                                )
                            }
                        },
                        onExpandedChange = onPokeBallExpanded
                    )
                }
            }
        ) { innerPadding ->
            // Hueco inferior del contenido. Mientras hay pantalla de juego se
            // reserva **siempre** la altura del menú, se dibuje o no: cuando el
            // `Scaffold` dependía de si el menú estaba compuesto, ocultarlo
            // (cortinilla de arranque, selector de principal) devolvía el área
            // completa al mundo y, al reaparecer, el entrenador saltaba ~90 dp
            // hacia arriba. En las escenas a pantalla completa (gimnasio,
            // captura, intro) no se reserva nada: el contenido no se dibuja y la
            // escena sigue usando todo el alto, como hasta ahora.
            //
            // No se lee `innerPadding`: durante el fundido de salida el menú
            // todavía ocupa su alto y el contenido saltaría al desaparecer del
            // todo. Ambos huecos se derivan de los insets, que no animan.
            val reserveMenu = !sceneActive && !state.needsStarter
            val contentBottom = if (reserveMenu) menuReserved else navInset
            // En la pestaña Mapa el contenido tampoco se desplaza por la altura
            // del bottom menu: `MapScreen` compone su fondo y su dibujo hasta el
            // fondo de pantalla (así el mapa ampliado se ve tras las partes
            // transparentes del menú) y recibe como `bottomReserved` la altura
            // reservada, que descuenta solo de su área de gestos y de referencia.
            // El resto de pestañas usan el hueco estable de arriba.
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
                            else -> PaddingValues(
                                top = innerPadding.calculateTopPadding(),
                                start = innerPadding.calculateStartPadding(layoutDirection),
                                end = innerPadding.calculateEndPadding(layoutDirection),
                                bottom = contentBottom
                            )
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
                        AppTab.Map -> MapCarcasaScreen(
                            bottomReserved = menuReserved
                        )
                    }
                }

                gym?.let { activeGym ->
                    GymOverlay(
                        state = activeGym,
                        onProjectileHit = onGymProjectileHit,
                        onProjectileMiss = onGymProjectileMiss,
                        onRingTap = onGymRingTap,
                        onVoltorbTap = onGymVoltorbTap,
                        onEnemyHit = onGymEnemyHit,
                        onRageSpeechDone = onGymRageSpeechDone,
                        onVictoryDone = onGymVictoryDone,
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
            gymId = state.gymId,
            leaderName = state.gymLeaderName,
            mechanic = state.gymMechanic,
            onWatchTutorial = onWatchGymTutorial,
            onSkip = onGymIntroSkip,
            onAccepted = onGymIntroAccepted,
            onCurtainClosed = { gymRevealed = true }
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

    // Diálogos del tutorial: el último en la ruta principal para que su caja
    // quede por encima del resto de capas del juego (la hoja de opciones vive
    // en su propia ventana y ya es transitoria en el paso de la Pokédex).
    TutorialLayer(
        state = state,
        bottomReserved = menuReserved,
        onTutorialAction = onTutorialAction,
        onTutorialMessageTap = onTutorialMessageTap
    )
}

/**
 * Los diálogos del tutorial, ya resueltos por la máquina de
 * estados. Se compone en las dos rutas del shell (aventura/mapa y la rama
 * temprana de la Pokédex); si la fase no muestra mensaje, no compone nada.
 */
@Composable
private fun TutorialLayer(
    state: GameUiState,
    bottomReserved: Dp,
    onTutorialAction: (TutorialAction) -> Unit,
    onTutorialMessageTap: () -> Unit
) {
    TutorialOverlay(
        phase = state.tutorialPhase,
        playerName = state.playerName,
        messageClosed = state.tutorialMessageClosed,
        bottomReserved = bottomReserved,
        onMessageTap = onTutorialMessageTap,
        onAcceptOffer = { onTutorialAction(TutorialAction.ACCEPT_OFFER) },
        onDeclineOffer = { onTutorialAction(TutorialAction.DECLINE_OFFER) },
        onBeginTutorial = { onTutorialAction(TutorialAction.BEGIN_TUTORIAL) },
        onSkipTutorial = { onTutorialAction(TutorialAction.SKIP_TUTORIAL) }
    )
}

/**
 * ¿Oculta el shell el menú por una capa del tutorial? Solo las fases
 * **modales** (preguntas y despedida): en los pasos guiados la Pokéball y los
 * botones del menú son la interacción del propio paso y deben seguir
 * compuestos. Con un mensaje de paso visible, la capa del diálogo los cubre
 * sin ocultarlos (ver `tutorialDialogVisible`).
 */
internal fun tutorialHidesMenu(phase: TutorialPhase): Boolean = phase.isModal

/**
 * ¿Hay un mensaje del tutorial visible en pantalla? Es el criterio de la capa
 * modal unificada (scrim + `modalBlocking`) y del bloqueo del Atrás: mientras
 * duela, solo el diálogo responde; al cerrarse, la capa se retira y las
 * interacciones autorizadas por la fase vuelven a operar.
 */
internal fun tutorialDialogVisible(
    phase: TutorialPhase,
    messageClosed: Boolean
): Boolean = phase.isMessageVisible(messageClosed)

/**
 * ¿Están operativos los destinos del menú (Mapa/Pokédex) en la pantalla de la
 * Pokédex? Solo en juego libre: durante el tutorial el regreso de la Pokédex
 * es **exclusivo de la Pokéball** — que es a su vez el botón Home —, así que el
 * botón Mapa debe quedar inerte para no cerrar la pantalla por una vía
 * alternativa.
 */
internal fun tutorialDexDestinationsEnabled(phase: TutorialPhase): Boolean =
    phase.isFreePlay

/**
 * ¿Está la Pokéball del menú **habilitada** en el momento actual del tutorial?
 *
 * Regla principal de QA: mientras cualquier mensaje esté visible,
 * la bola queda completamente inerte (sin pulsaciones, arrastre, expansión ni
 * contracción). Con el mensaje cerrado, solo los pasos donde la Pokéball es la
 * interacción la habilitan: el despliegue (`DEPLOY_POKEBALL`, solo el gesto
 * real de arrastre) y los regresos de Mapa y Pokédex (solo el toque de
 * vuelta). Fuera del tutorial, juego libre: como siempre.
 */
internal fun tutorialPokeBallEnabled(
    phase: TutorialPhase,
    messageClosed: Boolean
): Boolean = when (phase) {
    TutorialPhase.DEPLOY_POKEBALL -> messageClosed
    TutorialPhase.RETURN_MAP -> messageClosed
    TutorialPhase.RETURN_POKEDEX -> messageClosed
    else -> phase.isFreePlay
}

/**
 * ¿Genera la Pokéball experiencia y movimiento con su clic de juego? Solo en
 * juego libre: en `RETURN_MAP` y `RETURN_POKEDEX` la bola sirve exclusivamente
 * para volver a la aventura, nunca para sumar experiencia; y con cualquier
 * mensaje visible, nada responde.
 */
internal fun tutorialPokeBallClickEnabled(phase: TutorialPhase): Boolean =
    phase.isFreePlay

/**
 * ¿Debe este clic de la Pokébola mover el fondo de ruta?
 *
 * En modo captura — hierba alta abierta o encuentro en curso — el mundo se
 * congela: el clic no desplaza el fondo ni cuenta para el avance de la zona
 * (eso ya lo evita `MainViewModel.onPokeBallTouch`, que no suma `zoneClicks`
 * en esos dos estados). Fuera de él, cada pulsación avanza el recorrido.
 */
internal fun advancesRouteWorld(
    isTallGrass: Boolean,
    encounterActive: Boolean,
    alwaysWilds: Boolean = false
): Boolean = (!isTallGrass || alwaysWilds) && !encounterActive

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
    isPokedexOpen: Boolean = false,
    tutorialPhase: TutorialPhase = TutorialPhase.COMPLETED,
    tutorialMessageClosed: Boolean = false
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
    gymId = "brock",
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
    ),
    tutorialPhase = tutorialPhase,
    tutorialMessageClosed = tutorialMessageClosed
)

/**
 * Fondo del gimnasio: arte de cada mecánica a pantalla completa.
 *
 * Se dibuja con [FilterQuality.None] (vecino más cercano) porque el arte es
 * pixel-art: el filtrado bilineal por defecto ablanda las líneas de una baldosa
 * al escalar. [ContentScale.Crop] llena cualquier pantalla sin deformar el
 * dibujo; el juego solo admite vertical y el arte (2:3) es más ancho que
 * cualquier teléfono, así que el recorte cae siempre en los laterales
 * decorativos y la composición vertical —plataforma del líder, suelo del
 * jugador— se conserva entera. En un plegable o tablet vertical, más ancho que
 * 2:3, recorta una franja superior e inferior también decorativa.
 */
@Composable
private fun GymBackground(@DrawableRes resId: Int) {
    // Bitmap aparte (no `painterResource`) porque solo el overload de
    // `ImageBitmap` permite `FilterQuality.None`. El recurso vive en
    // `drawable-nodpi`, así que `decodeResource` no lo reescala por densidad.
    val context = LocalContext.current
    val bitmap = remember(resId) {
        BitmapFactory.decodeResource(context.resources, resId).asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.None,
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
        onGymProjectileMiss = {},
        onGymRingTap = {},
        onGymVoltorbTap = {},
        onGymEnemyHit = {},
        onGymRageSpeechDone = {},
        onGymVictoryDone = {},
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
        onPokeBallExpanded = {},
        onTutorialAction = {},
        onTutorialMessageTap = {}
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

/** Tutorial: oferta inicial (pregunta modal con Oak). */
@Preview(
    name = "7 · Tutorial (oferta)",
    device = PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun AppRootTutorialOfferPreview() = MaterialTheme {
    AppRootStage(
        state = previewGameState(tutorialPhase = TutorialPhase.OFFER)
    )
}

/** Tutorial: paso guiado de la Pokéball (caja sobre el menú, juego debajo). */
@Preview(
    name = "8 · Tutorial (paso Pokéball)",
    device = PREVIEW_DEVICE,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun AppRootTutorialDeployPreview() = MaterialTheme {
    AppRootStage(
        state = previewGameState(tutorialPhase = TutorialPhase.DEPLOY_POKEBALL)
    )
}
