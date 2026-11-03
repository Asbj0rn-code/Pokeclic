package com.dsrs.pokeclic.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La cámara mide siempre [RouteScrollDefaults.CAMERA_WIDTH] ×
 * [RouteScrollDefaults.CAMERA_HEIGHT] píxeles de arte, sea cual sea el drawable.
 * La escala se calcula solo con la pantalla, así que estas cuentas se comprueban
 * sin emulador ni imágenes.
 */
class RouteCameraScaleTest {

    private val cameraWidth = RouteScrollDefaults.CAMERA_WIDTH
    private val cameraHeight = RouteScrollDefaults.CAMERA_HEIGHT

    @Test
    fun aScreenOfCameraSize_drawsTheCameraAtOneToOne() {
        assertEquals(1f, routeCameraScale(cameraWidth, cameraHeight), 0f)
    }

    @Test
    fun theCameraAlwaysFitsTheScreen() {
        val screens = listOf(
            1080f to 2400f,
            720f to 1280f,
            1440f to 3200f,
            2000f to 1920f,
            800f to 800f
        )
        screens.forEach { (width, height) ->
            val scale = routeCameraScale(width, height)
            assertTrue(
                "cámara más ancha que la pantalla $width x $height",
                cameraWidth * scale <= width + 0.001f
            )
            assertTrue(
                "cámara más alta que la pantalla $width x $height",
                cameraHeight * scale <= height + 0.001f
            )
        }
    }

    @Test
    fun aTallerScreenLimitsTheWidth() {
        // 1080/1152 = 0,9375 sale por ancho; el alto daría 2400/1920 = 1,25.
        assertEquals(0.9375f, routeCameraScale(1080f, 2400f), 0.0001f)
    }

    @Test
    fun aWiderScreenLimitsTheHeight() {
        // 1920/1920 = 1 sale por alto; el ancho daría 2000/1152 > 1.
        assertEquals(1f, routeCameraScale(2000f, 1920f), 0.0001f)
    }

    @Test
    fun anEmptyScreenHasNoScale() {
        assertEquals(0f, routeCameraScale(0f, 2400f), 0f)
        assertEquals(0f, routeCameraScale(1080f, 0f), 0f)
        assertEquals(0f, routeCameraScale(-10f, -10f), 0f)
    }
}
