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
import com.upbcientifica.mobile.data.sharedfile.SharedFileApi
import com.upbcientifica.mobile.data.sharedfile.SharedFileRepository
import com.upbcientifica.mobile.data.storage.StorageApi
import com.upbcientifica.mobile.data.storage.StorageRepository
import com.upbcientifica.mobile.data.streaming.StreamingApi
import com.upbcientifica.mobile.data.streaming.StreamingRepository
import com.upbcientifica.mobile.network.Configuracion
import com.upbcientifica.mobile.network.RestClient
import com.upbcientifica.mobile.network.SoapClient
import com.upbcientifica.mobile.session.TokenManager
import com.upbcientifica.mobile.ui.screens.*
import com.upbcientifica.mobile.ui.screens.PantallaConVolver
import com.upbcientifica.mobile.ui.viewmodel.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Composición manual de dependencias ---
        //
        // Este es el único sitio donde se decide qué implementación usa cada
        // repositorio. Las direcciones salen de Configuracion; para cambiar de
        // emulador a celular o al laboratorio no hay que tocar este archivo.
        val tokenManager = TokenManager(applicationContext)
        val soapClient = SoapClient()
        val authRepository = AuthRepository(soapClient, tokenManager)

        // Distributed Storage (node-repository, puerto 8084) — BACKEND REAL.
        val storageRepository = StorageRepository(
            api = RestClient.build(Configuracion.urlStorage, tokenManager)
                .create(StorageApi::class.java),
            usarDatosSimulados = false
        )

        // Streaming (node-media, puerto 8083) — BACKEND REAL.
        val streamingRepository = StreamingRepository(
            api = RestClient.build(Configuracion.urlMedia, tokenManager)
                .create(StreamingApi::class.java),
            usarDatosSimulados = false
        )

        /*
         * Shared File sigue SIMULADO a propósito: no existe ningún servicio que
         * responda las rutas `/shared-file`. El diagrama de despliegue lo dibuja como
         * ejecutable `shared-file-svc` dentro de la VM node-repository, pero
         * todavía no está implementado.
         *
         * Cuando exista, basta cambiar SHARED_FILE_DISPONIBLE en Configuracion.
         */
        val sharedFileRepository =
            if (Configuracion.SHARED_FILE_DISPONIBLE) {
                SharedFileRepository(
                    api = RestClient.build(Configuracion.urlStorage, tokenManager)
                        .create(SharedFileApi::class.java),
                    usarDatosSimulados = false
                )
            } else {
                SharedFileRepository()
            }

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