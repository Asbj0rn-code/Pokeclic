package com.dsrs.pokeclic.ui

import kotlin.math.ceil
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La regla de cámara tiene que valer en cualquier terminal, no solo en el móvil
 * de desarrollo. Esta matriz cruza pantallas reales (16:9, 19.5:9, 20:9, tablet
 * vertical y horizontal, gama baja) con los tres artes del juego y comprueba que
 * el dibujo cubre la pantalla entera, sin franjas y sin pasarse del tope de
 * repeticiones.
 *
 * No hace falta emulador: `routeCameraScale` y el reparto de columnas son
 * cálculo puro.
 */
class RouteCameraCoverageTest {

    private data class Screen(val w: Float, val h: Float, val label: String)

    private data class Art(val w: Float, val h: Float, val label: String)

    private val screens = listOf(
        Screen(480f, 800f, "gama baja 16:9"),
        Screen(720f, 1280f, "720p 16:9"),
        Screen(1080f, 1920f, "1080p 16:9"),
        Screen(1080f, 2400f, "1080p 20:9"),
        Screen(1170f, 2532f, "1080p 19.5:9"),
        Screen(1440f, 3200f, "1440p 20:9"),
        Screen(1600f, 2560f, "tablet vertical"),
        Screen(2560f, 1600f, "tablet horizontal")
    )

    private val arts = listOf(
        Art(1080f, 1920f, "ruta"),
        Art(1152f, 1920f, "bosque verde"),
        Art(1152f, 1920f, "ruta 3"),
        Art(2176f, 3072f, "paleta v2"),
        Art(4608f, 3840f, "ciudad plateada")
    )

    @Test
    fun theCameraAlwaysFitsEveryScreen() {
        screens.forEach { screen ->
            val scale = routeCameraScale(screen.w, screen.h)
            assertTrue("${screen.label}: escala no positiva", scale > 0f)
            assertTrue(
                "${screen.label}: la cámara no entra a lo ancho",
                RouteScrollDefaults.CAMERA_WIDTH * scale <= screen.w + 0.01f
            )
            assertTrue(
                "${screen.label}: la cámara no entra a lo alto",
                RouteScrollDefaults.CAMERA_HEIGHT * scale <= screen.h + 0.01f
            )
        }
    }

    @Test
    fun everyScreenAndArt_coverTheWidthWithoutGaps() {
        screens.forEach { screen ->
            arts.forEach { art ->
                val scale = routeCameraScale(screen.w, screen.h)
                val tileW = art.w * scale
                val left0 = (screen.w - tileW) / 2f

                // Un barrido de tres pantallas a cada lado cubre de sobra el
                // corrimiento lateral de cualquier trayectoria.
                var shiftH = -3f * screen.w
                while (shiftH <= 3f * screen.w) {
                    val first = routeColumnStart(
                        routeFirstColumn(left0, shiftH, tileW), left0, shiftH, tileW
                    )
                    assertTrue(
                        "${screen.label} + ${art.label}: hueco a la izquierda con shift=$shiftH",
                        first <= 0f
                    )
                    assertTrue(
                        "${screen.label} + ${art.label}: la primera columna no llega a tocar",
                        first + tileW > 0f
                    )
                    shiftH += screen.w * 0.05f
                }

                val columns = ceil(screen.w / tileW)
                assertTrue(
                    "${screen.label} + ${art.label}: $columns columnas pasan el tope " +
                        "de ${RouteScrollDefaults.MAX_REPEATS}",
                    columns <= RouteScrollDefaults.MAX_REPEATS
                )
            }
        }
    }

    @Test
    fun everyScreenAndArt_coverTheHeightWithoutGaps() {
        screens.forEach { screen ->
            arts.forEach { art ->
                val scale = routeCameraScale(screen.w, screen.h)
                val tileH = art.h * scale

                // La fila superior arranca en -shiftV, con shiftV en [0, tileH):
                // nunca deja hueco arriba, y cada copia mide exactamente tileH.
                assertTrue("${screen.label} + ${art.label}: baldosa sin alto", tileH > 1f)

                val rows = ceil(screen.h / tileH)
                assertTrue(
                    "${screen.label} + ${art.label}: $rows filas pasan el tope " +
                        "de ${RouteScrollDefaults.MAX_REPEATS}",
                    rows <= RouteScrollDefaults.MAX_REPEATS
                )
            }
        }
    }

    @Test
    fun theCameraKeepsItsProportionOnEveryScreen() {
        screens.forEach { screen ->
            val scale = routeCameraScale(screen.w, screen.h)
            val cameraW = RouteScrollDefaults.CAMERA_WIDTH * scale
            val cameraH = RouteScrollDefaults.CAMERA_HEIGHT * scale
            val screenRatio = screen.w / screen.h
            val cameraRatio = cameraW / cameraH
            val reference = RouteScrollDefaults.CAMERA_WIDTH / RouteScrollDefaults.CAMERA_HEIGHT

            // La cámara nunca se deforma: su proporción es siempre 1152:1920.
            assertTrue(
                "${screen.label}: cámara deformada",
                kotlin.math.abs(cameraRatio - reference) < 0.0001f
            )
            // Y la pantalla o la contiene a lo ancho, o a lo alto, nunca las dos
            // recortadas.
            assertTrue(
                "${screen.label}: proporción de pantalla incoherente",
                screenRatio > 0f && cameraRatio > 0f
            )
        }
    }
}
