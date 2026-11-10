/**
 * Pantalla de la Pokédex con estética de dispositivo: carcasa roja plana,
 * **franja de estado compacta** de rojo oscuro y **una única pantalla LCD**
 * oscura y redondeada que ocupa el resto. Todo con los tokens compartidos
 * (`RetroTheme`); sin imágenes reales de Pokémon.
 *
 * Reglas de la información (estados):
 * - **UNKNOWN**: número y visor con «?» pale green de alto contraste; sin
 *   nombre, tipo ni Pokéball (y sin textos de accesibilidad que revelen
 *   identidad).
 * - **SEEN**: identificador provisional (nombre + «Falta imagen» dentro del
 *   visor, que crece en alto para nombres largos), nombre independiente
 *   fuera del visor, tipo debajo del nombre y **Pokéball gris tenue**
 *   (escala de grises, opacidad 0.35): especie conocida que no está poseída
 *   en la partida actual.
 * - **CAUGHT**: misma estructura, con Pokéball a color y opacidad plena:
 *   especie poseída en la partida actual o inicial elegido. La distinción
 *   no depende solo del color: cada Pokéball lleva su descripción accesible.
 *
 * **Imágenes (decisión de alcance TEST): no se carga, decodifica ni presenta
 * ninguna imagen de Pokémon.** Las referencias de sprite se conservan para el
 * futuro. El estado CAUGHT lo marca la posesión de la run (`ownedSpecies` /
 * `starterSpeciesId`), no el historial permanente.
 */
package com.dsrs.pokeclic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.DexStatus
import com.dsrs.pokeclic.model.PokedexRow
import com.dsrs.pokeclic.viewmodel.GameUiState

/**
 * Referencias de sprite por especie, **reservadas para una versión futura**:
 * la Pokédex (TEST) actual NO carga, decodifica ni presenta imágenes de
 * Pokémon (decisión: no mostrar imágenes reales aunque los recursos existan).
 * El mapa se conserva como punto de reintroducción documentado.
 */
private val DEX_SPRITES: Map<String, Int> = mapOf(
    "bulbasaur" to R.drawable.bulbasaur,
    "charmander" to R.drawable.charmander,
    "squirtle" to R.drawable.squirtle,
    "golem" to R.drawable.golem,
    "raichu" to R.drawable.raichu,
    "starmie" to R.drawable.starmie
)

/**
 * Paleta de tipos de la Pokédex (decoración, no reglas de juego): nombres en
 * español tal como los aporta el catálogo adaptado. Incluye tipos que **no**
 * existen como constantes de combate (`PokemonType` queda intacto).
 */
private val DEX_TYPE_COLORS: Map<String, Color> = mapOf(
    "Normal" to Color(0xFFA8A878),
    "Fuego" to Color(0xFFF08030),
    "Agua" to Color(0xFF6890F0),
    "Planta" to Color(0xFF78C850),
    "Eléctrico" to Color(0xFFF8D030),
    "Roca" to Color(0xFFB8A038),
    "Volador" to Color(0xFFA890F0),
    "Veneno" to Color(0xFFA040A0),
    "Bicho" to Color(0xFFA8B820),
    "Tierra" to Color(0xFFE0C068),
    "Lucha" to Color(0xFFC03028),
    "Psíquico" to Color(0xFFF85888),
    "Hielo" to Color(0xFF98D8D8),
    "Fantasma" to Color(0xFF705898),
    "Dragón" to Color(0xFF7038F8)
)

/** Color de respaldo para un tipo fuera de la paleta. */
private val DEX_TYPE_FALLBACK = Color(0xFF9DA0A4)

/** Texto principal sobre la pantalla LCD: verde pálido como el HUD. */
private val DexLcdText = RetroColors.LcdText

/** Texto secundario del LCD (número de fila y etiquetas atenuadas). */
private val DexLcdTextDim = RetroColors.LcdText.copy(alpha = 0.72f)

/** Línea fina de los visores y divisor entre filas sobre la LCD. */
private val DexLcdLine = Color(0xFF4E5A66)

/** Divisor fino entre filas dentro de la pantalla LCD. */
private val DexRowDivider = DexLcdLine.copy(alpha = 0.35f)

/** Área táctil de la flecha: su centro (24.dp) define la línea visual. */
private val DexHeaderArrowTouch = 48.dp

