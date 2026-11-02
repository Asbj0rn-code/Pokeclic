/**
 * Escena, HUD y sprites del combate de gimnasio.
 *
 * Cada mecánica tiene su escena: rocas por columnas (Brock) o anillos que se
 * cierran sobre el jugador (Misty). Ambas animan con un reloj de fotogramas y
 * reparten los toques entre el líder expuesto y el ataque en curso; el HUD pinta
 * las fracciones de vida y stamina ya calculadas por el modelo. No hay lógica de
 * combate aquí: solo presentación y eventos hacia el ViewModel.
 */
package com.dsrs.pokeclic.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.Stroke
import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.GymMechanic
import com.dsrs.pokeclic.model.GymOutcome
import com.dsrs.pokeclic.model.GymPhase
import com.dsrs.pokeclic.model.GymState
import com.dsrs.pokeclic.model.PokemonType
import com.dsrs.pokeclic.model.RingOutcome
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import androidx.annotation.DrawableRes
import androidx.compose.foundation.shape.CircleShape

/**
 * Escena del combate de gimnasio, a pantalla completa sobre el fondo que dibuja
 * [AppRoot].
 *
 * Combate por **fases excluyentes** (ver [GymPhase]):
 * - **Ataque**: corre la oleada y la stamina del líder baja **por tiempo**. La
 *   defensa depende de la mecánica: romper rocas (Brock) o acertar el círculo
 *   que converge (Misty).
 * - **Expuesta**: sin ataque enemigo, tocar al líder le resta vida (con ventaja
 *   de tipo).
 *
 * Por debajo del 25 % de vida el líder entra en **furia**: los ataques son más
 * densos y rápidos (ver `Gym.rage*`).
 *
 * Todos los sprites son solo visuales y **una única capa de toque** reparte los
 * gestos: primero al enemigo si está expuesto y el toque cae sobre él, y si no,
 * la mecánica del líder (la roca más cercana para Brock, el anillo más cercano
 * para Misty). Así la Pokéball del menú deja de ser el pulsador de ataque.
 */
