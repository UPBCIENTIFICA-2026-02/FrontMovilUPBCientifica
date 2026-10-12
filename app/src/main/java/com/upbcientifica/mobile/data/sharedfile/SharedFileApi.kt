package com.upbcientifica.mobile.data.sharedfile

import retrofit2.http.*

/**
 * Shared File Service (Contrato_SharedFile, HTTP/REST), puerto 8085 en la VM
 * node-repository. Rutas de node-shared-file/src/rutas/compartidos.js.
 */
interface SharedFileApi {

    /** CompartirArchivo. */
    @POST("/shared-file/compartir")
    suspend fun compartir(@Body request: CompartirRequest): CompartirResponse

    /** CompartirDirectorio: el permiso se hereda a todo el contenido, presente y futuro. */
    @POST("/shared-file/compartir-directorio")
    suspend fun compartirDirectorio(@Body request: CompartirDirectorioRequest): CompartirResponse

    /** ListarCompartidosConmigo. */
    @GET("/shared-file/compartidos-conmigo")
    suspend fun listarCompartidosConmigo(@Query("pagina") pagina: Int = 0): List<RelacionCompartidoDto>

    /** ListarQueYoComparto. */
    @GET("/shared-file/que-yo-comparto")
    suspend fun listarQueYoComparto(@Query("pagina") pagina: Int = 0): List<RelacionCompartidoDto>

    /** ActualizarPermisosCompartidos. */
    @PATCH("/shared-file/compartir/{id}")
    suspend fun actualizar(@Path("id") compartidoId: String, @Body cuerpo: ActualizarPermisoRequest): ActualizacionResponse

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

data class CompartirDirectorioRequest(
    val directorioId: String,
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

data class ActualizarPermisoRequest(val nuevoNivelPermiso: NivelPermiso)

/** advertencia: SHR-201, cuando se degrada a alguien que ya volvió a compartir. */
data class ActualizacionResponse(val compartidoId: String, val nivelPermiso: NivelPermiso, val advertencia: Advertencia?)

data class Advertencia(val codigo: String, val mensaje: String)

/** RelacionCompartido con los metadatos actuales del elemento (vienen de Storage). */
data class RelacionCompartidoDto(
    val compartidoId: String,
    val elementoId: String,
    val elementoTipo: String,        // ARCHIVO | DIRECTORIO
    val nombre: String,
    val tamanoBytes: Long,
    val propietario: String,
    val compartidoPor: String,
    val destinatarioTipo: TipoDestinatario,
    val destinatarioId: String,
    val nivelPermiso: NivelPermiso,
    val fechaCompartido: String?,
    val fechaExpiracion: String?,
) {
    val esDirectorio get() = elementoTipo == "DIRECTORIO"
}
