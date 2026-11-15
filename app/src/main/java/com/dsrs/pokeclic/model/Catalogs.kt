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

    private val species: List<Species> = listOf(
        Species("bulbasaur", "Bulbasaur", PokemonType.GRASS, 0.1f, 10, dexNumber = 1),
        Species("ivysaur", "Ivysaur", PokemonType.GRASS, 0.45f, 12, dexNumber = 2),
        Species("venusaur", "Venusaur", PokemonType.GRASS, 0.6f, 20, dexNumber = 3),
        Species("charmander", "Charmander", PokemonType.FIRE, 0.1f, 10, dexNumber = 4),
        Species("charmeleon", "Charmeleon", PokemonType.FIRE, 0.45f, 12, dexNumber = 5),
        Species("charizard", "Charizard", PokemonType.FIRE, 0.6f, 20, dexNumber = 6),
        Species("squirtle", "Squirtle", PokemonType.WATER, 0.1f, 10, dexNumber = 7),
        Species("wartortle", "Wartortle", PokemonType.WATER, 0.45f, 12, dexNumber = 8),
        Species("blastoise", "Blastoise", PokemonType.WATER, 0.6f, 20, dexNumber = 9),
        Species("caterpie", "Caterpie", PokemonType.BUG, 0.3f, 6, dexNumber = 10),
        Species("metapod", "Metapod", PokemonType.BUG, 0.45f, 12, dexNumber = 11),
        Species("butterfree", "Butterfree", PokemonType.BUG, 0.6f, 20, dexNumber = 12),
        Species("weedle", "Weedle", PokemonType.BUG, 0.35f, 7, dexNumber = 13),
        Species("kakuna", "Kakuna", PokemonType.BUG, 0.45f, 12, dexNumber = 14),
        Species("beedrill", "Beedrill", PokemonType.BUG, 0.6f, 20, dexNumber = 15),
        Species("pidgey", "Pidgey", PokemonType.FLYING, 0.2f, 5, dexNumber = 16),
        Species("pidgeotto", "Pidgeotto", PokemonType.FLYING, 0.45f, 12, dexNumber = 17),
        Species("pidgeot", "Pidgeot", PokemonType.FLYING, 0.6f, 20, dexNumber = 18),
        Species("rattata", "Rattata", PokemonType.NORMAL, 0.15f, 4, dexNumber = 19),
        Species("raticate", "Raticate", PokemonType.NORMAL, 0.45f, 12, dexNumber = 20),
        Species("spearow", "Spearow", PokemonType.FLYING, 0.25f, 6, dexNumber = 21),
        Species("fearow", "Fearow", PokemonType.FLYING, 0.45f, 12, dexNumber = 22),
        Species("ekans", "Ekans", PokemonType.POISON, 0.2f, 5, dexNumber = 23),
        Species("arbok", "Arbok", PokemonType.POISON, 0.45f, 12, dexNumber = 24),
        Species("pikachu", "Pikachu", PokemonType.ELECTRIC, 0.35f, 8, dexNumber = 25),
        Species("raichu", "Raichu", PokemonType.ELECTRIC, 0.6f, 20, dexNumber = 26),
        Species("sandshrew", "Sandshrew", PokemonType.GROUND, 0.2f, 5, dexNumber = 27),
        Species("sandslash", "Sandslash", PokemonType.GROUND, 0.45f, 12, dexNumber = 28),
        Species("nidoran_f", "Nidoran♀", PokemonType.POISON, 0.2f, 5, dexNumber = 29),
        Species("nidorina", "Nidorina", PokemonType.POISON, 0.45f, 12, dexNumber = 30),
        Species("nidoqueen", "Nidoqueen", PokemonType.POISON, 0.6f, 20, dexNumber = 31),
        Species("nidoran_m", "Nidoran♂", PokemonType.POISON, 0.2f, 5, dexNumber = 32),
        Species("nidorino", "Nidorino", PokemonType.POISON, 0.45f, 12, dexNumber = 33),
        Species("nidoking", "Nidoking", PokemonType.POISON, 0.6f, 20, dexNumber = 34),
        Species("clefairy", "Clefairy", PokemonType.NORMAL, 0.35f, 8, dexNumber = 35),
        Species("clefable", "Clefable", PokemonType.NORMAL, 0.6f, 20, dexNumber = 36),
        Species("vulpix", "Vulpix", PokemonType.FIRE, 0.35f, 8, dexNumber = 37),
        Species("ninetales", "Ninetales", PokemonType.FIRE, 0.6f, 20, dexNumber = 38),
        Species("jigglypuff", "Jigglypuff", PokemonType.NORMAL, 0.45f, 10, dexNumber = 39),
        Species("wigglytuff", "Wigglytuff", PokemonType.NORMAL, 0.6f, 20, dexNumber = 40),
        Species("zubat", "Zubat", PokemonType.POISON, 0.5f, 10, dexNumber = 41),
        Species("golbat", "Golbat", PokemonType.POISON, 0.6f, 20, dexNumber = 42),
        Species("oddish", "Oddish", PokemonType.GRASS, 0.3f, 6, dexNumber = 43),
        Species("gloom", "Gloom", PokemonType.GRASS, 0.45f, 12, dexNumber = 44),
        Species("vileplume", "Vileplume", PokemonType.GRASS, 0.6f, 20, dexNumber = 45),
        Species("paras", "Paras", PokemonType.BUG, 0.2f, 5, dexNumber = 46),
        Species("parasect", "Parasect", PokemonType.BUG, 0.45f, 12, dexNumber = 47),
        Species("venonat", "Venonat", PokemonType.BUG, 0.2f, 5, dexNumber = 48),
        Species("venomoth", "Venomoth", PokemonType.BUG, 0.45f, 12, dexNumber = 49),
        Species("diglett", "Diglett", PokemonType.GROUND, 0.2f, 5, dexNumber = 50),
        Species("dugtrio", "Dugtrio", PokemonType.GROUND, 0.45f, 12, dexNumber = 51),
        Species("meowth", "Meowth", PokemonType.NORMAL, 0.3f, 7, dexNumber = 52),
        Species("persian", "Persian", PokemonType.NORMAL, 0.45f, 12, dexNumber = 53),
        Species("psyduck", "Psyduck", PokemonType.WATER, 0.2f, 5, dexNumber = 54),
        Species("golduck", "Golduck", PokemonType.WATER, 0.45f, 12, dexNumber = 55),
        Species("mankey", "Mankey", PokemonType.FIGHTING, 0.35f, 8, dexNumber = 56),
        Species("primeape", "Primeape", PokemonType.FIGHTING, 0.45f, 12, dexNumber = 57),
        Species("growlithe", "Growlithe", PokemonType.FIRE, 0.35f, 8, dexNumber = 58),
        Species("arcanine", "Arcanine", PokemonType.FIRE, 0.6f, 20, dexNumber = 59),
        Species("poliwag", "Poliwag", PokemonType.WATER, 0.35f, 8, dexNumber = 60),
        Species("poliwhirl", "Poliwhirl", PokemonType.WATER, 0.45f, 12, dexNumber = 61),
        Species("poliwrath", "Poliwrath", PokemonType.WATER, 0.6f, 20, dexNumber = 62),
        Species("abra", "Abra", PokemonType.PSYCHIC, 0.35f, 8, dexNumber = 63),
        Species("kadabra", "Kadabra", PokemonType.PSYCHIC, 0.45f, 12, dexNumber = 64),
        Species("alakazam", "Alakazam", PokemonType.PSYCHIC, 0.6f, 20, dexNumber = 65),
        Species("machop", "Machop", PokemonType.FIGHTING, 0.35f, 8, dexNumber = 66),
        Species("machoke", "Machoke", PokemonType.FIGHTING, 0.45f, 12, dexNumber = 67),
        Species("machamp", "Machamp", PokemonType.FIGHTING, 0.6f, 20, dexNumber = 68),
        Species("bellsprout", "Bellsprout", PokemonType.GRASS, 0.2f, 5, dexNumber = 69),
        Species("weepinbell", "Weepinbell", PokemonType.GRASS, 0.45f, 12, dexNumber = 70),
        Species("victreebel", "Victreebel", PokemonType.GRASS, 0.6f, 20, dexNumber = 71),
        Species("tentacool", "Tentacool", PokemonType.WATER, 0.2f, 5, dexNumber = 72),
        Species("tentacruel", "Tentacruel", PokemonType.WATER, 0.45f, 12, dexNumber = 73),
        Species("geodude", "Geodude", PokemonType.ROCK, 0.55f, 12, dexNumber = 74),
        Species("graveler", "Graveler", PokemonType.ROCK, 0.6f, 20, dexNumber = 75),
        Species("golem", "Golem", PokemonType.ROCK, 0.7f, 30, dexNumber = 76),
        Species("ponyta", "Ponyta", PokemonType.FIRE, 0.35f, 8, dexNumber = 77),
        Species("rapidash", "Rapidash", PokemonType.FIRE, 0.45f, 12, dexNumber = 78),
        Species("slowpoke", "Slowpoke", PokemonType.WATER, 0.35f, 8, dexNumber = 79),
        Species("slowbro", "Slowbro", PokemonType.WATER, 0.45f, 12, dexNumber = 80),
        Species("magnemite", "Magnemite", PokemonType.ELECTRIC, 0.35f, 8, dexNumber = 81),
        Species("magneton", "Magneton", PokemonType.ELECTRIC, 0.45f, 12, dexNumber = 82),
        Species("farfetchd", "Farfetch'd", PokemonType.FLYING, 0.7f, 30, dexNumber = 83),
        Species("doduo", "Doduo", PokemonType.FLYING, 0.2f, 5, dexNumber = 84),
        Species("dodrio", "Dodrio", PokemonType.FLYING, 0.45f, 12, dexNumber = 85),
        Species("seel", "Seel", PokemonType.WATER, 0.35f, 8, dexNumber = 86),
        Species("dewgong", "Dewgong", PokemonType.ICE, 0.45f, 12, dexNumber = 87),
        Species("grimer", "Grimer", PokemonType.POISON, 0.35f, 8, dexNumber = 88),
        Species("muk", "Muk", PokemonType.POISON, 0.45f, 12, dexNumber = 89),
        Species("shellder", "Shellder", PokemonType.WATER, 0.35f, 8, dexNumber = 90),
        Species("cloyster", "Cloyster", PokemonType.ICE, 0.6f, 20, dexNumber = 91),
        Species("gastly", "Gastly", PokemonType.GHOST, 0.35f, 8, dexNumber = 92),
        Species("haunter", "Haunter", PokemonType.GHOST, 0.45f, 12, dexNumber = 93),
        Species("gengar", "Gengar", PokemonType.GHOST, 0.6f, 20, dexNumber = 94),
        Species("onix", "Onix", PokemonType.ROCK, 0.7f, 30, dexNumber = 95),
        Species("drowzee", "Drowzee", PokemonType.PSYCHIC, 0.2f, 5, dexNumber = 96),
        Species("hypno", "Hypno", PokemonType.PSYCHIC, 0.45f, 12, dexNumber = 97),
        Species("krabby", "Krabby", PokemonType.WATER, 0.2f, 5, dexNumber = 98),
        Species("kingler", "Kingler", PokemonType.WATER, 0.45f, 12, dexNumber = 99),
        Species("voltorb", "Voltorb", PokemonType.ELECTRIC, 0.35f, 8, dexNumber = 100),
        Species("electrode", "Electrode", PokemonType.ELECTRIC, 0.45f, 12, dexNumber = 101),
        Species("exeggcute", "Exeggcute", PokemonType.GRASS, 0.35f, 8, dexNumber = 102),
        Species("exeggutor", "Exeggutor", PokemonType.GRASS, 0.6f, 20, dexNumber = 103),
        Species("cubone", "Cubone", PokemonType.GROUND, 0.35f, 8, dexNumber = 104),
        Species("marowak", "Marowak", PokemonType.GROUND, 0.45f, 12, dexNumber = 105),
        Species("hitmonlee", "Hitmonlee", PokemonType.FIGHTING, 0.7f, 30, dexNumber = 106),
        Species("hitmonchan", "Hitmonchan", PokemonType.FIGHTING, 0.7f, 30, dexNumber = 107),
        Species("lickitung", "Lickitung", PokemonType.NORMAL, 0.7f, 30, dexNumber = 108),
        Species("koffing", "Koffing", PokemonType.POISON, 0.35f, 8, dexNumber = 109),
        Species("weezing", "Weezing", PokemonType.POISON, 0.45f, 12, dexNumber = 110),
        Species("rhyhorn", "Rhyhorn", PokemonType.ROCK, 0.35f, 8, dexNumber = 111),
        Species("rhydon", "Rhydon", PokemonType.ROCK, 0.45f, 12, dexNumber = 112),
        Species("chansey", "Chansey", PokemonType.NORMAL, 0.7f, 30, dexNumber = 113),
        Species("tangela", "Tangela", PokemonType.GRASS, 0.7f, 30, dexNumber = 114),
        Species("kangaskhan", "Kangaskhan", PokemonType.NORMAL, 0.7f, 30, dexNumber = 115),
        Species("horsea", "Horsea", PokemonType.WATER, 0.35f, 8, dexNumber = 116),
        Species("seadra", "Seadra", PokemonType.WATER, 0.45f, 12, dexNumber = 117),
        Species("goldeen", "Goldeen", PokemonType.WATER, 0.2f, 5, dexNumber = 118),
        Species("seaking", "Seaking", PokemonType.WATER, 0.45f, 12, dexNumber = 119),
        Species("staryu", "Staryu", PokemonType.WATER, 0.35f, 8, dexNumber = 120),
        Species("starmie", "Starmie", PokemonType.WATER, 0.6f, 20, dexNumber = 121),
        Species("mr_mime", "Mr. Mime", PokemonType.PSYCHIC, 0.7f, 30, dexNumber = 122),
        Species("scyther", "Scyther", PokemonType.BUG, 0.7f, 30, dexNumber = 123),
        Species("jynx", "Jynx", PokemonType.ICE, 0.7f, 30, dexNumber = 124),
        Species("electabuzz", "Electabuzz", PokemonType.ELECTRIC, 0.7f, 30, dexNumber = 125),
        Species("magmar", "Magmar", PokemonType.FIRE, 0.7f, 30, dexNumber = 126),
        Species("pinsir", "Pinsir", PokemonType.BUG, 0.7f, 30, dexNumber = 127),
        Species("tauros", "Tauros", PokemonType.NORMAL, 0.7f, 30, dexNumber = 128),
        Species("magikarp", "Magikarp", PokemonType.WATER, 0.2f, 5, dexNumber = 129),
        Species("gyarados", "Gyarados", PokemonType.WATER, 0.7f, 30, dexNumber = 130),
        Species("lapras", "Lapras", PokemonType.WATER, 0.8f, 45, dexNumber = 131),
        Species("ditto", "Ditto", PokemonType.NORMAL, 0.7f, 30, dexNumber = 132),
        Species("eevee", "Eevee", PokemonType.NORMAL, 0.35f, 8, dexNumber = 133),
        Species("vaporeon", "Vaporeon", PokemonType.WATER, 0.6f, 20, dexNumber = 134),
        Species("jolteon", "Jolteon", PokemonType.ELECTRIC, 0.6f, 20, dexNumber = 135),
        Species("flareon", "Flareon", PokemonType.FIRE, 0.6f, 20, dexNumber = 136),
        Species("porygon", "Porygon", PokemonType.NORMAL, 0.7f, 30, dexNumber = 137),
        Species("omanyte", "Omanyte", PokemonType.ROCK, 0.7f, 30, dexNumber = 138),
        Species("omastar", "Omastar", PokemonType.ROCK, 0.8f, 45, dexNumber = 139),
        Species("kabuto", "Kabuto", PokemonType.ROCK, 0.7f, 30, dexNumber = 140),
        Species("kabutops", "Kabutops", PokemonType.ROCK, 0.8f, 45, dexNumber = 141),
        Species("aerodactyl", "Aerodactyl", PokemonType.ROCK, 0.8f, 45, dexNumber = 142),
        Species("snorlax", "Snorlax", PokemonType.NORMAL, 0.8f, 45, dexNumber = 143),
        Species("articuno", "Articuno", PokemonType.ICE, 0.9f, 100, dexNumber = 144),
        Species("zapdos", "Zapdos", PokemonType.ELECTRIC, 0.9f, 100, dexNumber = 145),
        Species("moltres", "Moltres", PokemonType.FIRE, 0.9f, 100, dexNumber = 146),
        Species("dratini", "Dratini", PokemonType.DRAGON, 0.35f, 8, dexNumber = 147),
        Species("dragonair", "Dragonair", PokemonType.DRAGON, 0.45f, 12, dexNumber = 148),
        Species("dragonite", "Dragonite", PokemonType.DRAGON, 0.7f, 30, dexNumber = 149),
        Species("mewtwo", "Mewtwo", PokemonType.PSYCHIC, 0.95f, 150, dexNumber = 150),
        Species("mew", "Mew", PokemonType.PSYCHIC, 0.95f, 150, dexNumber = 151)
    )

    private val byId: Map<String, Species> = species.associateBy { it.id }

    /** Todas las especies del catálogo, en el orden en que se declararon. */
    val all: List<Species> = species

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
 * Ciudad Plateada → Ruta 3 → Mt.Moon → Ruta 4 → Ciudad Celeste (feature-35) →
 * Ruta 5 - Vía Subterránea (feature-39) → Ruta 6 (feature-40). El test
 * `CatalogTest` bloquea ese orden exacto y la regla de progresión: **cada zona
 * pide más clics que la anterior**.
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
            clickRequirement = 140L,
            wildSpeciesIds = emptyList(),
            gymId = "brock"
        ),
        Zone(
            id = "ruta3",
            name = "Ruta 3",
            clickRequirement = 150L,
            wildSpeciesIds = listOf("spearow", "jigglypuff")
        ),
        Zone(
            id = "mt_moon",
            name = "Mt.Moon",
            clickRequirement = 160L,
            wildSpeciesIds = listOf("zubat", "geodude"),
            // Cueva: los salvajes aparecen caminando, sin modo hierba alta.
            alwaysWilds = true
        ),
        Zone(
            id = "ruta4",
            name = "Ruta 4",
            clickRequirement = 210L,
            wildSpeciesIds = listOf("spearow", "rattata")
        ),
        Zone(
            id = "ciudad_celeste",
            name = "Ciudad Celeste",
            clickRequirement = 260L,
            wildSpeciesIds = emptyList(),
            gymId = "misty"
        ),
        Zone(
            id = "ruta5",
            name = "Ruta 5 - Vía Subterránea",
            clickRequirement = 320L,
            wildSpeciesIds = listOf("pidgey", "meowth", "oddish")
        ),
        Zone(
            id = "ruta6",
            name = "Ruta 6",
            clickRequirement = 380L,
            wildSpeciesIds = listOf("pidgey", "rattata", "oddish")
        ),
        Zone(
            id = "ciudad_carmin",
            name = "Ciudad Carmín",
            clickRequirement = 440L,
            wildSpeciesIds = emptyList(),
            gymId = "surge"
        )
    )
}

