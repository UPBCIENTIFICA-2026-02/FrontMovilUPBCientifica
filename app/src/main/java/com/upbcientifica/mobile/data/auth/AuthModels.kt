package com.upbcientifica.mobile.data.auth

data class SessionResult(
    val token: String,
    val roles: List<String>,
    val serviciosAutorizados: List<String>
)

sealed class AuthError : Exception() {
    data object CredencialesInvalidas : AuthError()
    data object CuentaBloqueada : AuthError()
    data class Desconocido(override val message: String) : AuthError()
}
