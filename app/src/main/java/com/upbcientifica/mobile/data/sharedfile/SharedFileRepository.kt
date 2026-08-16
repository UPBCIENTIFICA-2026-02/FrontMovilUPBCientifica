package com.upbcientifica.mobile.data.sharedfile

/**
 * >>> NOTA PARA EL PROGRAMADOR DEL SERVIDOR (Dev D, Shared File) <
 * Endpoints REST esperados (ver SharedFileApi.kt):
 *   POST   /shared-file/compartir          -> CompartirResponse
 *   GET    /shared-file/compartidos-conmigo -> lista de RecursoCompartidoDto
 *   DELETE /shared-file/compartir/{id}
 * RecursoCompartidoDto necesita: compartidoId, archivoId, nombreArchivo,
 * propietario, permisoUnix (ej. "r--"). Recordar que este servicio delega
 * la persistencia real en Distributed Storage (no guarda bytes).
 */
class SharedFileRepository(
    private val api: SharedFileApi? = null,
    private val usarDatosSimulados: Boolean = true
) {
    suspend fun compartir(request: CompartirRequest): CompartirResponse {
        if (usarDatosSimulados) return CompartirResponse("mock-shared-1")
        return api!!.compartir(request)
    }

    suspend fun compartidosConmigo(): List<RecursoCompartidoDto> {
        if (usarDatosSimulados) return datosSimulados()
        return api!!.listarCompartidosConmigo()
    }

    suspend fun revocar(compartidoId: String) {
        if (usarDatosSimulados) return
        api!!.revocar(compartidoId)
    }

    // TODO: eliminar cuando el backend real esté conectado
    private fun datosSimulados(): List<RecursoCompartidoDto> = listOf(
        RecursoCompartidoDto("s1", "10", "paper_borrador.docx", "maria.lopez", "r--"),
        RecursoCompartidoDto("s2", "11", "dataset_compartido.zip", "carlos.ruiz", "rw-"),
    )
}