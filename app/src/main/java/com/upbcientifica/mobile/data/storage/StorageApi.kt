package com.upbcientifica.mobile.data.storage

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.*

/**
 * Distributed Storage (node-repository, puerto 8084). Rutas de
 * node-repository/src/rutas/almacenamiento.js; son las mismas que usa el portal
 * web a través de node-web.
 */
interface StorageApi {

    @Multipart
    @POST("/storage/archivos")
    suspend fun subirArchivo(
        @Part archivo: MultipartBody.Part,
        @Query("directorioDestino") directorioDestino: String,
        @Query("sobrescribir") sobrescribir: Boolean = false
    ): ArchivoDto

    @GET("/storage/directorios/{id}")
    suspend fun listarDirectorio(@Path("id") directorioId: String): List<ArchivoDto>

    @POST("/storage/directorios")
    suspend fun crearDirectorio(@Body cuerpo: CrearDirectorioDto): ArchivoDto

    /** @Streaming: sin esto Retrofit carga el archivo entero en memoria antes de devolverlo. */
    @Streaming
    @GET("/storage/archivos/{id}/descargar")
    suspend fun descargarArchivo(@Path("id") archivoId: String): ResponseBody

    /** Solo archivos: node-repository rechaza borrar directorios por esta ruta. */
    @DELETE("/storage/archivos/{id}")
    suspend fun eliminarArchivo(@Path("id") archivoId: String)

    @GET("/storage/cuota")
    suspend fun cuota(): CuotaDto
}

data class ArchivoDto(
    val id: String,
    val nombre: String,
    val tipo: String, // "archivo" | "directorio"
    val tamanoBytes: Long,
    val version: Int,
    val directorioContenedorId: String?
)

data class CrearDirectorioDto(val nombre: String, val directorioPadreId: String)

/** Bytes. */
data class CuotaDto(val usados: Long, val cuota: Long, val disponibles: Long)
