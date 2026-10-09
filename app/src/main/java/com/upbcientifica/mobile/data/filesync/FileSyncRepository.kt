package com.upbcientifica.mobile.data.filesync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/** Lo que la UI necesita saber de File Sync para pintar el indicador superior. */
sealed class EstadoSincronizacion {
    /** [progreso] va de 0 a 1; [pendientes] son los cambios que faltan por subir. */
    data class Sincronizando(val progreso: Float, val pendientes: Int) : EstadoSincronizacion()
    data class AlDia(val ultimaSincronizacion: Long) : EstadoSincronizacion()
    data object SinConexion : EstadoSincronizacion()
}

/**
 * File Sync Service habla gRPC (Contrato_FileSync: streaming bidireccional
 * para DetectarCambiosLocales). Falta el .proto real de Dev D para generar el
 * stub Kotlin (FileSyncServiceCoroutineStub); gRPC está desactivado en
 * build.gradle.kts — ver GrpcClient.kt.
 *
 * MIENTRAS TANTO, [estado] SIMULA el ciclo de sincronización para que la UI
 * quede lista: detecta cambios, los "sube" con progreso y queda al día hasta
 * el siguiente ciclo. La conectividad sí es real (ConnectivityManager).
 *
 * Cuando exista el stub, este repositorio debe:
 *  1. Recibir el stub generado en el constructor.
 *  2. Convertir el stream de DetectarCambiosLocales en este mismo
 *     Flow<EstadoSincronizacion>; la UI no tiene que cambiar.
 *  (El versionado real lo delega en Storage — este servicio solo detecta
 *  cambios y orquesta la sesión.)
 */
class FileSyncRepository(private val context: Context? = null) {

    fun estado(): Flow<EstadoSincronizacion> = flow {
        while (true) {
            if (!hayRed()) {
                emit(EstadoSincronizacion.SinConexion)
                delay(REVISION_RED_MS)
                continue
            }

            // TODO: reemplazar por el stream gRPC cuando llegue el .proto
            val pendientes = Random.nextInt(2, 9)
            for (subidos in 0..pendientes) {
                emit(EstadoSincronizacion.Sincronizando(subidos / pendientes.toFloat(), pendientes - subidos))
                delay(450)
            }
            emit(EstadoSincronizacion.AlDia(System.currentTimeMillis()))

            // Espera al siguiente ciclo, pero si se cae la red lo muestra enseguida.
            var esperado = 0L
            while (esperado < INTERVALO_CICLO_MS && hayRed()) {
                delay(REVISION_RED_MS)
                esperado += REVISION_RED_MS
            }
        }
    }

    private fun hayRed(): Boolean {
        val cm = context?.getSystemService(ConnectivityManager::class.java) ?: return true
        val capacidades = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val INTERVALO_CICLO_MS = 60_000L
        const val REVISION_RED_MS = 3_000L
    }
}
