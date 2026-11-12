/**
 * Introducción del Profesor Oak y utilidades de GIF animado.
 *
 * La intro recoge nombre y starter en estado local de UI (no persiste nada hasta
 * completarse) y los entrega juntos al ViewModel. Las opciones de starter llegan
 * ya resueltas como `StarterOption`, sin que la UI consulte catálogos.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.StarterOption
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import android.graphics.Color as AndroidColor
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView



/** Milisegundos entre cada letra del efecto "máquina de escribir". */
private const val TYPE_CHAR_MS = 35L

/** Milisegundos de cada frame de la animación de habla (boca abierta / cerrada). */
private const val TALK_FRAME_MS = 160L

/** Longitud máxima del nombre del entrenador. */
private const val MAX_NAME_LENGTH = 12

/**
 * Proporción `ancho / alto` del retrato de Oak. Las seis imágenes comparten
 * exactamente 399x680, así que una sola proporción sirve para todas.
 */
private const val OAK_ASPECT = 399f / 680f

/** Fracción del ancho de pantalla que ocupa el retrato de Oak. */
private const val OAK_WIDTH_FRACTION = 0.80f

/** Tope de ancho del retrato, para que no crezca sin límite en pantallas grandes. */
private val OAK_MAX_WIDTH = 360.dp

/** Margen superior del retrato; el diálogo arranca justo debajo de él. */
private val OAK_TOP_PADDING = 16.dp

/** Tamaño del starter elegido cuando Oak lo describe en el centro de la pantalla. */
private val STARTER_FOCUS_SIZE = 160.dp

/** Emoción de Oak: decide qué par de retratos se anima mientras habla. */
internal enum class OakMood { TALKING, ANGRY, DROP, OFFERING }

/**
 * Retratos de una emoción: [closed] es el frame en reposo y [open] el de boca
 * abierta que se alterna mientras el texto se escribe. Un [open] nulo significa
 * retrato fijo (las emociones que no tienen pareja dibujada).
 */
internal data class OakPortrait(
    @param:DrawableRes val closed: Int,
    @param:DrawableRes val open: Int? = null
)

/** Par de imágenes de cada emoción, en el orden del guion. */
internal val OakMood.portrait: OakPortrait
    get() = when (this) {
        OakMood.TALKING -> OakPortrait(R.drawable.profesor_oak, R.drawable.profesor_oak_talk)
        OakMood.ANGRY -> OakPortrait(R.drawable.profesor_oak_angry_closed, R.drawable.profesor_oak_angry)
        OakMood.DROP -> OakPortrait(R.drawable.profesor_oak_drop)
        OakMood.OFFERING -> OakPortrait(R.drawable.profesor_oak_offering)
    }

/**
 * Widget que aparece bajo la línea cuando esta termina de escribirse.
 *
 * [CONFIRM_NAME] y [CONFIRM_STARTER] son salvaguardas de la elección: repiten lo
 * que el jugador acaba de escoger y le dejan corregirlo ([introNameStep] y
 * [introStarterStep] son los pasos a los que vuelve "No").
 */
internal enum class IntroWidget { NONE, NAME, CONFIRM_NAME, STARTER, CONFIRM_STARTER }

/** Una línea del guion: texto, emoción y el widget que la acompaña. */
internal data class IntroLine(
    @param:StringRes val textRes: Int,
    val mood: OakMood,
    val widget: IntroWidget = IntroWidget.NONE,
    val formatArgs: List<String> = emptyList()
)

/** Acción que dispara un toque en la caja de diálogo. */
internal enum class IntroTap { REVEAL_LINE, ADVANCE }

/**
 * Regla del toque: el primero completa la línea que se está escribiendo; solo
 * con la línea ya completa se pasa a la siguiente. Nunca se saltan líneas.
 */
internal fun introTap(lineComplete: Boolean): IntroTap =
    if (lineComplete) IntroTap.ADVANCE else IntroTap.REVEAL_LINE

/**
 * Una línea con widget no avanza con el toque en la caja: la confirma el propio
 * widget (aceptar el nombre o elegir el starter). Se aísla para poder probarla.
 */
internal fun introCanAdvance(widget: IntroWidget): Boolean = widget == IntroWidget.NONE

/**
 * Paso al que vuelve el jugador al responder "No" en la confirmación del nombre:
 * la línea que abre el campo, conservando lo ya escrito para poder corregirlo.
 */
internal fun introNameStep(script: List<IntroLine>): Int =
    script.indexOfFirst { it.widget == IntroWidget.NAME }

