package com.dsrs.pokeclic.ui

import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.Direction
import com.dsrs.pokeclic.model.ZoneCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Arte real (`drawable-nodpi`) de cada fondo horizontal, por `Zone.id`. El
 * catálogo solo guarda el id del recurso, así que el fichero se nombra aquí.
 */
private val HORIZONTAL_ARTWORK = mapOf(
    "ruta3" to "background_ruta3.png",
    "mt_moon" to "background_mt_moon.png"
)

/**
 * Ancho del PNG en píxeles de arte. El working dir de los tests es el módulo
 * `app`, con respaldo para invocaciones desde la raíz.
 */
private fun artworkWidth(fileName: String): Int {
    val candidates = listOf(
        File("src/main/res/drawable-nodpi/$fileName"),
        File("app/src/main/res/drawable-nodpi/$fileName")
    )
    val file = candidates.firstOrNull { it.exists() }
        ?: error("No se encontró el arte $fileName en $candidates")
    return ImageIO.read(file).width
}

/**
 * Todas las zonas tienen que poder animarse. Las que no tengan fondo propio caen
 * en [RouteBackgrounds.DEFAULT], pero la trayectoria tiene que cumplir las mismas
 * reglas que el default o la zona se queda sin movimiento.
 *
 * Con los dos ejes infinitos ya no hay ciclos que cerrar: lo que se comprueba es
 * que cada zona avanza, que cambia de dirección y que un clic de lado recorre lo
 * mismo que un clic recto.
 */
class RouteBackgroundsTest {

    @Test
    fun everyZone_resolvesToAUsableBackground() {
        ZoneCatalog.zones.forEach { zone ->
            val background = RouteBackgrounds.forZone(zone.id)
            assertTrue("${zone.id} sin arte", background.imageRes != 0)
            assertTrue("${zone.id} sin tramos", background.motion.legs.isNotEmpty())
        }
    }

    @Test
    fun everyZone_advancesAlongItsAxis() {
        ZoneCatalog.zones.forEach { zone ->
            val motion = RouteBackgrounds.forZone(zone.id).motion
            // Los tramos rectos son el avance: en vertical suben y en horizontal
            // se corren de lado, pero en los dos casos el ciclo recorre algo.
            val advance = motion.verticalDistanceAt(motion.cycleDistance.toDouble())
            assertTrue("${zone.id} sin avance", advance > 0.0)
        }
    }

    @Test
    fun everyZone_hasTheLegsItsAxisNeeds() {
        ZoneCatalog.zones.forEach { zone ->
            val motion = RouteBackgrounds.forZone(zone.id).motion
            assertTrue("${zone.id} sin tramo recto", motion.legs.any { !it.isLateral })
            // Las verticales actuales alternan recto y lateral (el entrenador
            // cambia de orientación). Un fondo horizontal puede ser recto; si
            // lleva laterales, los tests del modelo comprueban el giro.
            if (motion.axis == RouteScrollAxis.VERTICAL) {
                assertTrue("${zone.id} sin tramo lateral", motion.legs.any { it.isLateral })
            }
        }
    }

    @Test
    fun everyZone_movesTheSamePerClick() {
        // La ley del modelo: la distancia de un tramo ya no es un dato aparte,
        // sale de sus clics. Un clic de lado recorre lo mismo que uno recto.
        ZoneCatalog.zones.forEach { zone ->
            val motion = RouteBackgrounds.forZone(zone.id).motion
            motion.legs.forEach { leg ->
                assertEquals(
                    "${zone.id}: ${leg.clicks} clics no avanzan ${leg.distance}",
                    leg.clicks * RouteScrollDefaults.TILES_PER_CLICK,
                    leg.distance,
                    1e-6f
                )
            }
        }
    }

    @Test
    fun everyZone_hasASaneStart() {
        ZoneCatalog.zones.forEach { zone ->
            val background = RouteBackgrounds.forZone(zone.id)
            background.startAnchor?.let { anchor ->
                assertTrue(
                    "${zone.id}: startAnchor $anchor deja el arte fuera de la pantalla",
                    anchor in 0f..1f
                )
            }
            assertTrue(
                "${zone.id}: startOffset ${background.startOffset} corre la imagen más de una pantalla",
                // Hasta una pantalla entera: un arte del doble de ancho que la
                // pantalla necesita ±1 para dejar su marco izquierdo (o derecho)
                // bajo el entrenador.
                abs(background.startOffset) <= 1f
            )
        }
    }

