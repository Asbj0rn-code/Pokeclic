/**
 * Bottom menu (barra, botones y Pokéball interactiva) y la silueta con hendidura
 * de la barra.
 *
 * Aquí vive todo lo visual y gestual del menú: arrastre/toque de la Pokéball,
 * animaciones, haptics y medidas en `BottomMenuDefaults`. No contiene lógica de
 * juego; los clics se reenvían al llamante como lambdas.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.dsrs.pokeclic.R
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Shape



/**
 * Ajustes del bottom menu. Están aquí y no dentro del composable para poder
 * iterar sobre el aspecto sin tocar la lógica del gesto.
 */
object BottomMenuDefaults {

    /** Altura de la barra blanca. */
    val BAR_HEIGHT: Dp = 96.dp

    // ───────────────────────── Pokéball en reposo ─────────────────────────

    /** Diámetro de la Pokéball en reposo. */
    val POKEBALL_SIZE: Dp = 100.dp

    /**
     * Distancia del borde inferior de la Pokéball al borde inferior de la barra.
     * Es su posición vertical en reposo.
     */
    val POKEBALL_BOTTOM_PADDING: Dp = 45.dp

    /** Desplazamiento horizontal en reposo. Positivo = derecha, negativo = izquierda. */
    val POKEBALL_OFFSET_X: Dp = 0.dp

    // ──────────────────────── Pokéball expandida ─────────────────────────
    //
    // Tamaño y posición **absolutos**, no relativos al reposo. Se editan sin
    // tener que pensar en escalas ni en cuánto sube: pones directamente el
    // diámetro que quieres y dónde quieres su borde inferior.

    /** Diámetro de la Pokéball cuando está expandida. */
    val POKEBALL_EXPANDED_SIZE: Dp = 280.dp

    /**
     * Posición vertical expandida: distancia del borde **inferior** de la
     * Pokéball al borde inferior de la barra.
     *
     * Se mide desde el borde inferior y no desde el centro porque al cambiar
     * [POKEBALL_EXPANDED_SIZE] el centro se mueve solo, y obliga a rehacer la
     * cuenta. Para tenerla flotando sobre el contenido, sube este valor: cuanto
     * mayor, más alto queda.
     */
    val POKEBALL_EXPANDED_BOTTOM: Dp = 55.dp

    /** Desplazamiento horizontal expandido. Positivo = derecha. */
    val POKEBALL_EXPANDED_OFFSET_X: Dp = 0.dp

    /**
     * Sombra que proyecta la Pokéball al expandirse. En reposo es `0f` por el
     * `progress`, así que **no aparece nunca en la hendidura**. Ponlo a `0.dp`
     * si tampoco quieres sombra al expandirla.
     */
    val EXPANDED_SHADOW: Dp = 0.dp

    // ──────────────────────────── Hendidura ──────────────────────────────

    /**
     * Semiancho de la hendidura, en dp, con la Pokéball **en reposo**.
     * **No** depende de la altura de la barra: se puede ensanchar o estrechar sin
     * tocar [NOTCH_DEPTH].
     *
     * La Pokéball tiene [POKEBALL_SIZE] de diámetro, así que por debajo de su
     * radio no cabe suelta: el valor grande deja ver fondo a los lados, el
     * pequeño la abraza.
     */
    val NOTCH_WIDTH: Dp = 75.dp

    /**
     * Semiancho de la hendidura, en dp, con la Pokéball **expandida**. El hueco
     * se abre a la vez que ella, interpolando entre los dos con el progreso del
     * gesto, así que la Pokéball no llega a invadir la barra.
     *
     * Igual de libre que [NOTCH_WIDTH]: ponle `POKEBALL_EXPANDED_SIZE` para que
     * el hueco mida lo mismo que la bola, o menos si quieres que quede barra
     * blanca a los lados. Ponlo igual que [NOTCH_WIDTH] si prefieres que el hueco
     * no cambie de tamaño durante el gesto.
     *
     * Con un valor muy grande la barra se queda partida en dos tramos, porque el
     * semiancho no puede pasar de la mitad del ancho (ver [BottomMenuNotchShape]).
     */
    val NOTCH_EXPANDED_WIDTH: Dp = 140.dp

