package com.upbcientifica.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upbcientifica.mobile.data.auth.AuthRepository
import com.upbcientifica.mobile.data.auth.SessionResult
import com.upbcientifica.mobile.ui.state.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Manejo de estado: StateFlow + ViewModel (arquitectura recomendada por Android/
 * Jetpack). Se prefiere sobre Redux-Toolkit/Zustand porque esas son librerías
 * pensadas para React/React Native; en Kotlin nativo, StateFlow ya es la
 * solución estándar del propio framework — sin dependencias extra.
 */
class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<SessionResult>>(UiState.Idle)
    val state: StateFlow<UiState<SessionResult>> = _state

    fun login(usuario: String, contrasena: String) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                UiState.Success(authRepository.login(usuario, contrasena))
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Error al iniciar sesión")
            }
        }
    }
}
