package com.upbcientifica.mobile.data

/**
 * Lo que necesita el reproductor para pedir un video al servidor: la URL
 * completa y las cabeceras (el token). El reproductor pide el archivo por
 * rangos, así que no se descarga entero antes de empezar.
 */
data class FuenteVideo(val url: String, val cabeceras: Map<String, String>)

/** URL absoluta a partir de la base del servicio ("http://host:8083/") y una ruta ("/streaming/x"). */
internal fun unirUrl(base: String, ruta: String): String = base.trimEnd('/') + "/" + ruta.trimStart('/')

internal fun cabecerasToken(token: String?): Map<String, String> =
    if (token == null) emptyMap() else mapOf("Authorization" to "Bearer $token")
