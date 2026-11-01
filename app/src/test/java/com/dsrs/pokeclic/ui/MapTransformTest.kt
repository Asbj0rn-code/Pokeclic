/**
 * Pruebas de la matemática compartida del mapa ([MapTransform]): conversión
 * pantalla↔imagen y límites de desplazamiento.
 *
 * Todo trabaja con píxeles planos, sin `Density`, para poder probarlo en JVM.
 */
package com.dsrs.pokeclic.ui

import androidx.compose.ui.geometry.Offset
import com.dsrs.pokeclic.model.KantoRegionMap
import com.dsrs.pokeclic.model.MapRegion
import com.dsrs.pokeclic.model.MapRegionGeometry
import com.dsrs.pokeclic.model.regionAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Escenario de referencia: contenedor 800×600 px, imagen ajustada a 800×600
 * (fit sin márgenes), centro del contenedor en (400, 300).
 */
private const val CONTAINER_W = 800f
private const val CONTAINER_H = 600f
private const val FIT_W = 800f
private const val FIT_H = 600f
private const val CENTER_X = CONTAINER_W / 2f
private const val CENTER_Y = CONTAINER_H / 2f

/** Tolerancia para comparaciones de coma flotante, relativa a los px del caso. */
private const val EPS = 1e-4f

class MapTransformTest {

    // ───────────────── pantalla → imagen (screenToUv) ─────────────────

    @Test
    fun screenToUv_imageCentre_atContainerCentre_withoutTransform() {
        val uv = screenToUv(
            CENTER_X, CENTER_Y, CENTER_X, CENTER_Y,
            offsetX = 0f, offsetY = 0f, zoom = 1f,
            fitWidthPx = FIT_W, fitHeightPx = FIT_H
        )
        assertEquals(0.5f, uv.u, EPS)
        assertEquals(0.5f, uv.v, EPS)
    }

    @Test
    fun screenToUv_imageCorners_withoutTransform() {
        val topLeft = screenToUv(
            0f, 0f, CENTER_X, CENTER_Y,
            0f, 0f, 1f, FIT_W, FIT_H
        )
        assertEquals(0f, topLeft.u, EPS)
        assertEquals(0f, topLeft.v, EPS)

        val bottomRight = screenToUv(
            CONTAINER_W, CONTAINER_H, CENTER_X, CENTER_Y,
            0f, 0f, 1f, FIT_W, FIT_H
        )
        assertEquals(1f, bottomRight.u, EPS)
        assertEquals(1f, bottomRight.v, EPS)
    }

    /** Todo lo que esté fuera de la imagen sale de rango, sin recortar. */
    @Test
    fun screenToUv_outsideTheImage_isUnclamped() {
        val uv = screenToUv(
            CONTAINER_W + 100f, CENTER_Y, CENTER_X, CENTER_Y,
            0f, 0f, 1f, FIT_W, FIT_H
        )
        assertTrue(uv.u > 1f)
        assertEquals(0.5f, uv.v, EPS)
    }

    @Test
    fun screenToUv_respectsZoom() {
        // Con zoom 2, el punto (0.25, 0.5) de la imagen se dibuja en
        // centro + 2 * (-0.25 * 800, 0) = (400 - 400, 300) = (0, 300).
        val uv = screenToUv(
            0f, 300f, CENTER_X, CENTER_Y,
            0f, 0f, 2f, FIT_W, FIT_H
        )
        assertEquals(0.25f, uv.u, EPS)
        assertEquals(0.5f, uv.v, EPS)
    }

    @Test
    fun screenToUv_respectsOffset() {
        val uv = screenToUv(
            400f, 300f, CENTER_X, CENTER_Y,
            100f, -50f, 1f, FIT_W, FIT_H
        )
        // 400 = 400 + 100 + 1 * (u - 0.5) * 800  →  u = 0.375
        assertEquals(0.375f, uv.u, EPS)
        // 300 = 300 - 50 + (v - 0.5) * 600  →  v = 0.5833333
        assertEquals(0.5f + 50f / FIT_H, uv.v, EPS)
    }

    @Test
    fun screenToUv_rejectsNonPositiveZoom() {
        assertThrows(IllegalArgumentException::class.java) {
            screenToUv(0f, 0f, 0f, 0f, 0f, 0f, 0f, FIT_W, FIT_H)
        }
    }

    // ───────────────── imagen → pantalla (uvToScreen) ─────────────────

    @Test
    fun uvToScreen_isTheInverseOfScreenToUv() {
        val offset = Offset(37.5f, -12.25f)
        for (zoom in listOf(1f, 1.7f, 3f, 5f)) {
            for ((u, v) in listOf(
                0f to 0f, 0.5f to 0.5f, 1f to 1f, 0.2f to 0.9f, 0.77f to 0.33f
            )) {
                val screen = uvToScreen(
                    u, v, CENTER_X, CENTER_Y,
                    offset.x, offset.y, zoom, FIT_W, FIT_H
                )
                val roundTrip = screenToUv(
                    screen.x, screen.y, CENTER_X, CENTER_Y,
                    offset.x, offset.y, zoom, FIT_W, FIT_H
                )
                assertTrue(
                    "u diverge en zoom=$zoom (u=$u, v=$v): ${roundTrip.u}",
                    abs(roundTrip.u - u) < EPS
                )
                assertTrue(
                    "v diverge en zoom=$zoom (u=$u, v=$v): ${roundTrip.v}",
                    abs(roundTrip.v - v) < EPS
                )
            }
        }
    }

