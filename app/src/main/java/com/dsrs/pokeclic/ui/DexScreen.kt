/**
 * Pantalla de la Pokédex con la **carcasa adaptable**: cabecera, marco
 * NinePatch y base roja, más el respaldo a pantalla completa para evitar
 * franjas de otro color.
 *
 * Estructura (de arriba a abajo):
 * - **Cabecera** (`pokedex_cabecera.webp`, 941×280 px): imagen del chasis
 *   escalada proporcionalmente al ancho disponible; su alto es
 *   `ancho × 280/941` y nunca se estira de forma independiente. El asset no
 *   trae controles dibujados: los contadores «Avistados/Capturados» se
 *   superponen como componentes reales sobre la zona plana roja derecha. La
 *   flecha de vuelta y el título desaparecieron: la vuelta la ofrece el menú
 *   inferior real y el botón físico (`BackHandler`).
 * - **Marco** (`pokedex_marco.9.png`, 943×1106 px): el único elemento
 *   extensible. Se carga como `NinePatchDrawable` mediante los recursos de
 *   Android (`painterResource` no soporta NinePatch) y se dibuja estirando
 *   solo los parches declarados, sin tocar esquinas ni luces. El centro del
 *   asset es transparente; detrás se compone la **pantalla LCD** oscura, y la
 *   lista vive **solo** dentro del interior declarado por el propio
 *   NinePatch (`getPadding`, marcadores inferior y derecho). Sin lista
 *   residual sobre la cabecera ni bajo la base.
 * - **Base** (`pokedex_base.webp`, 941×288 px): fondo rojo sin controles,
 *   dibujado detrás del espacio que ocupa el menú de la aplicación. Como no
 *   contiene círculos ni botones, se ajusta (estira) a la altura reservada.
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
 * `pokedexSprite` (mapa de `PokedexSprites.kt`). Se recorta el margen
 * transparente del PNG (`opaqueBounds`) y la figura se normaliza para llenar
 * el recuadro fijo (88×72 dp): en una lista la figura es un identificador y
 * prima que se lea; el tamaño relativo entre especies se conserva en la
 * escena de captura. Se dibuja con `FilterQuality.None` (pixel art sin
 * interpolación). El estado CAUGHT lo marca la posesión de la run
 * (`ownedSpecies` / `starterSpeciesId`), no el historial permanente.
 */
package com.dsrs.pokeclic.ui

import android.graphics.drawable.NinePatchDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.DexStatus
import com.dsrs.pokeclic.model.PokedexRow
import com.dsrs.pokeclic.viewmodel.GameUiState
import java.util.Locale
import kotlin.math.min

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

/**
 * Rojo de la carcasa: respaldo a pantalla completa detrás de cabecera, marco
 * y base, para que nunca se vean franjas de otro color en los huecos. Es el
 * color medido del propio asset (el LEEME admite `#FA1B1C`); no es el
 * `RetroColors.PokedexRed` del HUD, que no coincide con la carcasa.
 */
private val DexCarcasaRed = Color(0xFFFA1B1C)

/** Proporción nativa de la cabecera (ancho ÷ alto en px del asset). */
private const val DEX_HEADER_ASPECT = 941f / 280f

/** Texto principal sobre la pantalla LCD: verde pálido como el HUD. */
private val DexLcdText = RetroColors.LcdText

/** Texto secundario del LCD (número de fila y etiquetas atenuadas). */
private val DexLcdTextDim = RetroColors.LcdText.copy(alpha = 0.72f)

/** Línea fina de los visores y divisor entre filas sobre la LCD. */
private val DexLcdLine = Color(0xFF4E5A66)

/** Divisor fino entre filas dentro de la pantalla LCD. */
private val DexRowDivider = DexLcdLine.copy(alpha = 0.35f)

/** Recuadro del sprite en la fila (ancho × alto). */
private val DexSpriteSlotWidth = 88.dp
private val DexSpriteSlotHeight = 72.dp

/** Métricas de los contadores superpuestos en la cabecera. */
private val DexHeaderValueSize = 16.sp
private val DexHeaderValueLine = 19.sp
private val DexHeaderLabelSize = 10.sp
private val DexHeaderLabelLine = 12.sp

