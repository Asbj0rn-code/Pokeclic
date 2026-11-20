package com.dsrs.pokeclic.ui

import kotlin.math.floor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Punto de partida de una zona: dónde cae el **borde inferior del arte** al entrar,
 * con cero clics. Es cálculo puro, así que se comprueba sin emulador.
 *
 * La cuenta vive aparte de la capa de dibujo a propósito: el ancla solo decide por
 * dónde se empieza a ver el arte, nunca la trayectoria ni la dirección.
 */
class RouteStartShiftTest {

    private val tileH = 1800f
    private val screenH = 2400f

    /** Alto del arte de Ruta 3, en px de arte (el mismo de Mt. Moon). */
    private val artH = 1920f

    private fun shiftFor(screenY: Float): Float = routeStartShift(tileH, screenY)

    @Test
    fun startingAtTheTopOfTheArtwork_isTheZeroShift() {
        // El renderer pasa `tileH` cuando la zona no tiene ancla: el arte arranca
        // por su principio, como siempre.
        assertEquals(0f, shiftFor(tileH), 0f)
    }

    @Test
    fun theArtworkBottomLandsWhereItIsAsked() {
        val shift = shiftFor(screenH / 2f)
        // La fila final del arte se dibuja en `tileH - shift`: tiene que caer en el
        // punto pedido.
        assertEquals(screenH / 2f, tileH - shift, 0.001f)
    }

    @Test
    fun shift_alwaysStaysInsideOneTile() {
        listOf(-4000f, -1800f, 0f, 137f, 1200f, 1800f, 2400f, 9000f).forEach { y ->
            val shift = shiftFor(y)
            assertTrue("y=$y fuera de baldosa: $shift", shift >= 0f && shift < tileH)
        }
    }

    /**
     * La regresión que motivó todo esto: el encuadre de una zona tiene que ser el
     * mismo en cualquier móvil. Antes el ancla era una fracción del alto de
     * pantalla y el arte se movía bajo los pies del entrenador al cambiar de
     * móvil; ahora se mide en alturas de cámara y la distancia entrenador ↔ arte
     * no depende del tamaño ni del aspecto de la pantalla.
     */
    @Test
    fun theArtworkUnderTheTrainerIsTheSameOnEveryScreen() {
        val screens = listOf(
            1080f to 2400f, // el emulador de referencia
            1080f to 1920f, // 16:9
            1080f to 2340f, // 19.5:9
            1440f to 3200f, // 1440p 20:9
            480f to 800f    // gama baja
        )
        val anchor = RouteBackgrounds.forZone("ruta3").startAnchor!!
        val expected = trainerToArtworkBottom(anchor, 1080f, 2400f)

        screens.forEach { (width, height) ->
            val distance = trainerToArtworkBottom(anchor, width, height)
            assertEquals(
                "$width x $height: el paisaje se mueve bajo el entrenador",
                expected,
                distance,
                0.05f
            )
        }
    }

    /** Distancia con signo, en px de arte, del entrenador al borde inferior del arte. */
    private fun trainerToArtworkBottom(anchor: Float, width: Float, height: Float): Float {
        val frame = worldFrame(width, height)
        val tile = artH * frame.scale
        val trainerY = frame.screenY(WorldDefaults.TRAINER_CENTER_ART_Y)
        val shift = routeStartShift(
            tile,
            frame.screenY(anchor * RouteScrollDefaults.CAMERA_HEIGHT)
        )

        // Los bordes inferiores de las baldosas caen en `tile - shift + k·tile`: se
        // toma el que queda por encima del entrenador, que es el que lo enmarca.
        val base = tile - shift
        val k = floor((trainerY - base) / tile)
        val bottomScreenY = base + k * tile

        val trainerArtY = trainerY / frame.scale + frame.artTop
        val bottomArtY = bottomScreenY / frame.scale + frame.artTop
        var distance = (trainerArtY - bottomArtY) % artH
        if (distance > artH / 2f) distance -= artH
        if (distance < -artH / 2f) distance += artH
        return distance
    }
}