/** Métricas comunes de línea del encabezado: cifra/título y etiqueta. */
private val DexHeaderValueSize = 16.sp
private val DexHeaderValueLine = 19.sp
private val DexHeaderLabelSize = 10.sp
private val DexHeaderLabelLine = 12.sp

/**
 * Pantalla de la Pokédex. Sin estado: recibe el [GameUiState] ya derivado
 * (filas, contadores y flag de error) y notifica la vuelta con [onBack]. El
 * botón físico «Atrás» también vuelve, con `BackHandler`.
 */
@Composable
fun DexScreen(
    state: GameUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Atrás físico y de gesto: siempre vuelve al juego.
    BackHandler(enabled = true) { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RetroColors.PokedexRed)
            .statusBarsPadding()
    ) {
        // Módulo de encabezado: mismo lenguaje que el listado (LCD oscura,
        // borde fino, esquinas redondeadas) y los mismos márgenes laterales,
        // de modo que ambos comparten anchura exterior. Envuelve su
        // contenido; la LCD central recibe el resto con `weight(1f)`.
        DexHeader(
            seenCount = state.dexSeenCount,
            caughtCount = state.dexCaughtCount,
            onBack = onBack,
            modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
        )

        if (state.dexLoadFailed) {
            DexError(modifier = Modifier.fillMaxSize().padding(16.dp))
        } else {
            // Única pantalla LCD: esquinas suavizadas, borde fino y sin sombra
            // dura; separación vertical pequeña con la franja superior (6.dp).
            RetroLcdPanel(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 10.dp)
                    .fillMaxWidth(),
                fill = true,
                shape = RoundedCornerShape(8.dp),
                borderWidth = 1.dp,
                innerGap = 1.dp,
                shadowOffset = 0.dp,
                contentPadding = PaddingValues(6.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(
                        state.dexEntries,
                        key = { _, entry -> entry.dexNumber }
                    ) { index, entry ->
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(DexRowDivider)
                            )
                        }
                        DexRow(entry)
                    }
                }
            }
        }
    }
}

/**
 * Módulo de encabezado del dispositivo: fondo **burdeos muy oscuro**
 * ([RetroColors.HeaderBurgundy]) que se distingue del rojo vivo de la carcasa
 * y del LCD del listado, borde fino y esquinas redondeadas del mismo
 * lenguaje visual que el listado, y grosor compacto. Textos y flecha en
 * blanco, legibles. **Sin scanlines** en este módulo.
 *
 * **Alineación (estructura común de filas, sin compensaciones
 * independientes):** la línea visual del encabezado es el **centro del área
 * táctil de la flecha** (la mitad de [DexHeaderArrowTouch]). Cada celda de
 * texto reserva exactamente ese aire con el mismo desplazamiento (`lineTop`,
 * calculado una vez con las métricas compartidas) — el título y las cifras
 * comparten tipografía y `lineHeight` ([DexHeaderValueLine]), así que sus
 * cajas de línea quedan centradas en esa misma línea y las etiquetas
 * «Avistados»/«Capturados» cuelgan debajo de sus cifras dentro de la propia
 * celda, sin desplazar la línea. No se centra el título contra la altura
 * completa del bloque cifra+etiqueta (el desajuste que había antes): el
 * centro lo fijan la flecha y las métricas compartidas.
 */
@Composable
private fun DexHeader(
    seenCount: Int,
    caughtCount: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Aire desde la fila hasta la línea visual: mitad del área táctil menos
    // la mitad de la línea de cifra/título. Con escala de fuente ajustada,
    // `sp.toDp()` sigue la métrica y la alineación se conserva.
    val headerLineTop = with(LocalDensity.current) {
        DexHeaderArrowTouch / 2 - DexHeaderValueLine.toDp() / 2
    }

    RetroPanel(
        modifier = modifier,
        backgroundColor = RetroColors.HeaderBurgundy,
        outerBorder = RetroColors.DialogBorderOuter,
        innerBorder = DexLcdLine,
        shape = RoundedCornerShape(8.dp),
        borderWidth = 1.dp,
        innerGap = 1.dp,
        shadowOffset = 0.dp,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 5.dp)
    ) {
        // La Row es top-aligned: la flecha ocupa su área táctil completa y
        // las celdas de texto descienden hasta la línea compartida.
        Row(modifier = Modifier.fillMaxWidth()) {
            // Flecha sin texto; IconButton = área táctil de 48.dp (suficiente)
            // y descripción accesible «Volver».
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.dex_close),
                    tint = Color.White
                )
            }

            Text(
                text = stringResource(R.string.dex_title).uppercase(),
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = DexHeaderValueSize,
                lineHeight = DexHeaderValueLine,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = headerLineTop, start = 4.dp),
                style = TextStyle(
                    shadow = Shadow(
                        color = RetroColors.HardShadow,
                        offset = Offset(0f, 1.4f),
                        blurRadius = 0f
                    )
                )
            )

            Spacer(modifier = Modifier.width(12.dp))
            DexStatBlock(count = seenCount, labelRes = R.plurals.dex_stat_seen, lineTop = headerLineTop)
            Spacer(modifier = Modifier.width(10.dp))
            DexStatBlock(count = caughtCount, labelRes = R.plurals.dex_stat_caught, lineTop = headerLineTop)
        }
    }
}

