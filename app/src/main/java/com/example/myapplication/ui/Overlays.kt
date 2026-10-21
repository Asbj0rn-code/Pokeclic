/**
 * Capas modales y pantallas auxiliares del shell: encuentro salvaje, selector de
 * Pokémon principal, hoja de opciones y placeholder del mapa.
 *
 * Todas son **sin estado**: reciben datos ya resueltos por el ViewModel y
 * notifican acciones con lambdas. En particular, el selector de principal recibe
 * `LeadOption` con id y nombre, así que no necesita conocer el catálogo.
 */
package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.model.Encounter
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import com.example.myapplication.model.LeadOption
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp



/**
 * Overlay del encuentro salvaje. En la Etapa 1 la captura se resuelve tocando la
 * Pokéball del menú inferior, así que aquí solo se informa del salvaje y de los
 * intentos que quedan. La Etapa 3 lo sustituye por el minijuego de pesca.
 */
@Composable
fun CaptureOverlay(
    encounter: Encounter,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.catch_title, encounter.speciesName),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.catch_type, encounter.speciesType.name),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.catch_hint, encounter.attemptsLeft),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}



/**
 * Selector de Pokémon principal. Ofrece las opciones ya resueltas ([options])
 * y marca la actual.
 *
 * Es **sin estado**: recibe los candidatos y la selección como datos, y avisa por
 * lambda.
 */
@Composable
fun LeadOverlay(
    options: List<LeadOption>,
    currentId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
            modifier = Modifier.padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.lead_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.lead_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(options, key = { it.id }) { option ->
                        LeadRow(
                            option = option,
                            selected = option.id == currentId,
                            onClick = { onSelect(option.id) }
                        )
                    }
                }

                OutlinedButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.lead_close))
                }
            }
        }
    }
}

@Composable
private fun LeadRow(option: LeadOption, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = {}, enabled = false) { Text(text = option.name) }
    } else {
        OutlinedButton(onClick = onClick) { Text(text = option.name) }
    }
}



/**
 * Menú de opciones como `ModalBottomSheet` de Material 3.
 *
 * Sustituye al antiguo `OptionsBottomSheetFragment`: al ser ya Compose puro, el
 * sheet se dibuja dentro del árbol de `AppRoot` y no necesita diálogo ni
 * `FragmentManager`. Su visibilidad la manda `GameUiState.isOptionsOpen`.
 *
 * @param onDismiss se invoca al cerrar (arrastre hacia abajo, toque fuera o
 *   botón atrás). El estado lo actualiza el `MainViewModel`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsSheet(onDismiss: () -> Unit) {
    // Sin estado intermedio: se abre directamente al contenido, como hacía el
    // fragment con STATE_EXPANDED.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        OptionsContent()
    }
}

/**
 * Contenido de la hoja de opciones. Placeholder: título, separador y dos filas
 * decorativas sin acción asociada.
 */
@Composable
fun OptionsContent() {
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Text(
            text = stringResource(R.string.options_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        HorizontalDivider()

        ListItem(
            headlineContent = { Text(stringResource(R.string.options_settings)) },
            leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.options_about)) },
            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}



/**
 * Pantalla de mapa. Ocupa todo el espacio del contenedor (debajo del bottom menu).
 * Placeholder: de momento sin contenido ni lógica.
 */
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.map_placeholder),
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MapScreenPreview() {
    MaterialTheme { MapScreen() }
}
