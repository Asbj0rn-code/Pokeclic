/**
 * Carga del catálogo de la Pokédex desde assets.
 *
 * La parte **pura** ([parsePokedexCatalog]) es testeable en JVM (los tests
 * dependen del artefacto `org.json:json` vía `testImplementation`, igual que
 * `MapRegionsJson`). La parte de Android ([PokedexCatalogLoader]) lee el asset
 * **una sola vez** por proceso y nunca lanza: si el fichero falta, está vacío
 * o es inválido, devuelve una carga fallida con catálogo vacío para que la
 * pantalla de Pokédex muestre su mensaje de error y el juego siga
 * funcionando; el diagnóstico queda en `Logcat` con la etiqueta
 * [DEX_LOG_TAG].
 *
 * Fuente de datos: `assets/pokedex_catalogo.json`, catálogo único de la
 * Pokédex con identificadores explícitos y estables, independizado del juego
 * (`SpeciesCatalog`): es la única fuente que lee la ejecución y no se empaqueta
 * nada adicional del catálogo en el APK.
 */
package com.dsrs.pokeclic.data

import android.content.Context
import android.util.Log
import com.dsrs.pokeclic.model.PokedexCatalog
import com.dsrs.pokeclic.model.PokedexCatalogLoad
import com.dsrs.pokeclic.model.PokedexSpecies
import org.json.JSONObject

/** Nombre del asset del catálogo adaptado de la Pokédex. */
const val DEX_CATALOG_ASSET = "pokedex_catalogo.json"

/** Etiqueta de «log» de la carga del catálogo de la Pokédex. */
const val DEX_LOG_TAG = "PokedexCatalog"

/**
 * Parsea el catálogo adaptado y lo valida con las invariantes de
 * [PokedexCatalog]. Cualquier problema de esquema sale como
 * [PokedexDataException] con contexto de la entrada (1-based para humanos).
 */
fun parsePokedexCatalog(text: String): PokedexCatalog =
    try {
        val root = JSONObject(text)
        val entriesJson = root.optJSONArray("especies")
            ?: throw PokedexDataException(
                "El catálogo no aporta \"especies\" como array"
            )
        val entries = (0 until entriesJson.length()).map { index ->
            val entry = index + 1
            val element = entriesJson.optJSONObject(index)
                ?: throw PokedexDataException("La entrada $entry del catálogo no es un objeto")
            val id = element.opt("id") as? String
                ?: throw PokedexDataException("La entrada $entry no aporta \"id\" (texto)")
            val dex = (element.opt("dex") as? Number)?.toInt()
                ?: throw PokedexDataException("La entrada $entry ($id) no aporta \"dex\" (entero)")
            val name = (element.opt("nombre") as? String)?.takeIf { it.isNotBlank() }
                ?: throw PokedexDataException("La entrada $entry ($id) no aporta \"nombre\"")
            val type = (element.opt("tipo") as? String)?.takeIf { it.isNotBlank() }
                ?: throw PokedexDataException("La entrada $entry ($id) no aporta \"tipo\"")
            PokedexSpecies(
                speciesId = id,
                dexNumber = dex,
                name = name,
                typeName = type
            )
        }
        // El orden del documento ya es el orden de la Pokédex: el `init` del
        // catálogo rebota si los números no son consecutivos desde 1.
        PokedexCatalog(entries)
    } catch (error: PokedexDataException) {
        throw error
    } catch (error: IllegalArgumentException) {
        // Invariantes de PokedexCatalog violadas: mensaje directo, sin la
        // coletilla de "JSON válido" (el documento era JSON, no un catálogo).
        throw PokedexDataException(error.message ?: "El catálogo de la Pokédex es inválido", error)
    } catch (error: Exception) {
        throw PokedexDataException("El catálogo de la Pokédex no es un JSON válido", error)
    }

/** Error de esquema o de datos del catálogo de la Pokédex. */
class PokedexDataException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Lee el asset **una vez** por proceso. La paridad con el JSON original de la
 * primera generación no se comprueba en ejecución: la cubren los tests, que
 * leen el asset de ejecución (`app/src/main/assets/pokedex_catalogo.json`)
 * desde el directorio del módulo.
 *
 * Nunca lanza: el fallo se refleja en `loadFailed` y en el log.
 */
object PokedexCatalogLoader {

    @Volatile
    private var cached: PokedexCatalogLoad? = null

    /** Carga unitaria por proceso; reutiliza la instancia ya cargada. */
    fun load(context: Context): PokedexCatalogLoad {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: runCatching {
                val text = context.assets.open(DEX_CATALOG_ASSET)
                    .bufferedReader()
                    .use { it.readText() }
                PokedexCatalogLoad(parsePokedexCatalog(text), loadFailed = false)
            }.getOrElse { error ->
                Log.w(
                    DEX_LOG_TAG,
                    "El catálogo de la Pokédex no está disponible: ${error.message}",
                    error
                )
                PokedexCatalogLoad.FAILED
            }.also { cached = it }
        }
    }
}
