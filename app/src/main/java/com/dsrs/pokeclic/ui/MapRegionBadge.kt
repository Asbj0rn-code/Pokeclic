/**
 * Indicador informativo de la zona o zonas pulsadas en el mapa de Kanto.
 *
 * Se compone en la esquina superior derecha del **área visible** de
 * [MapScreen], de modo que permanece **fijo en pantalla** sea cual sea el
 * zoom o el desplazamiento del mapa. Sin selección no se compone (la
 * llamante gestiona el `null`), así que el estado vacío es literalmente
 * ausencia.
 *
 * Es puramente presentación: no sabe de zonas, tipos ni catálogo; recibe los
 * nombres ya resueltos.
 *
 * Estética: la misma del cuadro «Que comience el combate» de los gimnasios
 * ([CountdownBanner]): el marco biselado compartido [RetroPanel] (fondo
 * crema, doble borde y sombra dura) con la tipografía del chasis. La zona
 * destacada ([primary], ciudad o ruta) lleva `titleMedium` semibold y las
 * coexistentes ([others], especiales) `bodyMedium`, de menor tamaño, para
 * jerarquía visual clara sin diseñar nada nuevo.
 *
 * Cada línea es de una sola línea con el acortamiento de final para que
 * nombres largos no rompan el cuadro; la altura crece con el número de
 * ubicaciones coexistentes y la anchura la limita la llamante
 * ([MapBadgeMaxWidthFraction] del ancho del área visible).
 */
package com.dsrs.pokeclic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Respiro fijo del cuadro respecto a cada borde: se añade a los insets ya
 * consumidos por el `Scaffold` ([AppRoot]) y al recorte de pantalla (que el
 * `Scaffold` no descuenta), dentro del rango 12–16 dp de la mejora.
 */
internal val MapBadgeEdgeMargin: Dp = 16.dp

/**
 * Ancho máximo del cuadro, en fracción del área visible del mapa: cabe en
 * cualquier dispositivo y deja el resto del mapa utilizable.
 */
internal const val MapBadgeMaxWidthFraction = 0.7f

/**
 * Muestra la zona destacada [primary] y, debajo, las coexistentes en [others]
 * (puede ser vacío: solo la destacada).
 *
 * @param modifier anclado y limitado en anchura por la llamante (normalmente
 *   `Alignment.TopEnd` con los márgenes seguros, dentro del área visible).
 */
@Composable
internal fun MapRegionBadge(
    primary: String,
    others: List<String>,
    modifier: Modifier = Modifier
) {
    RetroPanel(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = RetroColors.InkBlack,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            others.forEach { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RetroColors.InkBlack,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
