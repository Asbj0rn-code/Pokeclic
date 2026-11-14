/**
 * Carcasa visual del mapa (issue #57): cabecera, marco NinePatch, módulo
 * inferior y base del menú, separadas del diseño aprobado de 910 × 1728 px.
 *
 * Es **solo presentación**: no toca el estado del juego, no calcula nada de
 * negocio y no lleva manejo de toques — el mapa se compone debajo, exactamente
 * donde estaba (mismo tamaño, misma posición y mismos gestos), y se ve a
 * través de la apertura transparente del marco.
 *
 * Estructura (de arriba a abajo), con el patrón ya probado en la carcasa de la
 * Pokédex (`DexScreen`):
 * - **Cabecera** (`mapa_cabecera.webp`, 910×138 px): franja superior con tres
 *   luces verdes. Escalada proporcionalmente al ancho disponible; su alto es
 *   `ancho × 138/910` y nunca se estira de forma independiente.
 * - **Marco** (`mapa_marco.9.png`, 912×1256 px, incluye un borde técnico de
 *   1 px): el único elemento extensible. Se carga como `NinePatchDrawable`
 *   mediante los recursos de Android (`painterResource` no soporta NinePatch)
 *   y se dibuja estirando solo los parches declarados, con esquinas y luces
 *   intactas. Su apertura central tiene alfa real: el mapa se ve por ella y
 *   el negro que se aprecia en algunos visores es solo su fondo de
 *   visualización.
 * - **Módulo inferior** (`mapa_modulo_inferior.webp`, 910×114 px): franja
 *   naranja/amarilla bajo el mapa, con su proporción nativa.
 * - **Base del menú** (`mapa_base_menu.webp`, 910×222 px): fondo amarillo sin
 *   controles dibujados, detrás del espacio que ocupa el menú de la
 *   aplicación. El menú y la Pokéball reales los compone el `Scaffold`
 *   encima (no se pintan botones). Como es una superficie amarilla, se
 *   rellena con recorte (Crop) centrado —preferible a deformar los módulos—
 *   sobre un respaldo del mismo amarillo medido del asset, que evita huecos
 *   mientras carga y en los bordes recortados.
 *
 * Los insets del sistema no se aplican aquí: el contenedor que la carcasa
 * recibe de `AppRoot` ya los lleva aplicados una sola vez, y la altura de la
 * zona de navegación ([bottomReserved], que incluye el volumen de la Pokéball
 * que sobresale por arriba) se lo cede el shell. No hay márgenes contados dos
 * veces.
 *
 * Los controles flotantes del mapa (indicador de zonas, restaurar vista y
 * editor de desarrollo) se componen **encima** de la carcasa (ver el slot
 * `frameOverlay` de [MapScreen]), así que siguen visibles y operativos.
 */
package com.dsrs.pokeclic.ui

import android.graphics.drawable.NinePatchDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dsrs.pokeclic.R
import androidx.compose.material3.MaterialTheme

/**
 * Amarillo de la carcasa, muestreado del propio asset (`#FDC702`, común a
 * cabecera, módulo y base). Respaldo de la base del menú mientras carga y
 * relleno de los bordes que el recorte deja fuera; nunca enseña blanco.
 */
private val MapCarcasaYellow = Color(0xFFFDC702)

/** Proporciones nativas de las piezas fijas (ancho ÷ alto en px del asset). */
private const val MAP_HEADER_ASPECT = 910f / 138f
private const val MAP_BOTTOM_MODULE_ASPECT = 910f / 114f

/**
 * Pestaña Mapa con su carcasa: compone el [MapScreen] **intacto** (mismo
 * tamaño, misma posición y funcionalidad completa) y le superpone el marco
 * como capa visual entre el mapa y sus controles flotantes; bajo el hueco del
 * menú dibuja la base amarilla, sobre la que el `Scaffold` compone el menú
 * real. Sin estado: solo compone, no decide nada.
 */
@Composable
fun MapCarcasaScreen(bottomReserved: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        // El mapa sigue mandando: se compone primero y ocupa todo el área
        // disponible. La carcasa entra por el slot `frameOverlay`, entre el
        // mapa y sus controles flotantes.
        MapScreen(
            bottomReserved = bottomReserved,
            frameOverlay = { MapCarcasaFrame() }
        )

        // Zona de navegación: base amarilla detrás del menú real (alto
        // derivado: contenedor del menú + franja de navegación del sistema,
        // con el volumen de la Pokéball incluido).
        if (bottomReserved > 0.dp) {
            MapCarcasaBaseMenu(
                bottomReserved = bottomReserved,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * Cabecera + marco + módulo inferior, en columna. Se compone dentro del área
 * visible del mapa (ver [MapScreen.frameOverlay]), así que el módulo termina
 * justo donde empieza la base del menú y no hay costuras ni solapes.
 *
 * El marco se dibuja en `drawBehind` de la caja central: su centro es
 * transparente y el mapa, que está debajo, se ve por la apertura. No consume
 * gestos: cualquier toque pasa al mapa.
 */
@Composable
private fun MapCarcasaFrame() {
    val context = LocalContext.current
    // Marco NinePatch. `nodpi`: los caps fijos se dibujan 1:1 en px nativos y
    // solo el centro se estira hasta las medidas del layout. Instancia propia
    // de este contenedor (no se comparte una instancia mutable).
    val frame = remember(context) {
        ContextCompat.getDrawable(context, R.drawable.mapa_marco) as? NinePatchDrawable
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1 · Cabecera: franja superior con las tres luces verdes. Ocupa su
        // proporción nativa respecto al ancho y no se estira aparte.
        Image(
            painter = painterResource(R.drawable.mapa_cabecera),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(MAP_HEADER_ASPECT)
        )

        // 2 · Zona central: el marco NinePatch ocupa todo el hueco restante.
        // La apertura es transparente: el mapa de debajo se ve por ella, en
        // su posición de siempre, sin reescalarse.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .drawBehind {
                    if (frame != null && size.width >= 1f && size.height >= 1f) {
                        frame.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                        frame.draw(drawContext.canvas.nativeCanvas)
                    }
                }
        )

        // 3 · Módulo inferior: franja naranja/amarilla bajo el mapa, con su
        // proporción nativa. Ancho común con la cabecera: no hay costuras.
        Image(
            painter = painterResource(R.drawable.mapa_modulo_inferior),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(MAP_BOTTOM_MODULE_ASPECT)
        )
    }
}

/**
 * Base del menú: fondo amarillo sin controles dibujados, detrás del espacio
 * reservado al menú (incluida la franja de navegación del sistema y el volumen
 * de la Pokéball que sobresale por arriba). El menú real lo compone el
 * `Scaffold` encima, como componente interactivo que es.
 */
@Composable
private fun MapCarcasaBaseMenu(bottomReserved: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(bottomReserved)
            .background(MapCarcasaYellow)
    ) {
        Image(
            painter = painterResource(R.drawable.mapa_base_menu),
            contentDescription = null,
            // Recorte centrado: es una superficie amarilla y el recorte es
            // preferible a deformar los módulos detallados del asset.
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(
    name = "Carcasa del mapa",
    device = "spec:width=411dp,height=914dp,dpi=420",
    showBackground = true
)
@Composable
private fun MapCarcasaScreenPreview() {
    MaterialTheme {
        // Alto de reserva equivalente al del shell real en previews
        // (`CONTAINER_HEIGHT` + franja de navegación), como en la Pokédex.
        MapCarcasaScreen(bottomReserved = BottomMenuDefaults.CONTAINER_HEIGHT + 24.dp)
    }
}
