package com.upbcientifica.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upbcientifica.mobile.data.hpc.EstadoCluster
import com.upbcientifica.mobile.data.hpc.HpcRepository
import com.upbcientifica.mobile.data.hpc.SolicitudTrabajo
import com.upbcientifica.mobile.data.hpc.TrabajoHpc
import com.upbcientifica.mobile.ui.state.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DatosHpc(val cluster: EstadoCluster, val trabajos: List<TrabajoHpc>)

class HpcViewModel(private val repo: HpcRepository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<DatosHpc>>(UiState.Idle)
    val state: StateFlow<UiState<DatosHpc>> = _state

    private val _enviando = MutableStateFlow(false)
    val enviando: StateFlow<Boolean> = _enviando

    /**
     * Consulta clúster y trabajos. Solo muestra la pantalla de carga la
     * primera vez: en las consultas periódicas los datos se reemplazan sin
     * parpadeo, y si una falla se conserva lo último que se tenía.
     */
    fun actualizar() {
        viewModelScope.launch {
            val primeraVez = _state.value !is UiState.Success
            if (primeraVez) _state.value = UiState.Loading
            try {
                // coroutineScope: si una de las dos consultas falla, la
                // excepción llega a este catch en vez de tumbar la app.
                _state.value = UiState.Success(coroutineScope {
                    val cluster = async { repo.estadoCluster() }
                    val trabajos = async { repo.consultarTrabajos() }
                    DatosHpc(cluster.await(), trabajos.await())
                })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (primeraVez) _state.value = UiState.Error(e.message ?: "Error al consultar el clúster")
            }
        }
    }

    fun enviarTrabajo(solicitud: SolicitudTrabajo, onResultado: (Result<TrabajoHpc>) -> Unit) {
        viewModelScope.launch {
            _enviando.value = true
            val resultado = try {
                Result.success(repo.enviarTrabajo(solicitud))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            _enviando.value = false
            onResultado(resultado)
            actualizar()
        }
    }
}
