package com.upbcientifica.mobile.data.auth

data class SessionResult(
    val token: String,
    val roles: List<String>,
    val serviciosAutorizados: List<String>
)

/**
 * Errores de autenticación.
 *
 * Cada caso lleva su mensaje al constructor de [Exception]. Antes heredaban de
 * `Exception()` sin texto, así que `CredencialesInvalidas` y `CuentaBloqueada`
 * tenían `message == null` y LoginScreen acababa mostrando siempre el genérico
 * "Error al iniciar sesión" (ver LoginViewModel: `e.message ?: ...`), aunque el
 * servidor hubiera explicado exactamente qué pasó.
 */
sealed class AuthError(mensaje: String) : Exception(mensaje) {
    data object CredencialesInvalidas : AuthError("Usuario o contraseña incorrectos")
    data object CuentaBloqueada : AuthError("La cuenta está bloqueada")
    data class Desconocido(val detalle: String) : AuthError(detalle)
}
