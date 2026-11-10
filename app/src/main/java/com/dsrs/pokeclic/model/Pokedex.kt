/**
 * Catálogo de la **Pokédex**: las 151 especies de la primera generación, con
 * número de Pokédex, nombre visible y **un único tipo para mostrar**.
 *
 * Es un catálogo de **solo lectura y de presentación**: decide qué se muestra
 * en la pantalla de Pokédex, no cómo se juega. La fuente de verdad del juego
 * sigue siendo `SpeciesCatalog` (captura, recompensas, tipos de combate).
 *
 * **Tipo de Pokédex separado del combate** (decisión de alcance): `typeName`
 * es el nombre del tipo tal como lo aporta el catálogo adaptado
 * (`assets/pokedex_catalogo.json`), conservado como texto para
 * **no tocar** `PokemonType` ni sus tablas de efectividad (los tipos Bicho,
 * Tierra, Lucha, Psíquico, Hielo, Fantasma y Dragón no existen en el enum del
 * combate).
 *
 * **Discrepancia temporal Caterpie / Weedle**: el catálogo de combate
 * (`SpeciesCatalog`) declara Caterpie de tipo GRASS y Weedle de POISON, pero
 * la Pokédex muestra ambos como Bicho según el JSON de la primera generación.
 * Esta incongruencia es deliberada en esta etapa: la Pokédex enseña el dato
 * original y el combate conserva sus valores provisionales; no conviene
 * leerla como coherencia completa entre ambas fuentes.
 *
 * **Discrepancia temporal de los tipos fuera de `PokemonType`**: especies cuyo
 * único tipo JSON no existe como constante de combate (Bicho/Tierra/Lucha/
 * Psíquico/Hielo/Fantasma/Dragón) se muestran igualmente por su nombre; su
 * color de presentación es una decoración de UI, no una regla de juego.
 *
 * Nada aquí depende de Android: idéntico tratamiento que el resto de `model/`.
 */
package com.dsrs.pokeclic.model

/** Estado de una especie en la Pokédex del jugador. */
enum class DexStatus {
    /** Nunca avistada ni obtenida: solo se muestra el número. */
    UNKNOWN,

    /** Avistada y sin atrapar: número, nombre e imagen; sin Pokéball. */
    SEEN,

    /** Atrapada: número, nombre, imagen y Pokéball como indicador. */
    CAUGHT
}

/**
 * Una especie de la Pokédex.
 *
 * @param speciesId clave estable e inmutable, coincide con los IDs con los que
 *   se guardan las partidas (`bulbasaur`, `pidgey`…). NO es el número de
 *   Pokédex: esa diferencia es deliberada.
 * @param dexNumber número de Pokédex (1..151), clave de ordenación y de
 *   presentación.
 * @param name nombre visible.
 * @param typeName nombre del tipo para mostrar, único por especie (decisión
 *   deliberada de la primera generación simplificada).
 */
data class PokedexSpecies(
    val speciesId: String,
    val dexNumber: Int,
    val name: String,
    val typeName: String
)

/**
 * Una fila de la Pokédex ya resuelta para la UI: catálogo + estado del
 * jugador. La especies desconocidas llevan `name`/`typeName` en `null` para
 * que la presentación no pueda filtrar información identificativa (ni si
 *quiera vía accesibilidad).
 */
data class PokedexRow(
    val dexNumber: Int,
    val speciesId: String,
    val name: String?,
    val typeName: String?,
    val status: DexStatus
)

/**
 * Estado de una fila de la Pokédex, derivado de la **posesión en la partida
 * actual** y del historial permanente:
 *
 * - **CAUGHT**: especie poseída **ahora** (en `ownedSpecies`) o el inicial
 *   elegido (`starterSpeciesId`). Tras un reset, solo el inicial conserva el
 *   estado: las demás especies pasan a SEEN aunque se hubieran capturado
 *   antes (no se usa `caughtSpecies` ni `rewardedSpecies`, que son historia
 *   permanente).
 * - **SEEN**: especie conocida (avistada alguna vez o con captura histórica)
 *   pero no poseída en la partida actual. `caughtSpecies` **sí** sirve para
 *   "reconocer" especies conocidas, pero **no** para darle el estado CAUGHT.
 * - **UNKNOWN**: especie nunca conocida.
 */