    @Test
    fun aZoneWithOnlyArtwork_usesTheStandardWalk() {
        // La forma mínima de dar de alta una zona: solo el arte. Hereda la
        // trayectoria estándar y el arranque de siempre.
        val minimal = ZoneBackground(imageRes = R.drawable.background_bosque_verde)
        assertSame(RouteScrollDefaults.MOTION, minimal.motion)
        assertEquals(0, minimal.seamCutRow)
        assertEquals(null, minimal.startAnchor)
        assertEquals(0f, minimal.startOffset, 0f)
    }

    @Test
    fun catalogKeys_matchRealZoneIds() {
        // Un id mal escrito en el catálogo caería en DEFAULT sin avisar.
        val zoneIds = ZoneCatalog.zones.map { it.id }.toSet()
        RouteBackgrounds.configuredZoneIds.forEach { id ->
            assertTrue("'$id' no es una zona del catálogo", id in zoneIds)
        }
    }

    @Test
    fun puebloPaleta_usesItsOwnArtwork() {
        assertEquals(
            R.drawable.background_pallet_town,
            RouteBackgrounds.forZone("pueblo_paleta").imageRes
        )
    }

    @Test
    fun onlyZonesWithAnEntry_showTheAnimatedBackground() {
        // El fondo animado se muestra en zonas con fauna y, además, en las que
        // tienen arte propio: Pueblo Paleta y Ciudad Verde no tienen encuentros y
        // aun así lo enseñan. Las ciudades sin entrada siguen con el fondo liso.
        assertTrue(RouteBackgrounds.hasOwnArtwork("pueblo_paleta"))
        assertTrue(RouteBackgrounds.hasOwnArtwork("ciudad_verde"))
        assertTrue(RouteBackgrounds.hasOwnArtwork("bosque_verde"))
        assertTrue(RouteBackgrounds.hasOwnArtwork("ruta3"))
        assertFalse(RouteBackgrounds.hasOwnArtwork("ruta1"))
        assertFalse(RouteBackgrounds.hasOwnArtwork("zona_inexistente"))
    }

    @Test
    fun rutaTres_usesItsOwnArtwork() {
        assertEquals(
            R.drawable.background_ruta3,
            RouteBackgrounds.forZone("ruta3").imageRes
        )
    }

    @Test
    fun rutaTres_scrollsSideways() {
        // La ruta 3 es la primera zona que avanza en horizontal.
        assertEquals(
            RouteScrollAxis.HORIZONTAL,
            RouteBackgrounds.forZone("ruta3").motion.axis
        )
    }

    @Test
    fun rutaTres_startsAtTheLeftFrameOnTheCentreLine() {
        // -0,5 de ancho de pantalla lleva al entrenador al **borde izquierdo** del
        // arte; el ancla deja el **eje central horizontal** del arte a su altura.
        val background = RouteBackgrounds.forZone("ruta3")
        assertEquals(-0.5f, background.startOffset, 1e-6f)
        assertEquals(0.057f, background.startAnchor!!, 1e-6f)
    }

    @Test
    fun rutaTres_closesItsLoop() {
        // El bucle puede durar varias vueltas: lo que cierra es el superciclo
        // completo. Sus rectos suman vueltas enteras al arte (15 clics cada una:
        // 1152 px / 76,8) y los laterales se compensan entre sí.
        val motion = RouteBackgrounds.forZone("ruta3").motion
        val cycle = motion.cycleDistance.toDouble()
        val straightClicks = motion.legs
            .filterNot { it.isLateral }
            .sumOf { it.clicks.toDouble() }
        val clicksPerArtWidth = 15.0

        assertTrue(
            "Los rectos deben avanzar al menos un ancho de arte",
            straightClicks >= clicksPerArtWidth
        )
        assertEquals(
            "Los clics rectos deben sumar vueltas enteras al arte",
            0.0,
            straightClicks % clicksPerArtWidth,
            1e-9
        )
        assertEquals(
            "Los laterales deben volver a la misma altura",
            0f,
            motion.lateralDistanceAt(cycle),
            1e-6f
        )
    }

