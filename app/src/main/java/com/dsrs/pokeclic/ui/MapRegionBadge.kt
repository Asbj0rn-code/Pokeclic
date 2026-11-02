/**
 * Indicador discreto del nombre de la zona pulsada en el mapa de Kanto.
 *
 * Se compone en la esquina superior derecha del **área visible** de
 * [MapScreen] (fase de gestos y transformación, no de la imagen), de modo que
 * permanece **fijo en pantalla** sea cual sea el zoom o el desplazamiento del
 * mapa: ni se mueve con la imagen ni queda detrás del bottom menu.
 *
 * Es puramente presentación: no sabe de zonas, tipos ni catálogo; recibe el
 * nombre ya resuelto. Sin selección no se compone (la llamante gestiona el
 * `null`), así que el estado vacío es literalmente ausencia.
 *
 * Estética deliberadamente discreta, más parecida al botón de restauración
 * que a un HUD de guerra: fondo blanco translúcido, esquinas poco redondeadas
 * y tipografía monoespaciada del chasis.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Muestra [regionName] pegado a la esquina superior derecha.
 *
 * @param modifier anclado por la llamante (normalmente `Alignment.TopEnd`
 *   dentro del área visible del mapa).
 */
@Composable
internal fun MapRegionBadge(regionName: String, modifier: Modifier = Modifier) {
    Text(
        text = regionName,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .padding(top = 12.dp, end = 12.dp)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}
