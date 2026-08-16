package com.upbcientifica.mobile.data.streaming

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Según Contrato_Streaming: HTTP/REST para catálogo/metadatos, HTTP por rangos para el video. */
interface StreamingApi {

    @GET("/streaming/catalogo")
    suspend fun listarCatalogo(@Query("pagina") pagina: Int): List<ContenidoDto>

    @GET("/streaming/buscar")
    suspend fun buscarContenido(@Query("criterioTexto") criterio: String): List<ContenidoDto>

    @GET("/streaming/{id}/sesion")
    suspend fun iniciarSesion(@Path("id") contenidoId: String): SesionStreamingDto
}

data class ContenidoDto(val id: String, val titulo: String, val duracionSegundos: Int)
data class SesionStreamingDto(val sesionId: String, val manifestUrl: String)
