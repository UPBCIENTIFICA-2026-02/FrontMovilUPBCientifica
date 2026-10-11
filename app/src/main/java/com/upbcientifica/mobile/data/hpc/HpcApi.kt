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
 * Cada ruta corresponde a un método de la interfaz RMI `JobService`. El token va
 * en la cabecera Authorization (lo pone RestClient); el gestor lo valida y
 * solo devuelve o deja cancelar los trabajos de ese usuario.
 */
interface HpcApi {

    @GET("jobs")
    suspend fun listarTrabajos(): List<TrabajoDto>

    @GET("jobs/{id}")
    suspend fun obtenerTrabajo(@Path("id") id: String): TrabajoDto

    @POST("jobs")
    suspend fun enviarTrabajo(@Body solicitud: EnviarTrabajoDto): TrabajoIdDto

    @DELETE("jobs/{id}")
    suspend fun cancelarTrabajo(@Path("id") id: String): CancelacionDto
}

/** Cuerpo de POST /jobs (SubmitJobRequest.java). Prioridad de 1 a 10. */
data class EnviarTrabajoDto(val nombre: String, val comando: String, val prioridad: Int)

data class TrabajoIdDto(val id: String)

data class CancelacionDto(val id: String, val cancelado: Boolean)

/**
 * JobResponse.java. Las fechas llegan en ISO-8601 UTC ("2026-10-11T03:40:17.7169415Z").
 * Estados: QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED.
 */
data class TrabajoDto(
    val id: String,
    val nombre: String,
    val comando: String,
    val prioridad: Int,
    val estado: String,
    val fechaCreacion: String?,
    val fechaInicio: String?,
    val fechaFin: String?,
    val resultado: String?,
    val mensajeError: String?,
)