    /**
     * Fondo de la hendidura, en dp, medido desde el borde superior de la barra.
     * No cambia durante el gesto: solo se abre el ancho.
     *
     * La Pokéball **flota** dentro del hueco, así que el fondo tiene que quedar
     * por debajo de su parte baja:
     *
     * ```
     * fondo de la Pokéball = BAR_HEIGHT - POKEBALL_BOTTOM_PADDING   → 96 - 50 = 46 dp
     * NOTCH_DEPTH          = ese valor + el hueco que dejes debajo   → 46 + 14 = 60 dp
     * ```
     *
     * Con un valor **menor** que esa cuenta la bola queda medio enterrada en la
     * barra blanca: se ve maciza, no flotando. Con uno bastante mayor, la barra
     * se queda sin apenas franja blanca en el centro.
     */
    val NOTCH_DEPTH: Dp = 60.dp

    // ───────────────────────────── Gesto ─────────────────────────────────

    /** Recorrido de arrastre necesario para recorrer todo el progreso. */
    val DRAG_SENSITIVITY: Dp = 110.dp

    /** Umbral para quedar arriba al soltar, y para volver abajo estando arriba. */
    const val SNAP_THRESHOLD = 0.20f

    /**
     * Factor de escala del "golpe" que da la Pokéball al recibir un toque, antes
     * de rebotar de vuelta a `1f`. Es solo respuesta visual: `1f` lo desactiva.
     */
    const val TAP_PUNCH_SCALE = 0.95f

    /**
     * Alto que reserva el `Scaffold` para el menú. **Derivado**: no lo edites.
     *
     * Es la altura de la barra más la parte de la Pokéball que sobresale por
     * encima. Calcularlo aquí evita que, al cambiar el tamaño o la posición de
     * la Pokéball, el hueco reservado se quede corto o sobre.
     */
    val CONTAINER_HEIGHT: Dp = BAR_HEIGHT +
        (POKEBALL_BOTTOM_PADDING + POKEBALL_SIZE - BAR_HEIGHT).coerceAtLeast(0.dp)

}

/**
 * Iconos provisionales del menú, mientras no haya arte definitivo.
 */
private object MenuIcons {
    val Map: ImageVector = Icons.Filled.Map
    val Pokedex: ImageVector = Icons.Filled.Book
}

/**
 * Datos de la transformación de la Pokéball, ya en píxeles y listos para el
 * `graphicsLayer`. Es lo único que se interpola con el `progress` del gesto.
 */
private data class PokeBallTransform(
    /** Desplazamiento horizontal en reposo, en px. */
    val restOffsetXPx: Float,
    /** Desplazamiento horizontal expandido, en px. */
    val expandedOffsetXPx: Float,
    /** Cuánto hay que subir la capa para llegar al estado expandido, en px. */
    val expandedOffsetYPx: Float,
    /** Factor de escala al llegar al estado expandido (1f = sin cambio). */
    val expandedScale: Float
)

/**
 * Traduce los ajustes en dp de [BottomMenuDefaults] a los números que necesita
 * `graphicsLayer`.
 *
 * La vertical se calcula a partir de los **centros**: como la escala se aplica
 * alrededor del centro del layout, para llevar la Pokéball de su posición de
 * reposo a la expandida hay que mover ese centro. De ahí la cuenta de las dos
 * líneas centrales: centro de reposo = `POKEBALL_BOTTOM_PADDING + radio`, centro
 * expandido = `POKEBALL_EXPANDED_BOTTOM + radio expandido`.
 *
 * Gracias a esto, [BottomMenuDefaults.POKEBALL_EXPANDED_SIZE] y
 * [BottomMenuDefaults.POKEBALL_EXPANDED_BOTTOM] se pueden cambiar por separado
 * sin que uno rompa el otro.
 */
