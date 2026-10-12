package com.upbcientifica.mobile.data.streaming

import com.upbcientifica.mobile.data.FuenteVideo
import com.upbcientifica.mobile.data.cabecerasToken
import com.upbcientifica.mobile.data.unirUrl

/**
 * Streaming y Photo Album (node-media). Ver [StreamingApi].
 *
 * El video no lo descarga este repositorio: entrega una [FuenteVideo] y el
 * reproductor lo pide por rangos a node-media, que a su vez lo lee de Storage.
 * [urlBase] y [token] existen solo para eso.
 */
class StreamingRepository(
    private val api: StreamingApi? = null,
    private val usarDatosSimulados: Boolean = true,
    private val urlBase: String = "",
    private val token: () -> String? = { null },
) {
    suspend fun catalogo(pagina: Int = 0, orden: OrdenMedios = OrdenMedios.RECIENTES): List<ContenidoDto> {
        if (usarDatosSimulados) return datosSimulados()
        return api!!.listarCatalogo(pagina, orden.name)
    }

    suspend fun buscar(criterio: String): List<ContenidoDto> {
        if (usarDatosSimulados) return datosSimulados().filter { it.titulo.contains(criterio, true) }
        return api!!.buscarContenido(criterio)
    }

    /** Abre una sesión de reproducción y devuelve de dónde leer el video. */
    suspend fun reproducir(contenidoId: String): FuenteVideo {
        if (usarDatosSimulados) error("No hay video real en modo simulado")
        val sesion = api!!.iniciarSesion(contenidoId)
        return FuenteVideo(unirUrl(urlBase, sesion.manifestUrl), cabecerasToken(token()))
    }

    suspend fun fotos(pagina: Int = 0, orden: OrdenMedios = OrdenMedios.RECIENTES): List<FotoDto> {
        if (usarDatosSimulados) return emptyList()
        return api!!.listarFotos(pagina, orden.name)
    }

    suspend fun miniatura(fotoId: String): ByteArray = api!!.miniatura(fotoId).use { it.bytes() }

    suspend fun miniaturaVideo(videoId: String): ByteArray = api!!.miniaturaVideo(videoId).use { it.bytes() }

    suspend fun contenidoFoto(fotoId: String): ByteArray = api!!.contenidoFoto(fotoId).use { it.bytes() }

    suspend fun eliminarVideo(videoId: String) {
        if (usarDatosSimulados) error("No disponible en modo simulado")
        api!!.eliminarVideo(videoId)
    }

    suspend fun eliminarFoto(fotoId: String) {
        if (usarDatosSimulados) error("No disponible en modo simulado")
        api!!.eliminarFoto(fotoId)
    }

    suspend fun indexar(): IndexacionDto {
        if (usarDatosSimulados) return IndexacionDto(0, 0)
        return api!!.indexar()
    }

    // TODO: eliminar cuando el backend real esté conectado
    private fun datosSimulados(): List<ContenidoDto> = listOf(
        ContenidoDto("v1", "Presentación proyecto UPB", 320),
        ContenidoDto("v2", "Demo del clúster HPC", 540),
        ContenidoDto("v3", "Charla resultados 2026", 1260),
    )
}
