package com.upbcientifica.mobile.data.hpc

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * job-api (repositorio RMIUPBCientifica): la fachada HTTP del gestor RMI.
 *
 * Android no trae `java.rmi`, así que la app no habla RMI con el gestor: habla
 * REST con job-api, que es quien hace la llamada RMI. Es el mismo camino que usa
 * el portal web (a través de node-web).
 *
 * Cada ruta corresponde a una operación de Contrato_HPC. El token va en la
 * cabecera Authorization (lo pone RestClient); el gestor lo valida y solo
 * devuelve o deja cancelar los trabajos de ese usuario.
 *
 * ANTES: POST /jobs { nombre, comando, prioridad 1-10 } y sin /cluster/estado.
 */
interface HpcApi {

    @GET("jobs")
    suspend fun listarTrabajos(): List<TrabajoDto>

    @GET("jobs/{id}")
    suspend fun obtenerTrabajo(@Path("id") id: String): TrabajoDto

    @POST("jobs")
    suspend fun enviarTrabajo(@Body solicitud: EnviarTrabajoDto): RespuestaEnvioDto

    @DELETE("jobs/{id}")
    suspend fun cancelarTrabajo(@Path("id") id: String): CancelacionDto

    @POST("jobs/{id}/reintento")
    suspend fun reintentarTrabajo(@Path("id") id: String, @Body vacio: Map<String, String>): RespuestaEnvioDto

    @GET("jobs/{id}/logs")
    suspend fun logsTrabajo(@Path("id") id: String): LogsDto

    @GET("cluster/estado")
    suspend fun estadoCluster(): EstadoClusterDto
}

/** Cuerpo de POST /jobs: EnviarTrabajo (Contrato_HPC §3.1). */
data class EnviarTrabajoDto(
    val nombreTrabajo: String,
    /** Ruta en el Home ("/Proyectos/pi.c") o id de Storage. */
    val rutaCodigo: String,
    val rutaDataset: String?,
    val numeroProcesos: Int,
    /** Segundos. */
    val tiempoMaximoEjecucion: Int,
    /** BAJA, NORMAL o ALTA. */
    val prioridad: String,
    val parametrosEjecucion: Map<String, String>,
)

data class RespuestaEnvioDto(
    val trabajoId: String?,
    val estadoInicial: String?,
    val posicionEstimadaCola: Int?,
    val fechaEnvio: String?,
    val advertencia: String?,
)

data class CancelacionDto(val trabajoId: String?, val estadoFinal: String?)

data class LogsDto(val trabajoId: String?, val logsEjecucion: String?)

/**
 * TrabajoResponse.java. Gson no aplica los valores por defecto de Kotlin: todo
 * lo que puede faltar es nullable. Fechas en ISO-8601 UTC.
 * Estados: PENDIENTE, EN_COLA, EJECUTANDO, COMPLETADO_PENDIENTE_ALMACENAMIENTO,
 * COMPLETADO, FALLIDO, FALLIDO_TIMEOUT, CANCELADO.
 */
data class TrabajoDto(
    val id: String,
    val nombreTrabajo: String?,
    val rutaCodigo: String?,
    val rutaDataset: String?,
    val numeroProcesos: Int?,
    val tiempoMaximoEjecucion: Int?,
    val prioridad: String?,
    val estado: String?,
    val posicionCola: Int?,
    val nodosAsignados: List<String>?,
    val fechaEnvio: String?,
    val fechaInicio: String?,
    val fechaFin: String?,
    val codigoSalida: Int?,
    val rutaResultado: String?,
    val resultadoIncompleto: Boolean?,
    val mensajeError: String?,
    val trabajoOriginalId: String?,
)

/** ObtenerEstadoCluster + ObtenerEstadoCola (Contrato_HPC §3.9). */
data class EstadoClusterDto(
    val nodosTotales: Int?,
    val nodosDisponibles: Int?,
    val nodosEnFalla: Int?,
    val procesosTotales: Int?,
    val procesosEnUso: Int?,
    val trabajosEnCola: Int?,
    val trabajosEjecutando: Int?,
    val ejecutorConectado: Boolean?,
    val nodos: List<NodoDto>?,
)

data class NodoDto(
    val id: String?,
    val estado: String?,
    val capacidadProcesos: Int?,
    val cargaActual: Int?,
    val origen: String?,
)
