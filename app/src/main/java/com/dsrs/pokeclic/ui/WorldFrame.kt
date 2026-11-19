/**
 * Proyección del mundo del juego sobre la pantalla.
 *
 * El mundo **no se mide en dp**: se mide en píxeles de arte de una cámara fija
 * ([RouteScrollDefaults.CAMERA_WIDTH] × [RouteScrollDefaults.CAMERA_HEIGHT]) y se
 * proyecta con **una sola escala derivada del ancho de pantalla**. La cámara ocupa
 * siempre el ancho completo y su centro vertical cae en el centro de la pantalla,
 * así que:
 *
 * - la relación entre cualquier elemento del mundo (el entrenador) y el arte
 *   del fondo es idéntica en cualquier móvil,
 * - lo único que cambia con la relación de aspecto es **cuánto arte** se ve por
 *   arriba y por abajo, que el bucle del fondo rellena,
 * - la densidad de pantalla no interviene: un sprite de [WorldDefaults.TRAINER_BOX_ART_SIZE]
 *   px de arte mide siempre la misma fracción del ancho (224/1152 ≈ 19,4 %).
 *
 * Antes convivían dos sistemas: el fondo se escalaba con `min(ancho/1152,
 * alto/1920)` y anclaba por fracción del alto de pantalla, y el entrenador se
 * posicionaba en `dp` y porcentajes del área de contenido. En cuanto el móvil no
 * tenía el aspecto del emulador de referencia (1080×2400) o su densidad, el
 * paisaje se movía bajo los pies del entrenador.
 */
package com.dsrs.pokeclic.ui

/**
 * Cámara proyectada: cuántos píxeles de pantalla mide un píxel de arte ([scale])
 * y qué coordenada de arte cae en el borde superior de la pantalla ([artTop]).
 *
 * Con la cámara centrada verticalmente, `artTop` es negativo en pantallas más
 * altas que la cámara y positivo en las más anchas.
 */
data class WorldFrame(
    val scale: Float,
    val artTop: Float
) {
    /** `y` de pantalla (px) de una coordenada de arte. */
    fun screenY(artY: Float): Float = (artY - artTop) * scale

    /** Longitud de pantalla (px) de una longitud de arte. */
    fun screenPx(artPx: Float): Float = artPx * scale
}

/**
 * Proyección de la cámara para una pantalla de [screenWidth] × [screenHeight] px.
 *
 * Devuelve una escala nula si la pantalla no tiene tamaño: el renderer lo trata
 * como "no hay nada que dibujar".
 */
fun worldFrame(screenWidth: Float, screenHeight: Float): WorldFrame {
    if (screenWidth <= 0f || screenHeight <= 0f) return WorldFrame(0f, 0f)
    val scale = screenWidth / RouteScrollDefaults.CAMERA_WIDTH
    val visibleArtHeight = screenHeight / scale
    return WorldFrame(
        scale = scale,
        artTop = (RouteScrollDefaults.CAMERA_HEIGHT - visibleArtHeight) / 2f
    )
}

/**
 * Medidas del mundo, todas en **píxeles de arte** de la cámara.
 *
 * Los valores equivalen a los `dp` del emulador de referencia (1080×2400 px a
 * 420 dpi, escala 0,9375 px de pantalla por px de arte): 80 dp = 210 px = 224 px
 * de arte. Al expresarlos en arte, la proporción se mantiene en cualquier móvil.
 */
internal object WorldDefaults {

    /**
     * Centro del sprite del entrenador, en px de arte. Calibrado sobre el emulador
     * de referencia para que en 1080×2400 caiga exactamente donde caía con el
     * ancla porcentual anterior (y = 1032 px de pantalla).
     */
    const val TRAINER_CENTER_ART_Y = 780.8f

    /** Lado del recuadro del entrenador (80 dp). */
    const val TRAINER_BOX_ART_SIZE = 224f
}