/**
 * Pantalla de la Pokédex. Sin estado: recibe el [DexUiState] ya derivado
 * (filas, contadores y flag de error), el espacio que la aplicación reserva
 * abajo para el menú ([bottomReserved]) y notifica la vuelta con [onBack]. El
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
    bottomReserved: Dp = 0.dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Atrás físico y de gesto: siempre vuelve al juego. Sin flecha en
    // pantalla: la vuelta visual la ofrece el menú inferior real.
    BackHandler(enabled = true) { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Respaldo rojo a pantalla completa: cabecera, marco y base tienen
            // variaciones propias del original; el hueco entre piezas nunca
            // debe enseñar blanco.
            .background(DexCarcasaRed)
            .statusBarsPadding()
    ) {
        // Cabecera del dispositivo: imagen del chasis con los contadores
        // superpuestos. Ocupa su proporción nativa y no se estira aparte.
        DexHeader(
            seenCount = state.seenCount,
            caughtCount = state.caughtCount
        )

        if (state.loadFailed) {
            DexError(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp))
        } else {
            // Marco extensible con la pantalla LCD y la lista dentro: recibe
            // toda la altura que no se llevan la cabecera ni el menú.
            DexList(
                entries = state.entries,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }

        // Zona del menú: la base roja se dibuja detrás del espacio reservado
        // (menú + franja de navegación del sistema). El menú en sí lo compone
        // el llamante encima de esta área, como componente interactivo real.
        if (bottomReserved > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomReserved)
            ) {
                Image(
                    painter = painterResource(R.drawable.pokedex_base),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Cabecera de la carcasa: la imagen del chasis escalada al ancho disponible
 * con su proporción nativa (941×280), nunca con la altura independiente. El
 * asset no lleva controles: los contadores «Avistados/Capturados» se
 * componen encima, en la mitad derecha de la cabecera a medio camino entre
 * el centro y el borde inferior (sesgo vertical 0.5), envueltos en una
 * vitrina LCD estilo
 * HUD: el mismo `RetroLcdPanel` del menú, con su fondo profundo, doble borde
 * y scanlines. Sin `fill`: la vitrina envuelve su contenido, no la cabecera.
 */
@Composable
private fun DexHeader(seenCount: Int, caughtCount: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(DEX_HEADER_ASPECT)
    ) {
        Image(
            painter = painterResource(R.drawable.pokedex_cabecera),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        RetroLcdPanel(
            modifier = Modifier
                .align(BiasAlignment(horizontalBias = 1f, verticalBias = 0.5f))
                .padding(end = 10.dp),
            shadowOffset = 0.dp,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DexStatBlock(count = seenCount, labelRes = R.plurals.dex_stat_seen)
                Spacer(modifier = Modifier.width(14.dp))
                DexStatBlock(count = caughtCount, labelRes = R.plurals.dex_stat_caught)
            }
        }
    }
}

/**
 * Bloque de estadística de la vitrina HUD: cifra Monospace arriba y etiqueta
 * pluralizada debajo, en la misma celda. Paleta de pantalla LCD: cifra en
 * verde pálido de alto contraste y etiqueta atenuada, como el resto del HUD.
 */
@Composable
private fun DexStatBlock(count: Int, labelRes: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier.widthIn(min = 56.dp)
    ) {
        Text(
            text = count.toString(),
            color = DexLcdText,
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
            color = DexLcdTextDim,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = DexHeaderLabelSize,
            lineHeight = DexHeaderLabelLine,
            maxLines = 1
        )
    }
}

/**
 * Listado dentro del marco de la carcasa. El marco se carga como
 * `NinePatchDrawable` por los recursos de Android —`painterResource` no
 * soporta NinePatch— y se dibuja estirando solo los parches declarados, con
 * las esquinas y luces del bezel intactas. El interior lo declara el propio
 * NinePatch (`getPadding`, marcadores inferior y derecho): la pantalla LCD
 * oscura y la lista viven exactamente ahí, así que **no hay lista residual
 * sobre la cabecera ni bajo la base**, y ninguna fila toca el borde.
 */
@Composable
private fun DexList(entries: List<PokedexRow>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // Marco NinePatch. `nodpi`: los caps fijos se dibujan 1:1 en px nativos y
    // solo el centro se estira hasta las medidas del layout.
    val frame = remember(context) {
        ContextCompat.getDrawable(context, R.drawable.pokedex_marco) as? NinePatchDrawable
    }
    // Interior declarado por el asset: (56, 24, 56, 25) px nativos, leídos del
    // drawable y convertidos a dp con la densidad real del dispositivo.
    // `getPadding(Rect)` es método Java: rellena el rectángulo que se le pasa.
    val interiorPadding = remember(frame, density) {
        if (frame == null) {
            PaddingValues(0.dp)
        } else {
            val p = android.graphics.Rect()
            frame.getPadding(p)
            with(density) {
                PaddingValues(
                    start = p.left.toDp(),
                    top = p.top.toDp(),
                    end = p.right.toDp(),
                    bottom = p.bottom.toDp()
                )
            }
        }
    }

    Box(
        modifier = modifier.drawBehind {
            if (frame != null && size.width >= 1f && size.height >= 1f) {
                frame.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                frame.draw(drawContext.canvas.nativeCanvas)
            }
        }
    ) {
        // Pantalla LCD: superficie oscura ajustada al interior real del marco
        // (el centro del asset es transparente; el rojo de la carcasa queda
        // fuera, tras el bezel). La lista no puede salirse de aquí.
        Box(
            modifier = Modifier
                .padding(interiorPadding)
                .background(RetroColors.LcdDark)
                .fillMaxSize()
        ) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(
                    entries,
                    key = { _, entry -> entry.dexNumber }
                ) { index, entry ->
                    DexRow(entry, showTopDivider = index > 0)
                }
            }
        }
    }
}

