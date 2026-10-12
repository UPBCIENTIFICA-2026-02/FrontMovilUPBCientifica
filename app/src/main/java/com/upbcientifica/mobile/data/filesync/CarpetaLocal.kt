package com.upbcientifica.mobile.data.filesync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * La carpeta del teléfono que se respalda, elegida con el selector del sistema
 * (Storage Access Framework, ACTION_OPEN_DOCUMENT_TREE). No pide permisos de
 * almacenamiento: el usuario concede acceso solo a esa carpeta y la app lo
 * conserva entre reinicios (takePersistableUriPermission).
 *
 * Las rutas son relativas a la carpeta y usan "/" ("Fotos/2026/a.jpg").
 * Se usa DocumentsContract directamente y no DocumentFile porque este último
 * hace una consulta por archivo para leer nombre, tamaño y fecha; en una
 * carpeta con miles de fotos la diferencia es de segundos a minutos.
 */
class CarpetaLocal(context: Context, val arbol: Uri) {

    private val resolver = context.contentResolver
    private val raizId: String = DocumentsContract.getTreeDocumentId(arbol)

    /** Un archivo de la carpeta. [modificado] en milisegundos. */
    data class Entrada(val rel: String, val documentoId: String, val tamano: Long, val modificado: Long)

    private data class Hijo(
        val id: String, val nombre: String, val esDirectorio: Boolean,
        val tamano: Long, val modificado: Long,
    )

    private fun uri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(arbol, id)

    private fun hijos(padreId: String): List<Hijo> {
        val columnas = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED,
        )
        val consulta = DocumentsContract.buildChildDocumentsUriUsingTree(arbol, padreId)
        val out = mutableListOf<Hijo>()
        resolver.query(consulta, columnas, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val nombre = c.getString(1) ?: c.getString(0)
                out += Hijo(
                    id = c.getString(0),
                    nombre = nombre,
                    esDirectorio = c.getString(2) == Document.MIME_TYPE_DIR,
                    tamano = if (c.isNull(3)) 0 else c.getLong(3),
                    modificado = if (c.isNull(4)) 0 else c.getLong(4),
                )
            }
        } ?: throw IOException("No se puede leer la carpeta del teléfono")
        return out
    }

    /** false si el usuario retiró el permiso o borró la carpeta. */
    fun accesible(): Boolean = try {
        hijos(raizId); true
    } catch (e: Exception) {
        false
    }

    /** Nombre visible de la carpeta ("DCIM", "Documentos"...). */
    fun nombre(): String {
        resolver.query(uri(raizId), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { return it }
        }
        return raizId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Carpeta" }
    }

    /**
     * Todos los archivos, recorriendo subcarpetas. Se omiten los ocultos
     * (".nomedia", ".thumbnails", ".trashed-...") y los temporales: no son
     * datos del usuario y cambian solos, lo que provocaría subidas sin fin.
     */
    fun listar(): List<Entrada> {
        val out = mutableListOf<Entrada>()
        fun recorrer(id: String, prefijo: String) {
            for (h in hijos(id)) {
                if (ignorar(h.nombre)) continue
                val rel = if (prefijo.isEmpty()) h.nombre else "$prefijo/${h.nombre}"
                if (h.esDirectorio) recorrer(h.id, rel)
                else out += Entrada(rel, h.id, h.tamano, h.modificado)
            }
        }
        recorrer(raizId, "")
        return out
    }

    fun abrir(documentoId: String): InputStream =
        resolver.openInputStream(uri(documentoId)) ?: throw IOException("No se pudo leer el archivo")

    fun abrirRuta(rel: String): InputStream =
        abrir(buscar(rel)?.id ?: throw FileNotFoundException(rel))

    fun existe(rel: String): Boolean = buscar(rel) != null

    private fun buscar(rel: String): Hijo? {
        var actual = raizId
        val partes = rel.split('/')
        for ((i, nombre) in partes.withIndex()) {
            val h = hijos(actual).firstOrNull { it.nombre == nombre } ?: return null
            if (i == partes.lastIndex) return h.takeUnless { it.esDirectorio }
            if (!h.esDirectorio) return null
            actual = h.id
        }
        return null
    }

    /** Id del directorio [partes], creando los que falten (como "mkdir -p"). */
    private fun asegurarDirectorio(partes: List<String>): String {
        var actual = raizId
        for (nombre in partes) {
            val existente = hijos(actual).firstOrNull { it.nombre == nombre }
            actual = when {
                existente == null -> {
                    val creado = DocumentsContract.createDocument(resolver, uri(actual), Document.MIME_TYPE_DIR, nombre)
                        ?: throw IOException("No se pudo crear la carpeta $nombre")
                    DocumentsContract.getDocumentId(creado)
                }
                existente.esDirectorio -> existente.id
                else -> throw IOException("$nombre existe y es un archivo, no una carpeta")
            }
        }
        return actual
    }

    /**
     * Copia [origen] (ya descargado y verificado) a [rel], reemplazando el
     * contenido si existe. La descarga se hace antes a un temporal para que un
     * corte de red nunca deje un archivo del usuario a medias.
     */
    fun escribir(rel: String, origen: File) {
        val partes = rel.split('/')
        val destino = buscar(rel)?.let { uri(it.id) } ?: run {
            val dir = asegurarDirectorio(partes.dropLast(1))
            DocumentsContract.createDocument(resolver, uri(dir), "application/octet-stream", partes.last())
                ?: throw IOException("No se pudo crear ${partes.last()}")
        }
        val salida = resolver.openOutputStream(destino, "wt") ?: throw IOException("No se pudo escribir $rel")
        salida.use { s -> origen.inputStream().use { it.copyTo(s) } }
    }

    fun borrar(rel: String) {
        val h = buscar(rel) ?: return
        DocumentsContract.deleteDocument(resolver, uri(h.id))
    }

    /** Cambia el nombre (no la carpeta) de [rel]. */
    fun renombrar(rel: String, nuevoNombre: String) {
        val h = buscar(rel) ?: throw FileNotFoundException(rel)
        DocumentsContract.renameDocument(resolver, uri(h.id), nuevoNombre)
            ?: throw IOException("No se pudo renombrar $rel")
    }

    companion object {
        fun ignorar(nombre: String): Boolean =
            nombre.startsWith(".") || nombre.endsWith(".tmp", ignoreCase = true) || nombre.endsWith("~")

        /** SHA-256 en hexadecimal: el checksum que exige el contrato (SYNC-005). */
        fun sha256(entrada: InputStream): String {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(64 * 1024)
            entrada.use {
                while (true) {
                    val n = it.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { b -> "%02x".format(b) }
        }
    }
}