private fun pokeBallTransform(density: Density): PokeBallTransform = with(density) {
    val restSizePx = BottomMenuDefaults.POKEBALL_SIZE.toPx()
    val expandedSizePx = BottomMenuDefaults.POKEBALL_EXPANDED_SIZE.toPx()

    val restCenterPx = BottomMenuDefaults.POKEBALL_BOTTOM_PADDING.toPx() + restSizePx / 2f
    val expandedCenterPx = BottomMenuDefaults.POKEBALL_EXPANDED_BOTTOM.toPx() + expandedSizePx / 2f

    PokeBallTransform(
        restOffsetXPx = BottomMenuDefaults.POKEBALL_OFFSET_X.toPx(),
        expandedOffsetXPx = BottomMenuDefaults.POKEBALL_EXPANDED_OFFSET_X.toPx(),
        // Positivo = hacia arriba, porque los dos centros se miden hacia arriba
        // desde el borde inferior de la barra.
        expandedOffsetYPx = restCenterPx - expandedCenterPx,
        expandedScale = if (restSizePx > 0f) expandedSizePx / restSizePx else 1f
    )
}

/**
 * Bottom menu de la aplicación: barra blanca con hendidura central y Pokéball
 * interactiva.
 *
 * La Pokéball es el control principal y tiene tres comportamientos:
 *
 * - **Tocada en reposo** → `onHomeClick`: navega a la pantalla principal.
 * - **Tocada ya expandida** → `onPokeBallGameClick` con las coordenadas locales
 *   del toque, que es lo que necesita la pokéball de juego (mover al trainer
 *   hacia el lado tocado).
 * - **Arrastrada hacia arriba y soltada** → se expande, crece y avisa con
 *   `onExpandedChange(true)`. Arrastrándola hacia abajo vuelve a su sitio.
 *
 * Toque y arrastre se resuelven en un único `pointerInput` en lugar de con
 * `detectVerticalDragGestures`: aquel no distingue el toque final, y combinarlo
 * con un `clickable` aparte depende del orden de los modificadores. Aquí solo se
 * consume el evento cuando el dedo supera el *slop*, así que un toque limpio
 * nunca se pierde.
 *
 * @param isMapSelected marca el botón izquierdo como la pantalla visible.
 * @param isPokedexSelected marca el botón derecho, es decir, con la hoja abierta.
 * @param isHomeSelected marca la Pokéball como la pantalla visible.
 * @param initialProgress progreso inicial del gesto, de `0f` (reposo) a `1f`
 *   (completamente expandida). La app siempre usa `0f`; los `@Preview` lo
 *   cambió para poder ver el menú a medio caminho o expandido sin arrastrar.
 */
@Composable
fun PokemonBottomMenu(
    modifier: Modifier = Modifier,
    isMapSelected: Boolean = false,
    isPokedexSelected: Boolean = false,
    isHomeSelected: Boolean = false,
    onMapClick: () -> Unit = {},
    onPokedexClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onPokeBallGameClick: (Offset) -> Unit = {},
    onExpandedChange: (Boolean) -> Unit = {},
    initialProgress: Float = 0f
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Estado lógico: false = en reposo, true = elevada y grande.
    val isExpanded = remember {
        mutableStateOf(initialProgress > BottomMenuDefaults.SNAP_THRESHOLD)
    }

    // Progreso continuo de la animación: 0f (abajo) a 1f (arriba).
    // Es un estado simple y no un `Animatable` porque durante el arrastre se
    // escribe en cada evento: con `Animatable.snapTo` habría que lanzar una
    // corrutina por cada píxel movido. El muelle solo se usa al soltar.
    val progress = remember { mutableFloatStateOf(initialProgress.coerceIn(0f, 1f)) }

    val sensitivityPx = with(density) { BottomMenuDefaults.DRAG_SENSITIVITY.toPx() }
    val transform = remember(density) { pokeBallTransform(density) }

    // El `pointerInput(Unit)` no se reinicia al recomponer, así que los callbacks
    // se leen siempre a través de estos estados para no invocar los antiguos.
    val currentHomeClick by rememberUpdatedState(onHomeClick)
    val currentGameClick by rememberUpdatedState(onPokeBallGameClick)
    val currentExpandedChange by rememberUpdatedState(onExpandedChange)

    /**
     * Asienta la Pokéball en [target] (true = expandida) animando el progreso con
     * un muelle. El aviso y el *haptic* solo se emiten cuando el estado cambia.
     * Es la única vía para plegar/desplegar por código, así que el gesto y los
     * botones del menú comparten el mismo comportamiento.
     */
    fun settleTo(target: Boolean) {
        if (target != isExpanded.value) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            isExpanded.value = target
            currentExpandedChange(target)
        }
        scope.launch {
            Animatable(progress.floatValue)
                .animateTo(
                    targetValue = if (target) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) {
                    progress.floatValue = value
                }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(BottomMenuDefaults.CONTAINER_HEIGHT),
        contentAlignment = Alignment.BottomCenter
    ) {
        MenuBar(
            progress = progress,
            isMapSelected = isMapSelected,
            isPokedexSelected = isPokedexSelected,
            // Pulsar un destino del menú pliega la Pokéball si estaba expandida.
            onMapClick = {
                if (isExpanded.value) settleTo(false)
                onMapClick()
            },
            onPokedexClick = {
                if (isExpanded.value) settleTo(false)
                onPokedexClick()
            }
        )

        MenuPokeBall(
            progress = progress,
            transform = transform,
            sensitivityPx = sensitivityPx,
            isExpanded = isExpanded,
            selected = isHomeSelected,
            onTap = { position ->
                if (isExpanded.value) currentGameClick(position) else currentHomeClick()
            },
            onDragFinished = { target -> settleTo(target) }
        )
    }
}