/**
 * Paso al que vuelve el jugador al responder "No" en la descripción del starter:
 * el selector, con el elegido devuelto a su sitio para poder escoger otro.
 */
internal fun introStarterStep(script: List<IntroLine>): Int =
    script.indexOfFirst { it.widget == IntroWidget.STARTER }

/**
 * Ficha que Oak lee del starter elegido. La pregunta de confirmación va incluida
 * en el propio texto; sin especie conocida queda solo la pregunta.
 */
internal fun starterBriefRes(speciesId: String?): Int = when (speciesId) {
    "bulbasaur" -> R.string.intro_starter_brief_bulbasaur
    "charmander" -> R.string.intro_starter_brief_charmander
    "squirtle" -> R.string.intro_starter_brief_squirtle
    else -> R.string.intro_starter_brief_default
}

/**
 * Guion completo de la intro. El nombre y el starter elegido solo rellenan los
 * textos (y la ficha del starter), así que la lista tiene siempre la misma
 * longitud: el índice de paso es seguro de guardar entre recomposiciones.
 */
internal fun introScript(name: String, starter: StarterOption?): List<IntroLine> = listOf(
    IntroLine(R.string.intro_line_welcome, OakMood.TALKING),
    IntroLine(R.string.intro_ask_name, OakMood.TALKING, IntroWidget.NAME),
    IntroLine(
        R.string.intro_confirm_name,
        OakMood.TALKING,
        IntroWidget.CONFIRM_NAME,
        formatArgs = listOf(name)
    ),
    IntroLine(R.string.intro_greet, OakMood.TALKING, formatArgs = listOf(name)),
    IntroLine(R.string.intro_line_danger, OakMood.TALKING),
    IntroLine(R.string.intro_line_rocket_power, OakMood.ANGRY),
    IntroLine(R.string.intro_line_loop, OakMood.ANGRY),
    IntroLine(R.string.intro_line_trapped, OakMood.ANGRY),
    IntroLine(R.string.intro_line_pokedex, OakMood.TALKING),
    IntroLine(R.string.intro_line_charge, OakMood.TALKING),
    IntroLine(R.string.intro_line_needs, OakMood.TALKING),
    IntroLine(R.string.intro_line_green, OakMood.TALKING),
    IntroLine(R.string.intro_line_green_impulsive, OakMood.DROP),
    IntroLine(R.string.intro_line_green_risk, OakMood.TALKING),
    IntroLine(R.string.intro_line_ask_help, OakMood.OFFERING),
    IntroLine(R.string.intro_ask_starter, OakMood.OFFERING, IntroWidget.STARTER),
    IntroLine(starterBriefRes(starter?.id), OakMood.OFFERING, IntroWidget.CONFIRM_STARTER),
    IntroLine(R.string.intro_goodbye, OakMood.OFFERING, formatArgs = listOf(starter?.name.orEmpty()))
)

/**
 * Introducción de la primera partida: el Profesor Oak da la bienvenida, pregunta
 * el nombre del entrenador y ofrece elegir el starter permanente.
 *
 * Es **sin estado respecto al juego**: recoge nombre y starter en local y los
 * entrega juntos al terminar con [onFinish], de modo que nada se persiste hasta
 * que la intro se completa (si se cierra a medias, vuelve a empezar).
 *
 * El nodo raíz aplica `Modifier.modalBlocking()` (issue #21): la intro es opaca
 * y debe aislar por completo el juego, de modo que ningún toque fuera de sus
 * controles llegue a la UI oculta de debajo.
 *
 * Dos reglas de presentación viven aquí y no en el estado del juego:
 * - **Toque en la caja**: el primero completa la línea que se está escribiendo;
 *   hace falta un segundo toque para pasar a la siguiente ([introTap]).
 * - **Retrato**: se alterna el par de frames de la emoción de la línea mientras
 *   el texto se escribe, y el tamaño depende solo del ancho de pantalla, nunca
 *   de los widgets que aparezcan debajo ([OakMood.portrait]).
 *
 * El diálogo va inmediatamente bajo el retrato y los widgets debajo de él, en
 * una capa que se dibuja sobre la imagen: cuando el teclado se despliega es esa
 * capa la que se mueve, y el retrato nunca se desplaza.
 *
 * @param starters opciones de starter ya resueltas por el ViewModel.
 * @param onFinish nombre recortado y id del starter elegido.
 */
