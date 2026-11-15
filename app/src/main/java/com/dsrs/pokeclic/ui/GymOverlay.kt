/**
 * Escena, HUD y sprites del combate de gimnasio.
 *
 * Cada mecánica tiene su escena: rocas por columnas (Brock), anillos que se
 * cierran sobre el jugador (Misty) o una cuadrícula de Voltorb (Lt. Surge). Las
 * tres animan con un reloj de fotogramas y reparten los toques entre el líder
 * expuesto y el ataque en curso; el HUD pinta las fracciones de vida y postura ya
 * calculadas por el modelo. No hay lógica de combate aquí: solo presentación y
 * eventos hacia el ViewModel.
 */
package com.dsrs.pokeclic.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.GymMechanic
import com.dsrs.pokeclic.model.GymOutcome
import com.dsrs.pokeclic.model.GymPhase
import com.dsrs.pokeclic.model.GymState
import com.dsrs.pokeclic.model.PokemonType
import com.dsrs.pokeclic.model.RingOutcome
import com.dsrs.pokeclic.model.Voltorb
import com.dsrs.pokeclic.model.VoltorbFeedback
import com.dsrs.pokeclic.model.VoltorbOutcome
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.annotation.DrawableRes
import androidx.compose.foundation.shape.CircleShape

/**
 * Escena del combate de gimnasio, a pantalla completa sobre el fondo que dibuja
 * [AppRoot].
 *
 * Combate por **fases excluyentes** (ver [GymPhase]):
 * - **Ataque**: el líder ataca con su mecánica y el jugador llena la barra de
 *   **postura** con cada acierto. Un fallo resta vida y no suma postura. Al
 *   llenarla, la postura se rompe y el líder queda expuesto. La defensa depende
 *   de la mecánica: romper rocas (Brock), acertar el círculo que converge (Misty)
 *   o apagar el Voltorb cargado (Lt. Surge).
 * - **Expuesta**: sin ataque enemigo, tocar al líder le resta vida (con ventaja
 *   de tipo) durante una ventana que sigue funcionando **por tiempo**.
 *
 * Por debajo del 25 % de vida el líder entra en **furia**: los ataques son más
 * densos y rápidos (ver `Gym.rage*`).
 *
 * Todos los sprites son solo visuales y **una única capa de toque** reparte los
 * gestos: primero al enemigo si está expuesto y el toque cae sobre él, y si no,
 * la mecánica del líder (la roca más cercana para Brock, el anillo más cercano
 * para Misty). Así la Pokéball del menú deja de ser el pulsador de ataque.
 *
 * @param showDefeat si el panel de derrota debe dibujarse. Se apaga mientras haya
 *   una capa de entrada al gimnasio encima: al reintentar, el cartel de arranque y
 *   el panel de derrota se superponían.
 */
@Composable
fun GymOverlay(
    state: GymState,
    onProjectileHit: (Long) -> Unit,
    onProjectileMiss: () -> Unit,
    onRingTap: () -> Unit,
    onVoltorbTap: (Int) -> Unit,
    onEnemyHit: () -> Unit,
    onRageSpeechDone: () -> Unit,
    onVictoryDone: () -> Unit,
    onRetry: () -> Unit,
    onLeave: () -> Unit,
    showDefeat: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Discurso de furia: sustituye la escena entera. El ViewModel deja el combate
    // en pausa mientras `rageSpeech` esté activo, así que aquí no hay nada que
    // pintar del HUD ni de los sprites.
    if (state.rageSpeech) {
        GymRageSpeech(
            gymId = state.gymId,
            leaderName = state.leaderName,
            onFinished = onRageSpeechDone,
            modifier = modifier
        )
        return
    }

    val exposed = state.phase == GymPhase.EXPOSED
    // Composición propia del gimnasio: cada arte coloca la plataforma del líder y
    // el suelo del jugador a otra altura.
    val layout = GymSceneLayouts.forGym(state.gymId)

    // Victoria: primero el Pokémon del líder se hunde y se apaga; después llega
    // su despedida. Sin guion propio, el combate se cierra en cuanto termina el
    // desvanecimiento.
    val won = state.outcome == GymOutcome.WON
    val victoryScript = remember(state.gymId) { victoryScriptFor(state.gymId) }
    val defeatFade = remember { Animatable(0f) }
    var victoryVisible by remember(state.gymId) { mutableStateOf(false) }

    LaunchedEffect(won) {
        if (!won) return@LaunchedEffect
        defeatFade.animateTo(1f, tween(VICTORY_FADE_MS, easing = FastOutSlowInEasing))
        if (victoryScript == null) onVictoryDone() else victoryVisible = true
    }

    if (victoryVisible && victoryScript != null) {
        GymVictory(
            script = victoryScript,
            leaderName = state.leaderName,
            onFinished = onVictoryDone,
            modifier = modifier
        )
        return
    }

    // El combate oculta el bottom menu, así que el `Scaffold` ya no reserva el
    // inset inferior: se aplica aquí para que el sprite del jugador no quede
    // debajo de la barra de navegación.
    //
    // El minijuego de anillos juzga los toques en la **raíz** del overlay, como
    // hace la captura: así ningún sprite, barra o capa intermedia puede dejar el
    // gesto sin receptor.
    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .pointerInput(state.mechanic, exposed, layout) {
                if (state.mechanic != GymMechanic.TIMING_RINGS) return@pointerInput

                val enemyLeftPx = (size.width - layout.enemyWidth.toPx()) / 2f
                val enemyTopPx = layout.enemyTop.toPx()
                val enemyRightPx = enemyLeftPx + layout.enemyWidth.toPx()
                val enemyBottomPx = enemyTopPx + layout.enemyHeight.toPx()

                awaitEachGesture {
                    // El minijuego se juzga al **apoyar** el dedo, no al
                    // levantarlo: soltar tarde no debe convertir un acierto en
                    // fallo.
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!exposed) {
                        onRingTap()
                    } else if (
                        down.position.x in enemyLeftPx..enemyRightPx &&
                        down.position.y in enemyTopPx..enemyBottomPx
                    ) {
                        onEnemyHit()
                    }
                    waitForUpOrCancellation()
                }
            }
    ) {

        when (state.mechanic) {
            GymMechanic.PROJECTILES -> GymScene(
                state = state,
                exposed = exposed,
                layout = layout,
                enemyFade = defeatFade.value,
                onProjectileHit = onProjectileHit,
                onMiss = onProjectileMiss,
                onEnemyHit = onEnemyHit,
                modifier = Modifier.fillMaxSize()
            )
            GymMechanic.TIMING_RINGS -> RingGymScene(
                state = state,
                exposed = exposed,
                layout = layout,
                enemyFade = defeatFade.value,
                modifier = Modifier.fillMaxSize()
            )
            GymMechanic.VOLTORB_GRID -> VoltorbGridScene(
                state = state,
                exposed = exposed,
                layout = layout,
                enemyFade = defeatFade.value,
                onVoltorbTap = onVoltorbTap,
                onEnemyHit = onEnemyHit,
                modifier = Modifier.fillMaxSize()
            )
        }

        GymHud(
            state = state,
            exposed = exposed,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        )

        PlayerHpBar(
            state = state,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = PLAYER_HP_BOTTOM_PADDING)
        )

        if (state.outcome == GymOutcome.LOST && showDefeat) {
            DefeatPanel(
                onRetry = onRetry,
                onLeave = onLeave,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/**
 * Escena vertical: enemigo arriba-centro, jugador abajo-centro y las rocas cayendo
 * rectas por columnas entre ambos. Los sprites no reciben toques; la interacción
 * entera vive en la capa `pointerInput` final.
 */
@Composable
private fun GymScene(
    state: GymState,
    exposed: Boolean,
    layout: GymSceneLayout,
    enemyFade: Float,
    onProjectileHit: (Long) -> Unit,
    onMiss: () -> Unit,
    onEnemyHit: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Reloj de fotogramas: la posición de cada roca sale de su progreso, así que
    // la escena se repinta sola sin que el ViewModel actualice cada frame.
    val clock = remember { mutableLongStateOf(nowMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            clock.longValue = nowMs()
        }
    }
    val now = clock.longValue
    val currentState by rememberUpdatedState(state)

    // Bitmap aparte (no `painterResource`) porque solo el overload de
    // `ImageBitmap` permite `FilterQuality.None`: la roca es pixel-art.
    val context = LocalContext.current
    val rockBitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.rock).asImageBitmap()
    }

    // Cada rotura dispara una animación de ataque (hacia adelante y vuelta) y un
    // estallido de roca en el punto tocado. Son el sustituto del sprite de
    // ataque real.
    var attackNonce by remember { mutableIntStateOf(0) }
    val bursts = remember { mutableStateListOf<RockBurst>() }
    var nextBurstId by remember { mutableLongStateOf(0L) }

    val lunge = remember { Animatable(0f) }
    LaunchedEffect(attackNonce) {
        if (attackNonce == 0) return@LaunchedEffect
        lunge.snapTo(0f)
        lunge.animateTo(1f, tween(durationMillis = 90))
        lunge.animateTo(0f, tween(durationMillis = 110))
    }

    // Sprite sheet del estallido: una sola decodificación por escena, compartida
    // por todos los estallidos activos.
    val shatterSheet = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.rock_shatter).asImageBitmap()
    }

    // Latido del enemigo mientras está expuesto.
    val pulse = rememberInfiniteTransition(label = "exposed")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 600), RepeatMode.Reverse),
        label = "pulseScale"
    )

    BoxWithConstraints(modifier = modifier) {
        val columnCount = state.columns.coerceAtLeast(1)
        val laneSpan = minOf(maxWidth * LANE_WIDTH_FRACTION, LANE_MAX_WIDTH)
        val laneLeft = (maxWidth - laneSpan) / 2f
        val cellWidth = laneSpan / columnCount

        // Reparto vertical: el enemigo arranca por debajo del HUD y las rocas caen
        // desde su base hasta la cabeza del jugador.
        val spawnY = layout.enemyTop + layout.enemyHeight
        val impactY = (maxHeight - layout.playerBottom - layout.playerHeight + PLAYER_HIT_INSET)
            .coerceAtLeast(spawnY + 24.dp)

        val rockSize = projectileSizeFor(cellWidth)

        state.projectiles.forEach { projectile ->
            val progress = projectile.progressAt(now)
            val x = laneLeft + cellWidth * (projectile.column + 0.5f) - rockSize / 2
            val y = spawnY + (impactY - spawnY) * progress - rockSize / 2
            Image(
                bitmap = rockBitmap,
                contentDescription = null,
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .offset(x = x, y = y)
                    .size(rockSize)
            )
        }

        // Enemigo, con pulso mientras está expuesto.
        GymSpriteBox(
            gifRes = enemyGifFor(state.gymId),
            name = state.leaderName,
            type = state.gymType,
            contentDescription = stringResource(R.string.gym_title, state.leaderName),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = layout.enemyTop)
                .size(layout.enemyWidth, layout.enemyHeight)
                .graphicsLayer {
                    val scale = if (exposed) pulseScale else 1f
                    scaleX = scale
                    scaleY = scale
                    // Al perder, el Pokémon del líder se hunde y se apaga.
                    alpha = 1f - enemyFade
                    translationY = enemyFade * VICTORY_ENEMY_SINK.toPx()
                }
        )

        // Jugador; placeholder por tipo si aún no hay back.
        GymSpriteBox(
            gifRes = backGifFor(state.leadSpeciesId),
            name = state.leadName,
            type = state.leadType,
            contentDescription = state.leadName,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -layout.playerBottom)
                .size(layout.playerWidth, layout.playerHeight)
                .graphicsLayer {
                    translationY = -26.dp.toPx() * lunge.value
                    val scale = 1f + 0.05f * lunge.value
                    scaleX = scale
                    scaleY = scale
                }
        )

        // Estallidos de las rocas rotas. Van encima de los sprites: la roca se
        // parte donde estaba y los trozos salen hacia la cámara.
        bursts.forEach { burst ->
            key(burst.id) {
                RockShatterBurst(
                    sheet = shatterSheet,
                    center = burst.center,
                    rockSize = burst.size,
                    onFinished = { bursts.removeAll { it.id == burst.id } }
                )
            }
        }

        // Barra de postura justo debajo del enemigo: se llena con los aciertos
        // del jugador y, al completarse, abre la ventana. En furia se tiñe de rojo.
        // Capa de toque única: enemigo si está expuesto, o la roca más cercana.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(cellWidth, laneLeft, spawnY, impactY, exposed) {
                    val cellPx = cellWidth.toPx()
                    val laneLeftPx = laneLeft.toPx()
                    val spawnPx = spawnY.toPx()
                    val impactPx = impactY.toPx()
                    val hitRadius = maxOf(
                        cellPx * PROJECTILE_HIT_FRACTION,
                        PROJECTILE_MIN_HIT_RADIUS.toPx()
                    )

                    val enemyLeftPx = (size.width - layout.enemyWidth.toPx()) / 2f
                    val enemyTopPx = layout.enemyTop.toPx()
                    val enemyRightPx = enemyLeftPx + layout.enemyWidth.toPx()
                    val enemyBottomPx = enemyTopPx + layout.enemyHeight.toPx()

                    // El acierto se mide donde estaba la roca **al apoyar el dedo**:
                    // al caer se movería y el toque "se escaparía".
                    var pressAt = Offset.Zero
                    var pressTime = 0L

                    detectTapGestures(
                        onPress = { offset ->
                            pressAt = offset
                            pressTime = nowMs()
                        },
                        onTap = { offset ->
                            val tapAt = if (pressTime > 0L) pressAt else offset
                            val tapTime = if (pressTime > 0L) pressTime else nowMs()

                            val onEnemy = exposed &&
                                tapAt.x in enemyLeftPx..enemyRightPx &&
                                tapAt.y in enemyTopPx..enemyBottomPx

                            if (onEnemy) {
                                onEnemyHit()
                            } else {
                                val hit = currentState.projectiles
                                    .map { projectile ->
                                        val cx = laneLeftPx + cellPx * (projectile.column + 0.5f)
                                        val cy = spawnPx + (impactPx - spawnPx) * projectile.progressAt(tapTime)
                                        val dx = tapAt.x - cx
                                        val dy = tapAt.y - cy
                                        Triple(projectile, dx * dx + dy * dy, Offset(cx, cy))
                                    }
                                    // La más cercana dentro del radio, para que dos
                                    // columnas contiguas no se acierten de más.
                                    .filter { it.second <= hitRadius * hitRadius }
                                    .minByOrNull { it.second }
                                if (hit == null) {
                                    // Toque al aire: resta postura, igual que un
                                    // fallo en las otras mecánicas.
                                    onMiss()
                                } else {
                                    attackNonce++
                                    bursts += RockBurst(nextBurstId++, hit.third, rockSize)
                                    onProjectileHit(hit.first.id)
                                }
                            }
                        }
                    )
                }
        )
    }
}