/**
 * Barra blanca con la hendidura central y los dos botones laterales.
 *
 * La hendidura se abre a la vez que la Pokéball: ambos leen el mismo `progress`
 * del gesto, así que crecen juntos y la bola no llega a invadir la barra.
 *
 * @param progress se pasa como estado y no como `Float` a propósito. Si se leyera
 *   un `Float`, la suscripción quedaría en [PokemonBottomMenu] y el menú entero se
 *   recompone en cada frame del arrastre; con el estado, solo se recompone la
 *   barra. El resto del menú sigue leyendo el progreso dentro del
 *   `graphicsLayer`, que difiere la lectura.
 */
@Composable
private fun MenuBar(
    progress: MutableFloatState,
    isMapSelected: Boolean,
    isPokedexSelected: Boolean,
    onMapClick: () -> Unit,
    onPokedexClick: () -> Unit
) {
    // Se recuerda: `BottomMenuNotchShape` crea un objeto nuevo en cada llamada y
    // una instancia distinta en cada recomposición hace redibujar la Surface.
    val shape = rememberBottomMenuNotchShape(
        width = lerp(
            BottomMenuDefaults.NOTCH_WIDTH,
            BottomMenuDefaults.NOTCH_EXPANDED_WIDTH,
            progress.floatValue
        ),
        depth = BottomMenuDefaults.NOTCH_DEPTH
    )

    // **Sin `Modifier.shadow`.** La sombra de una forma cóncava no se queda
    // dentro de la silueta: al desenfocarse sangra hacia el interior del hueco,
    // y como la hendidura es justo donde vive la Pokéball, aparecía un halo
    // oscuro alrededor y debajo de ella. La separación con el contenido la hace
    // el `border` de 1 dp, que sí sigue el contorno de la hendidura.
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(BottomMenuDefaults.BAR_HEIGHT),
        shape = shape,
        color = Color.White,
        border = BorderStroke(1.dp, Color(0x14000000))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MenuIconItem(
                label = stringResource(R.string.nav_map),
                contentDescription = stringResource(R.string.nav_map),
                icon = MenuIcons.Map,
                selected = isMapSelected,
                onClick = onMapClick
            )

            // Hueco central: debe superar el ancho de la hendidura para que los
            // botones laterales no se metan debajo de la Pokéball.
            Spacer(modifier = Modifier.width(BottomMenuDefaults.BAR_HEIGHT))

            MenuIconItem(
                label = stringResource(R.string.nav_pokedex),
                contentDescription = stringResource(R.string.pokedex_desc),
                icon = MenuIcons.Pokedex,
                selected = isPokedexSelected,
                onClick = onPokedexClick
            )
        }
    }
}