@Composable
fun IntroOverlay(
    starters: List<StarterOption>,
    onFinish: (playerName: String, starterSpeciesId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var starterId by rememberSaveable { mutableStateOf<String?>(null) }

    val chosenStarter = starters.firstOrNull { it.id == starterId }
    val script = introScript(name = name, starter = chosenStarter)
    val line = script[stepIndex.coerceIn(script.indices)]

    val fullText = if (line.formatArgs.isEmpty()) {
        stringResource(line.textRes)
    } else {
        stringResource(line.textRes, *line.formatArgs.toTypedArray())
    }

    // Efecto máquina de escribir: se reinicia en cada paso. Un toque puede
    // adelantarlo a la línea completa (ver `onBoxTap`).
    var revealed by remember { mutableIntStateOf(0) }
    // Desplazamiento de la capa de diálogo: con el teclado abierto se mueve ella
    // y no el retrato (ver el layout al final).
    val scroll = rememberScrollState()
    LaunchedEffect(stepIndex, fullText) {
        revealed = 0
        while (revealed < fullText.length) {
            delay(TYPE_CHAR_MS)
            revealed++
        }
    }
    val lineComplete = revealed >= fullText.length
    val shown = fullText.take(revealed)

    // Animación de hablar de dos frames: alterna boca abierta / cerrada
    // mientras la línea se escribe y se queda en el frame cerrado en reposo.
    val portrait = line.mood.portrait
    var mouthOpen by remember { mutableStateOf(false) }
    LaunchedEffect(lineComplete, portrait) {
        mouthOpen = portrait.open != null
        while (!lineComplete) {
            delay(TALK_FRAME_MS)
            mouthOpen = !mouthOpen
        }
        mouthOpen = false
    }

    // Se limpia el revelado al cambiar de línea para que la nueva no herede un
    // `lineComplete` viejo durante un frame.
    fun nextStep() {
        revealed = 0
        stepIndex++
    }

    fun advance() {
        when {
            // Las líneas con widget esperan a que el jugador lo confirme.
            !introCanAdvance(line.widget) -> Unit
            stepIndex == script.lastIndex -> onFinish(name.trim(), starterId.orEmpty())
            else -> nextStep()
        }
    }

    fun onBoxTap() {
        when (introTap(lineComplete)) {
            IntroTap.REVEAL_LINE -> revealed = fullText.length
            IntroTap.ADVANCE -> advance()
        }
    }

    // Al cambiar de línea la capa vuelve arriba: el diálogo queda de nuevo justo
    // debajo del retrato aunque el teclado la hubiera desplazado.
    LaunchedEffect(stepIndex) { scroll.scrollTo(0) }

    // Con un widget a la vista la capa se desplaza lo justo para que entre
    // entero en pantallas cortas; el retrato no se mueve porque vive en otra capa.
    LaunchedEffect(line.widget, lineComplete) {
        if (line.widget == IntroWidget.NONE || !lineComplete) return@LaunchedEffect
        val overflow = snapshotFlow { scroll.maxValue }.first { it > 0 }
        scroll.animateScrollTo(overflow)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .modalBlocking()
    ) {
        // El retrato se dimensiona solo por el ancho de pantalla: ni el campo de
        // texto ni los sprites de los starters pueden cambiarlo.
        val portraitWidth = minOf(maxWidth * OAK_WIDTH_FRACTION, OAK_MAX_WIDTH)
        val portraitHeight = portraitWidth / OAK_ASPECT

        // Capa 1: el retrato. Nunca se mueve ni se encoge.
        Image(
            painter = painterResource(portrait.open?.takeIf { mouthOpen } ?: portrait.closed),
            contentDescription = stringResource(R.string.intro_oak_desc),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = OAK_TOP_PADDING)
                .size(portraitWidth, portraitHeight)
        )

        // Capa 2: el starter elegido, centrado mientras Oak lo describe.
        if (line.widget == IntroWidget.CONFIRM_STARTER && chosenStarter != null) {
            AnimatedGifImage(
                resId = starterGif(chosenStarter.id),
                contentDescription = chosenStarter.name,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(STARTER_FOCUS_SIZE)
            )
        }

        // Capa 3 (por encima del retrato): el diálogo arranca justo debajo de la
        // imagen y el campo de nombre / los starters van a continuación. Al abrir
        // el teclado la capa se desplaza sobre la imagen: la imagen no se mueve.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(
                    state = scroll,
                    // Sin widget no hay nada que desplazar: se evita que un
                    // arrastre mueva el diálogo sobre el retrato.
                    enabled = line.widget != IntroWidget.NONE
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(portraitHeight + OAK_TOP_PADDING))

            DialogueBox(
                text = shown,
                complete = lineComplete,
                onClick = ::onBoxTap,
                modifier = Modifier.fillMaxWidth()
            )

            // Zona interactiva (nombre, confirmación del nombre o starter).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    line.widget == IntroWidget.NAME && lineComplete ->
                        NameEntry(
                            name = name,
                            onNameChange = { if (it.length <= MAX_NAME_LENGTH) name = it },
                            onAccept = { nextStep() }
                        )
                    line.widget == IntroWidget.CONFIRM_NAME && lineComplete ->
                        ConfirmationButtons(
                            // "No" vuelve al campo conservando lo escrito.
                            onChange = { stepIndex = introNameStep(script); revealed = 0 },
                            onAccept = { nextStep() }
                        )
                    line.widget == IntroWidget.STARTER && lineComplete ->
                        StarterPicker(
                            starters = starters,
                            onSelect = { id -> starterId = id; nextStep() }
                        )
                    line.widget == IntroWidget.CONFIRM_STARTER && lineComplete ->
                        ConfirmationButtons(
                            // "No" devuelve el starter a su sitio y reabre el selector.
                            onChange = {
                                starterId = null
                                stepIndex = introStarterStep(script)
                                revealed = 0
                            },
                            onAccept = { nextStep() }
                        )
                }
            }
        }
    }
}