/**
 * Bloque de estadística del encabezado: cifra Monospace arriba y etiqueta
 * pluralizada debajo, en la misma celda. El aire superior compartido
 * ([lineTop], el mismo del título) centra la línea de cifra sobre la línea
 * visual del encabezado; la etiqueta cuelga debajo sin desplazarla. Tres
 * cifras (151 máx.) caben sin recortar las etiquetas en pantallas estrechas:
 * los bloques envuelven su contenido y el título coordina con `weight(1f)`.
 */
@Composable
private fun DexStatBlock(count: Int, labelRes: Int, lineTop: Dp) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .padding(top = lineTop)
            .widthIn(min = 56.dp)
    ) {
        Text(
            text = count.toString(),
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = DexHeaderValueSize,
            lineHeight = DexHeaderValueLine,
            maxLines = 1
        )
        Text(
            // Etiqueta singular/plural con el plural correcto para la cifra
            // (p. ej. «Capturado» con 1, «Capturados» a partir de 0/2+).
            text = pluralStringResource(labelRes, count),
            color = Color.White.copy(alpha = 0.85f),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = DexHeaderLabelSize,
            lineHeight = DexHeaderLabelLine,
            maxLines = 1
        )
    }
}

/**
 * Fila de error de catálogo: mensaje sobre el panel crema clásico (superficie
 * distinta del chasis, para que el fallo se distinga a simple vista) sin
 * bloquear el juego: el resto sigue funcionando y la vuelta está disponible
 * en la franja de estado. La anchura queda limitada para no estirar en
 * pantallas grandes.
 */
@Composable
private fun DexError(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        RetroPanel(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = stringResource(R.string.dex_error_title),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.dex_error_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Una fila del listado dentro de la pantalla LCD, en el orden pactado:
 * número → visor provisional → nombre exterior y tipo → Pokéball.
 */
@Composable
private fun DexRow(entry: PokedexRow) {
    val known = entry.status != DexStatus.UNKNOWN
    val caught = entry.status == DexStatus.CAUGHT

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "%03d".format(entry.dexNumber),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp,
            color = DexLcdTextDim,
            modifier = Modifier.width(44.dp)
        )

        ReservedImageSlot(entry, known)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            if (known) {
                // Nombre exterior destacado (más grande y brillante que el
                // número) y tipo debajo, más pequeño.
                Text(
                    text = entry.name ?: "",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = DexLcdText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.typeName != null) {
                    DexTypeBadge(entry.typeName)
                }
            }
        }

        // Hueco fijo del indicador: la Pokéball nunca desplaza al nombre ni a
        // la interrogación, y ocupa la misma posición en SEEN y CAUGHT.
        if (known) {
            BallIndicator(
                isCaught = caught,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Indicador de posesión (marcador en fila): recorta los colores de la Pokéball
 * y baja su opacidad para la especie conocida pero no poseída; a plena opacidad
 * y a color cuando está en posesión. La distinción no depende solo del color:
 * cada variante lleva su descripción accesible.
 */
@Composable
private fun BallIndicator(isCaught: Boolean, modifier: Modifier = Modifier) {
    val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    Image(
        painter = painterResource(R.drawable.pokeball_menu),
        contentDescription = stringResource(
            if (isCaught) R.string.dex_caught_desc else R.string.dex_seen_desc
        ),
        modifier = modifier
            .size(22.dp)
            .then(if (isCaught) Modifier else Modifier.alpha(0.35f)),
        colorFilter = if (isCaught) null else grayscale
    )
}

/**
 * Visor **reservado para la futura imagen** de la especie: ventana oscura con
 * una sola línea de marco fino, sin bisel ni sombras (adorno repetido evitado
 * por diseño). Su **geometría es uniforme en los tres estados**: ancho fijo
 * 76.dp y altura mínima 48.dp que crece con los nombres largos para no
 * recortarlos. Contenido provisional (sin decodificar ni pintar imágenes):
 * - **UNKNOWN** → «?» pale green centrada de alto contraste (verde pálido
 *   sobre casi negro), sin ningún texto accesible que revele la identidad
 *   (el número es el único dato de la fila).
 * - **SEEN/CAUGHT** → nombre de la especie como identificador temporal con
 *   la etiqueta «Falta imagen» debajo. La imagen futura sustituirá a este
 *   bloque; el nombre exterior de la fila ya es permanente.
 */
@Composable
private fun ReservedImageSlot(entry: PokedexRow, known: Boolean) {
    Box(
        modifier = Modifier
            .width(76.dp)
            .heightIn(min = 48.dp)
            .border(1.dp, DexLcdLine, RoundedCornerShape(6.dp))
            .background(RetroColors.BarTrack, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (known) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp)
            ) {
                Text(
                    text = entry.name ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = DexLcdText
                )
                Text(
                    text = stringResource(R.string.dex_missing_image),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    textAlign = TextAlign.Center,
                    color = DexLcdTextDim
                )
            }
        } else {
            // «?» de alto contraste: verde pálido, grande y en negrita. Sin
            // voces de accesibilidad: la fila no puede revelar identidad.
            Text(
                text = "?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                color = DexLcdText
            )
        }
    }
}

/** Etiqueta del tipo: punto con el color del tipo + nombre pequeño. */
@Composable
private fun DexTypeBadge(typeName: String) {
    val color = typeColorFor(typeName)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = typeName,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp),
            color = DexLcdTextDim
        )
    }
}

