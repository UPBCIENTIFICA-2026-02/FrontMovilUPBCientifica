package com.upbcientifica.mobile.data.streaming

/**
 * >>> NOTA PARA EL PROGRAMADOR DEL SERVIDOR (Dev E, Streaming) <
 * Endpoints REST esperados (ver StreamingApi.kt):
 *   GET /streaming/catalogo?pagina=N   -> lista de ContenidoDto
 *   GET /streaming/buscar?criterioTexto=... -> lista de ContenidoDto
 *   GET /streaming/{id}/sesion         -> SesionStreamingDto (sesionId, manifestUrl)
 * El video en sí se sirve por HTTP con soporte de rangos (range requests)
 * desde manifestUrl. ContenidoDto necesita: id, titulo, duracionSegundos.
 */
class StreamingRepository(
    private val api: StreamingApi? = null,
    private val usarDatosSimulados: Boolean = true
) {
    suspend fun catalogo(pagina: Int = 0): List<ContenidoDto> {
        if (usarDatosSimulados) return datosSimulados()
        return api!!.listarCatalogo(pagina)
    }

    suspend fun buscar(criterio: String): List<ContenidoDto> {
        if (usarDatosSimulados) return datosSimulados().filter { it.titulo.contains(criterio, true) }
        return api!!.buscarContenido(criterio)
    }

    suspend fun reproducir(contenidoId: String): SesionStreamingDto {
        if (usarDatosSimulados) return SesionStreamingDto("mock-sesion", "http://mock/manifest.m3u8")
        return api!!.iniciarSesion(contenidoId)
    }

    // TODO: eliminar cuando el backend real esté conectado
    private fun datosSimulados(): List<ContenidoDto> = listOf(
        ContenidoDto("v1", "Presentación proyecto UPB", 320),
        ContenidoDto("v2", "Demo del clúster HPC", 540),
        ContenidoDto("v3", "Charla resultados 2026", 1260),
    )
}