    @Test
    fun uvToScreen_imageAnchorFollowsOffsetEvenWhenZoomed() {
        // El centro de la imagen se queda exactamente en centro + offset,
        // cualquier zoom: es el anclaje que hace el pellizco con sus centroides.
        val screen = uvToScreen(
            0.5f, 0.5f, CENTER_X, CENTER_Y,
            -23f, 88f, 3f, FIT_W, FIT_H
        )
        assertEquals(CENTER_X - 23f, screen.x, EPS)
        assertEquals(CENTER_Y + 88f, screen.y, EPS)
    }

    @Test
    fun uvToScreen_rejectsNonPositiveZoom() {
        assertThrows(IllegalArgumentException::class.java) {
            uvToScreen(0.5f, 0.5f, 0f, 0f, 0f, 0f, -1f, FIT_W, FIT_H)
        }
    }

    // ─────────────────────── Límites de desplazamiento ───────────────────────

    @Test
    fun clampMapOffset_keepsTheImageEdgesInsideTheContainer() {
        // Imagen 2× el contenedor: sobra media ventana a cada lado
        // (400 px horizontal, 300 px vertical).
        val clamped = clampMapOffset(
            Offset(-1000f, -900f), CONTAINER_W, CONTAINER_H, FIT_W * 2, FIT_H * 2, 1f
        )
        assertEquals(-400f, clamped.x, EPS)
        assertEquals(-300f, clamped.y, EPS)
    }

    @Test
    fun clampMapOffset_axesSmallerThanTheView_stayCentred() {
        // Vertical: imagen 100 px frente a 600 de vista → sin rango (offset 0).
        val clamped = clampMapOffset(
            Offset(0f, 500f), CONTAINER_W, CONTAINER_H, 800f, 100f, 1f
        )
        assertEquals(0f, clamped.y, EPS)
        assertEquals(0f, clamped.x, EPS)
    }

    @Test
    fun clampMapOffset_zoomWidensTheAllowedRange() {
        // Con zoom 2, la imagen mide 1600 px en la vista de 800: el sobrante
        // a cada lado es 400 px (antes, con zoom 1, era 0).
        val clamped = clampMapOffset(
            Offset(-1000f, 0f), CONTAINER_W, CONTAINER_H, FIT_W, FIT_H, 2f
        )
        assertEquals(-400f, clamped.x, EPS)
    }

    @Test
    fun clampMapOffset_insideTheRange_isUntouched() {
        val candidate = Offset(-120f, 45f)
        val clamped = clampMapOffset(
            candidate, CONTAINER_W, CONTAINER_H, FIT_W * 2, FIT_H * 2, 1f
        )
        assertEquals(candidate, clamped)
    }

    // ─────────────── Cadena de selección de zonas (Etapa 3) ───────────────

    /**
     * La selección de una zona ha de recaer **exactamente** en los píxeles
     * pintados de la imagen, sea cual sea el zoom y el desplazamiento: la
     * conversión vive en los dos sentidos de la misma fórmula, así que si se
     * toca un punto de pantalla que corresponde a una geometría mapeada,
     * `regionAt` tiene que encontrarla.
     */
    @Test
    fun selectionChain_tapUnderZoomAndPan_hitsTheMappedRegion() {
        val zoom = 3.5f
        val offset = Offset(123.75f, -88.25f)

        // Zona mapeada: un pequeño rectángulo alrededor del punto (0.6, 0.7)
        // del PNG, como quedará una ciudad real dibujada en el mapeo.
        val city = MapRegion(
            id = "ciudad",
            geometry = MapRegionGeometry.Envelope(0.57f, 0.66f, 0.63f, 0.74f),
            priority = 3
        )
        val map = KantoRegionMap(version = 1, regions = listOf(city))

        for ((u, v) in listOf(0.6f to 0.7f, 0.58f to 0.72f, 0.62f to 0.68f)) {
            // El píxel de pantalla donde se dibuja ese (u, v)…
            val tap = uvToScreen(
                u, v, CENTER_X, CENTER_Y,
                offset.x, offset.y, zoom, FIT_W, FIT_H
            )
            // …vuelto a coordenadas de imagen tiene que volver a dar
            // la misma zona, como hará `onTap` de `MapScreen`.
            val uv = screenToUv(
                tap.x, tap.y, CENTER_X, CENTER_Y,
                offset.x, offset.y, zoom, FIT_W, FIT_H
            )
            val selected = map.regionAt(uv.u, uv.v)
            assertEquals(city.id, selected?.id)
        }
    }

    /** Fuera de la geometría, con la misma vista movida, no hay selección. */
    @Test
    fun selectionChain_tapOutsideTheMappedRegion_hasNoSelection() {
        val zoom = 3.5f
        val offset = Offset(123.75f, -88.25f)
        val city = MapRegion(
            id = "ciudad",
            geometry = MapRegionGeometry.Envelope(0.57f, 0.66f, 0.63f, 0.74f),
            priority = 3
        )
        val map = KantoRegionMap(version = 1, regions = listOf(city))

        // Un punto de imagen lejos del rectángulo.
        val tap = uvToScreen(
            0.1f, 0.2f, CENTER_X, CENTER_Y,
            offset.x, offset.y, zoom, FIT_W, FIT_H
        )
        val uv = screenToUv(
            tap.x, tap.y, CENTER_X, CENTER_Y,
            offset.x, offset.y, zoom, FIT_W, FIT_H
        )
        assertNull(map.regionAt(uv.u, uv.v))
    }
}
