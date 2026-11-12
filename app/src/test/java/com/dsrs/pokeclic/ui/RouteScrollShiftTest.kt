package com.dsrs.pokeclic.ui

import com.dsrs.pokeclic.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Traducción del recorrido a desplazamiento de dibujo. Es la única parte del
 * renderer que se puede comprobar sin emulador: decide **qué eje** mueve el fondo
 * y con qué fase.
 *
 * Un fondo vertical avanza recto por la pantalla; uno horizontal hace lo mismo
 * pero de lado: el entrenador camina a la derecha y la imagen se corre a la
 * izquierda. En horizontal el marco está girado 90°, así que **los laterales
 * mueven la fase vertical**: el personaje sube o baja y el arte lo hace al revés.
 */
class RouteScrollShiftTest {

    @Test
    fun verticalMotion_keepsMovingTheArtworkVertically() {
        // Regresión: el eje vertical tiene que comportarse igual que antes de
        // existir el horizontal. Con 1 baldosa recorrida se está a mitad del giro
        // derecho, así que la imagen ya bajó 0,96 y se corrió 0,04.
        val shift = routeScrollShiftAt(
            motion = RouteScrollDefaults.MOTION,
            position = 1.0,
            artHeight = 1920
        )

        assertEquals(0.04f, shift.verticalPhase, 1e-4f)
        assertEquals(0.04f, shift.lateral, 1e-4f)
    }

    @Test
    fun horizontalMotion_movesTheArtworkOnlySideways() {
        val motion = RouteBackgroundMotion(
            legs = listOf(RouteLeg(clicks = 15f)),
            axis = RouteScrollAxis.HORIZONTAL
        )

        val shift = routeScrollShiftAt(motion, position = 3.0, artHeight = 1920)

        assertEquals("El fondo horizontal no debe subir ni bajar", 0f, shift.verticalPhase, 0f)
        assertEquals(3f, shift.lateral, 1e-4f)
    }

    @Test
    fun horizontalMotion_atRestDoesNotMove() {
        val motion = RouteBackgroundMotion(
            legs = listOf(RouteLeg(clicks = 15f)),
            axis = RouteScrollAxis.HORIZONTAL
        )

        val shift = routeScrollShiftAt(motion, position = 0.0, artHeight = 1920)

        assertEquals(0f, shift.verticalPhase, 0f)
        assertEquals(0f, shift.lateral, 0f)
    }

    @Test
    fun horizontalMotion_sideLegsMoveTheArtworkVertically() {
        val motion = RouteBackgroundMotion(
            legs = listOf(
                RouteLeg(clicks = 10f),
                RouteLeg(clicks = 5f, direction = Direction.RIGHT)
            ),
            axis = RouteScrollAxis.HORIZONTAL
        )

        // 12 clics recorridos: 10 rectos (0,4 de baldosa) y 2 de bajada (0,08).
        val shift = routeScrollShiftAt(motion, position = 0.48, artHeight = 1920)

        assertEquals("El tramo RIGHT debe subir el arte", 0.08f, shift.verticalPhase, 1e-4f)
        assertEquals(0.4f, shift.lateral, 1e-4f)
    }

    @Test
    fun horizontalMotion_walkingUpMovesTheArtworkDown() {
        val motion = RouteBackgroundMotion(
            legs = listOf(
                RouteLeg(clicks = 10f),
                RouteLeg(clicks = 5f, direction = Direction.LEFT)
            ),
            axis = RouteScrollAxis.HORIZONTAL
        )

        // 2 clics de subida: el arte baja, o sea fase negativa que el bucle deja
        // en 0,92 para no salir de [0f, 1f).
        val shift = routeScrollShiftAt(motion, position = 0.48, artHeight = 1920)

        assertEquals(0.92f, shift.verticalPhase, 1e-4f)
    }

    @Test
    fun horizontalMotion_keepsTheSamePaceWhateverTheArtworkSize() {
        // La fase lateral se mide en alturas de baldosa, no en píxeles: el arte
        // mida lo que mida, un clic recorre lo mismo que en un fondo vertical.
        val motion = RouteBackgroundMotion(
            legs = listOf(RouteLeg(clicks = 15f)),
            axis = RouteScrollAxis.HORIZONTAL
        )

        val small = routeScrollShiftAt(motion, position = 2.0, artHeight = 1920)
        val large = routeScrollShiftAt(motion, position = 2.0, artHeight = 3072)

        assertEquals(small.lateral, large.lateral, 1e-6f)
    }
}
