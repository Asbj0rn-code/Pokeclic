/**
 * Proyección de la Pokédex: convierte el catálogo y el historial del jugador
 * en lo que la pantalla necesita, sin depender de Android ni del ViewModel.
 *
 * Las 151 filas se reconstruyen solo cuando cambia alguno de los conjuntos que
 * las definen; el resto de `derive` reutiliza la última proyección.
 */
package com.dsrs.pokeclic.model

/** Filas y contadores listos para la pantalla de Pokédex. */
data class PokedexProjection(
    val entries: List<PokedexRow>,
    val seenCount: Int,
    val caughtCount: Int
) {
    companion object {
        val EMPTY: PokedexProjection = PokedexProjection(emptyList(), 0, 0)
    }
}

/**
 * Proyector con memoización por firma.
 *
 * La firma es la posesión de la run (`ownedSpecies` + `starterSpeciesId`) y la
 * historia permanente (`seenSpecies` + `caughtSpecies`), que es exactamente lo
 * que decide el estado de cada fila. No es thread-safe: el ViewModel lo usa
 * desde el hilo principal.
 *
 * `seenCount` cuenta toda especie conocida (SEEN, REGISTERED y CAUGHT) y
 * `caughtCount` cuenta las capturadas **alguna vez** (CAUGHT y REGISTERED),
 * de modo que un reset no hace bajar el contador.
 */
class PokedexProjector(private val catalog: PokedexCatalog) {

    private data class History(
        val ownedSpecies: Set<String>,
        val starterSpeciesId: String?,
        val seenSpecies: Set<String>,
        val caughtSpecies: Set<String>
    )

    private var lastHistory: History? = null
    private var lastProjection: PokedexProjection = PokedexProjection.EMPTY

    fun project(
        ownedSpecies: Set<String>,
        starterSpeciesId: String?,
        seenSpecies: Set<String>,
        caughtSpecies: Set<String>
    ): PokedexProjection {
        val history = History(ownedSpecies, starterSpeciesId, seenSpecies, caughtSpecies)
        if (history == lastHistory) return lastProjection

        val rows = catalog.rowsFor(ownedSpecies, starterSpeciesId, seenSpecies, caughtSpecies)
        lastProjection = PokedexProjection(
            entries = rows,
            seenCount = rows.count { it.status != DexStatus.UNKNOWN },
            caughtCount = rows.count {
                it.status == DexStatus.CAUGHT || it.status == DexStatus.REGISTERED
            }
        )
        lastHistory = history
        return lastProjection
    }
}
