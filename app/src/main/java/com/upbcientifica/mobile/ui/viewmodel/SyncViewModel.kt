package com.upbcientifica.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upbcientifica.mobile.data.filesync.EstadoSincronizacion
import com.upbcientifica.mobile.data.filesync.FileSyncRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SyncViewModel(repo: FileSyncRepository) : ViewModel() {

    /**
     * Se mantiene vivo 5 s sin observadores para que un giro de pantalla no
     * reinicie el ciclo de sincronización.
     */
    val estado: StateFlow<EstadoSincronizacion> = repo.estado().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoSincronizacion.Sincronizando(progreso = 0f, pendientes = 0)
    )
}