/**
 * Botón del menú: icono dentro de un círculo y etiqueta debajo. Al seleccionarse
 * se tiñe con el color de acento del tema.
 */
@Composable
private fun MenuIconItem(
    label: String,
    contentDescription: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = if (selected) accent.copy(alpha = 0.16f) else Color(0xFFEEEEEE),
                    shape = CircleShape
                )
                .clickable(onClick = onClick)
                .semantics {
                    this.contentDescription = contentDescription
                    role = Role.Button
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accent else Color(0xFF4A4A4A),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) accent else Color(0xFF4A4A4A)
        )
    }
}

/**
 * Pokéball central: se coloca en la hendidura, sube y crece con el progreso
 * del arrastre y se dibuja por encima del contenido de la pantalla.
 *
 * La posición y la escala se aplican con `graphicsLayer`, que no consume espacio
 * de layout: por eso el `Scaffold` solo reserva [BottomMenuDefaults.CONTAINER_HEIGHT]
 * y la Pokéball expandida queda flotando sobre el contenido.
 *
 * @param onTap invoked al soltar sin superar el *slop*, es decir, en un toque.
 * @param onDragFinished invoked al terminar el arrastre con el estado al que
 *   debe asentarse.
 */
@Composable
private fun MenuPokeBall(
    progress: MutableFloatState,
    transform: PokeBallTransform,
    sensitivityPx: Float,
    isExpanded: MutableState<Boolean>,
    selected: Boolean,
    onTap: (Offset) -> Unit,
    onDragFinished: (Boolean) -> Unit
) {
    val description = stringResource(R.string.poke_ball_desc)

    // Respuesta visual al toque: la bola se encoge de golpe y rebota a su tamaño.
    // Se lee dentro del `graphicsLayer`, así que la animación solo invalida la
    // capa y no recompone nada.
    val punch = remember { Animatable(1f) }
    val punchScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .padding(bottom = BottomMenuDefaults.POKEBALL_BOTTOM_PADDING)
            .size(BottomMenuDefaults.POKEBALL_SIZE)
            .graphicsLayer {
                val value = progress.floatValue
                translationX = transform.restOffsetXPx +
                    (transform.expandedOffsetXPx - transform.restOffsetXPx) * value
                translationY = transform.expandedOffsetYPx * value
                val scale = (1f + (transform.expandedScale - 1f) * value) * punch.value
                scaleX = scale
                scaleY = scale
                shadowElevation = BottomMenuDefaults.EXPANDED_SHADOW.toPx() * value
                // El shadow de `graphicsLayer` usa la silueta de la capa, que por
                // defecto es el rectángulo del layout: sin esto se ve un cuadrado
                // alrededor de la Pokéball al expandirla. Con `clip = false` la
                // forma solo afecta al shadow y el contenido no se recorta.
                shape = CircleShape
                clip = false
                // Fuera de la pantalla principal la Pokéball queda algo apagada,
                // que es la única pista de qué pantalla está activa.
                alpha = if (selected) 1f else 0.85f
            }
            .pointerInput(Unit) {
                val touchSlop = viewConfiguration.touchSlop

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var previousY = down.position.y
                    var dragging = false
                    var cancelled = false

                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }

                        // El orden importa: en el evento de `up` Compose ya marca
                        // `pressed = false`, así que hay que mirar antes si el dedo
                        // se ha levantado que si sigue apoyado.
                        if (change == null) {
                            cancelled = true
                            break
                        }
                        if (change.changedToUpIgnoreConsumed()) break
                        if (!change.pressed) {
                            cancelled = true
                            break
                        }

                        if (!dragging && abs(change.position.y - down.position.y) > touchSlop) {
                            dragging = true
                        }

                        if (dragging) {
                            // `previousY - position.y` es positivo al subir, que es
                            // justo cuando el progreso debe crecer.
                            val delta = (previousY - change.position.y) / sensitivityPx
                            progress.floatValue = (progress.floatValue + delta).coerceIn(0f, 1f)
                            change.consume()
                        }

                        previousY = change.position.y
                    }

                    when {
                        // Se canceló el gesto a medias: se vuelve al estado anterior.
                        cancelled -> onDragFinished(isExpanded.value)
                        // Arrastre completo: se decide el estado final por umbral,
                        // tanto si estaba abajo como si estaba arriba.
                        dragging -> {
                            val threshold = BottomMenuDefaults.SNAP_THRESHOLD
                            val target = if (isExpanded.value) {
                                progress.floatValue > 1f - threshold
                            } else {
                                progress.floatValue > threshold
                            }
                            onDragFinished(target)
                        }
                        // Toque limpio: comportamiento según el estado.
                        else -> {
                            onTap(down.position)
                            punchScope.launch {
                                punch.snapTo(BottomMenuDefaults.TAP_PUNCH_SCALE)
                                punch.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        }
                    }
                }
            }
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.pokeball_menu),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ─────────────────────────── Previews ───────────────────────────

