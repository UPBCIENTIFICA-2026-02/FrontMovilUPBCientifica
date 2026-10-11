package com.upbcientifica.mobile.network

import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException

/**
 * Mensaje que se le puede mostrar al usuario a partir de un fallo de red.
 *
 * Los servicios responden los errores como `{ error, mensaje }` (Storage,
 * Media) o `{ status, error, mensaje }` (job-api). Sin esto la pantalla diría
 * "HTTP 409" en vez de "Ya existe un archivo con ese nombre".
 */
fun Throwable.mensajeLegible(servicio: String): String = when (this) {
    is HttpException -> {
        val mensaje = runCatching {
            JSONObject(response()?.errorBody()?.string().orEmpty()).optString("mensaje")
        }.getOrNull()
        when {
            !mensaje.isNullOrBlank() -> mensaje
            code() == 401 -> "La sesión no es válida. Vuelve a iniciar sesión."
            code() == 403 -> "No tienes permiso para hacer esto."
            else -> "$servicio respondió ${code()}"
        }
    }
    is IOException -> "No hay conexión con $servicio. ¿Está encendido?"
    else -> message ?: "Error inesperado en $servicio"
}
