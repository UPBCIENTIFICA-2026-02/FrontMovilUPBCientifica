package com.upbcientifica.mobile.data.sharedfile

import retrofit2.http.*

/**
 * Shared File Service (Contrato_SharedFile, HTTP/REST).
 *
 * OJO: el servicio todavía NO existe en el backend. Los nombres de campos y
 * valores siguen el contrato (CompartirArchivo, ListarCompartidosConmigo,
 * DejarDeCompartir); las RUTAS no las fija el contrato y son una propuesta que
 * hay que confirmar con quien implemente el servicio.
 */
interface SharedFileApi {

    /** CompartirArchivo. */
    @POST("/shared-file/compartir")
    suspend fun compartir(@Body request: CompartirRequest): CompartirResponse

    /** ListarCompartidosConmigo. */
    @GET("/shared-file/compartidos-conmigo")
    suspend fun listarCompartidosConmigo(): List<RecursoCompartidoDto>

    /** DejarDeCompartir. */
    @DELETE("/shared-file/compartir/{id}")
    suspend fun revocar(@Path("id") compartidoId: String)
}

enum class TipoDestinatario { USUARIO, GRUPO }

/** Niveles del contrato: el tercero permite que el destinatario vuelva a compartir. */
enum class NivelPermiso(val etiqueta: String) {
    LECTURA("Solo ver"),
    LECTURA_ESCRITURA("Ver y editar"),
    LECTURA_ESCRITURA_COMPARTIR("Ver, editar y compartir"),
}

/** Entrada de CompartirArchivo. `expiracion` (ISO-8601) es opcional: sin ella el permiso no caduca. */
data class CompartirRequest(
    val archivoId: String,
    val destinatarioTipo: TipoDestinatario,
    val destinatarioId: String,
    val nivelPermiso: NivelPermiso,
    val expiracion: String? = null,
)

/** Salida de CompartirArchivo. estadoInvitacion: PENDIENTE o ACTIVO. */
data class CompartirResponse(
    val compartidoId: String,
    val fechaCompartido: String? = null,
    val estadoInvitacion: String? = null,
)

data class RecursoCompartidoDto(
    val compartidoId: String,
    val archivoId: String,
    val nombreArchivo: String,
    val propietario: String,
    val permisoUnix: String
)
