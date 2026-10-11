package com.upbcientifica.mobile.data.sharedfile

/**
 * Shared File Service. Ver [SharedFileApi].
 *
 * Sin [api] (Configuracion.SHARED_FILE_DISPONIBLE = false) NO simula: antes
 * devolvía un id de mentira al compartir y una lista inventada, y la app
 * parecía compartir cuando no pasaba nada. Ahora lo dice.
 */
class SharedFileRepository(private val api: SharedFileApi? = null) {

    suspend fun compartir(request: CompartirRequest): CompartirResponse =
        disponible().compartir(request)

    suspend fun compartidosConmigo(): List<RecursoCompartidoDto> =
        disponible().listarCompartidosConmigo()

    suspend fun revocar(compartidoId: String) = disponible().revocar(compartidoId)

    private fun disponible(): SharedFileApi = api ?: throw IllegalStateException(NO_DISPONIBLE)

    companion object {
        const val NO_DISPONIBLE =
            "El servicio Shared File todavía no existe en el backend, así que no se puede compartir ni ver lo compartido contigo."
    }
}
