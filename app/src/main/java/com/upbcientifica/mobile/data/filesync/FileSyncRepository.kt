package com.upbcientifica.mobile.data.filesync

/**
 * TODO: File Sync Service habla gRPC (Contrato_FileSync: streaming
 * bidireccional para DetectarCambiosLocales). Falta el .proto real de
 * Dev D para generar el stub Kotlin (FileSyncServiceCoroutineStub).
 *
 * gRPC está desactivado en build.gradle.kts por ahora — ver GrpcClient.kt.
 * Cuando esté disponible, este repositorio debe:
 *  1. Recibir el stub generado en el constructor.
 *  2. Exponer una función suspend/Flow que dispare la sincronización y
 *     emita el progreso (el versionado real lo delega en Storage — este
 *     servicio solo detecta cambios y orquesta la sesión).
 */
class FileSyncRepository {
    // Placeholder — implementar cuando exista el stub generado del .proto
}
