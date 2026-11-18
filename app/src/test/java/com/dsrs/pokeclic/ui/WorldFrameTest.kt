package com.dsrs.pokeclic.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La proyección del mundo tiene que ser la misma en cualquier móvil: la cámara se
 * escala **solo con el ancho** y se centra en vertical, así que la relación entre
 * el entrenador y el paisaje no depende del alto de pantalla ni de la densidad.
 *
 * Es cálculo puro: no hace falta emulador.
 */
class WorldFrameTest {

    private val cameraWidth = RouteScrollDefaults.CAMERA_WIDTH
    private val cameraHeight = RouteScrollDefaults.CAMERA_HEIGHT

    private val screens = listOf(
        480f to 800f,      // gama baja 16:9
        720f to 1280f,     // 720p 16:9
        1080f to 1920f,    // 1080p 16:9
        1080f to 2340f,    // 19.5:9
        1080f to 2400f,    // 20:9, el emulador de referencia
        1170f to 2532f,    // 19.5:9 real
        1440f to 3200f,    // 1440p 20:9
        1600f to 2560f,    // tablet vertical
        2560f to 1600f     // tablet horizontal
    )

    @Test
    fun theScaleDependsOnlyOnTheWidth() {
        screens.forEach { (width, height) ->
            assertEquals(
                "$width x $height: la escala no sale del ancho",
                width / cameraWidth,
                worldFrame(width, height).scale,
                1e-6f
            )
        }
        // Mismo ancho y altos muy distintos: misma escala.
        assertEquals(
            worldFrame(1080f, 2400f).scale,
            worldFrame(1080f, 1920f).scale,
            0f
        )
    }

    @Test
    fun theReferenceDevice_rendersTheCameraWidthOneToOne() {
        // El emulador de referencia: 1080 px de ancho, cámara de 1152.
        val frame = worldFrame(1080f, 2400f)
        assertEquals(1080f / 1152f, frame.scale, 1e-6f)
        assertEquals(-320f, frame.artTop, 1e-3f)
    }

    @Test
    fun theCameraIsCentredVertically() {
        screens.forEach { (width, height) ->
            val frame = worldFrame(width, height)
            assertEquals(
                "$width x $height: el centro de la cámara no cae en el centro de la pantalla",
                height / 2f,
                frame.screenY(cameraHeight / 2f),
                0.01f
            )
        }
    }

    @Test
    fun theTrainerKeepsItsFractionOfTheWidthOnEveryDevice() {
        screens.forEach { (width, height) ->
            val frame = worldFrame(width, height)
            val fraction = frame.screenPx(WorldDefaults.TRAINER_BOX_ART_SIZE) / width
            assertEquals(
                "$width x $height: el entrenador cambia de tamaño relativo",
                WorldDefaults.TRAINER_BOX_ART_SIZE / cameraWidth,
                fraction,
                1e-6f
            )
        }
    }

    @Test
    fun theTrainerSitsAtTheSameArtworkPointOnEveryDevice() {
        screens.forEach { (width, height) ->
            val frame = worldFrame(width, height)
            val screenY = frame.screenY(WorldDefaults.TRAINER_CENTER_ART_Y)
            // La inversa de la proyección devuelve la coordenada de arte original,
            // sin importar el tamaño ni el aspecto de la pantalla.
            assertEquals(
                "$width x $height: el entrenador se sale del punto de arte",
                WorldDefaults.TRAINER_CENTER_ART_Y,
                screenY / frame.scale + frame.artTop,
                0.01f
            )
        }
    }

    @Test
    fun theTrainerStaysOnScreenEvenOnTheWidestPhone() {
        screens.forEach { (width, height) ->
            val frame = worldFrame(width, height)
            val top = frame.screenY(WorldDefaults.TRAINER_CENTER_ART_Y) -
                frame.screenPx(WorldDefaults.TRAINER_BOX_ART_SIZE) / 2f
            assertTrue("$width x $height: el entrenador se sale por arriba", top > 0f)
            assertTrue(
                "$width x $height: el entrenador se sale por abajo",
                top + frame.screenPx(WorldDefaults.TRAINER_BOX_ART_SIZE) < height
            )
        }
    }

    @Test
    fun anEmptyScreenHasNoFrame() {
        assertEquals(0f, worldFrame(0f, 2400f).scale, 0f)
        assertEquals(0f, worldFrame(1080f, 0f).scale, 0f)
        assertEquals(0f, worldFrame(-10f, -10f).scale, 0f)
    }
}
