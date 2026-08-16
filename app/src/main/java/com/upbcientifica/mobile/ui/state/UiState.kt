package com.upbcientifica.mobile.ui.state

/** Envoltorio genérico para el estado de cualquier pantalla que llama a un servicio. */
sealed class UiState<out T> {
    data object Idle : UiState<Nothing>()
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
