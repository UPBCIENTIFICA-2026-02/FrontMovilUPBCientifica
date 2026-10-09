package com.upbcientifica.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upbcientifica.mobile.data.storage.ArchivoDto
import com.upbcientifica.mobile.data.storage.StorageRepository
import com.upbcientifica.mobile.ui.state.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Un nivel de la ruta de carpetas que se muestra como migas de pan. */
data class Carpeta(val id: String, val nombre: String)

class StorageViewModel(private val repo: StorageRepository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<ArchivoDto>>>(UiState.Idle)
    val state: StateFlow<UiState<List<ArchivoDto>>> = _state

    /** Ruta desde la raíz hasta la carpeta abierta; nunca vacía. */
    private val _ruta = MutableStateFlow(listOf(Carpeta("root", "Mis archivos")))
    val ruta: StateFlow<List<Carpeta>> = _ruta

    private var cargaActual: Job? = null

    /** Carga la carpeta actual solo si todavía no hay nada (al volver a la pestaña). */
    fun cargarSiHaceFalta() {
        if (_state.value is UiState.Idle) recargar()
    }

    fun recargar() = cargar(_ruta.value.last().id)

    fun abrirCarpeta(carpeta: ArchivoDto) {
        _ruta.value = _ruta.value + Carpeta(carpeta.id, carpeta.nombre)
        recargar()
    }

    /** Sube un nivel. Devuelve false si ya estaba en la raíz. */
    fun subirNivel(): Boolean {
        if (_ruta.value.size <= 1) return false
        _ruta.value = _ruta.value.dropLast(1)
        recargar()
        return true
    }

    /** Salta a un nivel concreto de las migas de pan. */
    fun irANivel(indice: Int) {
        if (indice >= _ruta.value.lastIndex) return
        _ruta.value = _ruta.value.take(indice + 1)
        recargar()
    }

    private fun cargar(directorioId: String) {
        // Si el usuario navega rápido, la respuesta de una carpeta anterior no
        // debe pisar la de la carpeta que está viendo ahora.
        cargaActual?.cancel()
        cargaActual = viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                UiState.Success(repo.listarHome(directorioId))
            } catch (e: CancellationException) {
                throw e // cancelada por otra navegación: no es un error que mostrar
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Error al cargar archivos")
            }
        }
    }
}
