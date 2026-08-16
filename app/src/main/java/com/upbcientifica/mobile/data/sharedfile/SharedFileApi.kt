package com.upbcientifica.mobile.data.sharedfile

import retrofit2.http.*

/** Según Contrato_SharedFile: HTTP/REST, comparte un archivo/directorio con otro usuario o grupo. */
interface SharedFileApi {

    @POST("/shared-file/compartir")
    suspend fun compartir(@Body request: CompartirRequest): CompartirResponse

    @GET("/shared-file/compartidos-conmigo")
    suspend fun listarCompartidosConmigo(): List<RecursoCompartidoDto>

    @DELETE("/shared-file/compartir/{id}")
    suspend fun revocar(@Path("id") compartidoId: String)
}

data class CompartirRequest(
    val archivoId: String,
    val destinatarioUsuarioId: String?,
    val destinatarioGrupoId: String?,
    val permisoUnix: String // p.ej. "r--"
)

data class CompartirResponse(val compartidoId: String)

data class RecursoCompartidoDto(
    val compartidoId: String,
    val archivoId: String,
    val nombreArchivo: String,
    val propietario: String,
    val permisoUnix: String
)
