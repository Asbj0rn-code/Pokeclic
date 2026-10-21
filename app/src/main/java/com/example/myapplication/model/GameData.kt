package com.example.myapplication.model

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
 * @param gymId si no es `null`, la zona **termina** en ese gimnasio: al alcanzar
 *   el umbral no se avanza hasta que se gane su medalla.
 */
data class Zone(
    val id: String,
    val name: String,
    val clickRequirement: Long,
    val wildSpeciesIds: List<String>,
    val gymId: String? = null
)

/** Resultado de aplicar clics a una zona: nueva posición y nuevo progreso. */
data class ZoneAdvance(
    val zoneIndex: Int,
    val zoneClicks: Long
)

/**
 * Calcula a qué zona y con qué progreso se queda el jugador tras sumar [amount]
 * clics en la zona [zone].
 *
 * Reglas:
 * - Si la zona **termina en un gimnasio no medallado**, el progreso se capa en el
 *   umbral y no se avanza: hay que ganarlo primero.
 * - Si ya está medallado (p. ej. tras un reset), se avanza como una zona normal.
 * - En la última zona no se puede avanzar: se capa el progreso.
 *
 * Es una función pura para poder testearla en la JVM sin el ViewModel.
 */
fun advanceZone(
    zone: Zone,
    currentIndex: Int,
    currentClicks: Long,
    amount: Long,
    medals: Set<String>,
    lastIndex: Int
): ZoneAdvance {
    val total = currentClicks + amount
    val reached = total >= zone.clickRequirement
    val gymPending = zone.gymId != null && zone.gymId !in medals

    return when {
        reached && gymPending -> ZoneAdvance(currentIndex, zone.clickRequirement)
        reached && currentIndex < lastIndex -> ZoneAdvance(currentIndex + 1, 0L)
        currentIndex >= lastIndex -> ZoneAdvance(currentIndex, total.coerceAtMost(zone.clickRequirement))
        else -> ZoneAdvance(currentIndex, total)
    }
}

/**
 * Subconjunto persistible del estado de juego: solo lo que debe sobrevivir a
 * cerrar la app. La UI transitoria (encuentro, progreso de caminar, gimnasio,
 * navegación y animación del entrenador) queda fuera a propósito.
 *
 * **Permanentes**: `playerName`, `seenSpecies`, `rewardedSpecies`, `medals`,
 * `starterSpeciesId` y `starterBond`. **De la run**: `ownedSpecies`,
 * `leadSpeciesId` y el progreso de zona; se vacían (o vuelven al starter) al
 * resetear.
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
    val zoneIndex: Int = 0,
    val zoneClicks: Long = 0L
)
