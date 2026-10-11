package com.upbcientifica.mobile.data.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.io.InputStream

/**
 * Acceso a los archivos del teléfono que el usuario elige en el selector del
 * sistema (Storage Access Framework). No pide permisos de almacenamiento: el
 * selector entrega un Uri con permiso solo para ese archivo.
 */
class ArchivosDelDispositivo(private val resolver: ContentResolver) {

    /** Nombre visible del archivo ("informe.pdf"), o uno genérico si el proveedor no lo da. */
    fun nombre(uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) c.getString(i)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "archivo"
    }

    /**
     * Cuerpo de la petición que lee el archivo mientras se envía, sin cargarlo
     * entero en memoria: un video de cientos de MB no cabe en el heap del teléfono.
     */
    fun cuerpo(uri: Uri): RequestBody = object : RequestBody() {
        override fun contentType(): MediaType? = resolver.getType(uri)?.toMediaTypeOrNull()

        override fun contentLength(): Long =
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst() && i >= 0 && !c.isNull(i)) c.getLong(i) else -1L
            } ?: -1L

        override fun writeTo(sink: BufferedSink) {
            abrir(uri).source().use { sink.writeAll(it) }
        }
    }

    /** Copia [origen] al archivo que el usuario eligió en "Guardar como". */
    fun guardar(destino: Uri, origen: InputStream) {
        val salida = resolver.openOutputStream(destino) ?: throw IOException("No se pudo escribir el archivo")
        salida.use { s -> origen.use { it.copyTo(s) } }
    }

    private fun abrir(uri: Uri): InputStream =
        resolver.openInputStream(uri) ?: throw IOException("No se pudo leer el archivo")
}
