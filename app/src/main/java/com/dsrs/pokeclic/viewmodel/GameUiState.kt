package com.dsrs.pokeclic.viewmodel

import com.dsrs.pokeclic.model.AppTab
import com.dsrs.pokeclic.model.Encounter
import com.dsrs.pokeclic.model.GymState
import com.dsrs.pokeclic.model.LeadOption
import com.dsrs.pokeclic.model.PokemonType
import com.dsrs.pokeclic.model.StarterOption
import com.dsrs.pokeclic.model.TrainerState

/**
 * Estado completo de la aplicación: **única fuente de verdad** para la UI.
 *
 * Incluye tanto el estado de juego como el de navegación (pestaña visible y
 * capas modales), de modo que `MainViewModel` expone un solo `StateFlow`. El
 * ViewModel resuelve aquí también los valores derivados (nombre de zona,
 * potencia de clic, vida máxima, opciones de principal/starter…) para que la UI
 * no calcule nada de negocio: solo renderiza estos campos.
 *
 * Grupos:
 * - **meta permanente**: [playerName], [coins], [seenSpecies], [rewardedSpecies],
 *   [medals], [starterSpeciesId], [starterBond].
 * - **run** (se pierde al resetear): [ownedSpecies], [leadSpeciesId],
 *   [gymClearedRun], [zoneIndex], [zoneClicks].
 * - **transitorio/UI**: [isTallGrass], [walkProgress], [encounter], [gym],
 *   [trainer] y los campos de navegación.
 * - **derivados**: los rellena `MainViewModel.derive()` en cada cambio.
 *
 * @param clicksPerSecond producción pasiva; hoy 0, pero el bucle del ViewModel ya
 *   la aplica, así que conectar una mejora no requerirá tocar la UI.
 */
data class GameUiState(
    // --- meta permanente ---
    val playerName: String? = null,
    val coins: Long = 0L,
    val seenSpecies: Set<String> = emptySet(),
    val rewardedSpecies: Set<String> = emptySet(),
    val medals: Set<String> = emptySet(),
    val starterSpeciesId: String? = null,
    val starterBond: Int = 0,

    // --- run ---
    val ownedSpecies: Set<String> = emptySet(),
    val leadSpeciesId: String? = null,
    /** Gimnasios ganados durante la run actual (se vacía al resetear). */
    val gymClearedRun: Set<String> = emptySet(),
    val zoneIndex: Int = 0,
    val zoneClicks: Long = 0L,

    // --- hierba alta / encuentros ---
    val isTallGrass: Boolean = false,
    val walkProgress: Float = 0f,
    val encounter: Encounter? = null,

    // --- gimnasio ---
    val gym: GymState? = null,

    // --- entrenador (visual) ---
    val trainer: TrainerState = TrainerState(),
    val trainerFrameRes: Int = 0,

    // --- navegación (UI) ---
    val contentTab: AppTab = AppTab.Home,
    val isOptionsOpen: Boolean = false,
    val isLeadOpen: Boolean = false,

    // --- derivados (los rellena el ViewModel) ---
    val zoneName: String = "",
    val zoneRequirement: Long = 0L,
    /** Especies en posesión incluyendo al starter, igual que la potencia de clic. */
    val ownedCount: Int = 0,
    val clickPower: Long = 1L,
    val clicksPerSecond: Long = 0L,
    val maxHp: Int = 1,
    val leadName: String? = null,
    val leadType: PokemonType? = null,
    val isGymReady: Boolean = false,
    val gymLeaderName: String? = null,
    /**
     * Puede pulsarse el botón **Avanzar**: barra al 100 % y, en zonas con
     * gimnasio, gimnasio ganado durante la run. Siempre `false` en la última zona.
     */
    val canAdvanceZone: Boolean = false,
    val needsStarter: Boolean = false,
    val leadOptions: List<LeadOption> = emptyList(),
    val starterOptions: List<StarterOption> = emptyList()
) {
    /** Fracción 0f..1f de progreso en la zona actual, lista para la barra. */
    val zoneProgress: Float
        get() = if (zoneRequirement <= 0L) 0f
        else (zoneClicks.toFloat() / zoneRequirement).coerceIn(0f, 1f)

    /** ¿La especie [id] puede equiparse como principal en esta run? */
    fun isLeadCandidate(id: String): Boolean = leadOptions.any { it.id == id }
}
