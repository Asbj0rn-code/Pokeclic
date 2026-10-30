package com.dsrs.pokeclic.model

import com.dsrs.pokeclic.R

/**
 * Catálogos de contenido y acceso a sprites.
 *
 * Son la única fuente de datos "de diseño": especies, zonas, gimnasios y los
 * fotogramas del entrenador. Añadir contenido nuevo es añadir una entrada aquí,
 * no tocar la lógica del ViewModel.
 *
 * `SpriteRepository` se declara como interfaz sin dependencias de Android; su
 * implementación (`DefaultSpriteRepository`) es la que conoce `R.drawable`. Así
 * el ViewModel se puede probar en la JVM con un doble.
 */

/** Catálogo de especies disponibles. */
object SpeciesCatalog {

    private val byId: Map<String, Species> = listOf(
        Species("bulbasaur", "Bulbasaur", PokemonType.GRASS, 0.1f, 10),
        Species("charmander", "Charmander", PokemonType.FIRE, 0.1f, 10),
        Species("squirtle", "Squirtle", PokemonType.WATER, 0.1f, 10),
        Species("pidgey", "Pidgey", PokemonType.FLYING, 0.2f, 5),
        Species("rattata", "Rattata", PokemonType.NORMAL, 0.15f, 4),
        Species("caterpie", "Caterpie", PokemonType.GRASS, 0.3f, 6),
        Species("weedle", "Weedle", PokemonType.POISON, 0.35f, 7),
        Species("spearow", "Spearow", PokemonType.FLYING, 0.25f, 6),
        Species("jigglypuff", "Jigglypuff", PokemonType.NORMAL, 0.45f, 10),
        Species("zubat", "Zubat", PokemonType.POISON, 0.5f, 10),
        Species("geodude", "Geodude", PokemonType.ROCK, 0.55f, 12)
    ).associateBy { it.id }

    /** Starters de Kanto entre los que se elige al empezar. En orden de pantalla. */
    val starters: List<Species> = listOf("bulbasaur", "charmander", "squirtle").map { require(it) }

    fun byId(id: String): Species? = byId[id]

    fun require(id: String): Species =
        byId[id] ?: error("Especie desconocida: $id")
}

/**
 * Catálogo ordenado de zonas. El índice en la lista es la posición en el mapa,
 * así que el orden importa.
 *
 * Recorrido de Kanto tras la presentación y la elección del starter
 * (feature-29): Pueblo Paleta → Ruta 1 → Ciudad Verde → Ruta 2 → Bosque Verde →
 * Ciudad Plateada → Ruta 3 → Mt.Moon → Ruta 4. El test `CatalogTest` bloquea ese
 * orden exacto y la regla de progresión: **cada zona pide más clics que la
 * anterior**.
 *
 * Las ciudades (`wildSpeciesIds` vacía) son peldaños sin hierba alta: solo
 * avanzan con clics.
 *
 * Los umbrales siguen siendo **provisionales y bajos** para poder probar el
 * bucle (y el gimnasio) durante el desarrollo; se subirán al equilibrar.
 */
object ZoneCatalog {

    val zones: List<Zone> = listOf(
        Zone(
            id = "pueblo_paleta",
            name = "Pueblo Paleta",
            clickRequirement = 10L,
            wildSpeciesIds = emptyList()
        ),
        Zone(
            id = "ruta1",
            name = "Ruta 1",
            clickRequirement = 20L,
            wildSpeciesIds = listOf("pidgey", "rattata")
        ),
        Zone(
            id = "ciudad_verde",
            name = "Ciudad Verde",
            clickRequirement = 30L,
            wildSpeciesIds = emptyList()
        ),
        Zone(
            id = "ruta2",
            name = "Ruta 2",
            clickRequirement = 45L,
            wildSpeciesIds = listOf("pidgey", "rattata", "caterpie", "weedle")
        ),
        Zone(
            id = "bosque_verde",
            name = "Bosque Verde",
            clickRequirement = 65L,
            wildSpeciesIds = listOf("caterpie", "weedle")
        ),
        Zone(
            id = "ciudad_plateada",
            name = "Ciudad Plateada",
            clickRequirement = 90L,
            wildSpeciesIds = emptyList(),
            gymId = "brock"
        ),
        Zone(
            id = "ruta3",
            name = "Ruta 3",
            clickRequirement = 120L,
            wildSpeciesIds = listOf("spearow", "jigglypuff")
        ),
        Zone(
            id = "mt_moon",
            name = "Mt.Moon",
            clickRequirement = 160L,
            wildSpeciesIds = listOf("zubat", "geodude")
        ),
        Zone(
            id = "ruta4",
            name = "Ruta 4",
            clickRequirement = 210L,
            wildSpeciesIds = listOf("spearow", "rattata")
        )
    )
}

/**
 * Catálogo de gimnasios. En la Etapa 2 solo existe Brock, alojado en
 * Ciudad Plateada (feature-29, el gimnasio de piedra del recorrido canónico);
 * los siete restantes llegarán con el contenido de Kanto.
 */
object GymCatalog {

    val brock = Gym(
        id = "brock",
        leaderName = "Brock",
        type = PokemonType.ROCK,
        coinReward = 40L,
        leaderHp = 60,
        columns = 5,
        spawnIntervalMs = 650L,
        projectileTravelMs = 1_800L,
        projectileDamage = 1,
        waveDurationMs = 10_000L,
        exposureDurationMs = 2_500L,
        projectilesPerSpawn = 2,
        rageSpawnIntervalMs = 450L,
        rageProjectilesPerSpawn = 2,
        rageProjectileTravelMs = 1_100L
    )

    private val byId: Map<String, Gym> = listOf(brock).associateBy { it.id }

    fun byId(id: String): Gym? = byId[id]

    fun require(id: String): Gym =
        byId[id] ?: error("Gimnasio desconocido: $id")
}

/**
 * Fuente de datos de los frames de animación del trainer.
 *
 * Vive en la capa Model para que el ViewModel no dependa de recursos de Android:
 * así la lógica de negocio es testeable en la JVM sin instrumentación.
 */
interface SpriteRepository {
    fun frameCount(direction: Direction): Int
    fun frame(direction: Direction, index: Int): Int
}

/** Implementación real: mapea cada dirección a sus cuatro fotogramas. */
class DefaultSpriteRepository : SpriteRepository {

    private val frames: Map<Direction, IntArray> = mapOf(
        Direction.FRONT to intArrayOf(R.drawable.front0, R.drawable.front1, R.drawable.front2, R.drawable.front3),
        Direction.RIGHT to intArrayOf(R.drawable.right0, R.drawable.right1, R.drawable.right2, R.drawable.right3),
        Direction.LEFT to intArrayOf(R.drawable.left0, R.drawable.left1, R.drawable.left2, R.drawable.left3),
        Direction.BACK to intArrayOf(R.drawable.back0, R.drawable.back1, R.drawable.back2, R.drawable.back3)
    )

    override fun frameCount(direction: Direction): Int = frames.getValue(direction).size

    override fun frame(direction: Direction, index: Int): Int = frames.getValue(direction)[index]
}