/**
 * Catálogo de gimnasios. Brock (Ciudad Plateada) usa la lluvia de rocas, Misty
 * (Ciudad Celeste, feature-35) el minijuego de anillos rítmicos y Lt. Surge
 * (Ciudad Carmín, feature-41) la cuadrícula de Voltorb; los cinco restantes
 * llegarán con el contenido de Kanto.
 */
object GymCatalog {

    val brock = Gym(
        id = "brock",
        leaderName = "Brock",
        type = PokemonType.ROCK,
        coinReward = 40L,
        leaderHp = 80,
        mechanic = GymMechanic.PROJECTILES,
        columns = 5,
        spawnIntervalMs = 650L,
        projectileTravelMs = 1_800L,
        projectileDamage = 1,
        // Aciertos para romper la postura: ~2 rocas por ráfaga cada 650 ms.
        postureHits = 14,
        exposureDurationMs = 2_500L,
        projectilesPerSpawn = 2,
        rageSpawnIntervalMs = 450L,
        rageProjectilesPerSpawn = 2,
        rageProjectileTravelMs = 1_100L
    )

    /**
     * Misty: sin proyectiles; los anillos se cierran con cadencias distintas y
     * cada fallo resta vida. Los campos de proyectiles quedan a 0 porque esta
     * mecánica no los lee.
     */
    val misty = Gym(
        id = "misty",
        leaderName = "Misty",
        type = PokemonType.WATER,
        coinReward = 60L,
        leaderHp = 90,
        mechanic = GymMechanic.TIMING_RINGS,
        columns = 1,
        spawnIntervalMs = 0L,
        projectileTravelMs = 0L,
        projectileDamage = 0,
        // Aciertos para romper la postura: un anillo cada 750 ms.
        postureHits = 12,
        exposureDurationMs = 2_500L,
        ringSpawnIntervalMs = 750L,
        ringTravelMinMs = 950L,
        ringTravelMaxMs = 1_700L,
        ringWindowMs = 170L,
        ringMissDamage = 1,
        rageRingSpawnIntervalMs = 520L,
        rageRingTravelMinMs = 700L,
        rageRingTravelMaxMs = 1_200L
    )

