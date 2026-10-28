/**
 * Aislamiento de input para capas modales (issue #21 / FUN-01 de `ISSUES.md`).
 *
 * Toda capa modal que se dibuje sobre el juego debe ser la única receptora de
 * toques de la pantalla: los toques que no vayan dirigidos a un control propio
 * de la capa se quedan en la capa y no llegan a la UI del juego de debajo.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Consume **todos** los eventos de puntero que caen sobre este nodo (apoyos,
 * movimientos, levantamientos y gestos compuestos como arrastres), de modo que
 * ningún toque atraviese la capa y active la UI del juego de debajo.
 *
 * El consumo se hace en el pase `Main` **después** de que los controles propios
 * de la capa (hijos del nodo al que se aplica) hayan recibido cada evento, así
 * que la capa sigue operable con normalidad: un toque sobre la caja de diálogo,
 * un campo de texto o un botón suyos funciona igual que sin el modificador.
 *
 * Cómo funciona: los detectores de Compose (`clickable`, `detectTapGestures`,
 * …) comprueban el estado de consumo y abortan si el evento ya lo está. Los
 * hermanos dibujados por debajo reciben los eventos después (el orden de pase
 * `Main` va de la capa superior hacia abajo), así que ver el evento consumido
 * los desactiva.
 *
 * Limitación conocida: un detector que **ignore** el consumo no se puede
 * bloquear así. Es el caso del gesto de la Pokéball del menú
 * (`awaitFirstDown(requireUnconsumed = false)`), razón por la que `AppRoot`
 * además deja de componer el menú mientras haya una capa modal.
 *
 * Regla de casa (issue #21): todo overlay modal nuevo debe aplicar este
 * modificador en su nodo raíz, y `AppRoot` debe ocultar bajo esa capa cualquier
 * superficie del juego cuyos detectores ignoren el consumo.
 */
fun Modifier.modalBlocking(): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false).consume()
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
            if (event.changes.all { !it.pressed }) break
        }
    }
}
