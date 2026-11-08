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



private const val SPLASH_HOLD_MS = 800L
private const val SPLASH_SLIDE_MS = 1000

/**
 * Cortinilla de arranque: dos imágenes a pantalla completa que juntas cubren la
 * pantalla. La superior ([R.drawable.pokeball_splash_top], con su parte visual
 * arriba) sube hasta salir por el borde superior, y la inferior
 * ([R.drawable.pokeball_splash_bottom], visual abajo) baja hasta salir por el
 * inferior, dejando ver la app.
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

    LaunchedEffect(Unit) {
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
    }
}
