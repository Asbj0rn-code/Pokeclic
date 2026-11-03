package com.dsrs.pokeclic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato del contenido de los catálogos: el recorrido de Kanto y la progresión
 * de clics son decisiones de diseño y los tests las bloquean. Si cambia el
 * recorrido pactado, cambia el test (nunca al revés).
 */
class CatalogTest {

    /**
     * Orden de las zonas tras la presentación y la elección del starter
     * (feature-29): el índice del listado es la posición en el mapa, así que el
     * orden exacto es el pedido en la petición original y no se reordena.
     */
    @Test
    fun zoneCatalog_keepsTheKantoOrder() {
        val names = ZoneCatalog.zones.map { it.name }
        assertEquals(
            listOf(
                "Pueblo Paleta",
                "Ruta 1",
                "Ciudad Verde",
                "Ruta 2",
                "Bosque Verde",
                "Ciudad Plateada",
                "Ruta 3",
                "Mt.Moon",
                "Ruta 4",
                "Ciudad Celeste",
                "Ruta 5 - Vía Subterránea",
                "Ruta 6"
            ),
            names
        )
    }

    /**
     * Ciudad Celeste aloja el gimnasio de Misty y sigue a la Ruta 4. El "Avanzar"
     * de la ciudad queda bloqueado hasta ganar el combate en la run actual.
     */
    @Test
    fun zoneCatalog_cityCelesteFollowsRoute4AndHasMistysGym() {
        val zones = ZoneCatalog.zones
        val cityIndex = zones.indexOfFirst { it.id == "ciudad_celeste" }

        assertEquals("Ruta 4", zones[cityIndex - 1].name)
        assertEquals("misty", zones[cityIndex].gymId)
    }

    /**
     * La Ruta 5 sigue al gimnasio de Ciudad Celeste y ya no cierra el recorrido:
     * la Ruta 6 viene después (feature-40).
     */
    @Test
    fun zoneCatalog_route5FollowsCeleste() {
        val zones = ZoneCatalog.zones
        val routeIndex = zones.indexOfFirst { it.id == "ruta5" }

        assertEquals("ruta5", zones[routeIndex].id)
        assertEquals("Ruta 5 - Vía Subterránea", zones[routeIndex].name)
        assertEquals("ciudad_celeste", zones[routeIndex - 1].id)
    }

    /**
     * La Ruta 6 cierra el recorrido justo después de la Ruta 5. Al ser la última
     * zona, ya no ofrece "Avanzar" (feature-40).
     */
    @Test
    fun zoneCatalog_route6FollowsRoute5AndClosesTheRoute() {
        val zones = ZoneCatalog.zones
        val route = zones.last()

        assertEquals("ruta6", route.id)
        assertEquals("Ruta 6", route.name)
        assertEquals("ruta5", zones[zones.lastIndex - 1].id)
    }

    /** Cada líder conserva su mecánica: Brock rocas, Misty anillos. */
    @Test
    fun gymCatalog_keepsTheMechanicOfEachLeader() {
        assertEquals(GymMechanic.PROJECTILES, GymCatalog.brock.mechanic)
        assertEquals(GymMechanic.TIMING_RINGS, GymCatalog.misty.mechanic)
    }

    /** Regla de progresión: cada zona pide más clics que la anterior. */
    @Test
    fun zoneCatalog_requirementsStrictlyIncrease() {
        val requirements = ZoneCatalog.zones.map { it.clickRequirement }
        requirements.zip(requirements.drop(1)).forEach { (current, next) ->
            assertTrue(
                "La zona que pide $next clics debe pedir más que la anterior ($current).",
                next > current
            )
        }
    }

    /** Toda especie salvaje referenciada por una zona existe en el catálogo. */
    @Test
    fun zoneCatalog_wildSpeciesAllResolve() {
        ZoneCatalog.zones.forEach { zone ->
            zone.wildSpeciesIds.forEach { id ->
                SpeciesCatalog.require(id)
            }
        }
    }

    /** Todo gimnasio referenciado por una zona existe en el catálogo de gimnasios. */
    @Test
    fun zoneCatalog_gymReferencesResolve() {
        ZoneCatalog.zones.forEach { zone ->
            zone.gymId?.let { GymCatalog.require(it) }
        }
    }
}
