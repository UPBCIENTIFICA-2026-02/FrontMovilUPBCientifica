package com.upbcientifica.mobile.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.upbcientifica.mobile.data.filesync.ConfigSync
import com.upbcientifica.mobile.data.filesync.EstadoSincronizacion
import com.upbcientifica.mobile.data.filesync.FileSyncRepository
import com.upbcientifica.mobile.data.filesync.Programacion
import kotlinx.coroutines.flow.StateFlow

/**
 * Pantalla de File Sync e indicador de la barra superior. El estado vive en el
 * repositorio (uno por proceso) y no aquí, porque la sincronización la puede
 * disparar el Wi-Fi en segundo plano sin que esta pantalla exista.
 */
class SyncViewModel(private val repo: FileSyncRepository) : ViewModel() {

    val estado: StateFlow<EstadoSincronizacion> = repo.estado
    val config: StateFlow<ConfigSync> = repo.config

    fun elegirCarpeta(arbol: Uri) = repo.elegirCarpeta(arbol)
    fun cambiarCarpetaRemota(nombre: String) = repo.cambiarCarpetaRemota(nombre)
    fun alConectarWifi(activo: Boolean) = repo.alConectarWifi(activo)
    fun programar(programacion: Programacion, hora: Int, minuto: Int) = repo.programar(programacion, hora, minuto)
    fun sincronizarAhora() = repo.sincronizarAhora()
}
