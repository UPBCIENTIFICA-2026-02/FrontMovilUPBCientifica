package com.upbcientifica.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upbcientifica.mobile.data.streaming.ContenidoDto
import com.upbcientifica.mobile.data.streaming.StreamingRepository
import com.upbcientifica.mobile.ui.state.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StreamingViewModel(private val repo: StreamingRepository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<ContenidoDto>>>(UiState.Idle)
    val state: StateFlow<UiState<List<ContenidoDto>>> = _state

    /** Carga solo si todavía no hay nada (al volver a la pestaña). */
    fun cargarSiHaceFalta() {
        if (_state.value is UiState.Idle) cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                UiState.Success(repo.catalogo())
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Error al cargar catálogo")
            }
        }
    }
}