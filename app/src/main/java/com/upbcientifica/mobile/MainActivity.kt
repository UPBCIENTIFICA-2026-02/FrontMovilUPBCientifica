package com.upbcientifica.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.upbcientifica.mobile.data.filesync.FileSyncRepository
import com.upbcientifica.mobile.data.hpc.HpcRepository
import com.upbcientifica.mobile.network.Configuracion
import com.upbcientifica.mobile.network.RestClient
import com.upbcientifica.mobile.network.SoapClient
import com.upbcientifica.mobile.session.TokenManager
import com.upbcientifica.mobile.ui.screens.*
import com.upbcientifica.mobile.ui.theme.UpbCientificaTheme
import com.upbcientifica.mobile.ui.viewmodel.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Dibuja detrás de las barras del sistema; cada pantalla respeta los insets.
        enableEdgeToEdge()
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

        /*
         * File Sync (gRPC) y HPC (RMI) van SIMULADOS para que la interfaz esté
         * completa: el primero espera el .proto de Dev D y el segundo la
         * decisión de protocolo con Dev C (ver README y cada repositorio).
         * La conectividad que muestra File Sync sí es real.
         */
        val fileSyncRepository = FileSyncRepository(applicationContext)

        setContent {
            UpbCientificaTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    // usuario: null = sin sesión. `sesion` cambia en cada login para
                    // que los ViewModels de servicios empiecen limpios y no muestren
                    // los archivos de la cuenta anterior tras cerrar sesión.
                    var usuario by rememberSaveable { mutableStateOf<String?>(null) }
                    var sesion by rememberSaveable { mutableIntStateOf(0) }

                    val loginViewModel: LoginViewModel = viewModel(
                        factory = simpleFactory { LoginViewModel(authRepository) }
                    )

                    val actual = usuario
                    if (actual == null) {
                        LoginScreen(
                            viewModel = loginViewModel,
                            onLoginSuccess = {
                                sesion++
                                usuario = it
                            }
                        )
                    } else {
                        MainScreen(
                            usuario = actual,
                            storageViewModel = viewModel(
                                key = "storage-$sesion",
                                factory = simpleFactory { StorageViewModel(storageRepository) }
                            ),
                            sharedFileViewModel = viewModel(
                                key = "shared-$sesion",
                                factory = simpleFactory { SharedFileViewModel(sharedFileRepository) }
                            ),
                            streamingViewModel = viewModel(
                                key = "streaming-$sesion",
                                factory = simpleFactory { StreamingViewModel(streamingRepository) }
                            ),
                            // Repositorio nuevo por sesión: los trabajos simulados
                            // de una cuenta no deben verse en la siguiente.
                            hpcViewModel = viewModel(
                                key = "hpc-$sesion",
                                factory = simpleFactory { HpcViewModel(HpcRepository()) }
                            ),
                            syncViewModel = viewModel(
                                key = "sync-$sesion",
                                factory = simpleFactory { SyncViewModel(fileSyncRepository) }
                            ),
                            onCerrarSesion = {
                                loginViewModel.cerrarSesion(onCerrada = { usuario = null })
                            }
                        )
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