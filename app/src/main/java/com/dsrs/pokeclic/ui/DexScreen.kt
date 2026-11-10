/**
 * Pantalla de la Pokédex con estética de dispositivo: carcasa roja plana,
 * **franja de estado compacta** de rojo oscuro y **una única pantalla LCD**
 * oscura y redondeada que ocupa el resto. Todo con los tokens compartidos
 * (`RetroTheme`).
 *
 * Reglas de la información (estados):
 * - **UNKNOWN**: número y recuadro con «?» pale green de alto contraste; sin
 *   sprite, nombre, tipo ni Pokéball (y sin textos de accesibilidad que
 *   revelen identidad).
 * - **SEEN**: sprite completo y a color del Pokémon, nombre independiente
 *   fuera del recuadro, tipo debajo del nombre y **Pokéball gris tenue**
 *   (escala de grises, opacidad 0.35): especie conocida que no está poseída
 *   en la partida actual.
 * - **REGISTERED**: mismo sprite, con **Pokéball sin color a opacidad plena**
 *   y la etiqueta «Registrado» bajo el tipo: capturada en una run anterior y
 *   no poseída ahora (es el estado que sobrevive al reset).
 * - **CAUGHT**: mismo sprite, con Pokéball a color y opacidad plena: especie
 *   poseída en la partida actual o inicial elegido. La distinción no depende
 *   solo del color: cada Pokéball lleva su descripción accesible y REGISTERED
 *   se nombra en la fila.
 *
 * **Sprites reales**: cada fila resuelve su recurso por `dexNumber` con
 * `pokedexSprite` (mapa de `PokedexSprites.kt`) y se presenta el **canvas
 * oficial completo** de 96×96, centrado y a escala (`ContentScale.Fit`) en un
 * recuadro fijo (76×64 dp). Decisión de diseño: no se recorta el margen
 * transparente ni se normaliza el tamaño por especie, para preservar la
 * proporción relativa entre Pokémon; el filtro es `FilterQuality.None`
 * (`PixelImage`) porque el canvas se reescala y el pixel art no admite
 * interpolación bilineal. El estado CAUGHT lo marca la posesión de la run
 * (`ownedSpecies` / `starterSpeciesId`), no el historial permanente.
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.ContentScale
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
 * Pantalla de la Pokédex. Sin estado: recibe el [DexUiState] ya derivado
 * (filas, contadores y flag de error) y notifica la vuelta con [onBack]. El
 * botón físico «Atrás» también vuelve, con `BackHandler`.
 */
/**
 * Estado mínimo que consume la pantalla de Pokédex: filas y contadores ya
 * resueltos, más el flag de error de carga. Desacopla la UI del estado
 * completo del juego y mantiene los previews chicos.
 */
data class DexUiState(
    val entries: List<PokedexRow> = emptyList(),
    val seenCount: Int = 0,
    val caughtCount: Int = 0,
    val loadFailed: Boolean = false
)

/** Adapta el estado del juego a lo que la pantalla necesita. */
fun GameUiState.toDexUiState(): DexUiState = DexUiState(
    entries = dexEntries,
    seenCount = dexSeenCount,
    caughtCount = dexCaughtCount,
    loadFailed = dexLoadFailed
)