fun dexStatusFor(
    speciesId: String,
    ownedSpecies: Set<String>,
    starterSpeciesId: String?,
    seenSpecies: Set<String>,
    caughtSpecies: Set<String>
): DexStatus = when {
    // Posesión en la partida actual: lo poseído siempre gana, y el inicial
    // elegido es posesión por definición (sobrevive al reset).
    speciesId in ownedSpecies || speciesId == starterSpeciesId -> DexStatus.CAUGHT
    // Historia permanente: conocida de antes de esta run (avistada o con
    // captura histórica) queda como avistada, sin poseerla ahora.
    speciesId in seenSpecies || speciesId in caughtSpecies -> DexStatus.SEEN
    else -> DexStatus.UNKNOWN
}

/**
 * Catálogo completo y validado de la Pokédex.
 *
 * Invariantes (comprobadas al construir, para que un dato roto falle en
 * tests o al arrancar, no en producción):
 * - `dexNumber` consecutivos empezando en 1.
 * - `speciesId` únicos y en formato de clave estable (`[a-z0-9_]+`).
 * - `name` y `typeName` no vacíos.
 * - exactamente un tipo por especie (no hay campo múltiple en el modelo).
 */
data class PokedexCatalog(val entries: List<PokedexSpecies>) {

    init {
        val ids = mutableSetOf<String>()
        entries.forEachIndexed { index, species ->
            require(species.dexNumber == index + 1) {
                "El número de Pokédex debe ser consecutivo desde 1: " +
                    "entrada ${index + 1} con dexNumber ${species.dexNumber}"
            }
            require(species.name.isNotBlank()) {
                "La especie ${species.speciesId} no aporta nombre"
            }
            require(species.typeName.isNotBlank()) {
                "La especie ${species.speciesId} no aporta tipo"
            }
            // `Set.add` devuelve false cuando ya está presente: por eso el
            // `require` va directamente sobre su resultado (un `!ids.add`
            // lanzaría al REGISTERAR ids nuevos, justo lo contrario).
            require(ids.add(species.speciesId)) {
                "speciesId duplicado en la Pokédex: ${species.speciesId}"
            }
            require(species.speciesId.matches(ID_PATTERN)) {
                "speciesId con formato inválido: \"${species.speciesId}\""
            }
        }
    }

    val size: Int get() = entries.size

    /** Vista ordenada por número de Pokédex (1..151), lista para el listado. */
    val ordered: List<PokedexSpecies> get() = entries

    fun bySpeciesId(id: String): PokedexSpecies? =
        bySpeciesIdMap[id]

    private val bySpeciesIdMap: Map<String, PokedexSpecies> by lazy {
        entries.associateBy { it.speciesId }
    }

    /**
     * Resuelve la fila de una especie para la listas de la UI. Una fila la
     * pinta la UI, nunca el catálogo.
     */
    fun rowFor(
        species: PokedexSpecies,
        ownedSpecies: Set<String>,
        starterSpeciesId: String?,
        seenSpecies: Set<String>,
        caughtSpecies: Set<String>
    ): PokedexRow {
        val status = dexStatusFor(
            species.speciesId,
            ownedSpecies,
            starterSpeciesId,
            seenSpecies,
            caughtSpecies
        )
        return PokedexRow(
            dexNumber = species.dexNumber,
            speciesId = species.speciesId,
            name = if (status == DexStatus.UNKNOWN) null else species.name,
            typeName = if (status == DexStatus.UNKNOWN) null else species.typeName,
            status = status
        )
    }

    fun rowsFor(
        ownedSpecies: Set<String>,
        starterSpeciesId: String?,
        seenSpecies: Set<String>,
        caughtSpecies: Set<String>
    ): List<PokedexRow> =
        entries.map { rowFor(it, ownedSpecies, starterSpeciesId, seenSpecies, caughtSpecies) }

    companion object {
        private val ID_PATTERN = Regex("[a-z0-9_]+")

        /** Catálogo vacío: la pantalla muestra el error de carga. */
        val EMPTY: PokedexCatalog = PokedexCatalog(emptyList())
    }
}

/**
 * Resultado de la carga del catálogo: si [loadFailed], [catalog] es vacío y
 * la pantalla de Pokédex debe mostrar un mensaje de error sin bloquear el
 * juego (decisión de alcance: el juego sigue funcionando).
 */
data class PokedexCatalogLoad(
    val catalog: PokedexCatalog,
    val loadFailed: Boolean
) {
    companion object {
        /** Carga no disponible (por ejemplo, en previews o en tests sin asset). */
        val FAILED: PokedexCatalogLoad = PokedexCatalogLoad(PokedexCatalog.EMPTY, true)
    }
}
