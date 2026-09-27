package com.upbcientifica.mobile.data.auth

import com.upbcientifica.mobile.network.SoapClient
import com.upbcientifica.mobile.session.TokenManager
import org.w3c.dom.Document

/**
 * Único punto de entrada del ViewModel hacia Authentication Service.
 * El ViewModel no sabe (ni le importa) que por debajo esto es SOAP —
 * solo ve funciones suspend que devuelven resultados de dominio.
 *
 * El contrato es el de las operaciones `login`, `validarToken` y `logout` del
 * WSDL de node-soap (users.wsdl). `login` y `logout` existen ahí precisamente
 * como alias para este cliente: internamente node-soap las llama
 * `iniciarSesion` y `cerrarSesion`, con el parámetro `password` en vez de
 * `contrasena` y devolviendo roles y grupos como elementos repetidos. Los
 * alias mantienen los nombres y la forma que usa esta app (una sola cadena
 * separada por comas), así que aquí no hizo falta cambiar nada del contrato.
 */
class AuthRepository(
    private val soap: SoapClient,
    private val tokenManager: TokenManager
) {
    suspend fun login(usuario: String, contrasena: String, origenCliente: String = "android"): SessionResult {
        val response = soap.invoke(
            operation = "login",
            params = mapOf(
                "usuario" to usuario,
                "contrasena" to contrasena,
                "origenCliente" to origenCliente
            )
        )

        val token = response.tagText("token")
        if (token.isNullOrBlank()) {
            /*
             * Sin token, el servidor ya explicó por qué en alguno de estos
             * elementos. Antes se lanzaba siempre "El servicio no devolvió
             * token", que no distingue una clave mal escrita de un código MFA
             * pendiente o de un LDAP caído.
             */
            val motivo = response.tagText("message")
                ?: response.tagText("error")
                ?: response.tagText("faultstring")

            throw when {
                motivo == null -> AuthError.Desconocido("El servicio de autenticación no devolvió token")
                motivo.contains("bloquead", ignoreCase = true) -> AuthError.CuentaBloqueada
                motivo.contains("incorrect", ignoreCase = true) ||
                    motivo.contains("inválid", ignoreCase = true) ||
                    motivo.contains("invalid", ignoreCase = true) -> AuthError.CredencialesInvalidas
                else -> AuthError.Desconocido(motivo)
            }
        }

        tokenManager.saveToken(token)

        return SessionResult(
            token = token,
            roles = listaSeparadaPorComas(response.tagText("roles")),
            serviciosAutorizados = listaSeparadaPorComas(response.tagText("mapaServiciosAutorizados"))
        )
    }

    suspend fun validarToken(): Boolean {
        val token = tokenManager.currentToken() ?: return false
        val response = soap.invoke("validarToken", mapOf("token" to token))
        return response.tagText("valido")?.toBoolean() ?: false
    }

    suspend fun logout() {
        val token = tokenManager.currentToken() ?: return
        /*
         * El token se borra pase lo que pase: si el servidor no responde, la
         * sesión local tiene que cerrarse igual. Dejarlo guardado haría que la
         * app siguiera "dentro" con una credencial que el usuario ya quiso
         * abandonar.
         */
        try {
            soap.invoke("logout", mapOf("token" to token))
        } finally {
            tokenManager.clear()
        }
    }

    /** "a,b, c" -> ["a","b","c"]; nulo o vacío -> lista vacía. */
    private fun listaSeparadaPorComas(valor: String?): List<String> =
        valor?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    /** Lee el texto del primer elemento con ese nombre local, ignorando el namespace. */
    private fun Document.tagText(localName: String): String? {
        val nodes = getElementsByTagNameNS("*", localName)
        return if (nodes.length > 0) nodes.item(0).textContent?.trim() else null
    }
}