@Composable
fun GymOverlay(
    state: GymState,
    onProjectileHit: (Long) -> Unit,
    onRingTap: () -> Unit,
    onEnemyHit: () -> Unit,
    onRetry: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exposed = state.phase == GymPhase.EXPOSED

    // El combate oculta el bottom menu, así que el `Scaffold` ya no reserva el
    // inset inferior: se aplica aquí para que el sprite del jugador no quede
    // debajo de la barra de navegación.
    //
    // El minijuego de anillos juzga los toques en la **raíz** del overlay, como
    // hace la pesca: así ningún sprite, barra o capa intermedia puede dejar el
    // gesto sin receptor.
    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .pointerInput(state.mechanic, exposed) {
                if (state.mechanic != GymMechanic.TIMING_RINGS) return@pointerInput

                val enemyLeftPx = (size.width - ENEMY_WIDTH.toPx()) / 2f
                val enemyTopPx = ENEMY_TOP.toPx()
                val enemyRightPx = enemyLeftPx + ENEMY_WIDTH.toPx()
                val enemyBottomPx = enemyTopPx + ENEMY_HEIGHT.toPx()

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
                onProjectileHit = onProjectileHit,
                onEnemyHit = onEnemyHit,
                modifier = Modifier.fillMaxSize()
            )
            GymMechanic.TIMING_RINGS -> RingGymScene(
                state = state,
                exposed = exposed,
                modifier = Modifier.fillMaxSize()
            )
        }

        GymHud(
            state = state,
            exposed = exposed,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp)
        )

        PlayerHpBar(
            state = state,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = PLAYER_HP_BOTTOM_PADDING)
        )

        if (state.outcome == GymOutcome.LOST) {
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
    onProjectileHit: (Long) -> Unit,
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
    // destello en el punto tocado. Son el sustituto del sprite de ataque real.
    var attackNonce by remember { mutableIntStateOf(0) }
    var hitPoint by remember { mutableStateOf(Offset.Zero) }

    val lunge = remember { Animatable(0f) }
    LaunchedEffect(attackNonce) {
        if (attackNonce == 0) return@LaunchedEffect
        lunge.snapTo(0f)
        lunge.animateTo(1f, tween(durationMillis = 90))
        lunge.animateTo(0f, tween(durationMillis = 110))
    }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(attackNonce) {
        if (attackNonce == 0) return@LaunchedEffect
        flash.snapTo(0.85f)
        flash.animateTo(0f, tween(durationMillis = 220))
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
        val spawnY = ENEMY_TOP + ENEMY_HEIGHT
        val impactY = (maxHeight - PLAYER_BOTTOM - PLAYER_HEIGHT + PLAYER_HIT_INSET)
            .coerceAtLeast(spawnY + 24.dp)

        val rockSize = (cellWidth * 0.72f).coerceIn(36.dp, 56.dp)

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

        Canvas(modifier = Modifier.fillMaxSize()) {
            val alpha = flash.value
            if (alpha > 0f) {
                drawCircle(
                    color = typeColor(state.leadType).copy(alpha = alpha),
                    radius = 26.dp.toPx() * (1.4f - alpha * 0.4f),
                    center = hitPoint
                )
            }
        }

        // Enemigo, con pulso mientras está expuesto.
        GymSpriteBox(
            gifRes = enemyGifFor(state.gymId),
            name = state.leaderName,
            type = state.gymType,
            contentDescription = stringResource(R.string.gym_title, state.leaderName),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = ENEMY_TOP)
                .size(ENEMY_WIDTH, ENEMY_HEIGHT)
                .graphicsLayer {
                    val scale = if (exposed) pulseScale else 1f
                    scaleX = scale
                    scaleY = scale
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
                .offset(y = -PLAYER_BOTTOM)
                .size(PLAYER_WIDTH, PLAYER_HEIGHT)
                .graphicsLayer {
                    translationY = -26.dp.toPx() * lunge.value
                    val scale = 1f + 0.05f * lunge.value
                    scaleX = scale
                    scaleY = scale
                }
        )

        // Barra de stamina justo debajo del enemigo: es el reloj de la oleada
        // (baja por tiempo) y, en furia, se tiñe de rojo.
        GymStaminaBar(
            state = state,
            exposed = exposed,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = ENEMY_TOP + ENEMY_HEIGHT + 6.dp)
        )

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

                    val enemyLeftPx = (size.width - ENEMY_WIDTH.toPx()) / 2f
                    val enemyTopPx = ENEMY_TOP.toPx()
                    val enemyRightPx = enemyLeftPx + ENEMY_WIDTH.toPx()
                    val enemyBottomPx = enemyTopPx + ENEMY_HEIGHT.toPx()

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
                                hit?.let {
                                    hitPoint = it.third
                                    attackNonce++
                                    onProjectileHit(it.first.id)
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
 * El gesto se captura en la raíz de [GymOverlay], igual que en la pesca: esta
 * escena solo pinta. No hay acierto por posición: el minijuego es de **momento**,
 * no de puntería.
 */
@Composable
private fun RingGymScene(
    state: GymState,
    exposed: Boolean,
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

    // Acierto/fallo del último anillo: el ViewModel publica el resultado y aquí
    // se convierte en un estallido de agua o uno rojo, más la etiqueta flotante.
    val feedback = state.ringFeedback
    val feedbackProgress = remember { Animatable(1f) }
    LaunchedEffect(feedback?.ringId) {
        if (feedback == null) return@LaunchedEffect
        feedbackProgress.snapTo(0f)
        feedbackProgress.animateTo(1f, tween(durationMillis = RING_FEEDBACK_MS))
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
                y = size.height - PLAYER_BOTTOM.toPx() - PLAYER_HEIGHT.toPx() / 2f
            )
            val targetRadius = RING_TARGET_RADIUS.toPx()
            val startRadius = RING_START_RADIUS.toPx()
            val mainStroke = Stroke(width = RING_STROKE.toPx())
            val outlineStroke = Stroke(width = (RING_STROKE + 3.dp).toPx())
            val foamStroke = Stroke(width = 1.dp.toPx())

            // Charco de agua bajo el Pokémon: da profundidad al círculo fijo.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(WATER_POOL.copy(alpha = 0.34f), Color.Transparent),
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
                color = WATER_LIGHT.copy(alpha = 0.95f),
                radius = targetRadius,
                center = center,
                style = mainStroke
            )
            drawCircle(
                color = WATER_FOAM.copy(alpha = 0.95f),
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
                    color = WATER_FOAM.copy(alpha = 0.42f * (1f - phase)),
                    radius = if (index % 2 == 0) 3.dp.toPx() else 2.dp.toPx(),
                    center = Offset(bubbleX, bubbleY)
                )
            }

            // Anillos que se cierran: agua profunda, espuma, ondulación y gotas.
            currentState.rings.forEach { ring ->
                val radius = startRadius + (targetRadius - startRadius) * ring.progressAt(now)
                val onBeat = ring.isInWindow(now, currentState.ringWindowMs)

                drawCircle(
                    color = (if (onBeat) RING_ON_BEAT_COLOR else WATER_LIGHT).copy(alpha = 0.30f),
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

                if (onBeat) {
                    // En su ventana el anillo queda liso: es la señal exacta.
                    drawCircle(
                        color = RING_OUTLINE.copy(alpha = 0.65f),
                        radius = radius,
                        center = center,
                        style = outlineStroke
                    )
                    drawCircle(
                        brush = ringBrush,
                        radius = radius,
                        center = center,
                        style = mainStroke
                    )
                } else {
                    // Fuera de la ventana, el borde ondula como una ola.
                    val wave = Path()
                    val segments = 72
                    for (step in 0..segments) {
                        val angle = step * (2.0 * PI / segments).toFloat()
                        val ripple = sin(angle * RING_WAVE_CYCLES + now / 220f) *
                            RING_WAVE_AMPLITUDE.toPx()
                        val x = center.x + cos(angle) * (radius + ripple)
                        val y = center.y + sin(angle) * (radius + ripple)
                        if (step == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
                    }
                    wave.close()
                    drawPath(
                        path = wave,
                        color = RING_OUTLINE.copy(alpha = 0.65f),
                        style = outlineStroke
                    )
                    drawPath(path = wave, brush = ringBrush, style = mainStroke)
                }

                drawCircle(
                    color = WATER_FOAM.copy(alpha = if (onBeat) 0.95f else 0.65f),
                    radius = radius - 3.dp.toPx(),
                    center = center,
                    style = foamStroke
                )

                // Gotas de espuma que orbitan el anillo y respiran con el tiempo.
                val spin = (now % 7_000L) / 7_000f * 360f
                repeat(4) { dropIndex ->
                    val direction = if (dropIndex % 2 == 0) 1f else -1f
                    val angle = (dropIndex * 90f + spin * direction) * PI.toFloat() / 180f
                    val dropX = center.x + cos(angle) * radius
                    val dropY = center.y + sin(angle) * radius
                    val dropRadius = (dropIndex + 2).dp.toPx() *
                        (0.75f + 0.25f * sin(now / 300f + dropIndex))
                    drawCircle(
                        color = if (onBeat) Color.White.copy(alpha = 0.95f)
                        else WATER_FOAM.copy(alpha = 0.9f),
                        radius = dropRadius,
                        center = Offset(dropX, dropY)
                    )
                }
            }

            // Estallido del resultado: salpicadura de espuma o onda roja.
            val progress = feedbackProgress.value
            if (feedback != null && progress < 1f) {
                val fade = 1f - progress
                when (feedback.outcome) {
                    RingOutcome.HIT -> {
                        drawCircle(
                            color = WATER_FOAM.copy(alpha = 0.9f * fade),
                            radius = targetRadius + 42.dp.toPx() * progress,
                            center = center,
                            style = Stroke(width = (4f * fade + 1f).dp.toPx())
                        )
                        repeat(8) { dropIndex ->
                            val angle = dropIndex * 45f * PI.toFloat() / 180f
                            val distance = targetRadius + 60.dp.toPx() * progress
                            drawCircle(
                                color = WATER_LIGHT.copy(alpha = 0.9f * fade),
                                radius = (3.5f * fade + 1f).dp.toPx(),
                                center = Offset(
                                    center.x + cos(angle) * distance,
                                    center.y + sin(angle) * distance
                                )
                            )
                        }
                    }
                    RingOutcome.MISS -> {
                        drawCircle(
                            color = MISS_RED.copy(alpha = 0.9f * fade),
                            radius = targetRadius + 36.dp.toPx() * progress,
                            center = center,
                            style = Stroke(width = (5f * fade + 1f).dp.toPx())
                        )
                    }
                }
            }
        }

        // Enemigo, con pulso mientras está expuesto.
        GymSpriteBox(
            gifRes = enemyGifFor(state.gymId),
            name = state.leaderName,
            type = state.gymType,
            contentDescription = stringResource(R.string.gym_title, state.leaderName),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = ENEMY_TOP)
                .size(ENEMY_WIDTH, ENEMY_HEIGHT)
                .graphicsLayer {
                    val scale = if (exposed) pulseScale else 1f
                    scaleX = scale
                    scaleY = scale
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
                .offset(y = -PLAYER_BOTTOM)
                .size(PLAYER_WIDTH, PLAYER_HEIGHT)
        )

        GymStaminaBar(
            state = state,
            exposed = exposed,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = ENEMY_TOP + ENEMY_HEIGHT + 6.dp)
        )

        // Etiqueta del resultado, grande y con sombra dura para leerse sobre la
        // cueva. Sube y se desvanece con el mismo reloj que el estallido.
        feedback?.let { result ->
            val progress = feedbackProgress.value
            // Se queda legible un instante y después se desvanece.
            val fade = if (progress < 0.45f) {
                1f
            } else {
                ((1f - progress) / 0.55f).coerceIn(0f, 1f)
            }
            val hit = result.outcome == RingOutcome.HIT
            val label = when {
                hit -> stringResource(R.string.gym_ring_hit)
                result.count > 1 -> stringResource(R.string.gym_ring_miss_multi, result.count)
                else -> stringResource(R.string.gym_ring_miss)
            }
            Text(
                text = label,
                color = if (hit) WATER_FOAM else MISS_RED,
                style = MaterialTheme.typography.headlineSmall.copy(
                    shadow = Shadow(color = Color.Black, offset = Offset(3f, 3f), blurRadius = 0f)
                ),
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-36).dp)
                    .graphicsLayer {
                        alpha = fade
                        val scale = 1f + 0.18f * progress
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }

    }
}

