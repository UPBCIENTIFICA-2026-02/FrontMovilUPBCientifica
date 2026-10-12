package com.upbcientifica.mobile.data.sharedfile

/**
 * Shared File Service. Ver [SharedFileApi].
 *
 * Sin [api] (Configuracion.SHARED_FILE_DISPONIBLE = false) NO simula: antes
 * devolvía un id de mentira al compartir y una lista inventada, y la app
 * parecía compartir cuando no pasaba nada. Ahora lo dice.
 */
class SharedFileRepository(private val api: SharedFileApi? = null) {

    suspend fun compartirArchivo(
        archivoId: String, tipo: TipoDestinatario, destinatario: String, nivel: NivelPermiso
    ): CompartirResponse = disponible().compartir(CompartirRequest(archivoId, tipo, destinatario, nivel))

    suspend fun compartirDirectorio(
        directorioId: String, tipo: TipoDestinatario, destinatario: String, nivel: NivelPermiso
    ): CompartirResponse =
        disponible().compartirDirectorio(CompartirDirectorioRequest(directorioId, tipo, destinatario, nivel))

    suspend fun compartidosConmigo(): List<RelacionCompartidoDto> = disponible().listarCompartidosConmigo()

    suspend fun queYoComparto(): List<RelacionCompartidoDto> = disponible().listarQueYoComparto()

    suspend fun cambiarNivel(compartidoId: String, nivel: NivelPermiso): ActualizacionResponse =
        disponible().actualizar(compartidoId, ActualizarPermisoRequest(nivel))

    suspend fun revocar(compartidoId: String) = disponible().revocar(compartidoId)

    private fun disponible(): SharedFileApi = api ?: throw IllegalStateException(NO_DISPONIBLE)

    companion object {
        const val NO_DISPONIBLE =
            "El servicio Shared File no está configurado en la app (Configuracion.SHARED_FILE_DISPONIBLE)."
    }
}
