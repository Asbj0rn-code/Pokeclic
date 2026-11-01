/**
 * Editor visual de zonas de Kanto (solo compilaciones debug).
 *
 * Vive en el source set `debug`, junto al manifiesto que registra la
 * actividad (ambos desaparecen del artefacto release). Se abre desde el
 * pequeño botón de desarrollo del mapa a través de la acción
 * [com.dsrs.pokeclic.dbg.ACTION_OPEN_MAP_EDITOR] y se cierra con su botón
 * visible.
 *
 * En la subetapa 4A solo hospeda la pantalla de visualización
 * ([MapEditorScreen]); las herramientas de trazado y edición llegarán después.
 */
package com.dsrs.pokeclic.dbg

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme

class MapEditorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                MapEditorScreen(onClose = ::finish)
            }
        }
    }
}
