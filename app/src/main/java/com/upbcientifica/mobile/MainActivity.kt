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
import com.upbcientifica.mobile.data.storage.ArchivosDelDispositivo
import com.upbcientifica.mobile.data.storage.StorageApi
import com.upbcientifica.mobile.data.storage.StorageRepository
import com.upbcientifica.mobile.data.streaming.StreamingApi
import com.upbcientifica.mobile.data.streaming.StreamingRepository
import com.upbcientifica.mobile.data.filesync.FileSyncModulo
import com.upbcientifica.mobile.data.hpc.HpcApi
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
            usarDatosSimulados = false,
            // Para el reproductor de video, que pide el archivo por su cuenta.
            urlBase = Configuracion.urlStorage,
            token = tokenManager::currentToken
        )
        val archivosDelDispositivo = ArchivosDelDispositivo(applicationContext.contentResolver)

        // Streaming y Photo Album (node-media, puerto 8083) — BACKEND REAL.
        val streamingRepository = StreamingRepository(
            api = RestClient.build(Configuracion.urlMedia, tokenManager)
                .create(StreamingApi::class.java),
            usarDatosSimulados = false,
            urlBase = Configuracion.urlMedia,
            token = tokenManager::currentToken
        )

        // Shared File (node-shared-file, puerto 8085) — BACKEND REAL. Decide y
        // registra quién comparte con quién; Storage aplica el permiso, así que
        // lo compartido se abre y se descarga con storageRepository.
        val sharedFileRepository =
            if (Configuracion.SHARED_FILE_DISPONIBLE) {
                SharedFileRepository(
                    RestClient.build(Configuracion.urlCompartidos, tokenManager)
                        .create(SharedFileApi::class.java)
                )
            } else {
                SharedFileRepository()
            }

        // HPC (job-api, puerto 8082) — BACKEND REAL. Android no trae java.rmi,
        // así que se habla REST con job-api, la fachada HTTP del gestor RMI.
        val hpcApi = RestClient.build(Configuracion.urlTrabajos, tokenManager)
            .create(HpcApi::class.java)

        /*
         * File Sync (node-sync, gRPC 9090) — BACKEND REAL.
         * ANTES iba simulado (gRPC desactivado). AHORA respalda una carpeta del
         * teléfono en el Home y se sincroniza sola al llegar al Wi-Fi del
         * servidor. Lo crea FileSyncModulo y no este archivo porque el trabajo
         * en segundo plano necesita la misma instancia sin pasar por aquí.
         */
        val fileSyncRepository = FileSyncModulo.repositorio(applicationContext)

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
                    // La sincronización en segundo plano sigue al usuario con sesión.
                    LaunchedEffect(actual) { actual?.let(fileSyncRepository::iniciarSesion) }
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
                                factory = simpleFactory { StorageViewModel(storageRepository, archivosDelDispositivo, sharedFileRepository) }
                            ),
                            sharedFileViewModel = viewModel(
                                key = "shared-$sesion",
                                factory = simpleFactory {
                                    SharedFileViewModel(sharedFileRepository, storageRepository, archivosDelDispositivo)
                                }
                            ),
                            streamingViewModel = viewModel(
                                key = "streaming-$sesion",
                                factory = simpleFactory { StreamingViewModel(streamingRepository) }
                            ),
                            // El servidor filtra los trabajos por el usuario del token,
                            // así que la cuenta siguiente no ve los de la anterior.
                            hpcViewModel = viewModel(
                                key = "hpc-$sesion",
                                factory = simpleFactory { HpcViewModel(HpcRepository(hpcApi)) }
                            ),
                            syncViewModel = viewModel(
                                key = "sync-$sesion",
                                factory = simpleFactory { SyncViewModel(fileSyncRepository) }
                            ),
                            onCerrarSesion = {
                                fileSyncRepository.cerrarSesion()
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