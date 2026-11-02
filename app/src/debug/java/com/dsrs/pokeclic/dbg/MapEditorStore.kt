/**
 * Almacén del borrador del editor de zonas de Kanto (subetapa 4D).
 *
 * Persistencia **exclusiva del source set debug**: fichero en el espacio
 * privado de la aplicación (`filesDir/mapeo_kanto`), sin permisos de
 * almacenamiento. El documento usa exactamente el esquema versionado de
 * `kanto_regions.json` — se escribe con `KantoRegionMap.writeRegions` y se
 * lee con `parseRegions` — de modo que el borrador, el JSON exportado y los
 * assets del juego comparten formato y validación (serie de las Etapas 1-3).
 *
 * Escritura atomica: el contenido se escribe primero a un fichero temporal
 * en el mismo directorio y luego se mueve sobre el borrador en un solo
 * paso (`Files.move` REPLACE_EXISTING); si el proceso se corta a medias, o
 * no hay borrador o está el borrador íntegro anterior — nunca medio fichero.
 * Ninguna operación lanza: devuelven el motivo de fallo en claro para la
 * barra de estado de la UI (`String?`).
 *
 * El catálogo no se persiste (vive en los assets): los enlazados se
 * guardan con `priority` explícita (autocontenido, como exige `parseRegions`)
 * y su referencias de catálogo se validan al importar/recuperar con
 * `validateRegionsForEditor`.
 */
package com.dsrs.pokeclic.dbg

import android.content.Context
import com.dsrs.pokeclic.model.KantoRegionMap
import com.dsrs.pokeclic.model.KANTO_REGIONS_SCHEMA_VERSION
import com.dsrs.pokeclic.model.MapLocation
import com.dsrs.pokeclic.model.MapRegion
import com.dsrs.pokeclic.model.MapRegionsDataException
import com.dsrs.pokeclic.model.writeRegions
import com.dsrs.pokeclic.model.parseRegions
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Resultado de leer el borrador del disco. */
sealed interface MapEditorDraftLoad {

    /** No hay borrador (primera apertura o descartado a propósito). */
    data object None : MapEditorDraftLoad

    /** Borrador íntegro: las zonas confirmadas de una sesión anterior. */
    data class Loaded(val regions: List<MapRegion>) : MapEditorDraftLoad

    /**
     * El borrador existe pero no se puede leer. Permanece en disco: el
     * usuario decide si lo descarta (diálogo de la UI); nunca se borra solo.
     */
    data class Corrupted(val reason: String) : MapEditorDraftLoad
}

/**
 * `null` = éxito; cualquier otro valor = motivo legible del fallo. Convenio
 * uniforme de las operaciones de I/O del editor (guardar, exportar…): la
 * lógica no lanza ni pinta, la UI traduce.
 */
typealias MapEditorIoOutcome = String?

class MapEditorStore(private val directory: File) {

    private val draftFile = File(directory, "kanto_regions_borrador.json")
    private val tempFile = File(directory, "kanto_regions_borrador.json.tmp")

    /** Directorio por defecto: privado de la app (sin permisos de I/O). */
    companion object {
        fun defaultDirectory(context: Context): File =
            File(context.filesDir, "mapeo_kanto").apply { mkdirs() }
    }

    fun exists(): Boolean = draftFile.isFile

    /**
     * Guarda [regions] como borrador con el esquema del juego. Las escrituras
     * son completas (todo el documento en un fichero temporal + movimiento
     * atómico) y dedupe del llamante: aquí se escribe cada vez que se invoca.
     *
     * @return `null` si guardó; el motivo del fallo en otro caso.
     */
    fun save(regions: List<MapRegion>, locations: List<MapLocation>): MapEditorIoOutcome {
        val payload = try {
            KantoRegionMap(version = KANTO_REGIONS_SCHEMA_VERSION, regions = regions)
                .writeRegions(locations)
        } catch (error: Exception) {
            return "no se pudo serializar el mapeo: ${error.message ?: error.javaClass.simpleName}"
        }
        return try {
            if (!directory.isDirectory) directory.mkdirs()
            tempFile.outputStream().use { output ->
                output.write(payload.toByteArray(Charsets.UTF_8))
                output.flush()
            }
            Files.move(
                tempFile.toPath(),
                draftFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
            null
        } catch (atomicFailure: Exception) {
            // ATOMIC_MOVE no está garantizado en todos los discos del
            // emulador: reintentar sin la exigencia (mismo movimiento lógico,
            // renombrado en un paso en la práctica dentro del mismo directorio).
            try {
                Files.move(
                    tempFile.toPath(),
                    draftFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
                null
            } catch (error: Exception) {
                "no se pudo escribir el borrador: ${error.message ?: error.javaClass.simpleName}"
            }
        }
    }

    /**
     * Lee el borrador si existe. Un fichero ilegible o con esquema inválido
     * devuelve [MapEditorDraftLoad.Corrupted] con su motivo (nunca lanza ni
     * borra): la UI pregunta antes de descartarlo.
     */
    fun load(): MapEditorDraftLoad {
        if (!exists()) return MapEditorDraftLoad.None
        val text = try {
            draftFile.readText(Charsets.UTF_8)
        } catch (error: Exception) {
            return MapEditorDraftLoad.Corrupted(
                error.message ?: error.javaClass.simpleName
            )
        }
        return try {
            MapEditorDraftLoad.Loaded(parseRegions(text).regions)
        } catch (dataError: MapRegionsDataException) {
            MapEditorDraftLoad.Corrupted(dataError.message ?: "esquema inválido")
        } catch (anything: Exception) {
            MapEditorDraftLoad.Corrupted(anything.message ?: anything.javaClass.simpleName)
        }
    }

    /**
     * Borra el borrador (solo se invoca tras la confirmación explícita del
     * usuario; el borrador dañado también, porque ya no tiene contenido).
     */
    fun delete(): Boolean {
        val removedDraft = if (draftFile.exists()) draftFile.delete() else true
        val removedTemp = if (tempFile.exists()) tempFile.delete() else true
        return removedDraft && removedTemp
    }

    /**
     * Genera el documento JSON de **exportación** (mismo esquema y contenido
     * que el borrador): pre-validado por el llamante con
     * `validateRegionsForEditor` — este paso solo serializa.
     */
    fun exportPayload(regions: List<MapRegion>, locations: List<MapLocation>): String =
        KantoRegionMap(version = KANTO_REGIONS_SCHEMA_VERSION, regions = regions)
            .writeRegions(locations)
}
