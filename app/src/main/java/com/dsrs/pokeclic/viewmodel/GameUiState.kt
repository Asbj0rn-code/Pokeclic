package com.dsrs.pokeclic.viewmodel

import com.dsrs.pokeclic.model.AppTab
import com.dsrs.pokeclic.model.Encounter
import com.dsrs.pokeclic.model.CatchingState
import com.dsrs.pokeclic.model.GymIntroPhase
import com.dsrs.pokeclic.model.GymMechanic
import com.dsrs.pokeclic.model.GymState
import com.dsrs.pokeclic.model.LeadOption
import com.dsrs.pokeclic.model.PokedexRow
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
 *   [caughtSpecies], [medals], [starterSpeciesId], [starterBond].
 * - **run** (se pierde al resetear): [ownedSpecies], [leadSpeciesId],
 *   [gymClearedRun], [zoneIndex], [zoneClicks].
 * - **transitorio/UI**: [isTallGrass], [walkProgress], [encounter], [catching],
 *   [gym], [trainer] y los campos de navegación.
 * - **derivados**: los rellena `MainViewModel.derive()` en cada cambio.
 */
data class GameUiState(
    // --- meta permanente ---
    val playerName: String? = null,
    val coins: Long = 0L,
    val seenSpecies: Set<String> = emptySet(),
    val rewardedSpecies: Set<String> = emptySet(),
    /**
     * Capturas históricas (meta permanente): especies alguna vez obtenidas.
     * Sobrevive al reset y muere con el borrado total. Para la Pokédex sirve
     * para **reconocer especies conocidas** (avistadas), pero **NO**
     * determina el estado CAUGHT: ese lo marca la posesión de la run actual
     * (`ownedSpecies` o `starterSpeciesId`).
     */
    val caughtSpecies: Set<String> = emptySet(),
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

    // --- minijuego de captura (activo con encounter, no se persiste) ---
    val catching: CatchingState? = null,

    // --- gimnasio ---
    val gym: GymState? = null,
    /** Capa de entrada al gimnasio (bienvenida/tutorial/cartel); no se persiste. */
    val gymIntro: GymIntroPhase? = null,

    // --- entrenador (visual) ---
    val trainer: TrainerState = TrainerState(),
    val trainerFrameRes: Int = 0,

    // --- navegación (UI) ---
    val contentTab: AppTab = AppTab.Home,
    val isOptionsOpen: Boolean = false,
    val isLeadOpen: Boolean = false,
    /**
     * Pantalla propia de Pokédex a pantalla completa (TEST). No es un overlay:
     * mientras esté abierta no se compone el escenario, el mapa, el HUD ni el
     * menú inferior, y el avance de caminar queda en pausa (exige una
     * pulsación nueva al volver).
     */
    val isPokedexOpen: Boolean = false,

    // --- derivados (los rellena el ViewModel) ---
    val zoneName: String = "",
    /** Clave estable de la zona actual, para resolver su fondo animado. */
    val zoneId: String = "",
    val zoneRequirement: Long = 0L,
    /** Especies en posesión incluyendo al starter, igual que la potencia de clic. */
    val ownedCount: Int = 0,
    val clickPower: Long = 1L,
    val maxHp: Int = 1,
    val leadName: String? = null,
    val leadType: PokemonType? = null,
    val isGymReady: Boolean = false,
    val gymLeaderName: String? = null,
    /** Mecánica del gimnasio de la zona, también con la capa de entrada abierta. */
    val gymMechanic: GymMechanic = GymMechanic.PROJECTILES,
    /**
     * Puede pulsarse el botón **Avanzar**: barra al 100 % y, en zonas con
     * gimnasio, gimnasio ganado durante la run. Siempre `false` en la última zona.
     */
    val canAdvanceZone: Boolean = false,
    /** La zona tiene hierba alta con fauna; en ciudades la hierba no se ofrece. */
    val hasWilds: Boolean = true,
    val needsStarter: Boolean = false,
    val leadOptions: List<LeadOption> = emptyList(),
    val starterOptions: List<StarterOption> = emptyList(),

    // --- Pokédex (derivados del catálogo + historial) ---
    /** Filas 001..151 con el estado de cada especie, ya resuelto para la UI. */
    val dexEntries: List<PokedexRow> = emptyList(),
    /** Especies avistadas que constan en el catálogo (para el encabezado). */
    val dexSeenCount: Int = 0,
    /** Especies capturadas que constan en el catálogo (para el encabezado). */
    val dexCaughtCount: Int = 0,
    /** La carga del catálogo falló: la Pokédex muestra un error y permite volver. */
    val dexLoadFailed: Boolean = false
) {
    /** Fracción 0f..1f de progreso en la zona actual, lista para la barra. */
    val zoneProgress: Float
        get() = if (zoneRequirement <= 0L) 0f
        else (zoneClicks.toFloat() / zoneRequirement).coerceIn(0f, 1f)

    /** ¿La especie [id] puede equiparse como principal en esta run? */
    fun isLeadCandidate(id: String): Boolean = leadOptions.any { it.id == id }
}
