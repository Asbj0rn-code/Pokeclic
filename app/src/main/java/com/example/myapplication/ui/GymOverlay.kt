/**
 * Escena, HUD y sprites del combate de gimnasio.
 *
 * La escena anima las rocas con un reloj de fotogramas y reparte los toques entre
 * el líder expuesto y el proyectil más cercano; el HUD pinta las fracciones de
 * vida y stamina ya calculadas por el modelo. No hay lógica de combate aquí:
 * solo presentación y eventos hacia el ViewModel.
 */
package com.example.myapplication.ui

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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.model.GymOutcome
import com.example.myapplication.model.GymPhase
import com.example.myapplication.model.GymState
import com.example.myapplication.model.PokemonType
import kotlin.math.ceil
import androidx.annotation.DrawableRes
import androidx.compose.foundation.shape.CircleShape

/**
 * Escena del combate de gimnasio, a pantalla completa sobre el fondo que dibuja
 * [AppRoot].
 *
 * Combate por **fases excluyentes** (ver [GymPhase]):
 * - **Ataque**: cae una oleada de rocas y la stamina del líder baja **por
 *   tiempo**; hay que aguantarla rompiendo las rocas que impactarían.
 * - **Expuesta**: sin rocas, tocar al líder le resta vida (con ventaja de tipo).
 *
 * Por debajo del 25 % de vida el líder entra en **furia**: la oleada es más
 * densa y rápida (ver `Gym.rage*`).
 *
 * Todos los sprites son solo visuales y **una única capa de toque** reparte los
 * gestos: primero al enemigo si está expuesto y el toque cae sobre él, y si no, a
 * la roca más cercana. Así la Pokéball del menú deja de ser el pulsador de ataque.
 */
@Composable
fun GymOverlay(
    state: GymState,
    onProjectileHit: (Long) -> Unit,
    onEnemyHit: () -> Unit,
    onRetry: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exposed = state.phase == GymPhase.EXPOSED

    // El combate oculta el bottom menu, así que el `Scaffold` ya no reserva el
    // inset inferior: se aplica aquí para que el sprite del jugador no quede
    // debajo de la barra de navegación.
    Box(modifier = modifier.fillMaxSize().navigationBarsPadding()) {

        GymScene(
            state = state,
            exposed = exposed,
            onProjectileHit = onProjectileHit,
            onEnemyHit = onEnemyHit,
            modifier = Modifier.fillMaxSize()
        )

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
        val staminaColor = when {
            exposed -> EXPOSED_COLOR
            state.rage -> RAGE_COLOR
            else -> STAMINA_COLOR
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = ENEMY_TOP + ENEMY_HEIGHT + 6.dp)
                .width(STAMINA_WIDTH),
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
                        if (exposed) R.string.gym_enemy_hint else R.string.gym_projectile_hint
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
private val ENEMY_WIDTH = 120.dp
private val ENEMY_HEIGHT = 90.dp
private val ENEMY_TOP = 178.dp

/** Tamaño y posición del jugador. */
private val PLAYER_WIDTH = 110.dp
private val PLAYER_HEIGHT = 122.dp
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