/**
 * Barra de stamina del líder bajo su sprite: es el reloj de la oleada (baja por
 * tiempo) y, en furia, se tiñe de rojo. La comparten las dos mecánicas de
 * gimnasio para que la lectura del combate no cambie entre líderes.
 */
@Composable
private fun GymStaminaBar(
    state: GymState,
    exposed: Boolean,
    modifier: Modifier = Modifier
) {
    val staminaColor = when {
        exposed -> EXPOSED_COLOR
        state.rage -> RAGE_COLOR
        else -> STAMINA_COLOR
    }
    Column(
        modifier = modifier.width(STAMINA_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(
            progress = { state.staminaFraction },
            modifier = Modifier.fillMaxWidth(),
            color = staminaColor
        )
        Spacer(modifier = Modifier.size(2.dp))
        Text(
            text = if (exposed) {
                stringResource(R.string.gym_exposed)
            } else {
                stringResource(
                    R.string.gym_stamina_time,
                    ceil(state.waveRemainingMs / 1000f).toInt()
                )
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (exposed) FontWeight.Bold else FontWeight.Normal,
            color = if (exposed) EXPOSED_COLOR else Color.White
        )
    }
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
 * HUD superior: vida del líder y una pista que cambia con la fase. Usa `Box` en
 * vez de `Surface` para **no consumir** los toques que pasan por detrás.
 */
@Composable
private fun GymHud(
    state: GymState,
    exposed: Boolean,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalContentColor provides Color.White) {
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
                    text = stringResource(R.string.gym_title, state.leaderName),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(text = stringResource(R.string.gym_type, state.gymType.name))

                LinearProgressIndicator(
                    progress = { state.leaderHpFraction },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(text = stringResource(R.string.gym_leader_hp, state.leaderHp, state.leaderMaxHp))

                if (state.rage) {
                    Text(
                        text = stringResource(R.string.gym_rage),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = RAGE_COLOR
                    )
                }

                Text(
                    text = stringResource(
                        when {
                            exposed -> R.string.gym_enemy_hint
                            state.mechanic == GymMechanic.TIMING_RINGS -> R.string.gym_ring_hint
                            else -> R.string.gym_projectile_hint
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Vida del jugador anclada al borde inferior, lejos del HUD del líder. Se dibuja
 * como la barra de stamina (barra + etiqueta) para mantener el mismo lenguaje.
 */
@Composable
private fun PlayerHpBar(
    state: GymState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(STAMINA_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(
            progress = { state.playerHpFraction },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.size(2.dp))
        Text(
            text = stringResource(R.string.gym_hp, state.playerHp, state.maxHp),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}

/** Panel de derrota centrado, con reintentar y salir. */
@Composable
private fun DefeatPanel(
    onRetry: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 8.dp,
        modifier = modifier.padding(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.gym_lost),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) {
                    Text(text = stringResource(R.string.gym_retry))
                }
                OutlinedButton(onClick = onLeave) {
                    Text(text = stringResource(R.string.gym_leave))
                }
            }
        }
    }
}

private fun nowMs(): Long = System.nanoTime() / 1_000_000L

/** Ancho de la franja de columnas: una fracción del ancho, con tope. */
private const val LANE_WIDTH_FRACTION = 0.72f

/** Tope de la franja para que en pantallas grandes no se abran demasiado. */
private val LANE_MAX_WIDTH = 340.dp

/** Tamaño y posición del enemigo (el GIF de Golem es 72×57, ratio ~1.26). */
private val ENEMY_WIDTH = 180.dp
private val ENEMY_HEIGHT = 150.dp
private val ENEMY_TOP = 178.dp

/** Tamaño y posición del jugador. */
private val PLAYER_WIDTH = 90.dp
private val PLAYER_HEIGHT = 102.dp
private val PLAYER_BOTTOM = 160.dp

/** Separación de la barra de vida del jugador respecto al borde inferior. */
private val PLAYER_HP_BOTTOM_PADDING = 10.dp

/** Cuánto por debajo de la cabeza del jugador se considera impacto. */
private val PLAYER_HIT_INSET = 16.dp

/** Ancho de la barra de stamina. */
private val STAMINA_WIDTH = 200.dp

/** Colores de la barra de stamina, del estado expuesto y de la furia. */
private val STAMINA_COLOR = Color(0xFFFFC107)
private val EXPOSED_COLOR = Color(0xFFFFEB3B)
private val RAGE_COLOR = Color(0xFFFF5252)

/**
 * Anillos del minijuego de Misty: radio fijo alrededor del jugador, radio de
 * entrada y grosor del trazo. El anillo que entra se cierra desde
 * [RING_START_RADIUS] hasta [RING_TARGET_RADIUS].
 *
 * El radio fijo se calcula para el cuadro del jugador ([PLAYER_WIDTH] ×
 * [PLAYER_HEIGHT]): con 90 × 102 dp la semidiagonal mide ≈ 68 dp y 78 dp deja
 * unos 10 dp de margen, la misma holgura que había con 110 × 122 dp y 92 dp.
 * Si vuelven a cambiar esos tamaños, ajustar ambos radios en la misma
 * proporción.
 */
private val RING_TARGET_RADIUS = 78.dp
private val RING_START_RADIUS = 175.dp
private val RING_STROKE = 4.dp
private val RING_GLOW_STROKE = 11.dp

/** Ondulación del anillo de agua fuera de la ventana de acierto. */
private val RING_WAVE_AMPLITUDE = 2.5.dp
private const val RING_WAVE_CYCLES = 6f

/** Duración del estallido y de la etiqueta de acierto/fallo. */
private const val RING_FEEDBACK_MS = 900

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
 * Radio de acierto como fracción del ancho de columna (tolerante al toque), con
 * un mínimo absoluto para dedos: el radio efectivo es el mayor de los dos.
 */
private const val PROJECTILE_HIT_FRACTION = 0.6f

/** Radio de acierto mínimo, por debajo del cual la roca cuesta clicarla. */
private val PROJECTILE_MIN_HIT_RADIUS = 28.dp



/**
 * Sprites del combate de gimnasio y el placeholder para lo que aún no tiene arte.
 *
 * Es el único punto donde se mapea un id de dominio a un recurso, así que añadir
 * un gimnasio o el back de otra especie es añadir una línea aquí.
 */

/** Sprite del líder, o `null` si todavía no hay GIF para ese gimnasio. */
@DrawableRes
fun enemyGifFor(gymId: String): Int? = when (gymId) {
    "brock" -> R.drawable.golem
    "misty" -> R.drawable.starmie
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