/**
 * Fondo de los previews del menú. A propósito es **saturado y no blanco**: la
 * hendidura se dibuja dejando ver lo que hay detrás, así que con un fondo claro
 * la curva se pierde y no hay forma de ajustarla a ojo.
 */
private val PreviewBackground = Color(0xFF2E7D32)

/**
 * Pantalla de mentira para los `@Preview`: imita lo que se ve en el dispositivo
 * (contenido arriba, menú abajo) sin fragments, ViewModel ni Activity.
 *
 * El montaje completo de la app (este menú incluido) se previsualiza ahora desde
 * `AppRoot`, que ya no castea `LocalContext`; este `MenuStage` queda para iterar
 * el menú aislado con distintos estados del gesto.
 *
 * @param initialProgress estado del gesto que se quiere enseñar.
 * @param isMapSelected deja el botón izquierdo marcado como seleccionado.
 */
@Composable
private fun MenuStage(
    initialProgress: Float = 0f,
    isMapSelected: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PreviewBackground)
    ) {
        // Trainer falso, más o menos donde lo pone `MainLayout.TRAINER_BIAS`.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
                .size(120.dp)
                .background(Color(0x66FFFFFF), CircleShape)
        )

        PokemonBottomMenu(
            isMapSelected = isMapSelected,
            isHomeSelected = !isMapSelected,
            modifier = Modifier.align(Alignment.BottomCenter),
            initialProgress = initialProgress
        )
    }
}

/** Estado normal, tal como se ve al abrir la app. */
@Preview(name = "1 · En reposo", showBackground = true, backgroundColor = 0xFF2E7D32)
@Composable
private fun MenuRestingPreview() = MenuStage()

/** Mitad del gesto: el momento en que se ve si la Pokéball atraviesa la hendidura. */
@Preview(name = "2 · Arrastrando (50 %)", showBackground = true, backgroundColor = 0xFF2E7D32)
@Composable
private fun MenuDraggingPreview() = MenuStage(initialProgress = 0.5f)

/** Pokéball grande flotando sobre el contenido: cómo sube y cuánto crece. */
@Preview(name = "3 · Expandida", showBackground = true, backgroundColor = 0xFF2E7D32)
@Composable
private fun MenuExpandedPreview() = MenuStage(initialProgress = 1f)

/** Botón lateral seleccionado: comprueba el tinte con el color primario. */
@Preview(name = "4 · Con Mapa seleccionado", showBackground = true, backgroundColor = 0xFF2E7D32)
@Composable
private fun MenuMapSelectedPreview() = MenuStage(isMapSelected = true)

/**
 * La hendidura a sola, sin barra completa, iconos ni nada que la tape: es el
 * preview para ajustar `NOTCH_WIDTH`, `NOTCH_DEPTH` y `POKEBALL_BOTTOM_PADDING`
 * **en reposo**.
 *
 * Muestra el ancho en reposo porque `rememberBottomMenuNotchShape()` sin
 * argumentos usa los valores por defecto. Para ver el ancho expandido, mira el
 * preview `3 · Expandida`, que ya lo lleva aplicado.
 *
 * Dibuja la Pokéball en su posición real de reposo, así que se ve de un vistazo
 * si el hueco es más pequeño que la bola o si el fondo la deja enterrada.
 */
