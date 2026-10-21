/**
 * Introducción del Profesor Oak y utilidades de GIF animado.
 *
 * La intro recoge nombre y starter en estado local de UI (no persiste nada hasta
 * completarse) y los entrega juntos al ViewModel. Las opciones de starter llegan
 * ya resueltas como `StarterOption`, sin que la UI consulte catálogos.
 */
package com.example.myapplication.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.model.StarterOption
import kotlinx.coroutines.delay
import android.graphics.Color as AndroidColor
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView



/** Milisegundos entre cada letra del efecto "máquina de escribir". */
private const val TYPE_CHAR_MS = 35L

/** Longitud máxima del nombre del entrenador. */
private const val MAX_NAME_LENGTH = 12

/**
 * Introducción de la primera partida: el Profesor Oak da la bienvenida, pregunta
 * el nombre del entrenador y ofrece elegir el starter permanente.
 *
 * Es **sin estado respecto al juego**: recoge nombre y starter en local y los
 * entrega juntos al terminar con [onFinish], de modo que nada se persiste hasta
 * que la intro se completa (si se cierra a medias, vuelve a empezar).
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
    // Fases: 0..2 presentación · 3 nombre · 4 saludo · 5 starter · 6 despedida.
    var phase by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var starterId by rememberSaveable { mutableStateOf<String?>(null) }

    val starterName = starters.firstOrNull { it.id == starterId }?.name.orEmpty()

    val fullText = when (phase) {
        0 -> stringResource(R.string.intro_line_welcome)
        1 -> stringResource(R.string.intro_line_world)
        2 -> stringResource(R.string.intro_line_creatures)
        3 -> stringResource(R.string.intro_ask_name)
        4 -> stringResource(R.string.intro_greet, name)
        5 -> stringResource(R.string.intro_ask_starter)
        else -> stringResource(R.string.intro_goodbye, starterName)
    }

    // Efecto máquina de escribir: se reinicia en cada fase. Un toque puede
    // adelantarlo a la línea completa (ver `onBoxTap`).
    var revealed by remember { mutableIntStateOf(0) }
    LaunchedEffect(phase, fullText) {
        revealed = 0
        while (revealed < fullText.length) {
            delay(TYPE_CHAR_MS)
            revealed++
        }
    }
    val lineComplete = revealed >= fullText.length
    val shown = fullText.take(revealed)

    fun advance() {
        when (phase) {
            0 -> phase = 1
            1 -> phase = 2
            2 -> phase = 3
            4 -> phase = 5
            6 -> onFinish(name.trim(), starterId.orEmpty())
            // 3 y 5 son interactivas: avanzan al aceptar nombre / elegir starter.
        }
    }

    fun onBoxTap() {
        if (!lineComplete) revealed = fullText.length else advance()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Retrato de Oak, centrado arriba.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Image(
                painter = painterResource(R.drawable.profesor_oak),
                contentDescription = stringResource(R.string.intro_oak_desc),
                modifier = Modifier.fillMaxSize()
            )
        }

        // Zona interactiva (nombre o elección de starter), sobre la caja.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            when (phase) {
                3 -> if (lineComplete) {
                    NameEntry(
                        name = name,
                        onNameChange = { if (it.length <= MAX_NAME_LENGTH) name = it },
                        onAccept = { phase = 4 }
                    )
                }
                5 -> if (lineComplete) {
                    StarterPicker(starters = starters, onSelect = { id -> starterId = id; phase = 6 })
                }
            }
        }

        DialogueBox(
            text = shown,
            complete = lineComplete,
            onClick = ::onBoxTap,
            modifier = Modifier.fillMaxWidth()
        )
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
        keyboard?.hide()
        onAccept()
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
                Text(text = starter.name, color = Color.Black)
                Text(
                    text = stringResource(R.string.starter_type, starter.type.name),
                    color = Color.Black,
                    style = MaterialTheme.typography.bodySmall
                )
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
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, resId)).also {
                if (it is AnimatedImageDrawable) {
                    it.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    it.setFilterBitmap(false)
                    it.start()
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
