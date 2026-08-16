package com.upbcientifica.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upbcientifica.mobile.data.storage.ArchivoDto
import com.upbcientifica.mobile.data.storage.StorageRepository
import com.upbcientifica.mobile.ui.state.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StorageViewModel(private val repo: StorageRepository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<ArchivoDto>>>(UiState.Idle)
    val state: StateFlow<UiState<List<ArchivoDto>>> = _state

    fun cargar(directorioId: String = "root") {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                UiState.Success(repo.listarHome(directorioId))
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Error al cargar archivos")
            }
        }
    }
}