/**
 * Escena del minijuego de anillos de Misty.
 *
 * El Pokémon del jugador se queda abajo, con un **círculo fijo** a su
 * alrededor, y el del líder arriba. Durante la oleada aparecen anillos que se
 * cierran hacia el círculo fijo con cadencia distinta; el jugador pulsa cuando
 * el anillo que se cierra lo toca. El modelo decide aciertos y fallos: aquí solo
 * se pinta el radio derivado del tiempo y se envía el toque.
 *
 * El gesto se captura en la raíz de [GymOverlay], igual que en la captura: esta
 * escena solo pinta. No hay acierto por posición: el minijuego es de **momento**,
 * no de puntería.
 */
@Composable
private fun RingGymScene(
    state: GymState,
    exposed: Boolean,
    layout: GymSceneLayout,
    enemyFade: Float,
    modifier: Modifier = Modifier
) {
    // Reloj de fotogramas: el radio de cada anillo sale de su progreso, así que
    // la escena se repinta sola sin que el ViewModel actualice cada frame.
    val clock = remember { mutableLongStateOf(nowMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            clock.longValue = nowMs()
        }
    }
    val now = clock.longValue
    val currentState by rememberUpdatedState(state)
    val context = LocalContext.current

    // Acierto/fallo del último anillo: el ViewModel publica el resultado y aquí
    // se convierte en un estallido de agua (hoja de pixelfx), más el cartel.
    // El instante de arranque es la única autoridad temporal: el sprite sheet se
    // indexa por tiempo y el cartel se calcula desde los mismos segundos.
    val feedback = state.ringFeedback
    var fxStartMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(feedback?.ringId) {
        if (feedback != null) fxStartMs = nowMs()
    }

    // Una decodificación por escena para cada hoja.
    val burstSheet = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.hydro_burst).asImageBitmap()
    }
    val implodeSheet = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.hydro_implode).asImageBitmap()
    }

    // El resultado tiñe el **círculo fijo** que rodea al Pokémon, no el aro que se
    // cierra ni el estallido: esos siguen siendo agua. Así el color dice "acierto"
    // o "fallo" sin disfrazar el ataque de otra cosa.
    val resultTint = when (feedback?.outcome) {
        RingOutcome.HIT -> HIT_GREEN
        RingOutcome.MISS -> MISS_RED
        null -> null
    }
    val tintAge = now - fxStartMs
    val tintMix = if (resultTint != null && tintAge in 0 until RING_TINT_MS) {
        val u = tintAge / RING_TINT_MS.toFloat()
        // Aguanta encendido un tercio del tiempo y luego se apaga.
        if (u < 0.35f) 1f else (1f - (u - 0.35f) / 0.65f).coerceIn(0f, 1f)
    } else {
        0f
    }

    // Latido del enemigo mientras está expuesto.
    val pulse = rememberInfiniteTransition(label = "ringExposed")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 600), RepeatMode.Reverse),
        label = "ringPulseScale"
    )

    BoxWithConstraints(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(
                x = size.width / 2f,
                y = size.height - layout.playerBottom.toPx() - layout.playerHeight.toPx() / 2f
            )
            val targetRadius = RING_TARGET_RADIUS.toPx()
            val startRadius = RING_START_RADIUS.toPx()
            val mainStroke = Stroke(width = RING_STROKE.toPx())
            val outlineStroke = Stroke(width = (RING_STROKE + 3.dp).toPx())
            val foamStroke = Stroke(width = 1.dp.toPx())

            // El color del resultado. El círculo fijo es el que lo lleva: el aro
            // que se cierra y el estallido se quedan en agua.
            fun tinted(base: Color): Color =
                if (resultTint == null || tintMix <= 0f) base
                else lerp(base, resultTint, tintMix)

            // Halo del resultado: envuelve el círculo fijo mientras dura el aviso.
            if (resultTint != null && tintMix > 0.01f) {
                drawCircle(
                    color = resultTint.copy(alpha = 0.34f * tintMix),
                    radius = targetRadius,
                    center = center,
                    style = Stroke(width = RING_GLOW_STROKE.toPx() * 2f)
                )
            }

            // Charco de agua bajo el Pokémon: da profundidad al círculo fijo.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tinted(WATER_POOL).copy(alpha = 0.34f + 0.26f * tintMix),
                        Color.Transparent
                    ),
                    center = center,
                    radius = targetRadius
                ),
                radius = targetRadius,
                center = center
            )

            // Círculo fijo: contorno oscuro + doble borde de burbuja (agua +
            // espuma). El contorno lo separa del azul de la piscina y de las
            // líneas blancas del fondo.
            drawCircle(
                color = RING_OUTLINE.copy(alpha = 0.65f),
                radius = targetRadius,
                center = center,
                style = outlineStroke
            )
            drawCircle(
                color = tinted(WATER_LIGHT).copy(alpha = 0.95f),
                radius = targetRadius,
                center = center,
                style = mainStroke
            )
            drawCircle(
                color = tinted(WATER_FOAM).copy(alpha = 0.95f),
                radius = targetRadius - 3.dp.toPx(),
                center = center,
                style = foamStroke
            )

            // Burbujas que suben dentro del charco.
            repeat(5) { index ->
                val cycleMs = 2_600L
                val phase = ((now + index * 520L) % cycleMs) / cycleMs.toFloat()
                val bubbleX = center.x + (index - 2f) * (targetRadius * 0.28f)
                val bubbleY = center.y + targetRadius * 0.82f - phase * targetRadius * 1.6f
                drawCircle(
                    color = tinted(WATER_FOAM).copy(alpha = 0.42f * (1f - phase)),
                    radius = if (index % 2 == 0) 3.dp.toPx() else 2.dp.toPx(),
                    center = Offset(bubbleX, bubbleY)
                )
            }

            // Anillos que se cierran. El aro es una banda de agua: contorno oscuro
            // que lo separa del fondo, cuerpo con degradado, cresta de espuma,
            // cáusticas que giran y gotas que se desprenden hacia fuera.
            currentState.rings.forEach { ring ->
                val progress = ring.progressAt(now)
                val radius = startRadius + (targetRadius - startRadius) * progress
                val onBeat = ring.isInWindow(now, currentState.ringWindowMs)
                // Un aro grande ondula; al llegar al círculo fijo se alisa.
                val energy = ((radius - targetRadius) / (startRadius - targetRadius))
                    .coerceIn(0f, 1f)

                // Halo: el volumen de agua que empuja el aro.
                drawCircle(
                    color = (if (onBeat) RING_ON_BEAT_COLOR else WATER_LIGHT).copy(alpha = 0.26f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = RING_GLOW_STROKE.toPx())
                )

                val ringBrush = Brush.sweepGradient(
                    colors = if (onBeat) {
                        listOf(
                            RING_ON_BEAT_DEEP,
                            RING_ON_BEAT_COLOR,
                            Color.White,
                            RING_ON_BEAT_COLOR,
                            RING_ON_BEAT_DEEP
                        )
                    } else {
                        listOf(WATER_DEEP, WATER_MID, WATER_FOAM, WATER_MID, WATER_DEEP)
                    },
                    center = center
                )

                // La onda: dos armónicos con velocidades distintas, para que no se
                // lea como un círculo perfecto que respira.
                val wave = Path()
                val crestWave = Path()
                val segments = 72
                for (step in 0..segments) {
                    val angle = step * (2.0 * PI / segments).toFloat()
                    val ripple = if (onBeat) {
                        0f
                    } else {
                        (sin(angle * 5f - now / 260f) * 0.62f +
                            sin(angle * 11f + now / 190f) * 0.38f) *
                            RING_WAVE_AMPLITUDE.toPx() * (0.25f + 0.75f * energy)
                    }
                    val x = center.x + cos(angle) * (radius + ripple)
                    val y = center.y + sin(angle) * (radius + ripple)
                    if (step == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
                    val crestRadius = radius + ripple + RING_CREST_OFFSET.toPx()
                    val cx = center.x + cos(angle) * crestRadius
                    val cy = center.y + sin(angle) * crestRadius
                    if (step == 0) crestWave.moveTo(cx, cy) else crestWave.lineTo(cx, cy)
                }
                wave.close()
                crestWave.close()

                drawPath(
                    path = wave,
                    color = RING_OUTLINE.copy(alpha = 0.62f),
                    style = outlineStroke
                )
                drawPath(path = wave, brush = ringBrush, style = mainStroke)
                // Cresta de espuma en el borde exterior.
                drawPath(
                    path = crestWave,
                    color = WATER_FOAM.copy(alpha = if (onBeat) 1f else 0.78f),
                    style = foamStroke
                )

                // Cáusticas: arcos cortos que corren por la cresta a distinta
                // velocidad, como la luz sobre el agua en movimiento.
                if (!onBeat) {
                    repeat(4) { index ->
                        val spinMs = 2_100L + index * 380L
                        val base = (index * 90f) + (now % spinMs) / spinMs.toFloat() * 360f
                        val sweep = 16f + index * 4f
                        val arcRadius = radius + RING_CREST_OFFSET.toPx() + 1.dp.toPx()
                        drawArc(
                            color = Color.White.copy(alpha = 0.30f + 0.12f * energy),
                            startAngle = base,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                            size = Size(arcRadius * 2f, arcRadius * 2f),
                            style = Stroke(width = RING_CREST_STROKE.toPx())
                        )
                    }
                }

                // Gotas que se desprenden del aro hacia fuera y se apagan.
                repeat(6) { index ->
                    val periodMs = 900L + index * 120L
                    val phase = ((now + index * 217L) % periodMs) / periodMs.toFloat()
                    val angle = index * (360f / 6f) + progress * 90f
                    val rad = angle * PI.toFloat() / 180f
                    val distance = radius + phase * 7.dp.toPx()
                    drawCircle(
                        color = if (onBeat) Color.White.copy(alpha = 0.9f * (1f - phase))
                        else WATER_FOAM.copy(alpha = 0.85f * (1f - phase)),
                        radius = (1.2f + (index % 3) * 0.5f).dp.toPx() * (1f - phase * 0.5f),
                        center = Offset(center.x + cos(rad) * distance, center.y + sin(rad) * distance)
                    )
                }
            }

            // El resultado lo pinta la capa de sprite sheets que va por encima de
            // los sprites: el anillo fallado tiene que cerrarse *sobre* el Pokémon.
        }

        // Enemigo, con pulso mientras está expuesto.
        GymSpriteBox(
            gifRes = enemyGifFor(state.gymId),
            name = state.leaderName,
            type = state.gymType,
            contentDescription = stringResource(R.string.gym_title, state.leaderName),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = layout.enemyTop)
                .size(layout.enemyWidth, layout.enemyHeight)
                .graphicsLayer {
                    val scale = if (exposed) pulseScale else 1f
                    scaleX = scale
                    scaleY = scale
                    // Al perder, el Pokémon del líder se hunde y se apaga.
                    alpha = 1f - enemyFade
                    translationY = enemyFade * VICTORY_ENEMY_SINK.toPx()
                }
        )

        // Jugador; placeholder por tipo si aún no hay back.
        GymSpriteBox(
            gifRes = backGifFor(state.leadSpeciesId),
            name = state.leadName,
            type = state.leadType,
            contentDescription = state.leadName,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -layout.playerBottom)
                .size(layout.playerWidth, layout.playerHeight)
        )

        // Efecto del resultado, por encima de los sprites: el acierto salpica y el
        // fallo cierra el anillo sobre el Pokémon y estalla en el centro. Arranca
        // en el radio que tenía el anillo al resolverse, no en el del jugador.
        feedback?.let { result ->
            val spec = WaterRingSheet.forOutcome(result.outcome)
            val elapsed = now - fxStartMs
            if (elapsed >= 0L && elapsed < spec.durationMs) {
                val sheet = if (result.outcome == RingOutcome.HIT) burstSheet else implodeSheet
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val targetRadius = RING_TARGET_RADIUS.toPx()
                    val startRadius = RING_START_RADIUS.toPx()
                    val radius = startRadius + (targetRadius - startRadius) * result.ringProgress
                    drawWaterRingFx(
                        sheet = sheet,
                        spec = spec,
                        frame = spec.frameAt(elapsed),
                        center = Offset(
                            x = size.width / 2f,
                            y = size.height - layout.playerBottom.toPx() -
                                layout.playerHeight.toPx() / 2f
                        ),
                        radiusPx = radius
                    )
                }
            }
        }

        // Cartel del resultado, portado de `ui/combo-counter` de pixelfx: entra
        // coleteando, golpea y se va barrido. Verde para aciertos, rojo para fallos.
        feedback?.let { result ->
            val hit = result.outcome == RingOutcome.HIT
            val label = when {
                hit && result.combo >= 2 -> stringResource(R.string.gym_ring_combo, result.combo)
                hit -> stringResource(R.string.gym_ring_hit)
                result.count > 1 -> stringResource(R.string.gym_ring_miss_multi, result.count)
                else -> stringResource(R.string.gym_ring_miss)
            }
            val seconds = (now - fxStartMs) / 1000f
            if (seconds >= 0f && seconds < BANNER_LIFE_S) {
                RingResultBanner(
                    text = label,
                    color = if (hit) HIT_GREEN else MISS_RED,
                    combo = if (hit) result.combo else 0,
                    seconds = seconds,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

    }
}

/**
 * Cartel del resultado del anillo.
 *
 * Portado de `ui/combo-counter` de pixelfx: las letras entran coleteando desde la
 * izquierda con líneas de velocidad y dos estelas, frenan de golpe con un destello
 * blanco, un anillo y chispas, laten en el pulso y se van barridas a la derecha.
 * Todo se calcula desde [seconds], así que no necesita animaciones propias ni
 * acumular estado. La barra de tiempo del original no se trae: el cartel no dura
 * lo suficiente como para que informar del tiempo restante aporte algo.
 *
 * @param color verde del acierto o rojo del fallo.
 * @param combo aciertos seguidos; solo engorda la lluvia de chispas.
 */
@Composable
private fun RingResultBanner(
    text: String,
    color: Color,
    combo: Int,
    seconds: Float,
    modifier: Modifier = Modifier
) {
    val cell = BANNER_CELL
    val cells = PixelFont.widthOf(text)

    // Latido del pulso: las letras crecen un 12 % y destellan blanco en el pico.
    val fromBeat = (seconds - BANNER_BEAT_S) / 0.05f
    val pulse = exp(-(fromBeat * fromBeat))
    val flash = (seconds >= BANNER_HIT_S && seconds < BANNER_HIT_S + 0.07f) || pulse > 0.6f
    // Salida: se va barrida a la derecha.
    val gone = seg(seconds, BANNER_OUT_S + 0.08f, BANNER_OUT_S + 0.20f)
    val moving = seconds < BANNER_HIT_S || seconds > BANNER_OUT_S + 0.06f
    val stretch = when {
        !moving -> 1f
        seconds < BANNER_HIT_S -> BANNER_STRETCH_IN
        else -> BANNER_STRETCH_OUT
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(BANNER_HEIGHT)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val px = cell.toPx() * (1f + BANNER_PULSE_SCALE * pulse)
        val wordW = cells * px * stretch
        val wordH = PixelFont.HEIGHT * px
        val dx = bannerX(seconds).dp.toPx()
        val frame = (seconds * BANNER_FPS).toInt()

        // 1. Líneas de velocidad: se relanzan cada fotograma, como una viñeta.
        val lineK = when {
            seconds < BANNER_HIT_S -> 1f
            seconds < BANNER_OUT_S ->
                (1f - (seconds - BANNER_HIT_S) / 0.35f).coerceAtLeast(0f) * 0.9f
            else -> seg(seconds, BANNER_OUT_S + 0.04f, BANNER_OUT_S + 0.12f) *
                (1f - seg(seconds, BANNER_OUT_S + 0.18f, BANNER_OUT_S + 0.30f))
        }
        if (lineK > 0.02f) {
            val outgoing = seconds >= BANNER_OUT_S
            repeat(BANNER_SPEED_LINES) { index ->
                // Cada fotograma relanza las líneas: mismo índice, semilla distinta.
                val seed = frame * BANNER_FRAME_STRIDE + index
                if (hashNoise(seed, 3) > lineK) return@repeat
                val y = (hashNoise(seed, 1) - 0.5f) * 60f
                val half = 8f + hashNoise(seed, 2) * 25f
                val x = (hashNoise(seed, 4) - 0.5f) * 190f
                val left = center.x + (x - half).dp.toPx()
                val right = center.x + (x + half).dp.toPx()
                val lineY = center.y + y.dp.toPx()
                drawLine(
                    color = color.copy(alpha = 0.45f),
                    start = Offset(left, lineY),
                    end = Offset(right, lineY),
                    strokeWidth = BANNER_LINE_STROKE.toPx()
                )
                // La punta que marca el sentido de la marcha va en blanco.
                drawLine(
                    color = Color.White.copy(alpha = 0.6f),
                    start = Offset(if (outgoing) left else right - half * 0.35f, lineY),
                    end = Offset(if (outgoing) left + half * 0.35f else right, lineY),
                    strokeWidth = BANNER_LINE_STROKE.toPx()
                )
            }
        }

        // 2. El golpe: picos blancos dos fotogramas y un anillo que rueda.
        val sinceHit = seconds - BANNER_HIT_S
        if (sinceHit >= 0f && sinceHit < 0.07f) {
            val arm = if (sinceHit < 0.035f) 22.dp.toPx() else 30.dp.toPx()
            repeat(10) { index ->
                val angle = index / 10f * (2f * PI.toFloat()) + 0.25f
                val reach = arm * (0.6f + hashNoise(index, 5) * 0.7f)
                drawLine(
                    color = if (sinceHit < 0.035f) Color.White else color,
                    start = Offset(
                        center.x + dx + cos(angle) * reach * 0.35f,
                        center.y + sin(angle) * reach * 0.35f
                    ),
                    end = Offset(
                        center.x + dx + cos(angle) * reach,
                        center.y + sin(angle) * reach
                    ),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }
        val ringT = sinceHit / 0.32f
        if (ringT > 0f && ringT < 1f) {
            val r = 20.dp.toPx() + 34.dp.toPx() * easeOutCubic(ringT)
            drawOval(
                color = if (ringT < 0.3f) Color.White else color,
                topLeft = Offset(center.x + dx - r * 1.5f, center.y - r * 0.75f),
                size = Size(r * 3f, r * 1.5f),
                alpha = 1f - ringT,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 3. Chispas del impacto, resueltas en el sitio: no hay emisor.
        if (sinceHit >= 0f && sinceHit < 0.30f) {
            repeat(12 + combo.coerceAtMost(8)) { index ->
                val angle = index / 20f * (2f * PI.toFloat()) + 0.4f
                val speed = 90f + hashNoise(index, 7) * 160f
                val distance = speed * sinceHit * (1f - sinceHit * 1.2f).coerceAtLeast(0.1f)
                drawRect(
                    color = (if (index % 3 == 0) Color.White else color)
                        .copy(alpha = 1f - sinceHit / 0.30f),
                    topLeft = Offset(
                        center.x + dx + cos(angle) * distance.dp.toPx(),
                        center.y + sin(angle) * distance.dp.toPx()
                    ),
                    size = Size(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }

        // 4. Estelas: dos copias apagadas persiguiendo a las letras.
        if (moving && gone < 1f) {
            listOf(0.03f to 0.28f, 0.015f to 0.48f).forEach { (lag, tone) ->
                val lagged = if (seconds < BANNER_HIT_S) seconds - lag else seconds + lag
                PixelFont.run {
                    drawText(
                        text = text,
                        origin = Offset(
                            center.x + bannerX(lagged).dp.toPx() - wordW / 2f,
                            center.y - wordH / 2f
                        ),
                        pixel = px,
                        color = color.copy(alpha = tone),
                        stretchX = stretch
                    )
                }
            }
        }

        // 5. Las letras, con contorno oscuro, tramos de color y brillo que barre.
        if (gone < 1f) {
            val body = if (flash) Color.White else color
            val top = if (flash) Color.White else lerp(color, Color.White, 0.55f)
            val bottom = if (flash) Color.White else lerp(color, Color.Black, 0.55f)
            val sweep = seg(seconds, BANNER_SHINE_FROM_S, BANNER_SHINE_TO_S)
            PixelFont.run {
                drawText(
                    text = text,
                    origin = Offset(
                        center.x + dx - wordW / 2f,
                        center.y - wordH / 2f
                    ),
                    pixel = px,
                    color = body.copy(alpha = 1f - gone),
                    stretchX = stretch,
                    top = top.copy(alpha = 1f - gone),
                    bottom = bottom.copy(alpha = 1f - gone),
                    outline = RING_OUTLINE.copy(alpha = 1f - gone),
                    shine = if (sweep > 0f && sweep < 1f) {
                        -BANNER_SHINE_MARGIN + (cells + BANNER_SHINE_MARGIN * 2) * sweep
                    } else {
                        null
                    }
                )
            }
        }

        // 6. Salida: las motas que se escapan hacia la derecha.
        if (seconds > BANNER_OUT_S) {
            val fade = 1f - seg(seconds, BANNER_OUT_S + 0.06f, BANNER_OUT_S + 0.20f)
            if (fade > 0f) {
                repeat(10) { index ->
                    val y = (hashNoise(index, 11) - 0.5f) * 26f
                    val travel = (seconds - BANNER_OUT_S) * 260f *
                        (0.5f + hashNoise(index, 13))
                    drawRect(
                        color = Color.White.copy(alpha = fade * 0.8f),
                        topLeft = Offset(
                            center.x + travel.dp.toPx(),
                            center.y + y.dp.toPx()
                        ),
                        size = Size(6.dp.toPx(), 2.dp.toPx())
                    )
                }
            }
        }
    }
}

/**
 * Dónde caen las letras a lo largo de su vida, en dp respecto al centro: entran
 * desde la izquierda frenando, se pasan de largo y vibran, y salen disparadas a la
 * derecha. Es la curva del `xAt` de `ui/combo-counter`.
 */
private fun bannerX(t: Float): Float {
    if (t < BANNER_HIT_S) {
        val u = 1f - t / BANNER_HIT_S
        return -32f * u * u
    }
    if (t < BANNER_OUT_S) {
        val e = t - BANNER_HIT_S
        return 7f * exp(-e * 18f) * sin(e * 34f)
    }
    val u = ((t - BANNER_OUT_S) / 0.2f).coerceIn(0f, 1f)
    return if (u < 0.3f) -4f * easeOutQuad(u / 0.3f)
    else -4f + 24f * easeInQuad((u - 0.3f) / 0.7f)
}

/** Ruido determinista por índice: las líneas de velocidad cambian por fotograma. */
private fun hashNoise(index: Int, salt: Int): Float {
    var h = index * 374761393 + salt * 668265263
    h = (h xor (h shr 13)) * 1274126177
    return ((h xor (h shr 16)) and 0x7fffffff) / 2147483647f
}

/** Tramo normalizado de [t] entre [from] y [to]. */
private fun seg(t: Float, from: Float, to: Float): Float =
    ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun easeOutQuad(t: Float): Float {
    val u = 1f - t.coerceIn(0f, 1f)
    return 1f - u * u
}

private fun easeInQuad(t: Float): Float {
    val u = t.coerceIn(0f, 1f)
    return u * u
}

/** Golpe, pulso y salida del cartel, en segundos. Salen del efecto de pixelfx. */
private const val BANNER_HIT_S = 0.10f
private const val BANNER_BEAT_S = 0.42f
private const val BANNER_OUT_S = 0.95f

/** Vida total del cartel; pasado este tiempo el llamante deja de componerlo. */
private const val BANNER_LIFE_S = 1.20f

/** Ventana en la que el brillo barre las letras de izquierda a derecha. */
private const val BANNER_SHINE_FROM_S = 0.55f
private const val BANNER_SHINE_TO_S = 0.80f

/** Celdas de margen por cada lado que recorre el brillo antes y después del texto. */
private const val BANNER_SHINE_MARGIN = 2f

/** Lado de una celda de la fuente: el mando de tamaño de todo el cartel. */
private val BANNER_CELL = 4.dp

/** Alto del lienzo del cartel; deja aire para las líneas de velocidad. */
private val BANNER_HEIGHT = 150.dp

/** Grosor de las líneas de velocidad. */
private val BANNER_LINE_STROKE = 1.5.dp

/** Fotogramas por segundo con los que se relanzan las líneas de velocidad. */
private const val BANNER_FPS = 30f

/** Líneas de velocidad que se intentan por fotograma. */
private const val BANNER_SPEED_LINES = 18

/** Desfase entre fotogramas: mismas líneas, colocadas de otra forma cada vez. */
private const val BANNER_FRAME_STRIDE = 31

/** Ensanche horizontal de las letras mientras vuelan: al entrar y al salir. */
private const val BANNER_STRETCH_IN = 1.22f
private const val BANNER_STRETCH_OUT = 1.12f

/** Cuánto crecen las letras en el pico del latido. */
private const val BANNER_PULSE_SCALE = 0.12f

/** Interpolación lineal suelta, para no arrastrar otra dependencia. */
private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Curva de salida rápida y frenada, la que usan el confeti y el golpe. */
private fun easeOutCubic(t: Float): Float {
    val u = 1f - t.coerceIn(0f, 1f)
    return 1f - u * u * u
}

// ─────────────────────────── Voltorb (Lt. Surge) ───────────────────────────

/**
 * Escena del minijuego de Voltorb de Lt. Surge.
 *
 * El tablero es una cuadrícula de `rows × columns` celdas; varios Voltorb pueden
 * estar cargados a la vez y su calor crece con el tiempo. El jugador apaga los
 * cargados tocándolos; las celdas apagadas devuelven una descarga. El modelo
 * decide qué pasa —esta escena solo pinta y envía la celda tocada—, igual que las
 * otras mecánicas.
 *
 * La cuadrícula se dimensiona por **ancho**: ocupa de extremo a extremo de la
 * pantalla y el líder se dibuja más bajo para cederle altura. Si el alto no
 * alcanza (pantallas muy bajas), la celda se encoge y el tablero deja de llegar a
 * los bordes antes que desbordarse sobre el jugador o la barra de vida.
 *
 * La capa de toque es una sola, como en [GymScene]: enemigo si está expuesto, y
 * si no, la celda de la cuadrícula bajo el dedo.
 */
@Composable
private fun VoltorbGridScene(
    state: GymState,
    exposed: Boolean,
    layout: GymSceneLayout,
    enemyFade: Float,
    onVoltorbTap: (Int) -> Unit,
    onEnemyHit: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Reloj de fotogramas: el calor de cada Voltorb sale de su progreso, así que
    // la escena se repinta sola sin que el ViewModel actualice cada frame.
    val clock = remember { mutableLongStateOf(nowMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            clock.longValue = nowMs()
        }
    }
    val now = clock.longValue

    // Apagado, explosión o descarga: el ViewModel publica el resultado y aquí se
    // convierte en un destello sobre esa celda.
    val feedback = state.voltorbFeedback
    val feedbackProgress = remember { Animatable(1f) }
    LaunchedEffect(feedback) {
        if (feedback == null) return@LaunchedEffect
        feedbackProgress.snapTo(0f)
        feedbackProgress.animateTo(1f, tween(durationMillis = VOLTORB_FEEDBACK_MS))
    }

    // Latido del enemigo mientras está expuesto.
    val pulse = rememberInfiniteTransition(label = "voltorbExposed")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 600), RepeatMode.Reverse),
        label = "voltorbPulseScale"
    )

    BoxWithConstraints(modifier = modifier) {
        val rows = state.rows.coerceAtLeast(1)
        val cols = state.columns.coerceAtLeast(1)

        // Frames del Voltorb: reposo, carga progresiva y resolución. Se decodifican
        // una vez por combate y se reutilizan en las 16 celdas.
        val context = LocalContext.current
        val frames = remember {
            VOLTORB_FRAME_RES.map { res ->
                BitmapFactory.decodeResource(context.resources, res).asImageBitmap()
            }
        }

        // Hoja de la aurora eléctrica (pixelfx: lightning/voltorb-aura). 30 frames
        // de 160x160 en una rejilla de 6 columnas.
        val auraSheet = remember {
            BitmapFactory.decodeResource(context.resources, R.drawable.voltorb_aura)
                .asImageBitmap()
        }

        // Explosión de un Voltorb (pixelfx: explosions/voltorb-blast), recortada a
        // los 32 frames que se ven.
        val blastSheet = remember {
            BitmapFactory.decodeResource(context.resources, R.drawable.voltorb_blast)
                .asImageBitmap()
        }

        // Una entrada por celda afectada, con su instante de arranque y su
        // duración: así dos explosiones en el mismo tick se pintan las dos, cada
        // una con su reloj, y el fallo por tocar una celda apagada puede correr
        // más rápido que la explosión real.
        var blasts by remember { mutableStateOf(emptyList<VoltorbBlast>()) }
        LaunchedEffect(feedback) {
            if (feedback == null) return@LaunchedEffect
            val start = nowMs()
            val (cells, duration) = when (feedback.outcome) {
                VoltorbOutcome.EXPLODED -> feedback.cells to VOLTORB_BLAST_MS
                // Clic en una celda apagada: misma animación, sin bamboleo y más
                // corta, como castigo inmediato por el error.
                VoltorbOutcome.SHOCK -> listOf(feedback.cell) to VOLTORB_SHOCK_BLAST_MS
                VoltorbOutcome.DEFUSED -> return@LaunchedEffect
            }
            blasts = blasts.filter { start - it.startMs < it.durationMs } +
                cells.map { VoltorbBlast(it, start, duration) }
        }

        // La cuadrícula manda el layout: se dimensiona por **ancho** para llegar de
        // extremo a extremo de la pantalla. El alto disponible —entre la barra del
        // líder y la barra de vida del jugador— actúa solo como techo de seguridad:
        // si la pantalla es demasiado baja, la celda se encoge y la cuadrícula deja
        // de ocupar todo el ancho en vez de desbordarse.
        val cellFromWidth = (maxWidth - VOLTORB_GAP * (cols - 1)) / cols
        val gridTop = layout.enemyTop + layout.enemyHeight + VOLTORB_BAR_RESERVE
        val bottomReserved = layout.playerHeight + VOLTORB_PLAYER_GAP + VOLTORB_HP_BAR_RESERVE
        val maxGridHeight =
            (maxHeight - gridTop - bottomReserved).coerceAtLeast(VOLTORB_MIN_AREA)
        val cellFromHeight = (maxGridHeight - VOLTORB_GAP * (rows - 1)) / rows
        val cellSide = minOf(cellFromWidth, cellFromHeight)
            .coerceAtLeast(VOLTORB_MIN_CELL)

        val gridWidth = cellSide * cols + VOLTORB_GAP * (cols - 1)
        val gridHeight = cellSide * rows + VOLTORB_GAP * (rows - 1)
        val gridLeft = (maxWidth - gridWidth) / 2f
        // El jugador se ancla debajo de la cuadrícula, no al borde inferior: así el
        // tablero puede crecer sin taparlo.
        val playerTop = gridTop + gridHeight + VOLTORB_PLAYER_GAP

        Canvas(modifier = Modifier.fillMaxSize()) {
            val side = cellSide.toPx()
            val gapPx = VOLTORB_GAP.toPx()
            val left = gridLeft.toPx()
            val top = gridTop.toPx()

            // Tablero oscuro difuminado detrás de la cuadrícula: el fondo del
            // gimnasio es arte cargado y sin esto los frames de reposo y de carga
            // se confunden entre sí. Va como una sola pieza y no por celda porque
            // el sprite ocupa casi toda la celda: un halo individual quedaría
            // tapado por el propio Voltorb.
            //
            // El borde se difumina con pasos concéntricos (no hay `blur` fiable en
            // el Canvas por debajo de API 31). Pocos pasos bastan: negro sobre arte
            // no deja bandas visibles.
            val boardPad = VOLTORB_BOARD_PAD.toPx()
            val feather = VOLTORB_BOARD_FEATHER.toPx()
            val boardLeft = left - boardPad
            val boardTop = top - boardPad
            val boardWidth = (gridWidth.toPx() + boardPad * 2f)
            val boardHeight = (gridHeight.toPx() + boardPad * 2f)
            val boardCorner = VOLTORB_BOARD_CORNER.toPx()
            val stepAlpha = VOLTORB_BOARD_ALPHA / VOLTORB_BOARD_STEPS

            for (step in 0 until VOLTORB_BOARD_STEPS) {
                val grow = feather * (VOLTORB_BOARD_STEPS - step) / VOLTORB_BOARD_STEPS
                drawRoundRect(
                    color = Color.Black.copy(alpha = stepAlpha),
                    topLeft = Offset(boardLeft - grow, boardTop - grow),
                    size = Size(boardWidth + grow * 2f, boardHeight + grow * 2f),
                    cornerRadius = CornerRadius(boardCorner + grow)
                )
            }

            for (row in 0 until rows) {
                for (column in 0 until cols) {
                    val cell = row * cols + column
                    val center = Offset(
                        x = left + column * (side + gapPx) + side / 2f,
                        y = top + row * (side + gapPx) + side / 2f
                    )
                    val voltorb = state.voltorbs.firstOrNull { it.cell == cell }

                    // Aurora eléctrica solo mientras la celda está cargándose: es
                    // el aviso de que ese Voltorb está armado. Va detrás del sprite
                    // para no taparle la cara.
                    if (voltorb != null && !exposed) {
                        drawVoltorbAura(
                            sheet = auraSheet,
                            center = center,
                            cellSide = side,
                            now = now,
                            phaseOffsetMs = cell * VOLTORB_AURA_PHASE_STEP_MS
                        )
                    }

                    drawVoltorbCell(
                        center = center,
                        radius = side / 2f,
                        voltorb = voltorb,
                        feedback = feedback?.takeIf { it.cell == cell },
                        flash = feedbackProgress.value,
                        now = now,
                        frames = frames,
                        exposed = exposed
                    )
                }
            }

            // Los estallidos van por encima de todas las celdas: la celda afectada
            // queda tapada por su propia animación, no al revés.
            blasts.forEach { blast ->
                val progress = ((now - blast.startMs).toFloat() / blast.durationMs)
                    .coerceIn(0f, 1f)
                if (progress < 1f) {
                    val column = blast.cell % cols
                    val row = blast.cell / cols
                    drawVoltorbBlast(
                        sheet = blastSheet,
                        center = Offset(
                            x = left + column * (side + gapPx) + side / 2f,
                            y = top + row * (side + gapPx) + side / 2f
                        ),
                        cellSide = side,
                        progress = progress
                    )
                }
            }
        }

        // Enemigo, con pulso mientras está expuesto. Se estrecha en este gimnasio
        // para dejarle la altura a la cuadrícula.
        GymSpriteBox(
            gifRes = enemyGifFor(state.gymId),
            name = state.leaderName,
            type = state.gymType,
            contentDescription = stringResource(R.string.gym_title, state.leaderName),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = layout.enemyTop)
                .size(layout.enemyWidth, layout.enemyHeight)
                .graphicsLayer {
                    val scale = if (exposed) pulseScale else 1f
                    scaleX = scale
                    scaleY = scale
                    // Al perder, el Pokémon del líder se hunde y se apaga.
                    alpha = 1f - enemyFade
                    translationY = enemyFade * VICTORY_ENEMY_SINK.toPx()
                }
        )

        // Jugador; placeholder por tipo si aún no hay back. Va debajo de la
        // cuadrícula, así que se posiciona desde arriba y no desde el borde.
        GymSpriteBox(
            gifRes = backGifFor(state.leadSpeciesId),
            name = state.leadName,
            type = state.leadType,
            contentDescription = state.leadName,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (maxWidth - layout.playerWidth) / 2f, y = playerTop)
                .size(layout.playerWidth, layout.playerHeight)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(gridLeft, gridTop, cellSide, rows, cols, exposed) {
                    val left = gridLeft.toPx()
                    val top = gridTop.toPx()
                    val side = cellSide.toPx()
                    val gapPx = VOLTORB_GAP.toPx()

                    val enemyLeft = (size.width - layout.enemyWidth.toPx()) / 2f
                    val enemyTop = layout.enemyTop.toPx()
                    val enemyRight = enemyLeft + layout.enemyWidth.toPx()
                    val enemyBottom = enemyTop + layout.enemyHeight.toPx()

                    detectTapGestures { tap ->
                        if (
                            exposed &&
                            tap.x in enemyLeft..enemyRight &&
                            tap.y in enemyTop..enemyBottom
                        ) {
                            onEnemyHit()
                            return@detectTapGestures
                        }

                        val column = ((tap.x - left) / (side + gapPx)).toInt()
                        val row = ((tap.y - top) / (side + gapPx)).toInt()
                        if (row !in 0 until rows || column !in 0 until cols) {
                            return@detectTapGestures
                        }

                        // El hueco entre celdas no es tocable: solo cuenta el
                        // interior de la celda, no la franja que la separa.
                        val cellLeft = left + column * (side + gapPx)
                        val cellTop = top + row * (side + gapPx)
                        if (
                            tap.x in cellLeft..(cellLeft + side) &&
                            tap.y in cellTop..(cellTop + side)
                        ) {
                            onVoltorbTap(row * cols + column)
                        }
                    }
                }
        )
    }
}

/**
 * Pinta la aurora eléctrica de una celda cargada usando la hoja exportada por
 * pixelfx (`lightning/voltorb-aura`): 30 frames de 160x160 en una rejilla de 6
 * columnas a 30 fps.
 *
 * Se dibuja más grande que la celda para que los arcos caigan por fuera de la
 * silueta del Voltorb. Cada celda recibe un desfase propio: si compartieran fase,
 * las dieciséis irían al unísono y parecería un patrón, no electricidad.
 */
private fun DrawScope.drawVoltorbAura(
    sheet: ImageBitmap,
    center: Offset,
    cellSide: Float,
    now: Long,
    phaseOffsetMs: Long
) {
    val frameMs = 1_000L / VOLTORB_AURA_FPS
    val frame = (((now + phaseOffsetMs) / frameMs) % VOLTORB_AURA_FRAMES).toInt()
    val column = frame % VOLTORB_AURA_COLUMNS
    val row = frame / VOLTORB_AURA_COLUMNS

    val sidePx = cellSide * VOLTORB_AURA_SCALE
    drawImage(
        image = sheet,
        srcOffset = IntOffset(
            x = column * VOLTORB_AURA_FRAME_PX,
            y = row * VOLTORB_AURA_FRAME_PX
        ),
        srcSize = IntSize(VOLTORB_AURA_FRAME_PX, VOLTORB_AURA_FRAME_PX),
        dstOffset = IntOffset(
            x = (center.x - sidePx / 2f).roundToInt(),
            y = (center.y - sidePx / 2f).roundToInt()
        ),
        dstSize = IntSize(sidePx.roundToInt(), sidePx.roundToInt()),
        filterQuality = FilterQuality.None
    )
}

/**
 * Un estallido en curso sobre una celda: cuándo arrancó y cuánto dura. La
 * explosión real dura [VOLTORB_BLAST_MS]; la descarga por tocar una celda apagada
 * usa la misma hoja con [VOLTORB_SHOCK_BLAST_MS], más corta.
 */
private data class VoltorbBlast(
    val cell: Int,
    val startMs: Long,
    val durationMs: Long
)

/**
 * Pinta la explosión de una celda a partir de la hoja exportada por pixelfx
 * (`explosions/voltorb-blast`), un one-shot: [progress] va de 0 a 1 una sola vez y
 * el frame sale de ahí.
 *
 * El humo de la hoja cubre el cuerpo entero del Voltorb, así que el estallido tapa
 * la celda en vez de dejar que se vea el sprite de reposo por debajo.
 */
private fun DrawScope.drawVoltorbBlast(
    sheet: ImageBitmap,
    center: Offset,
    cellSide: Float,
    progress: Float
) {
    val frame = (progress * VOLTORB_BLAST_FRAMES).toInt()
        .coerceIn(0, VOLTORB_BLAST_FRAMES - 1)
    val column = frame % VOLTORB_BLAST_COLUMNS
    val row = frame / VOLTORB_BLAST_COLUMNS

    val widthPx = cellSide * VOLTORB_BLAST_SCALE
    val heightPx = widthPx * VOLTORB_BLAST_FRAME_H / VOLTORB_BLAST_FRAME_W
    drawImage(
        image = sheet,
        srcOffset = IntOffset(
            x = column * VOLTORB_BLAST_FRAME_W,
            y = row * VOLTORB_BLAST_FRAME_H
        ),
        srcSize = IntSize(VOLTORB_BLAST_FRAME_W, VOLTORB_BLAST_FRAME_H),
        dstOffset = IntOffset(
            x = (center.x - widthPx / 2f).roundToInt(),
            y = (center.y - heightPx / 2f).roundToInt()
        ),
        dstSize = IntSize(widthPx.roundToInt(), heightPx.roundToInt()),
        filterQuality = FilterQuality.None
    )
}

/**
 * Pinta una celda con los frames del Voltorb.
 *
 * El frame sale del estado de la celda: reposo ([VOLTORB_IDLE_FRAME]) si no hay
 * nada cargado, progresión de carga según su calor, y el último frame —el de
 * resolución— mientras dura el [feedback] de esa celda. A partir de
 * [VOLTORB_WOBBLE_FROM_FRAME] el sprite se balancea a los lados: es el aviso de
 * que está por explotar.
 */
private fun DrawScope.drawVoltorbCell(
    center: Offset,
    radius: Float,
    voltorb: Voltorb?,
    feedback: VoltorbFeedback?,
    flash: Float,
    now: Long,
    frames: List<ImageBitmap>,
    exposed: Boolean
) {
    // `flash` llega a 1f cuando la animación de feedback terminó: hasta entonces
    // la celda muestra el frame de resolución y después vuelve a reposo. Si la
    // celda ya volvió a cargarse, manda el Voltorb nuevo: el destello no puede
    // taparlo. Una descarga (clic en celda apagada) no resuelve el Voltorb, así
    // que no reproduce el frame de resolución.
    val resolvesCell = feedback != null &&
        (feedback.outcome == VoltorbOutcome.DEFUSED ||
            feedback.outcome == VoltorbOutcome.EXPLODED)
    val resolving = resolvesCell && flash < 1f && voltorb == null
    val heat = voltorb?.progressAt(now) ?: 0f

    // La progresión de carga usa todos los frames menos el último, que está
    // reservado para la resolución (apagado a tiempo o explosión). Durante la
    // ventana de golpeo al líder el tablero entero se queda en ese último frame:
    // no hay Voltorb armado y la cuadrícula deja de competir con el sprite al que
    // hay que tocar.
    val lastChargeFrame =
        (frames.lastIndex - 1).coerceAtLeast(VOLTORB_FIRST_CHARGE_FRAME)
    val frameIndex = when {
        exposed || resolving -> frames.lastIndex
        voltorb != null -> VOLTORB_FIRST_CHARGE_FRAME +
            ((lastChargeFrame - VOLTORB_FIRST_CHARGE_FRAME) * heat).roundToInt()
        else -> VOLTORB_IDLE_FRAME
    }.coerceIn(0, frames.lastIndex)

    // Vaivén lateral desde el frame de aviso: más amplitud cuanto más cerca de
    // explotar. Es el único movimiento de la celda, así que no compite con el HUD.
    val wobble = if (!resolving && !exposed && frameIndex >= VOLTORB_WOBBLE_FROM_FRAME) {
        val span = (frames.lastIndex - VOLTORB_WOBBLE_FROM_FRAME).coerceAtLeast(1)
        val strength = (frameIndex - VOLTORB_WOBBLE_FROM_FRAME + 1).toFloat() / span
        sin(now / VOLTORB_WOBBLE_PERIOD_MS) * radius * VOLTORB_WOBBLE_FRACTION * strength
    } else {
        0f
    }

    if (voltorb != null && !resolving) {
        // Halo de calor sobre el tablero oscuro: a tamaño de celda los frames se
        // parecen entre sí y esto marca cuál está armado.
        val glow = lerp(VOLTORB_CHARGE_COLD, VOLTORB_CHARGE_HOT, heat)
        drawCircle(
            color = glow.copy(alpha = 0.20f + 0.38f * heat),
            radius = radius * (1.05f + 0.20f * heat),
            center = center
        )
    }

    val frame = frames[frameIndex]
    val scale = minOf(
        (radius * 2f) / frame.width,
        (radius * 2f) / frame.height
    )
    val dstWidth = (frame.width * scale).roundToInt().coerceAtLeast(1)
    val dstHeight = (frame.height * scale).roundToInt().coerceAtLeast(1)
    drawImage(
        image = frame,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(frame.width, frame.height),
        dstOffset = IntOffset(
            (center.x - dstWidth / 2f + wobble).roundToInt(),
            (center.y - dstHeight / 2f).roundToInt()
        ),
        dstSize = IntSize(dstWidth, dstHeight),
        filterQuality = FilterQuality.None
    )

    if (feedback != null && flash < 1f) {
        val fade = (1f - flash).coerceIn(0f, 1f)
        when (feedback.outcome) {
            VoltorbOutcome.DEFUSED -> drawCircle(
                color = Color.White.copy(alpha = fade),
                radius = radius * (1f + 0.55f * flash),
                center = center,
                style = Stroke(width = radius * 0.18f)
            )

            // La explosión y la descarga las pinta `drawVoltorbBlast` con la hoja
            // de pixelfx: aquí solo queda el aviso de apagado.
            VoltorbOutcome.SHOCK,
            VoltorbOutcome.EXPLODED -> Unit
        }
    }
}

/** Color clásico del tubo según lo lleno que esté. */
private fun hpColor(fraction: Float): Color = when {
    fraction > 0.5f -> RetroColors.HpGreen
    fraction > 0.2f -> RetroColors.HpYellow
    else -> RetroColors.HpRed
}

/**
 * Tubo de vida estilo GBA: pista oscura, relleno sólido con brillo superior y
 * borde negro. Sustituye a `LinearProgressIndicator`, que pintaba el morado del
 * tema por defecto y no se leía como un juego de Pokémon.
 */
@Composable
private fun GymHpTube(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    tubeHeight: Dp = HP_TUBE_HEIGHT
) {
    val animated by animateFloatAsState(targetValue = fraction, label = "gymHpTube")
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .height(tubeHeight)
            .clip(shape)
            .background(RetroColors.BarTrack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .background(color)
        ) {
            // Brillo del tubo de cristal: franja clara en el tercio superior.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.34f)
                    .background(Color.White.copy(alpha = 0.35f))
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(HP_TUBE_BORDER, RetroColors.InkBlack, shape)
        )
    }
}

/**
 * Fila de vida: etiqueta PS, tubo y cifras. La comparten la placa del líder y la
 * del principal para que ambas barras se lean igual.
 *
 * [below] dibuja contenido pegado debajo del tubo, dentro de la misma columna:
 * así la barra de postura del líder queda exactamente del mismo ancho que su
 * barra de vida.
 */
@Composable
private fun GymHpRow(
    fraction: Float,
    hp: Int,
    maxHp: Int,
    modifier: Modifier = Modifier,
    below: @Composable ColumnScope.() -> Unit = {}
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.gym_hp_label),
            color = HP_LABEL_COLOR,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.width(5.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            GymHpTube(
                fraction = fraction,
                color = hpColor(fraction),
                modifier = Modifier.fillMaxWidth()
            )
            below()
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.gym_hp_pair, hp, maxHp),
            color = HP_NUMBER_COLOR,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

/**
 * Placa de estado de un Pokémon: nombre arriba y, debajo, su fila de vida. Sin
 * nivel —en este juego no existe— y con el color del tubo siguiendo el porcentaje.
 */
@Composable
private fun GymStatPlate(
    name: String?,
    fraction: Float,
    hp: Int,
    maxHp: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .retroPlate()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (!name.isNullOrBlank()) {
            Text(
                text = name,
                color = RetroColors.InkBlack,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        GymHpRow(fraction = fraction, hp = hp, maxHp = maxHp)
    }
}

/**
 * Tubo de **postura** del líder, pegado debajo de su barra de vida dentro de la
 * misma placa. Se llena con cada acierto del jugador y, al completarse, rompe la
 * postura y abre su ventana. Solo se pinta el tubo —sin cifras ni etiqueta— y el
 * estado se lee por color: ámbar mientras se llena, amarillo en la ventana
 * expuesta y rojo en furia.
 */
@Composable
private fun GymPostureBar(
    state: GymState,
    exposed: Boolean,
    modifier: Modifier = Modifier
) {
    val postureColor = when {
        exposed -> EXPOSED_COLOR
        state.rage -> RAGE_COLOR
        else -> POSTURE_COLOR
    }
    GymHpTube(
        fraction = state.postureFraction,
        color = postureColor,
        tubeHeight = POSTURE_TUBE_HEIGHT,
        modifier = modifier
    )
}

/**
 * Sprite del combate con sustituto: si [gifRes] es `null` dibuja
 * [GymSpritePlaceholder] con el nombre y el color del tipo.
 */
@Composable
private fun GymSpriteBox(
    gifRes: Int?,
    name: String?,
    type: PokemonType?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    if (gifRes != null) {
        AnimatedGifImage(
            resId = gifRes,
            contentDescription = contentDescription,
            modifier = modifier
        )
    } else {
        GymSpritePlaceholder(name = name, type = type, modifier = modifier)
    }
}

/**
 * HUD superior: placa de estado del Pokémon del líder —de lado a lado, con la
 * postura pegada debajo de la vida—. Sin `Surface` ni fondo translúcido, para
 * **no consumir** los toques que pasan por detrás.
 *
 * El nombre es el del Pokémon que pelea, no el del líder, y no hay nivel: en este
 * juego la barra mide la vida del Pokémon, no del entrenador. Se retiraron el
 * cartel "Gimnasio de…", la línea de tipo y la caja con la pista: cómo se juega lo
 * explica el tutorial de entrada al gimnasio.
 */
@Composable
private fun GymHud(
    state: GymState,
    exposed: Boolean,
    modifier: Modifier = Modifier
) {
    GymLeaderPlate(
        state = state,
        exposed = exposed,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Placa del Pokémon del líder: nombre —con el chip de furia a la derecha—, su
 * barra de vida y, justo debajo, la barra de postura. Ocupa todo el ancho para
 * que la vida se lea de lado a lado de la pantalla.
 */
@Composable
private fun GymLeaderPlate(
    state: GymState,
    exposed: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .retroPlate()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = enemyNameFor(state.gymId) ?: state.leaderName,
                color = RetroColors.InkBlack,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (state.rage) {
                Text(
                    text = stringResource(R.string.gym_rage),
                    color = RetroColors.DialogCream,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .background(RAGE_COLOR)
                        .border(PLATE_BORDER, RetroColors.InkBlack)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        GymHpRow(
            fraction = state.leaderHpFraction,
            hp = state.leaderHp,
            maxHp = state.leaderMaxHp,
            below = {
                GymPostureBar(
                    state = state,
                    exposed = exposed,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

/**
 * Vida del principal del jugador, anclada al borde inferior. Usa la misma placa
 * que el HUD del líder para que ambos se lean con el mismo lenguaje.
 */
@Composable
private fun PlayerHpBar(
    state: GymState,
    modifier: Modifier = Modifier
) {
    GymStatPlate(
        name = state.leadName,
        fraction = state.playerHpFraction,
        hp = state.playerHp,
        maxHp = state.maxHp,
        modifier = modifier.width(PLAYER_PLATE_WIDTH)
    )
}

/**
 * Panel de derrota centrado: placa y botones con el mismo lenguaje que el HUD de
 * vida, en lugar de `Surface` + `Button` de Material.
 */
@Composable
private fun DefeatPanel(
    onRetry: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .retroPlate()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.gym_lost),
            color = RetroColors.InkBlack,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RetroButton(
                label = stringResource(R.string.gym_retry),
                onClick = onRetry,
                modifier = Modifier.weight(1f)
            )
            RetroButton(
                label = stringResource(R.string.gym_leave),
                base = RetroColors.ResetBase,
                edge = RetroColors.ResetEdge,
                onClick = onLeave,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun nowMs(): Long = System.nanoTime() / 1_000_000L

/** Ancho de la franja de columnas: una fracción del ancho, con tope. */
private const val LANE_WIDTH_FRACTION = 0.72f

/** Tope de la franja para que en pantallas grandes no se abran demasiado. */
private val LANE_MAX_WIDTH = 340.dp

/**
 * Separación de la barra de vida del jugador respecto al borde inferior. El
 * tamaño y la posición de los sprites salen de [GymSceneLayouts], uno por
 * gimnasio.
 */
private val PLAYER_HP_BOTTOM_PADDING = 10.dp

/** Cuánto por debajo de la cabeza del jugador se considera impacto. */
private val PLAYER_HIT_INSET = 16.dp

/**
 * Medidas del HUD de placas: ancho de la placa del principal, alto del tubo de
 * cada barra y grosor del borde/sombra dura de la placa.
 */
private val PLAYER_PLATE_WIDTH = 196.dp
private val HP_TUBE_HEIGHT = 12.dp
private val POSTURE_TUBE_HEIGHT = 10.dp
private val HP_TUBE_BORDER = 2.dp
private val PLATE_BORDER = 2.dp

/** Tinta de la etiqueta PS y de las cifras dentro de la placa clara. */
private val HP_LABEL_COLOR = Color(0xFFE8A33D)
private val HP_NUMBER_COLOR = Color(0xFF3C4652)

/** Colores de la barra de postura, del estado expuesto y de la furia. */
private val POSTURE_COLOR = Color(0xFFFFC107)
private val EXPOSED_COLOR = Color(0xFFFFEB3B)
private val RAGE_COLOR = Color(0xFFFF5252)

/**
 * Anillos del minijuego de Misty: radio fijo alrededor del jugador, radio de
 * entrada y grosor del trazo. El anillo que entra se cierra desde
 * [RING_START_RADIUS] hasta [RING_TARGET_RADIUS].
 *
 * El radio fijo se calcula para el cuadro del jugador del gimnasio de Misty
 * ([GymSceneLayouts]): con 90 × 102 dp la semidiagonal mide ≈ 68 dp y 78 dp deja
 * unos 10 dp de margen. Si cambia ese cuadro, ajustar ambos radios en la misma
 * proporción.
 */
private val RING_TARGET_RADIUS = 78.dp
private val RING_START_RADIUS = 175.dp
private val RING_STROKE = 4.dp
private val RING_GLOW_STROKE = 11.dp

/** Ondulación del anillo de agua fuera de la ventana de acierto. */
private val RING_WAVE_AMPLITUDE = 2.5.dp

/** Distancia entre el cuerpo del aro y la cresta de espuma. */
private val RING_CREST_OFFSET = 1.5.dp
private val RING_CREST_STROKE = 1.dp

/**
 * Cuánto dura el tinte de resultado en el círculo fijo del jugador, en ms. Cubre
 * el arranque del efecto (0,83 s el acierto, 1,03 s el fallo) sin quedarse pegado.
 */
private const val RING_TINT_MS = 800L

/** Paleta de agua de los anillos. */
private val WATER_DEEP = Color(0xFF0D47A1)
private val WATER_MID = Color(0xFF1E88E5)
private val WATER_LIGHT = Color(0xFF4FC3F7)
private val WATER_FOAM = Color(0xFFE1F5FE)
private val WATER_POOL = Color(0xFF29B6F6)

/** Contorno oscuro de los anillos: los separa del agua y de las líneas blancas. */
private val RING_OUTLINE = Color(0xFF06111C)

/** Amarillo del anillo cuando está en su ventana de acierto. */
private val RING_ON_BEAT_COLOR = Color(0xFFFFEB3B)
private val RING_ON_BEAT_DEEP = Color(0xFFFFB300)

/** Rojo del fallo. */
private val MISS_RED = Color(0xFFFF5252)

/**
 * Verde del acierto. Es el paso claro de la rampa con la que se exportó
 * `hydro_burst.png`, así el mensaje y el estallido hablan el mismo idioma.
 */
private val HIT_GREEN = Color(0xFF7EE88A)

/** Frames del Voltorb: reposo, carga y resolución, en orden de reproducción. */
private val VOLTORB_FRAME_RES = intArrayOf(
    R.drawable.voltorb_capa1,
    R.drawable.voltorb_capa2,
    R.drawable.voltorb_capa3,
    R.drawable.voltorb_capa4,
    R.drawable.voltorb_capa5,
    R.drawable.voltorb_capa6,
    R.drawable.voltorb_capa7,
    R.drawable.voltorb_capa8,
    R.drawable.voltorb_capa9
)

/** Índice del frame de reposo: la celda sin cargar. */
private const val VOLTORB_IDLE_FRAME = 0

/** Primer frame de la progresión de carga (el segundo del set). */
private const val VOLTORB_FIRST_CHARGE_FRAME = 1

/** Frame desde el que el Voltorb se balancea: a partir de aquí está por explotar. */
private const val VOLTORB_WOBBLE_FROM_FRAME = 4

/**
 * Cuadrícula de Voltorb de Lt. Surge: separación entre celdas, tamaño mínimo de
 * celda, altura del sprite del líder en este gimnasio y los márgenes verticales
 * que reservan la barra de postura y la de vida del jugador.
 *
 * La celda se calcula por **ancho** para que el tablero llegue de extremo a
 * extremo; el alto disponible solo limita cuando la pantalla es demasiado baja.
 */
private val VOLTORB_GAP = 7.dp
private val VOLTORB_MIN_CELL = 26.dp
private val VOLTORB_BAR_RESERVE = 40.dp

/**
 * Tablero oscuro difuminado detrás de la cuadrícula: margen alrededor de las
 * celdas, grosor del borde difuminado, radio de las esquinas, opacidad total del
 * centro y pasos del degradado.
 *
 * El tablero da contraste contra el arte del fondo, sin el cual los frames de
 * reposo y de carga se confunden. Subir [VOLTORB_BOARD_ALPHA] oscurece más y
 * separa mejor; bajarlo devuelve protagonismo al escenario.
 */
private val VOLTORB_BOARD_PAD = 10.dp
private val VOLTORB_BOARD_FEATHER = 16.dp
private val VOLTORB_BOARD_CORNER = 18.dp
private const val VOLTORB_BOARD_ALPHA = 0.66f
private const val VOLTORB_BOARD_STEPS = 6

/** Separación entre la cuadrícula y el sprite del jugador. */
private val VOLTORB_PLAYER_GAP = 8.dp

/** Reserva inferior para la barra de vida que pinta `GymOverlay`. */
private val VOLTORB_HP_BAR_RESERVE = 50.dp

/** Altura mínima del hueco del tablero, para pantallas muy bajas. */
private val VOLTORB_MIN_AREA = 124.dp

/** Amplitud del vaivén como fracción del radio de la celda. Ajustable: más alto, balanceo más marcado. */
private const val VOLTORB_WOBBLE_FRACTION = 0.12f

/** Velocidad del vaivén: el seno divide el reloj por este valor. Más bajo = más nervioso. */
private const val VOLTORB_WOBBLE_PERIOD_MS = 90f

/** Duración del destello de apagado, explosión o descarga. Ajustable: cuánto dura el frame de resolución antes de volver a reposo. */
private const val VOLTORB_FEEDBACK_MS = 420

/**
 * Hoja de la aurora eléctrica (`lightning/voltorb-aura` de pixelfx): 30 frames de
 * 160x160 en 6 columnas a 30 fps, en bucle.
 */
private const val VOLTORB_AURA_FRAMES = 30
private const val VOLTORB_AURA_FPS = 30
private const val VOLTORB_AURA_COLUMNS = 6
private const val VOLTORB_AURA_FRAME_PX = 160

/**
 * Escala del frame respecto a la celda. El cuerpo de la aurora ocupa 0.42 del
 * frame; con 2,55 el anillo de arcos cae por fuera de la silueta del Voltorb en
 * vez de quedar tapado por el sprite.
 */
private const val VOLTORB_AURA_SCALE = 2.55f

/** Desfase entre celdas (ms) para que las auroras no vayan sincronizadas. */
private const val VOLTORB_AURA_PHASE_STEP_MS = 67L

/**
 * Explosión de un Voltorb (`explosions/voltorb-blast` de pixelfx), recortada a 32
 * frames de 192x192 en 7 columnas. Se reproduce una vez en ~1,07 s.
 */
private const val VOLTORB_BLAST_FRAMES = 32
private const val VOLTORB_BLAST_COLUMNS = 7
private const val VOLTORB_BLAST_FRAME_W = 192
private const val VOLTORB_BLAST_FRAME_H = 192
private const val VOLTORB_BLAST_MS = 1_070L

/**
 * Duración de la misma animación cuando el jugador toca una celda apagada. Corre
 * más rápido: es un castigo por el error, no el estallido real del Voltorb.
 */
private const val VOLTORB_SHOCK_BLAST_MS = 700L

/**
 * Escala del estallido respecto a la celda. La bola de humo ocupa ~0.36 del frame,
 * así que 2,75 hace que cubra la celda entera; los arcos y ascuas salen más allá.
 */
private const val VOLTORB_BLAST_SCALE = 2.75f

/** Del amarillo al rojo según el calor: es la cuenta atrás visible. */
private val VOLTORB_CHARGE_COLD = Color(0xFFFFB300)
private val VOLTORB_CHARGE_HOT = Color(0xFFFF3D00)

/**
 * Radio de acierto como fracción del ancho de columna (tolerante al toque), con
 * un mínimo absoluto para dedos: el radio efectivo es el mayor de los dos.
 */
private const val PROJECTILE_HIT_FRACTION = 0.6f

/** Radio de acierto mínimo, por debajo del cual la roca cuesta clicarla. */
private val PROJECTILE_MIN_HIT_RADIUS = 28.dp

/**
 * Tamaño del proyectil como fracción del ancho de columna: la roca ocupa la
 * mayor parte de su carril sin tocar la vecina. Los topes evitan que se achique
 * de más en pantallas angostas o se desborde en las anchas.
 */
private const val PROJECTILE_SIZE_FRACTION = 0.85f
private val PROJECTILE_MIN_SIZE = 36.dp
private val PROJECTILE_MAX_SIZE = 64.dp

/**
 * Tamaño del proyectil para un carril de [cellWidth]. El estallido se dibuja
 * proporcional a este valor ([RockShatterFx.SCALE]), así que agrandar la roca
 * agranda también su rotura.
 */
internal fun projectileSizeFor(cellWidth: Dp): Dp =
    (cellWidth * PROJECTILE_SIZE_FRACTION).coerceIn(PROJECTILE_MIN_SIZE, PROJECTILE_MAX_SIZE)



/**
 * Sprites del combate de gimnasio y el placeholder para lo que aún no tiene arte.
 *
 * Es el único punto donde se mapea un id de dominio a un recurso, así que añadir
 * un gimnasio o el back de otra especie es añadir una línea aquí.
 */

/** Sprite del líder, o `null` si todavía no hay arte para ese gimnasio. */
@DrawableRes
fun enemyGifFor(gymId: String): Int? = when (gymId) {
    "brock" -> R.drawable.golem
    "misty" -> R.drawable.starmie
    "surge" -> R.drawable.raichu
    else -> null
}

/**
 * Nombre del Pokémon del líder, o `null` si ese gimnasio aún no lo declara.
 *
 * Vive junto a [enemyGifFor] porque es el mismo punto donde un id de dominio se
 * traduce a lo que se pinta: la placa de estado muestra el Pokémon que pelea, no
 * al entrenador. Tiene que coincidir con el sprite de arriba.
 */
fun enemyNameFor(gymId: String): String? = when (gymId) {
    "brock" -> "Golem"
    "misty" -> "Starmie"
    "surge" -> "Raichu"
    else -> null
}

/** Sprite trasero del principal, o `null` si esa especie aún no tiene back. */
@DrawableRes
fun backGifFor(speciesId: String?): Int? = when (speciesId) {
    "bulbasaur" -> R.drawable.bulbasaur_back
    "charmander" -> R.drawable.charmander_back
    "squirtle" -> R.drawable.squirtle_back
    else -> null
}

/** Color representativo del tipo, para el placeholder y el destello de ataque. */
fun typeColor(type: PokemonType?): Color = when (type) {
    PokemonType.NORMAL -> Color(0xFFA8A77A)
    PokemonType.FIRE -> Color(0xFFEE8130)
    PokemonType.WATER -> Color(0xFF6390F0)
    PokemonType.GRASS -> Color(0xFF7AC74C)
    PokemonType.ELECTRIC -> Color(0xFFF7D02C)
    PokemonType.ROCK -> Color(0xFFB6A136)
    PokemonType.FLYING -> Color(0xFFA98FF3)
    PokemonType.POISON -> Color(0xFFA33EA1)
    PokemonType.BUG -> Color(0xFFA8B820)
    PokemonType.GROUND -> Color(0xFFE0C068)
    PokemonType.FIGHTING -> Color(0xFFC03028)
    PokemonType.PSYCHIC -> Color(0xFFF85888)
    PokemonType.ICE -> Color(0xFF98D8D8)
    PokemonType.GHOST -> Color(0xFF705898)
    PokemonType.DRAGON -> Color(0xFF7038F8)
    null -> Color(0xFF9E9E9E)
}

/**
 * Sustituto del sprite cuando no hay arte: un disco del color del tipo con el
 * nombre encima. Permite que un principal sin back (cualquier especie no starter)
 * siga siendo jugable.
 */
@Composable
fun GymSpritePlaceholder(
    name: String?,
    type: PokemonType?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(typeColor(type).copy(alpha = 0.45f), CircleShape)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
