package com.upbcientifica.mobile.data.storage

import okhttp3.MultipartBody
import retrofit2.http.*

/**
 * TODO: confirmar con Dev B los nombres de endpoint/parámetros exactos —
 * aquí se usan nombres razonables a partir de lo descrito en Contrato_Storage
 * (HTTP/REST, multipart para subida, versionado transparente por defecto).
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

    @GET("/storage/archivos/{id}/descargar")
    suspend fun descargarArchivo(@Path("id") archivoId: String): okhttp3.ResponseBody

    @DELETE("/storage/archivos/{id}")
    suspend fun eliminarArchivo(@Path("id") archivoId: String)
}

data class ArchivoDto(
    val id: String,
    val nombre: String,
    val tipo: String, // "archivo" | "directorio"
    val tamanoBytes: Long,
    val version: Int,
    val directorioContenedorId: String?
)
