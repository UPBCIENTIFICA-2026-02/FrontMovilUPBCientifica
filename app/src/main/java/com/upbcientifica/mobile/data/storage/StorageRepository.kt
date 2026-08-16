package com.upbcientifica.mobile.data.storage

import okhttp3.MultipartBody

/**
 * >>> NOTA PARA EL PROGRAMADOR DEL SERVIDOR (Dev B, Distributed Storage) <
 * El cliente espera estos endpoints REST (ver StorageApi.kt):
 *   GET  /storage/directorios/{id}    -> lista de ArchivoDto del directorio
 *   POST /storage/archivos (multipart) -> sube y devuelve el ArchivoDto creado
 *   GET  /storage/archivos/{id}/descargar
 *   DELETE /storage/archivos/{id}
 * Cada ArchivoDto necesita: id, nombre, tipo ("archivo"|"directorio"),
 * tamanoBytes, version, directorioContenedorId. Ajustar nombres si el
 * contrato real difiere y avisar al equipo de móvil.
 */
class StorageRepository(
    private val api: StorageApi? = null,   // null mientras no haya backend real
    private val usarDatosSimulados: Boolean = true
) {
    suspend fun listarHome(directorioId: String): List<ArchivoDto> {
        if (usarDatosSimulados) return datosSimulados()
        return api!!.listarDirectorio(directorioId)
    }

    suspend fun subir(archivo: MultipartBody.Part, directorioDestino: String): ArchivoDto {
        if (usarDatosSimulados) error("Subida no disponible en modo simulado")
        return api!!.subirArchivo(archivo, directorioDestino)
    }

    suspend fun eliminar(archivoId: String) {
        if (usarDatosSimulados) return
        api!!.eliminarArchivo(archivoId)
    }

    // TODO: eliminar esta función cuando el backend real esté conectado
    private fun datosSimulados(): List<ArchivoDto> = listOf(
        ArchivoDto("1", "Documentos", "directorio", 0, 1, null),
        ArchivoDto("2", "Datasets", "directorio", 0, 1, null),
        ArchivoDto("3", "resultados_hpc.csv", "archivo", 245_000, 3, null),
        ArchivoDto("4", "informe_final.pdf", "archivo", 1_200_000, 1, null),
    )
}