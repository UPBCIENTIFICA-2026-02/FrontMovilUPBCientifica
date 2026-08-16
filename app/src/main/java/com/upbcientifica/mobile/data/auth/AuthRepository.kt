package com.upbcientifica.mobile.data.auth

import com.upbcientifica.mobile.network.SoapClient
import com.upbcientifica.mobile.session.TokenManager
import org.w3c.dom.Document

/**
 * Único punto de entrada del ViewModel hacia Authentication Service.
 * El ViewModel no sabe (ni le importa) que por debajo esto es SOAP —
 * solo ve funciones suspend que devuelven resultados de dominio.
 *
 * Operaciones según Contrato_Auth: Login, ValidarToken, Logout,
 * RegistrarUsuario, ObtenerPerfil/ObtenerRoles/ObtenerPermisos.
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
            ?: throw AuthError.Desconocido("El servicio no devolvió token")

        tokenManager.saveToken(token)

        return SessionResult(
            token = token,
            roles = response.tagText("roles")?.split(",").orEmpty(),
            serviciosAutorizados = response.tagText("mapaServiciosAutorizados")?.split(",").orEmpty()
        )
    }

    suspend fun validarToken(): Boolean {
        val token = tokenManager.currentToken() ?: return false
        val response = soap.invoke("validarToken", mapOf("token" to token))
        return response.tagText("valido")?.toBoolean() ?: false
    }

    suspend fun logout() {
        val token = tokenManager.currentToken() ?: return
        soap.invoke("logout", mapOf("token" to token))
        tokenManager.clear()
    }

    /** Lee el texto del primer elemento con ese nombre local, ignorando el namespace. */
    private fun Document.tagText(localName: String): String? {
        val nodes = getElementsByTagNameNS("*", localName)
        return if (nodes.length > 0) nodes.item(0).textContent?.trim() else null
    }
}