@Composable
fun DexScreen(
    state: DexUiState,
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
            seenCount = state.seenCount,
            caughtCount = state.caughtCount,
            onBack = onBack,
            modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
        )

        if (state.loadFailed) {
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
                        state.entries,
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
 * número → sprite en recuadro fijo → nombre exterior y tipo → Pokéball.
 */
@Composable
private fun DexRow(entry: PokedexRow) {
    val known = entry.status != DexStatus.UNKNOWN
    val registered = entry.status == DexStatus.REGISTERED

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

        DexSpriteSlot(entry, known)

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
                if (registered) {
                    // Distinción no cromática: el estado histórico se nombra.
                    Text(
                        text = stringResource(R.string.dex_registered),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = DexLcdTextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Hueco fijo del indicador: la Pokéball nunca desplaza al nombre ni a
        // la interrogación, y ocupa la misma posición en los tres estados
        // conocidos.
        if (known) {
            BallIndicator(
                status = entry.status,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Indicador de posesión (marcador en fila), con tres variantes:
 * - **CAUGHT**: Pokéball a color y opacidad plena.
 * - **REGISTERED**: Pokéball sin color y opacidad plena (capturada antes,
 *   no poseída ahora).
 * - **SEEN**: Pokéball sin color y atenuada.
 *
 * La distinción no depende solo del color: cada variante lleva su descripción
 * accesible y REGISTERED además se nombra en la fila.
 */
@Composable
private fun BallIndicator(status: DexStatus, modifier: Modifier = Modifier) {
    val caught = status == DexStatus.CAUGHT
    val registered = status == DexStatus.REGISTERED
    val grayscale = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    Image(
        painter = painterResource(R.drawable.pokeball_menu),
        contentDescription = stringResource(
            when (status) {
                DexStatus.CAUGHT -> R.string.dex_caught_desc
                DexStatus.REGISTERED -> R.string.dex_registered_desc
                else -> R.string.dex_seen_desc
            }
        ),
        modifier = modifier
            .size(22.dp)
            .then(if (caught || registered) Modifier else Modifier.alpha(0.35f)),
        colorFilter = if (caught) null else grayscale
    )
}

/**
 * Recuadro **fijo** del sprite: una misma geometría en todas las filas y
 * estados (ancho 76.dp, alto 64.dp); su tamaño no depende de la especie ni
 * del estado, y el sprite no modifica ni el recuadro ni la altura de la
 * fila. La resolución del recurso es **directa por número**: entrada n →
 * `pokedexSprite(entry.dexNumber)`, siempre por dato de la fila y nunca por
 * posición o índice desplazado.
 *
 * - **UNKNOWN** (y `null` del mapa en especie conocida) → «?» pale green
 *   centrada de alto contraste, sin ningún texto accesible que revele la
 *   identidad en UNKNOWN (el número es el único dato de la fila).
 * - **SEEN/REGISTERED/CAUGHT** → **canvas oficial completo** de 96×96,
 *   centrado, a escala (`ContentScale.Fit`) y con margen de 4.dp. No se
 *   recorta el margen transparente ni se normaliza el tamaño por especie:
 *   se preserva la proporción relativa entre Pokémon (el canvas oficial ya
 *   la define). Se dibuja con `PixelImage` (`FilterQuality.None`) porque es
 *   pixel art reescalado y la interpolación bilineal emborrona los bordes.
 *   La opacidad es plena en los tres estados; el estado lo marca la Pokéball.
 */
@Composable
private fun DexSpriteSlot(entry: PokedexRow, known: Boolean) {
    val spriteRes = if (known) pokedexSprite(entry.dexNumber) else null
    Box(
        modifier = Modifier
            .width(76.dp)
            .height(64.dp)
            .border(1.dp, DexLcdLine, RoundedCornerShape(6.dp))
            .background(RetroColors.BarTrack, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (spriteRes != null) {
            PixelImage(
                res = spriteRes,
                // El nombre exterior de la fila es la descripción accesible:
                // el sprite no añade ni repite texto.
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxSize()
            )
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
 * estado vacío: el preview debe enseñar los cuatro estados de fila.
 */
private fun dexPreviewState(): DexUiState = DexUiState(
    seenCount = 4,
    caughtCount = 2,
    entries = listOf(
        PokedexRow(1, "bulbasaur", null, null, DexStatus.UNKNOWN),
        PokedexRow(4, "charmander", "Charmander", "Fuego", DexStatus.CAUGHT),
        PokedexRow(16, "pidgey", "Pidgey", "Volador", DexStatus.SEEN),
        PokedexRow(19, "rattata", "Rattata", "Normal", DexStatus.REGISTERED),
        PokedexRow(41, "zubat", "Zubat", "Veneno", DexStatus.SEEN),
        PokedexRow(61, "poliwhirl", "Poliwhirl", "Agua", DexStatus.UNKNOWN)
    )
)

/** Dex con catálogo cargado: muestra los cuatro estados de fila. */
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
        state = dexPreviewState().copy(loadFailed = true, entries = emptyList()),
        onBack = {}
    )
}