/**
 * Fila de error de catálogo: mensaje sobre el panel crema clásico (superficie
 * distinta del chasis, para que el fallo se distinga a simple vista) sin
 * bloquear el juego: el resto sigue funcionando y la vuelta está disponible
 * con el botón físico y el menú. La anchura queda limitada para no estirar en
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
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = RetroColors.InkBlack
                )
                Text(
                    text = stringResource(R.string.dex_error_body),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = RetroColors.InkBlack.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * Una fila del listado dentro de la pantalla LCD, en el orden pactado:
 * número → sprite en recuadro fijo → nombre exterior y tipo → Pokéball.
 * El divisor de 1 dp se dibuja en la propia fila (no como item aparte), así
 * que el `LazyColumn` solo tiene una entrada por especie.
 */
@Composable
private fun DexRow(entry: PokedexRow, showTopDivider: Boolean) {
    val known = entry.status != DexStatus.UNKNOWN
    val registered = entry.status == DexStatus.REGISTERED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (showTopDivider) {
                    drawRect(
                        color = DexRowDivider,
                        size = Size(size.width, 1.dp.toPx())
                    )
                }
            }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = String.format(Locale.ROOT, "%03d", entry.dexNumber),
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
 * Recuadro fijo del sprite: una misma geometría en todas las filas y estados
 * (ancho 88.dp, alto 72.dp); su tamaño no depende de la especie ni del estado,
 * y el sprite no modifica ni el recuadro ni la altura de la fila. La resolución
 * del recurso es **directa por número**: entrada n → `pokedexSprite(dexNumber)`,
 * siempre por dato de la fila y nunca por posición o índice desplazado.
 *
 * - **UNKNOWN** (y `null` del mapa en especie conocida) → «?» pale green
 *   centrada de alto contraste, sin ningún texto accesible que revele la
 *   identidad en UNKNOWN (el número es el único dato de la fila).
 * - **SEEN/REGISTERED/CAUGHT** → se recorta el margen transparente del PNG
 *   (`opaqueBounds`) y la figura se escala para **llenar el recuadro**, igual
 *   para todas las especies. Es la diferencia con la escena de captura, donde
 *   se preserva el tamaño relativo: en una lista de 151 filas la figura es un
 *   identificador y se busca que se lea, no comparar tamaños. Se dibuja con
 *   `FilterQuality.None` para no emborronar el pixel art; el estado lo marca
 *   la Pokéball.
 */
@Composable
private fun DexSpriteSlot(entry: PokedexRow, known: Boolean) {
    val spriteRes = if (known) pokedexSprite(entry.dexNumber) else null
    Box(
        modifier = Modifier
            .width(DexSpriteSlotWidth)
            .height(DexSpriteSlotHeight)
            .border(1.dp, DexLcdLine, RoundedCornerShape(6.dp))
            .background(RetroColors.BarTrack, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (spriteRes != null) {
            val image = ImageBitmap.imageResource(spriteRes)
            val bounds = remember(image) { image.opaqueBounds() }
            Canvas(modifier = Modifier.padding(2.dp).fillMaxSize()) {
                if (bounds.width <= 0 || bounds.height <= 0) return@Canvas
                val scale = min(size.width / bounds.width, size.height / bounds.height)
                val dstW = bounds.width * scale
                val dstH = bounds.height * scale
                drawImage(
                    image = image,
                    srcOffset = IntOffset(bounds.left, bounds.top),
                    srcSize = IntSize(bounds.width, bounds.height),
                    dstOffset = IntOffset(
                        ((size.width - dstW) / 2f).toInt(),
                        ((size.height - dstH) / 2f).toInt()
                    ),
                    dstSize = IntSize(dstW.toInt(), dstH.toInt()),
                    filterQuality = FilterQuality.None
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
 * Espacio de menú de los previews: el contenedor del menú real más una franja
 * de navegación de gesto orientativa. En la app el valor lo calcula el
 * llamante con `WindowInsets`; aquí solo da proporción al preview.
 */
private val DEX_PREVIEW_BOTTOM_RESERVED = BottomMenuDefaults.CONTAINER_HEIGHT + 24.dp

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

/** Dex con carcasa completa: cabecera, marco, base y los cuatro estados. */
@androidx.compose.ui.tooling.preview.Preview(
    name = "Pokédex · carcasa",
    device = DEX_PREVIEW_DEVICE,
    showSystemUi = true
)
@Composable
private fun DexScreenPreview() = MaterialTheme {
    DexScreen(
        state = dexPreviewState(),
        bottomReserved = DEX_PREVIEW_BOTTOM_RESERVED,
        onBack = {}
    )
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
        bottomReserved = DEX_PREVIEW_BOTTOM_RESERVED,
        onBack = {}
    )
}
