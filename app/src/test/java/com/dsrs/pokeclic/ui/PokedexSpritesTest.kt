package com.dsrs.pokeclic.ui

import com.dsrs.pokeclic.model.SpeciesCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El sprite del salvaje sale de `Pokedex/` por número de la Pokédex nacional.
 * Si una especie del catálogo apunta a un número sin sprite, la captura cae al
 * placeholder del gimnasio: estos tests lo impiden.
 *
 * La Pokédex pinta las 151 filas, no solo las del catálogo de juego, así que
 * el mapa completo también queda bloqueado acá.
 */
class PokedexSpritesTest {

    @Test
    fun everyDexNumber_hasASprite() {
        (1..151).forEach { dexNumber ->
            assertNotNull("Sin sprite para #$dexNumber", pokedexSprite(dexNumber))
        }
    }

    @Test
    fun outOfRangeDexNumbers_haveNoSprite() {
        assertNull(pokedexSprite(0))
        assertNull(pokedexSprite(152))
    }

    @Test
    fun everyCatalogSpecies_hasAPokedexSprite() {
        SpeciesCatalog.all.forEach { species ->
            assertNotNull(
                "Sin sprite para ${species.id} (#${species.dexNumber})",
                pokedexSprite(species.dexNumber)
            )
        }
    }

    @Test
    fun everyCatalogSpecies_hasAUniqueDexNumber() {
        val dexNumbers = SpeciesCatalog.all.map { it.dexNumber }
        assertEquals(dexNumbers.size, dexNumbers.toSet().size)
    }
}
