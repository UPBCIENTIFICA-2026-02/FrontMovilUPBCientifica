package com.upbcientifica.mobile.data.storage

import com.upbcientifica.mobile.data.FuenteVideo
import com.upbcientifica.mobile.data.cabecerasToken
import com.upbcientifica.mobile.data.unirUrl
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.InputStream

/**
 * Distributed Storage (node-repository). Ver [StorageApi].
 *
 * [urlBase] y [token] solo hacen falta para el reproductor de video: ExoPlayer
 * pide el archivo por su cuenta (por rangos), sin pasar por Retrofit.
 */
class StorageRepository(
    private val api: StorageApi? = null,   // null mientras no haya backend real
    private val usarDatosSimulados: Boolean = true,
    private val urlBase: String = "",
    private val token: () -> String? = { null },
) {
    suspend fun listarHome(directorioId: String): List<ArchivoDto> {
        if (usarDatosSimulados) return datosSimulados()
        return api!!.listarDirectorio(directorioId)
    }

    suspend fun subir(nombre: String, contenido: RequestBody, directorioDestino: String): ArchivoDto {
        if (usarDatosSimulados) error("Subida no disponible en modo simulado")
        val parte = MultipartBody.Part.createFormData("archivo", nombre, contenido)
        return api!!.subirArchivo(parte, directorioDestino)
    }

    suspend fun crearCarpeta(nombre: String, directorioPadreId: String): ArchivoDto {
        if (usarDatosSimulados) error("No disponible en modo simulado")
        return api!!.crearDirectorio(CrearDirectorioDto(nombre, directorioPadreId))
    }

    suspend fun eliminar(archivoId: String) {
        if (usarDatosSimulados) return
        api!!.eliminarArchivo(archivoId)
    }

    /** Flujo con el contenido del archivo. Quien lo pide lo tiene que cerrar. */
    suspend fun descargar(archivoId: String): InputStream {
        if (usarDatosSimulados) error("No disponible en modo simulado")
        return api!!.descargarArchivo(archivoId).byteStream()
    }

    /** Contenido completo en memoria: solo para imágenes, que el visor necesita enteras. */
    suspend fun bytes(archivoId: String): ByteArray = descargar(archivoId).use { it.readBytes() }

    /** Para reproducir un video guardado en Mis archivos sin descargarlo entero. */
    fun fuenteVideo(archivoId: String): FuenteVideo {
        if (usarDatosSimulados) error("No disponible en modo simulado")
        return FuenteVideo(unirUrl(urlBase, "/storage/archivos/$archivoId/descargar"), cabecerasToken(token()))
    }

    suspend fun cuota(): CuotaDto? {
        if (usarDatosSimulados) return null
        return api!!.cuota()
    }

    // TODO: eliminar esta función cuando el backend real esté conectado
    private fun datosSimulados(): List<ArchivoDto> = listOf(
        ArchivoDto("1", "Documentos", "directorio", 0, 1, null),
        ArchivoDto("2", "Datasets", "directorio", 0, 1, null),
        ArchivoDto("3", "resultados_hpc.csv", "archivo", 245_000, 3, null),
        ArchivoDto("4", "informe_final.pdf", "archivo", 1_200_000, 1, null),
    )
}