/** Caja de diálogo estilo Pokémon: rectángulo blanco con borde negro. */
@Composable
private fun DialogueBox(
    text: String,
    complete: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(3.dp, Color.Black, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = text,
            color = Color.Black,
            style = MaterialTheme.typography.titleMedium
        )
        if (complete) {
            Text(
                text = "▼",
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

/** Entrada del nombre con teclado en pantalla. */
@Composable
private fun NameEntry(
    name: String,
    onNameChange: (String) -> Unit,
    onAccept: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val accept = {
        if (name.isNotBlank()) {
            keyboard?.hide()
            onAccept()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(text = stringResource(R.string.intro_name_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { accept() })
        )
        Button(onClick = accept, enabled = name.isNotBlank()) {
            Text(text = stringResource(R.string.intro_accept))
        }
    }
}

/** Confirmación de una elección (nombre o starter): "Sí" sigue, "No" vuelve atrás. */
@Composable
private fun ConfirmationButtons(
    onChange: () -> Unit,
    onAccept: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = onAccept) {
            Text(text = stringResource(R.string.intro_confirm_yes))
        }
        OutlinedButton(onClick = onChange) {
            Text(text = stringResource(R.string.intro_confirm_no))
        }
    }
}

/** Fila con los starters animados, tocables. */
@Composable
private fun StarterPicker(
    starters: List<StarterOption>,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        starters.forEach { starter ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(starter.id) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedGifImage(
                    resId = starterGif(starter.id),
                    contentDescription = starter.name,
                    modifier = Modifier.size(88.dp)
                )
                // El tipo ya no se etiqueta aquí: lo explica Oak al elegirlo.
                Text(text = starter.name, color = Color.Black)
            }
        }
    }
}

private fun starterGif(speciesId: String): Int = when (speciesId) {
    "bulbasaur" -> R.drawable.bulbasaur
    "charmander" -> R.drawable.charmander
    "squirtle" -> R.drawable.squirtle
    else -> R.drawable.bulbasaur
}



/**
 * Muestra un **GIF animado** de recursos.
 *
 * Compose no anima GIFs con `painterResource`, así que se decodifica con
 * [ImageDecoder] (API 28+; el proyecto es minSdk 29) a un [AnimatedImageDrawable]
 * y se deja dentro de un [ImageView] vía [AndroidView]. No añade dependencias.
 *
 * El filtrado se desactiva porque son sprites de pixel-art diminutos: al
 * escalarlos se quieren los píxeles nítidos, no un suavizado borroso.
 *
 * @param resId recurso GIF (`R.drawable.*`).
 * @param contentDescription descripción para accesibilidad.
 */
@Composable
fun AnimatedGifImage(
    @DrawableRes resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val drawable: Drawable = remember(resId) {
        runCatching {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, resId))
                .also { decoded ->
                    if (decoded is AnimatedImageDrawable) {
                        decoded.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                        decoded.setFilterBitmap(false)
                        decoded.start()
                    }
                    // Los PNG estáticos (líderes sin GIF) también son pixel-art:
                    // sin esto, el escalado del ImageView los emborrona.
                    if (decoded is BitmapDrawable) {
                        decoded.setFilterBitmap(false)
                    }
                }
        }.getOrElse { ColorDrawable(AndroidColor.TRANSPARENT) }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(drawable)
                this.contentDescription = contentDescription
            }
        },
        update = { imageView ->
            imageView.setImageDrawable(drawable)
            imageView.contentDescription = contentDescription
        }
    )
}