    @Test
    fun rutaTres_readsItsDirectionsInTheHorizontalFrame() {
        // La dirección sale de cada tramo, leída en el marco girado: recto →
        // derecha, LEFT → arriba, RIGHT → abajo. Se comprueba la ruta que tenga la
        // zona en cada momento, sin fijar un diseño concreto.
        val motion = RouteBackgrounds.forZone("ruta3").motion
        val expected = mapOf(
            Direction.BACK to Direction.RIGHT,
            Direction.LEFT to Direction.BACK,
            Direction.RIGHT to Direction.FRONT
        )

        var step = 1L
        motion.legs.forEach { leg ->
            repeat(leg.clicks.toInt()) {
                assertEquals(
                    "clic $step del tramo ${leg.direction}",
                    expected.getValue(leg.direction),
                    motion.directionForStep(step)
                )
                step++
            }
        }
    }

    @Test
    fun mtMoon_usesItsOwnArtwork() {
        assertEquals(
            R.drawable.background_mt_moon,
            RouteBackgrounds.forZone("mt_moon").imageRes
        )
    }

    @Test
    fun mtMoon_scrollsSideways() {
        // Segundo fondo horizontal: el arte es más ancho (2304 px) que el de Ruta 3.
        assertEquals(
            RouteScrollAxis.HORIZONTAL,
            RouteBackgrounds.forZone("mt_moon").motion.axis
        )
    }

    @Test
    fun mtMoon_closesItsLoop() {
        // Une clic = 76,8 px de arte, salga del drawable que salga. Con 2304 px de
        // ancho, una vuelta al dibujo son 2304 / 76,8 = 30 clics rectos. Los
        // laterales tienen que volver a la misma altura.
        val motion = RouteBackgrounds.forZone("mt_moon").motion
        val cycle = motion.cycleDistance.toDouble()
        val straightClicks = motion.legs
            .filterNot { it.isLateral }
            .sumOf { it.clicks.toDouble() }
        val clicksPerArtWidth = 30.0

        assertTrue(
            "Los rectos deben avanzar al menos un ancho de arte",
            straightClicks >= clicksPerArtWidth
        )
        assertEquals(
            "Los clics rectos deben sumar vueltas enteras al arte",
            0.0,
            straightClicks % clicksPerArtWidth,
            1e-9
        )
        assertEquals(
            "Los laterales deben volver a la misma altura",
            0f,
            motion.lateralDistanceAt(cycle),
            1e-6f
        )
    }

    @Test
    fun mtMoon_startsOnTheLeftFrameOfItsArtworkOnTheCentreLine() {
        // El arte mide dos pantallas de ancho, así que poner al entrenador sobre su
        // marco izquierdo pide un corrimiento de una pantalla entera: -0,5 dejaría
        // el borde del dibujo en el borde de la pantalla (25 % del arte) y 0 en el
        // centro. El ancla hereda la calibración de Ruta 3, que deja el eje central
        // horizontal del arte a la altura del entrenador.
        val background = RouteBackgrounds.forZone("mt_moon")
        assertEquals(-1f, background.startOffset, 1e-6f)
        assertEquals(0.057f, background.startAnchor!!, 1e-6f)
    }

    @Test
    fun horizontalZones_closeTheirLoopAgainstTheRealArtwork() {
        // Un clic recorre CAMERA_HEIGHT / CLICKS_PER_TILE px de arte, así que una
        // vuelta completa al dibujo son `ancho * CLICKS_PER_TILE / CAMERA_HEIGHT`
        // clics rectos, sea cual sea el tamaño del PNG. Se mide sobre el fichero
        // real: si el arte se reemplaza por otro más ancho o más estrecho, este
        // test avisa de que los tramos ya no cierran el bucle.
        val horizontalZones = RouteBackgrounds.configuredZoneIds
            .filter { RouteBackgrounds.forZone(it).motion.axis == RouteScrollAxis.HORIZONTAL }
            .toSet()
        assertEquals(
            "Registrar en HORIZONTAL_ARTWORK el arte de cada fondo horizontal",
            horizontalZones,
            HORIZONTAL_ARTWORK.keys
        )

        horizontalZones.forEach { zone ->
            val motion = RouteBackgrounds.forZone(zone).motion
            val clicksPerArtWidth = artworkWidth(HORIZONTAL_ARTWORK.getValue(zone)) *
                RouteScrollDefaults.CLICKS_PER_TILE / RouteScrollDefaults.CAMERA_HEIGHT
            val straightClicks = motion.legs
                .filterNot { it.isLateral }
                .sumOf { it.clicks.toDouble() }

            assertTrue("$zone: el bucle no avanza", straightClicks > 0.0)
            assertEquals(
                "$zone: los rectos deben sumar vueltas enteras al arte " +
                    "(una vuelta = $clicksPerArtWidth clics)",
                0.0,
                straightClicks % clicksPerArtWidth,
                1e-6
            )
            assertEquals(
                "$zone: los laterales deben volver a la misma altura",
                0f,
                motion.lateralDistanceAt(motion.cycleDistance.toDouble()),
                1e-6f
            )
        }
    }

