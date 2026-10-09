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
                android.util.Log.e("LoginViewModel", "Fallo al iniciar sesion", e)
                UiState.Error(describir(e))
            }
        }
    }

    /**
     * Cierra la sesión y vuelve el estado a Idle. Sin ese reinicio, el
     * LaunchedEffect de LoginScreen vería otra vez Success y entraría solo.
     * Un fallo del servidor no importa: AuthRepository borra el token igual.
     */
    fun cerrarSesion(onCerrada: () -> Unit) {
        viewModelScope.launch {
            try {
                authRepository.logout()
            } catch (e: Exception) {
                android.util.Log.w("LoginViewModel", "Logout remoto falló; la sesión local se cierra igual", e)
            }
            _state.value = UiState.Idle
            onCerrada()
        }
    }

    /**
     * Mensaje que sirva para diagnosticar algo.
     *
     * Antes era `e.message ?: "Error al iniciar sesión"`, y el problema es que
     * muchas excepciones de Android llegan con `message == null` (las del
     * almacén de claves, entre otras). Con eso, un fallo posterior al login se
     * veía exactamente igual que una contraseña mal escrita, sin forma de
     * distinguirlos. Ahora, si no hay mensaje, al menos se muestra el tipo de
     * excepción, y la traza completa queda en Logcat.
     */
    private fun describir(e: Exception): String {
        val detalle = e.message?.takeIf { it.isNotBlank() }
            ?: e.cause?.message?.takeIf { it.isNotBlank() }
        return detalle ?: "Error al iniciar sesión (${e.javaClass.simpleName})"
    }
}