    /**
     * Lt. Surge: cuadrícula 4×4 de Voltorb que se encienden sin parar. No hay
     * tope de cargados a la vez —el único límite son las 16 celdas—, así que si
     * el jugador se atrasa los Voltorb se le apilan. Nunca empiezan dos en el
     * mismo instante: la cadencia los escalona. Apagar uno a tiempo evita el
     * daño; tocar una celda apagada descarga. En furia suben cadencia y calor.
     *
     * Los valores están sin equilibrar a propósito: se ajustan jugando.
     */
    val surge = Gym(
        // Clave estable de la medalla y del guardado. No cambiar sin migrar.
        id = "surge",
        // Mecánica de defensa: es el único gimnasio que usa la cuadrícula.
        mechanic = GymMechanic.VOLTORB_GRID,
        // Nombre que sale en el HUD y en el overlay de entrada al gimnasio.
        leaderName = "Lt. Surge",
        // Tipo del líder: decide el multiplicador de daño en la fase expuesta.
        type = PokemonType.ELECTRIC,
        // Monedas que paga la primera victoria.
        coinReward = 80L,
        // Vida total: cuántas exposiciones hacen falta para ganarle.
        leaderHp = 120,
        // Columnas de la cuadrícula. Con rows da las 16 celdas del tablero.
        columns = 4,
        // Filas de la cuadrícula.
        rows = 4,
        // Aciertos seguidos para romper la postura y abrir su ventana: un Voltorb
        // cada 520 ms, así que la ronda ronda los 8 segundos si no falla.
        postureHits = 16,
        // Cuánto dura la fase expuesta, la única ventana para golpearle.
        exposureDurationMs = 2_500L,

        // ── Tiempos del Voltorb ────────────────────────────────────────────
        // ADVERTENCIA: la ventana debe quedar SIEMPRE por encima de la cadencia
        // (voltorbWindowMs > voltorbSpawnIntervalMs, y lo mismo en furia). Si se
        // igualan o se invierten, las cargas dejan de solaparse y el tablero
        // vuelve a tener un solo Voltorb encendido; además falla el test
        // `surgeGym_usesAFourByFourGridWithoutConcurrencyCap`.
        //
        // Regla práctica: ventana / cadencia = Voltorb encendidos a la vez si el
        // jugador va al día. Hoy 1500/400 ≈ 3,75 en normal y 900/260 ≈ 3,5 en
        // furia. Si no llega a apagarlos, se le apilan hasta llenar las 16 celdas.

        // Ventana de un Voltorb cargado: desde que se enciende hasta que explota.
        voltorbWindowMs = 1_500L,
        // La misma ventana durante la furia (bajo el 25 % de vida).
        rageVoltorbWindowMs = 950L,
        // Cadencia entre encendidos: nunca dos empiezan en el mismo instante.
        // Con el tablero lleno el turno queda pendiente y entra al liberarse.
        voltorbSpawnIntervalMs = 520L,
        // La misma cadencia durante la furia: es la palanca de dificultad real.
        rageVoltorbSpawnIntervalMs = 340L,
        // HP que resta cada Voltorb que explota.
        voltorbExplosionDamage = 1,
        // HP que resta tocar una celda apagada.
        voltorbShockDamage = 1,

        // Campos que esta mecánica no lee: los usan proyectiles y anillos.
        spawnIntervalMs = 0L,
        projectileTravelMs = 0L,
        projectileDamage = 0
    )

    /** Todos los gimnasios del catálogo. */
    val all: List<Gym> = listOf(brock, misty, surge)

    private val byId: Map<String, Gym> = all.associateBy { it.id }

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
