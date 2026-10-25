package com.dsrs.pokeclic.model

/**
 * Entidades y datos persistentes del juego.
 *
 * Este archivo agrupa los tipos pequeños y estables que describen la partida
 * (destinos de navegación, entrenador, especies, zonas, encuentros y guardado)
 * para no repartirlos en una decena de archivos de una sola clase.
 *
 * Las reglas del combate viven en `Combat.kt` y los catálogos de contenido en
 * `Catalogs.kt`. Nada de este archivo depende de Android: se puede probar en la
 * JVM.
 */

/** Destinos de **contenido** del shell. Las capas modales viven en `GameUiState`. */
enum class AppTab {
    Map,
    Home
}

/** Dirección a la que mira el entrenador (y en la que anima). */
enum class Direction {
    FRONT,
    RIGHT,
    LEFT,
    BACK
}

/** Fotograma y orientación actuales del sprite del entrenador (solo visual). */
data class TrainerState(
    val currentDirection: Direction = Direction.FRONT,
    val currentFrame: Int = 0
)

/**
 * Una especie capturable.
 *
 * @param id clave estable usada en la Pokédex y en la persistencia (no traducir).
 * @param name nombre mostrado.
 * @param type tipo elemental, decide ventajas y desventajas.
 * @param captureDifficulty 0f (trivial) a 1f (muy difícil); alimenta la
 *   probabilidad de captura.
 * @param coinReward monedas otorgadas la **primera vez** que se captura la
 *   especie (una sola vez para siempre).
 */
data class Species(
    val id: String,
    val name: String,
    val type: PokemonType,
    val captureDifficulty: Float,
    val coinReward: Int
)

/**
 * Un Pokémon salvaje que ha aparecido en la hierba alta y espera a ser capturado.
 *
 * Es estado **transitorio**: no se persiste mientras está abierto. En la Etapa 1
 * la captura es simple (lanzamientos con probabilidad); la Etapa 3 la sustituye
 * por el minijuego de pesca.
 *
 * @param captureChance probabilidad de captura por lanzamiento (0f..1f).
 * @param attemptsLeft lanzamientos restantes antes de que el salvaje huya.
 */
data class Encounter(
    val speciesId: String,
    val speciesName: String,
    val speciesType: PokemonType,
    val captureChance: Float,
    val attemptsLeft: Int
)

/**
 * Opción ya resuelta para el selector de Pokémon principal. La UI no necesita
 * conocer el catálogo: el ViewModel le entrega id y nombre listos para pintar.
 */
data class LeadOption(
    val id: String,
    val name: String
)

/**
 * Opción ya resuelta para elegir el starter en la introducción.
 *
 * @param type tipo elemental mostrado junto al nombre.
 */
data class StarterOption(
    val id: String,
    val name: String,
    val type: PokemonType
)

/**
 * Una zona del mapa.
 *
 * @param id clave estable para la persistencia.
 * @param name nombre mostrado.
 * @param clickRequirement clics de zona necesarios para desbloquear el avance.
 * @param wildSpeciesIds especies que pueden aparecer en su hierba alta.
 * @param gymId si no es `null`, la zona **termina** en ese gimnasio: el botón de
 *   avance no aparece hasta ganarlo dentro de la run actual.
 */
data class Zone(
    val id: String,
    val name: String,
    val clickRequirement: Long,
    val wildSpeciesIds: List<String>,
    val gymId: String? = null
)

/**
 * Calcula el progreso que queda en la zona [zone] tras sumar [amount] clics.
 *
 * Reglas:
 * - El avance de zona ya **no es automático**: al alcanzar el umbral el
 *   progreso se capa (barra al 100 %) y es el jugador quien pulsa el botón
 *   «Avanzar», cuyo requisito se calcula aparte (ver `canAdvanceZone`).
 * - En ninguna zona (normal, de gimnasio o última) hay overshoot del umbral.
 *
 * Es una función pura para poder testearla en la JVM sin el ViewModel.
 */
fun advanceZone(zone: Zone, currentClicks: Long, amount: Long): Long =
    (currentClicks + amount).coerceAtMost(zone.clickRequirement)

/**
 * ¿Está pendiente de ganar el gimnasio de [zone] durante la run actual?
 *
 * A diferencia de las medallas (meta permanente que sobrevive al reset), solo
 * cuentan los gimnasios ganados **en esta run**: una medalla vieja no exime de
 * repetir el combate tras resetear.
 */
fun isGymPending(zone: Zone, gymClearedRun: Set<String>): Boolean =
    zone.gymId != null && zone.gymId !in gymClearedRun

/**
 * Subconjunto persistible del estado de juego: solo lo que debe sobrevivir a
 * cerrar la app. La UI transitoria (encuentro, progreso de caminar, gimnasio,
 * navegación y animación del entrenador) queda fuera a propósito.
 *
 * **Permanentes**: `playerName`, `seenSpecies`, `rewardedSpecies`, `medals`,
 * `starterSpeciesId` y `starterBond`. **De la run**: `ownedSpecies`,
 * `leadSpeciesId`, `gymClearedRun` y el progreso de zona; se vacían (o vuelven
 * al starter) al resetear.
 *
 * Las mejoras comprables (potencia de clic y vida) se retiraron por no tener UI
 * ni forma de adquirirse; volverán como sistema de progresión en una etapa
 * futura y, con ellas, sus campos de guardado.
 */
data class GameSnapshot(
    val playerName: String? = null,
    val coins: Long = 0L,
    val seenSpecies: Set<String> = emptySet(),
    val rewardedSpecies: Set<String> = emptySet(),
    val medals: Set<String> = emptySet(),
    val starterSpeciesId: String? = null,
    val starterBond: Int = 0,
    val ownedSpecies: Set<String> = emptySet(),
    val leadSpeciesId: String? = null,
    val gymClearedRun: Set<String> = emptySet(),
    val zoneIndex: Int = 0,
    val zoneClicks: Long = 0L
)