private fun typeColorFor(typeName: String?): Color =
    DEX_TYPE_COLORS[typeName] ?: DEX_TYPE_FALLBACK

// ─────────────────────────── Previews ───────────────────────────

/**
 * Especificación de dispositivo de los previews. Coincide **a propósito** con el
 * AVD de pruebas (`Medium_Phone`) como el resto de la app.
 */
private const val DEX_PREVIEW_DEVICE = "spec:width=411dp,height=914dp,dpi=420"

/**
 * Estado de ejemplo: starter + capturas y avistamientos concretos. No sirve un
 * estado vacío: el preview debe enseñar los tres estados de fila.
 */
private fun dexPreviewState(): GameUiState = GameUiState(
    caughtSpecies = setOf("charmander", "rattata"),
    seenSpecies = setOf("charmander", "rattata", "pidgey", "zubat"),
    isPokedexOpen = true,
    dexSeenCount = 4,
    dexCaughtCount = 2,
    dexEntries = listOf(
        PokedexRow(1, "bulbasaur", null, null, DexStatus.UNKNOWN),
        PokedexRow(4, "charmander", "Charmander", "Fuego", DexStatus.CAUGHT),
        PokedexRow(16, "pidgey", "Pidgey", "Volador", DexStatus.SEEN),
        PokedexRow(19, "rattata", "Rattata", "Normal", DexStatus.CAUGHT),
        PokedexRow(41, "zubat", "Zubat", "Veneno", DexStatus.SEEN),
        PokedexRow(61, "poliwhirl", "Poliwhirl", "Agua", DexStatus.UNKNOWN)
    )
)

/** Dex con catálogo cargado: muestra los tres estados de fila. */
@androidx.compose.ui.tooling.preview.Preview(
    name = "Pokédex · listado",
    device = DEX_PREVIEW_DEVICE,
    showSystemUi = true
)
@Composable
private fun DexScreenPreview() = MaterialTheme {
    DexScreen(state = dexPreviewState(), onBack = {})
}

/** Dex con la carga del catálogo fallida: mensaje y vuelta al juego. */
@androidx.compose.ui.tooling.preview.Preview(
    name = "Pokédex · error de catálogo",
    device = DEX_PREVIEW_DEVICE,
    showSystemUi = true
)
@Composable
private fun DexErrorPreview() = MaterialTheme {
    DexScreen(
        state = dexPreviewState().copy(dexLoadFailed = true, dexEntries = emptyList()),
        onBack = {}
    )
}
