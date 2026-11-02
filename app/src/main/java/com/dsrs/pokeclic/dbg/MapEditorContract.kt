/**
 * Puente mínimo entre el juego y el editor visual de Kanto, exclusivo de
 * desarrollo.
 *
 * Todo lo que aquí se declara vive en `main` y, por tanto, existe en las
 * compilaciones debug **y** release, pero no contiene ninguna referencia a las
 * clases del editor (estas viven solo en el source set `debug`): la conexión
 * entre ambos es un `Intent` por acción, no un tipo. Así la aplicación de
 * producción compila y funciona sin conocer el editor.
 *
 * La apertura del editor tiene **triple barrera** en release:
 *
 * 1. El botón que la invoca solo se compone si [isMapEditorAvailable] marca la
 *    compilación como depurable (`FLAG_DEBUGGABLE`).
 * 2. [MapEditorActivity] solo existe en código y manifiesto del source set
 *    `debug`: no va dentro del artefacto release.
 * 3. [openMapEditor] ignora en silencio el caso de que ninguna actividad
 *    responda a la acción (`ActivityNotFoundException`).
 */
package com.dsrs.pokeclic.dbg

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

/**
 * Acción del `Intent` que abre el editor del mapa. La actividad que la
 * declara está registrada únicamente en el manifiesto de debug.
 */
const val ACTION_OPEN_MAP_EDITOR = "com.dsrs.pokeclic.dbg.action.OPEN_MAP_EDITOR"

/**
 * Indica si esta compilación puede abrir el editor: la marca de depurado
 * (`FLAG_DEBUGGABLE`) la decide el tipo de variante, no el compilador, así
 * que es la comprobación más fiable en tiempo de ejecución.
 */
fun Context.isMapEditorAvailable(): Boolean =
    applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

/**
 * Abre el editor visual del mapa en las compilaciones de desarrollo.
 *
 * En release es inofensiva: la barrera de [isMapEditorAvailable] corta la
 * llamada antes de componer el botón, la actividad no está en el manifiesto
 * del artefacto y, si aun así se disparara, [ActivityNotFoundException] se
 * absorbe sin que la app se entere.
 */
fun Context.openMapEditor() {
    if (!isMapEditorAvailable()) return
    try {
        startActivity(
            Intent(ACTION_OPEN_MAP_EDITOR)
                .setPackage(packageName)
        )
    } catch (_: ActivityNotFoundException) {
        // Sin editor en esta compilación: no hay nada que hacer.
    }
}
