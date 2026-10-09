package com.upbcientifica.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.upbcientifica.mobile.ui.components.IndicadorSincronizacion
import com.upbcientifica.mobile.ui.components.MenuCuenta
import com.upbcientifica.mobile.ui.viewmodel.HpcViewModel
import com.upbcientifica.mobile.ui.viewmodel.SharedFileViewModel
import com.upbcientifica.mobile.ui.viewmodel.StorageViewModel
import com.upbcientifica.mobile.ui.viewmodel.StreamingViewModel
import com.upbcientifica.mobile.ui.viewmodel.SyncViewModel

/** Destinos de la barra inferior. Sustituye a las rutas en String que había en MainActivity. */
enum class Pestana(val etiqueta: String, val icono: ImageVector, val iconoActivo: ImageVector) {
    INICIO("Inicio", Icons.Outlined.Home, Icons.Filled.Home),
    ARCHIVOS("Archivos", Icons.Outlined.Folder, Icons.Filled.Folder),
    COMPARTIDOS("Compartidos", Icons.Outlined.FolderShared, Icons.Filled.FolderShared),
    STREAMING("Streaming", Icons.Outlined.PlayCircle, Icons.Filled.PlayCircle),
    HPC("HPC", Icons.Outlined.Memory, Icons.Filled.Memory),
}

/**
 * Estructura de la app ya autenticada: contenido de la pestaña activa y barra
 * de navegación inferior, como en Drive. Para el MVP basta con este estado
 * local; más adelante se puede migrar a Navigation-Compose.
 */
@Composable
fun MainScreen(
    usuario: String,
    storageViewModel: StorageViewModel,
    sharedFileViewModel: SharedFileViewModel,
    streamingViewModel: StreamingViewModel,
    hpcViewModel: HpcViewModel,
    syncViewModel: SyncViewModel,
    onCerrarSesion: () -> Unit
) {
    var pestana by rememberSaveable { mutableStateOf(Pestana.INICIO) }
    val estadoSync by syncViewModel.estado.collectAsState()

    // Lo que va a la derecha de la barra superior en todas las pestañas:
    // el estado de File Sync (solo informativo) y el avatar con la cuenta.
    val accionesCuenta: @Composable () -> Unit = {
        IndicadorSincronizacion(estadoSync)
        MenuCuenta(usuario, onCerrarSesion)
    }

    // Atrás desde cualquier pestaña vuelve a Inicio antes de salir de la app.
    // Se registra antes que el de StorageScreen, así que aquel (subir de
    // carpeta) tiene prioridad mientras esté habilitado.
    BackHandler(enabled = pestana != Pestana.INICIO) { pestana = Pestana.INICIO }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Pestana.entries.forEach { destino ->
                    val activa = destino == pestana
                    NavigationBarItem(
                        selected = activa,
                        onClick = { pestana = destino },
                        icon = {
                            Icon(
                                if (activa) destino.iconoActivo else destino.icono,
                                contentDescription = null
                            )
                        },
                        // Con cinco destinos el espacio es justo: una sola línea, sin cortar palabras.
                        label = {
                            Text(
                                destino.etiqueta,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Crossfade(targetState = pestana, label = "pestana") { actual ->
                when (actual) {
                    Pestana.INICIO -> HomeScreen(
                        usuario = usuario,
                        estadoSync = estadoSync,
                        onNavigate = { pestana = it },
                        accionesCuenta = accionesCuenta
                    )
                    Pestana.ARCHIVOS -> StorageScreen(storageViewModel, accionesCuenta)
                    Pestana.COMPARTIDOS -> SharedFileScreen(sharedFileViewModel, accionesCuenta)
                    Pestana.STREAMING -> StreamingScreen(streamingViewModel, accionesCuenta)
                    Pestana.HPC -> HpcScreen(hpcViewModel, accionesCuenta)
                }
            }
        }
    }
}
