package com.upbcientifica.mobile.data.streaming

import okhttp3.ResponseBody
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * node-media (puerto 8083): Streaming y Photo Album viven en el mismo servicio.
 * Rutas de node-media/src/rutas/streaming.js y album.js.
 *
 * Streaming: REST para catálogo y sesión; el video se pide por rangos a
 * `manifestUrl` (lo hace el reproductor, no Retrofit).
 */
interface StreamingApi {

    @GET("/streaming/catalogo")
    suspend fun listarCatalogo(@Query("pagina") pagina: Int): List<ContenidoDto>

    @GET("/streaming/buscar")
    suspend fun buscarContenido(@Query("criterioTexto") criterio: String): List<ContenidoDto>

    @GET("/streaming/{id}/sesion")
    suspend fun iniciarSesion(@Path("id") contenidoId: String): SesionStreamingDto

    /** Borra el video: el archivo en Storage y su entrada en el catálogo. Solo el propietario. */
    @DELETE("/streaming/{id}")
    suspend fun eliminarVideo(@Path("id") contenidoId: String)

    // --- Photo Album ---

    @GET("/album/fotos")
    suspend fun listarFotos(@Query("pagina") pagina: Int): List<FotoDto>

    @GET("/album/fotos/{id}/miniatura")
    suspend fun miniatura(@Path("id") fotoId: String): ResponseBody

    @GET("/album/fotos/{id}/contenido")
    suspend fun contenidoFoto(@Path("id") fotoId: String): ResponseBody

    /** Borra la foto: el archivo en Storage y su entrada en el catálogo. Solo el propietario. */
    @DELETE("/album/fotos/{id}")
    suspend fun eliminarFoto(@Path("id") fotoId: String)

    /**
     * Recorre Mis archivos y cataloga fotos y videos nuevos. Hasta que no se
     * indexa, lo que se sube a Storage no aparece en Fotos ni en Streaming.
     */
    @POST("/album/indexar")
    suspend fun indexar(): IndexacionDto
}

data class ContenidoDto(val id: String, val titulo: String, val duracionSegundos: Int, val fecha: String? = null)

/** manifestUrl llega como ruta relativa: "/streaming/{id}/contenido". */
data class SesionStreamingDto(val sesionId: String, val manifestUrl: String)

data class FotoDto(
    val id: String,
    val titulo: String,
    val tipo: String?,
    val tamanoBytes: Long,
    val ancho: Int?,
    val alto: Int?,
    val tieneMiniatura: Boolean,
    val fecha: String?,
)

/** Respuesta de /album/indexar (IndexadorService.indexarHome). */
data class IndexacionDto(val indexados: Int, val revisados: Int)