    @Test
    fun ciudadVerde_usesItsOwnArtwork() {
        assertEquals(
            R.drawable.background_ciudad_verde,
            RouteBackgrounds.forZone("ciudad_verde").imageRes
        )
    }

    @Test
    fun ciudadVerde_startsAtTheBottomOfItsArtwork() {
        // Arranca por el tramo bajo del arte, no por el principio.
        assertEquals(0.5f, RouteBackgrounds.forZone("ciudad_verde").startAnchor!!, 0f)
    }

    @Test
    fun ciudadVerde_returnsToTheCentreLineBecauseBothSidesBalance() {
        // 7 clics a cada lado: el zigzag no acumula deriva lateral. Si la
        // trayectoria deja de estar balanceada, el personaje quedará cada vuelta
        // más corrido y este test lo delatará.
        val motion = RouteBackgrounds.forZone("ciudad_verde").motion
        assertEquals(0f, motion.lateralDistanceAt(motion.cycleDistance.toDouble()), 1e-4f)
    }

    @Test
    fun bosqueVerde_usesItsOwnArtwork() {
        assertEquals(
            R.drawable.background_bosque_verde,
            RouteBackgrounds.forZone("bosque_verde").imageRes
        )
    }

    @Test
    fun bosqueVerde_startsAtTheBottomOfItsArtwork() {
        assertEquals(0.5f, RouteBackgrounds.forZone("bosque_verde").startAnchor!!, 0f)
    }

    @Test
    fun bosqueVerde_startsWithTheImageShiftedToTheRight() {
        // El signo es el del personaje: negativo = el personaje arranca a la
        // izquierda del centro, así que la imagen se ve corrida a la derecha.
        assertTrue(RouteBackgrounds.forZone("bosque_verde").startOffset < 0f)
    }

    @Test
    fun bosqueVerde_followsTheDesignedPath() {
        // La secuencia, tramo a tramo, con el clic que estrena cada uno.
        val motion = RouteBackgrounds.forZone("bosque_verde").motion
        val expected = mapOf(
            1L to Direction.BACK,     // 2 recto
            3L to Direction.RIGHT,    // 3 derecha
            5L to Direction.RIGHT,
            6L to Direction.BACK,     // 2 recto
            8L to Direction.RIGHT,    // 2 derecha
            10L to Direction.BACK,    // 6 recto
            16L to Direction.LEFT,    // 6 izquierda
            21L to Direction.LEFT,
            22L to Direction.BACK,    // 6 recto
            28L to Direction.RIGHT,   // 5 derecha
            33L to Direction.BACK,    // 5 recto
            38L to Direction.LEFT,    // 4 izquierda
            42L to Direction.BACK,    // 4 recto
            45L to Direction.BACK     // último clic del ciclo
        )
        expected.forEach { (step, direction) ->
            assertEquals("clic $step", direction, motion.directionForStep(step))
        }
    }

    @Test
    fun bosqueVerde_returnsToTheCentreLineBecauseBothSidesBalance() {
        // 10 clics a cada lado: el zigzag no acumula deriva. Si algún día deja de
        // estar balanceado, el personaje quedará cada vuelta más corrido (el
        // modelo lo permite) y este test lo delatará.
        val motion = RouteBackgrounds.forZone("bosque_verde").motion
        assertEquals(0f, motion.lateralDistanceAt(motion.cycleDistance.toDouble()), 1e-4f)
    }

    @Test
    fun otherZones_keepStartingAtTheTopOfTheirArtwork() {
        // El ancla es opt-in: las zonas sin entrada propia no cambian de aspecto.
        assertEquals(null, RouteBackgrounds.DEFAULT.startAnchor)
        assertEquals(0f, RouteBackgrounds.DEFAULT.startOffset, 0f)
    }

    @Test
    fun unknownZone_fallsBackToTheDefault() {
        assertEquals(RouteBackgrounds.DEFAULT, RouteBackgrounds.forZone("zona_inexistente"))
        assertEquals(RouteBackgrounds.DEFAULT, RouteBackgrounds.forZone(""))
    }
}
