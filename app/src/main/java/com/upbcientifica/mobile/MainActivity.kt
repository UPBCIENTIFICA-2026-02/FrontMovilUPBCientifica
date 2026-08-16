package com.upbcientifica.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.upbcientifica.mobile.data.auth.AuthRepository
import com.upbcientifica.mobile.data.sharedfile.SharedFileRepository
import com.upbcientifica.mobile.data.storage.StorageRepository
import com.upbcientifica.mobile.data.streaming.StreamingRepository
import com.upbcientifica.mobile.network.SoapClient
import com.upbcientifica.mobile.session.TokenManager
import com.upbcientifica.mobile.ui.screens.*
import com.upbcientifica.mobile.ui.screens.PantallaConVolver
import com.upbcientifica.mobile.ui.viewmodel.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Composición manual de dependencias ---
        val tokenManager = TokenManager(applicationContext)
        val soapClient = SoapClient()
        val authRepository = AuthRepository(soapClient, tokenManager)

        // Repositorios en modo simulado (usarDatosSimulados=true por defecto).
        // Cuando el backend real esté listo, pásales el api real y pon el flag en false.
        val storageRepository = StorageRepository()
        val sharedFileRepository = SharedFileRepository()
        val streamingRepository = StreamingRepository()

        setContent {
            MaterialTheme {
                Surface {
                    // pantallaActual: null = Home; "storage"/"shared_file"/"streaming" = servicio
                    var loggedIn by remember { mutableStateOf(false) }
                    var pantallaActual by remember { mutableStateOf<String?>(null) }

                    when {
                        // ---------- No autenticado: Login ----------
                        !loggedIn -> {
                            val loginViewModel: LoginViewModel = viewModel(
                                factory = simpleFactory { LoginViewModel(authRepository) }
                            )
                            LoginScreen(
                                viewModel = loginViewModel,
                                onLoginSuccess = { loggedIn = true }
                            )
                        }

                        // ---------- Autenticado, dentro de un servicio ----------
                        pantallaActual == "storage" -> {
                            val vm: StorageViewModel = viewModel(
                                factory = simpleFactory { StorageViewModel(storageRepository) }
                            )
                            PantallaConVolver("Mis archivos", onVolver = { pantallaActual = null }) {
                                StorageScreen(vm)
                            }
                        }
                        pantallaActual == "shared_file" -> {
                            val vm: SharedFileViewModel = viewModel(
                                factory = simpleFactory { SharedFileViewModel(sharedFileRepository) }
                            )
                            PantallaConVolver("Compartidos conmigo", onVolver = { pantallaActual = null }) {
                                SharedFileScreen(vm)
                            }
                        }
                        pantallaActual == "streaming" -> {
                            val vm: StreamingViewModel = viewModel(
                                factory = simpleFactory { StreamingViewModel(streamingRepository) }
                            )
                            PantallaConVolver("Streaming", onVolver = { pantallaActual = null }) {
                                StreamingScreen(vm)
                            }
                        }
                        pantallaActual == "file_sync" -> {
                            // File Sync (gRPC) sigue pendiente del .proto de Dev D
                            PantallaConVolver("Sincronización", onVolver = { pantallaActual = null }) {
                                Text("Servicio de sincronización aún no disponible.")
                            }
                        }

                        // ---------- Autenticado, en el Home ----------
                        else -> HomeScreen(onNavigate = { ruta -> pantallaActual = ruta })
                    }
                }
            }
        }
    }
}

/**
 * Fábrica genérica de ViewModels — evita repetir el objeto anónimo
 * ViewModelProvider.Factory en cada pantalla.
 */
private inline fun <VM : ViewModel> simpleFactory(crossinline crear: () -> VM) =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = crear() as T
    }