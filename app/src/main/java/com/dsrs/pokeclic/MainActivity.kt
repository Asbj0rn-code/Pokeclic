package com.dsrs.pokeclic

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dsrs.pokeclic.data.SharedPreferencesGameStateStore
import com.dsrs.pokeclic.model.DefaultSpriteRepository
import com.dsrs.pokeclic.ui.AppRoot
import com.dsrs.pokeclic.ui.PokeBallSplash
import com.dsrs.pokeclic.viewmodel.MainViewModel

/**
 * Única Activity de la aplicación. Solo hospeda la UI (Compose) y crea el
 * ViewModel; toda la lógica de negocio vive en él.
 *
 * [MainViewModel] se construye con una `Factory` explícita en lugar de dejar que
 * el framework lo instancie por reflexión: su constructor pide un
 * `SpriteRepository` y un `GameStateStore`, así que las dependencias se declaran
 * aquí, en el borde de la app.
 *
 * El ViewModel expone **un único** `StateFlow` con el estado de juego y el de
 * navegación, así que la Activity solo recoge ese flujo.
 */
class MainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModels {
        viewModelFactory {
            initializer {
                MainViewModel(
                    sprites = DefaultSpriteRepository(),
                    store = SharedPreferencesGameStateStore(applicationContext)
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val state by mainViewModel.uiState.collectAsStateWithLifecycle()

                // La cortinilla se muestra solo en un arranque en frío: se recuerda
                // con `rememberSaveable`, así que una rotación no la repite.
                var showSplash by rememberSaveable { mutableStateOf(true) }

                Box(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        state = state,
                        // Aislamiento de la cortinilla (issue #21): mientras se
                        // muestre, AppRoot no compone el menú y la propia
                        // cortinilla consume los toques.
                        isSplashShowing = showSplash,
                        onPokeBallTouch = mainViewModel::onPokeBallTouch,
                        onTallGrassToggle = mainViewModel::onTallGrassToggle,
                        onReset = mainViewModel::onReset,
                        onWipeGame = mainViewModel::onWipeGame,
                        onEnterGym = mainViewModel::onEnterGym,
                        onLeaveGym = mainViewModel::onLeaveGym,
                        onGymRetry = mainViewModel::onGymRetry,
                        onGymProjectileHit = mainViewModel::onGymProjectileHit,
                        onGymEnemyHit = mainViewModel::onGymEnemyHit,
                        onFishingHold = mainViewModel::onFishingHold,
                        onFishingResultContinue = mainViewModel::onFishingResultContinue,
                        onFinishIntro = mainViewModel::onFinishIntro,
                        onSelectLead = mainViewModel::onSelectLead,
                        onSelectTab = mainViewModel::onSelectTab,
                        onOpenOptions = mainViewModel::onOpenOptions,
                        onCloseOptions = mainViewModel::onCloseOptions,
                        onOpenLead = mainViewModel::onOpenLead,
                        onCloseLead = mainViewModel::onCloseLead,
                        onPokeBallExpanded = mainViewModel::onPokeBallExpanded
                    )

                    if (showSplash) {
                        PokeBallSplash(onFinished = { showSplash = false })
                    }
                }
            }
        }
    }
}