@Preview(name = "5 · Hendidura (silueta)", showBackground = true, backgroundColor = 0xFF1B5E20)
@Composable
private fun NotchPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BottomMenuDefaults.CONTAINER_HEIGHT)
            .background(Color(0xFF1B5E20))
    ) {
        Surface(
            shape = rememberBottomMenuNotchShape(),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .height(BottomMenuDefaults.BAR_HEIGHT)
                .align(Alignment.BottomCenter)
        ) {}

        Image(
            painter = painterResource(R.drawable.pokeball_menu),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = BottomMenuDefaults.POKEBALL_BOTTOM_PADDING)
                .size(BottomMenuDefaults.POKEBALL_SIZE)
        )
    }
}



/**
 * Silueta de la barra del bottom menu: un rectángulo con una hendidura curva en
 * centro del borde superior, donde se encaja la Pokéball.
 *
 * La hendidura se traza con dos curvas cúbicas que bajan desde los bordes del
 * hueco hasta el centro y vuelven a subir, de modo que no hay picos: el fondo es
 * una curva suave.
 *
 * Las medidas vienen **en píxeles y absolutas**, no como fracción de la altura
 * de la barra: así `NOTCH_WIDTH` y `NOTCH_DEPTH` se editan por separado, sin que
 * ensanchar la hendidura obligue a mover su fondo. Para trabajar en dp, usa
 * [rememberBottomMenuNotchShape].
 *
 * @param widthPx semiancho del hueco, en px. Debe ser mayor que el radio de la
 *   Pokéball para que esta quede suelta dentro.
 * @param depthPx fondo del hueco, en px.
 */
fun BottomMenuNotchShape(widthPx: Float, depthPx: Float): GenericShape = GenericShape { size, _ ->
    val height = size.height
    val centerX = size.width / 2f

    // Topes de seguridad: ninguna combinación de valores debe generar un path
    // inválido (un `lineTo` a coordenadas negativas, o un fondo más hondo que
    // la propia barra).
    val halfWidth = widthPx.coerceIn(0f, (centerX - 1f).coerceAtLeast(0f))
    val depth = depthPx.coerceIn(0f, height)

    moveTo(0f, 0f)

    // Borde superior izquierdo hasta el comienzo de la hendidura.
    lineTo(centerX - halfWidth, 0f)

    // Bajada suave hacia el centro.
    cubicTo(
        x1 = centerX - halfWidth * 0.8f, y1 = 0f,
        x2 = centerX - halfWidth * 0.7f, y2 = depth,
        x3 = centerX, y3 = depth
    )

    // Subida suave hacia la derecha.
    cubicTo(
        x1 = centerX + halfWidth * 0.7f, y1 = depth,
        x2 = centerX + halfWidth * 0.8f, y2 = 0f,
        x3 = centerX + halfWidth, y3 = 0f
    )

    lineTo(size.width, 0f)
    lineTo(size.width, height)
    lineTo(0f, height)
    close()
}

/**
 * Hendidura del menú con los valores por defecto de [BottomMenuDefaults],
 * convertidos de dp a píxeles.
 *
 * Se recuerda con `remember` a propósito: [BottomMenuNotchShape] devuelve un
 * `GenericShape` nuevo en cada llamada, y una instancia nueva en cada
 * recomposición hace que la `Surface` vuelva a dibujar su silueta sin motivo.
 *
 * @param width semiancho del hueco. Afecta solo a lo ancho de la hendidura.
 * @param depth fondo del hueco. Afecta solo a lo hondo.
 */
@Composable
fun rememberBottomMenuNotchShape(
    width: Dp = BottomMenuDefaults.NOTCH_WIDTH,
    depth: Dp = BottomMenuDefaults.NOTCH_DEPTH
): Shape {
    val density = LocalDensity.current
    return remember(width, depth, density) {
        with(density) { BottomMenuNotchShape(width.toPx(), depth.toPx()) }
    }
}
