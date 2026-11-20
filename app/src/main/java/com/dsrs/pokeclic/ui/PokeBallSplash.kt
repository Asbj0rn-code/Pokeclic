/**
 * Cortinilla de arranque animada (dos mitades de Pokéball que se separan).
 *
 * Es puramente visual: su temporización no afecta al juego y avisa al llamante
 * cuando termina. No conoce el ViewModel ni persiste nada.
 *
 * El nodo raíz aplica `Modifier.modalBlocking()` (issue #21): durante la
 * cortinilla ningún toque llega al juego que se está revelando; en `AppRoot`,
 * además, no se compone el menú mientras la cortinilla está visible.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.dsrs.pokeclic.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch



private const val SPLASH_HOLD_MS = 800L
private const val SPLASH_SLIDE_MS = 1000

/** Fundido de entrada del logo sobre la mitad superior. */
private const val SPLASH_LOGO_FADE_MS = 300

/** Ancho del logo como fracción del ancho de pantalla. */
private const val SPLASH_LOGO_WIDTH_FRACTION = 0.32f

/**
 * Borde superior del logo como fracción de la altura de pantalla. Deja el logo
 * dentro del campo rojo y por encima del aro de la Pokébola incluso en las
 * pantallas más cortas (16:9), donde el recorte desplaza el arte hacia arriba.
 */
private const val SPLASH_LOGO_TOP_FRACTION = 0.09f

/**
 * Cortinilla de arranque: dos imágenes a pantalla completa que juntas cubren la
 * pantalla. La superior ([R.drawable.pokeball_splash_top], con su parte visual
 * arriba) sube hasta salir por el borde superior, y la inferior
 * ([R.drawable.pokeball_splash_bottom], visual abajo) baja hasta salir por el
 * inferior, dejando ver la app.
 *
 * Encima del campo rojo de la mitad superior aparece el logo del juego
 * ([R.drawable.pokeclic_logo]) con un fundido corto; viaja con esa mitad, así
 * que sale de pantalla junto a ella.
 *
 * Se apoya en `graphicsLayer`, así que la animación solo invalida esas capas y no
 * recompone el resto. Al terminar, avisa con [onFinished] para que el llamante
 * deje de dibujarla.
 *
 * @param onFinished se invoca una vez terminada la animación de salida.
 */
@Composable
fun PokeBallSplash(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(true) }
    val progress = remember { Animatable(0f) }
    val logoAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // El fundido corre en paralelo a la espera: no alarga la cortinilla.
        launch { logoAlpha.animateTo(1f, animationSpec = tween(SPLASH_LOGO_FADE_MS)) }
        delay(SPLASH_HOLD_MS)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = SPLASH_SLIDE_MS,
                easing = FastOutSlowInEasing
            )
        )
        visible = false
        onFinished()
    }

    if (!visible) return

    BoxWithConstraints(modifier = modifier.fillMaxSize().modalBlocking()) {
        // Recorrido en px: la altura de la pantalla basta para sacar la imagen
        // (que ocupa toda la pantalla) por completo.
        val travel = constraints.maxHeight.toFloat()

        Image(
            painter = painterResource(R.drawable.pokeball_splash_top),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = -travel * progress.value }
        )

        Image(
            painter = painterResource(R.drawable.pokeball_splash_bottom),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = travel * progress.value }
        )

        // El logo viaja con la mitad superior: mismo desplazamiento, así que sale
        // por arriba con ella en vez de quedarse flotando sobre el juego.
        val logoWidth = maxWidth * SPLASH_LOGO_WIDTH_FRACTION
        Image(
            painter = painterResource(R.drawable.pokeclic_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(logoWidth)
                .offset(
                    x = (maxWidth - logoWidth) / 2f,
                    y = maxHeight * SPLASH_LOGO_TOP_FRACTION
                )
                .graphicsLayer {
                    translationY = -travel * progress.value
                    alpha = logoAlpha.value
                }
        )
    }
}
