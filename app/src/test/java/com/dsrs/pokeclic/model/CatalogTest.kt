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
                "Ruta 6",
                "Ciudad Carmín"
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
     * Ciudad Carmín cierra el recorrido justo después de la Ruta 6 y aloja el
     * gimnasio de Lt. Surge (feature-41). Al ser la última zona, ya no ofrece
     * "Avanzar".
     */
    @Test
    fun zoneCatalog_cityCarminFollowsRoute6AndClosesTheRoute() {
        val zones = ZoneCatalog.zones
        val city = zones.last()

        assertEquals("ciudad_carmin", city.id)
        assertEquals("Ciudad Carmín", city.name)
        assertEquals("surge", city.gymId)
        assertEquals("ruta6", zones[zones.lastIndex - 1].id)
    }

    /** Cada líder conserva su mecánica: Brock rocas, Misty anillos, Surge Voltorb. */
    @Test
    fun gymCatalog_keepsTheMechanicOfEachLeader() {
        assertEquals(GymMechanic.PROJECTILES, GymCatalog.brock.mechanic)
        assertEquals(GymMechanic.TIMING_RINGS, GymCatalog.misty.mechanic)
        assertEquals(GymMechanic.VOLTORB_GRID, GymCatalog.surge.mechanic)
    }

    /**
     * El tablero de Surge es 4×4 y no tiene tope de Voltorb cargados: el límite
     * son las 16 celdas. La ventana supera a la cadencia —las cargas se solapan y
     * se apilan si el jugador se atrasa—, pero la cadencia sigue siendo mayor que
     * cero, así que nunca empiezan dos a la vez.
     */
    @Test
    fun surgeGym_usesAFourByFourGridWithoutConcurrencyCap() {
        val surge = GymCatalog.surge

        assertEquals(4, surge.rows)
        assertEquals(4, surge.columns)
        assertTrue(surge.voltorbSpawnIntervalMs > 0L)
        assertTrue(surge.voltorbWindowMs > surge.voltorbSpawnIntervalMs)
        assertEquals(PokemonType.ELECTRIC, surge.type)
        assertTrue(surge.rageVoltorbSpawnIntervalMs < surge.voltorbSpawnIntervalMs)
        assertTrue(surge.rageVoltorbWindowMs < surge.voltorbWindowMs)
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

    /**
     * Mt.Moon es la cueva: tiene salvajes al avanzar sin modo hierba alta. Solo
     * esa zona lleva la marca, así que un despiste en otra se nota aquí.
     */
    @Test
    fun zoneCatalog_onlyMountMoonHasWildsWithoutTallGrass() {
        assertTrue(
            ZoneCatalog.zones.single { it.id == "mt_moon" }.alwaysWilds
        )
        assertEquals(
            listOf("mt_moon"),
            ZoneCatalog.zones.filter { it.alwaysWilds }.map { it.id }
        )
        // Una cueva sin especies dejaría el bucle de caminar girando en vacío.
        ZoneCatalog.zones.filter { it.alwaysWilds }.forEach { zone ->
            assertTrue(
                "${zone.id}: una cueva necesita especies salvajes",
                zone.wildSpeciesIds.isNotEmpty()
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
