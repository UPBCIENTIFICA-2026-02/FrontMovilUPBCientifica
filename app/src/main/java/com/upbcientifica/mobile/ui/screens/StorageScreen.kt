package com.upbcientifica.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.upbcientifica.mobile.data.storage.ArchivoDto
import com.upbcientifica.mobile.ui.state.UiState
import com.upbcientifica.mobile.ui.viewmodel.StorageViewModel
import java.util.Locale

/**
 * Tamaño legible.
 *
 * Antes se mostraba siempre `tamanoBytes / 1000` con la etiqueta "KB", así que
 * un archivo de 240 MB aparecía como "251658 KB". Con los datos simulados no se
 * notaba porque eran cifras pequeñas; con Storage real, sí.
 */
private fun formatearTamano(bytes: Long): String {
    val unidades = listOf("B", "KB", "MB", "GB", "TB")
    var valor = bytes.toDouble()
    var i = 0
    while (valor >= 1024 && i < unidades.lastIndex) {
        valor /= 1024
        i++
    }
    // Sin decimales para bytes y KB: "512 B" se lee mejor que "512,0 B".
    val patron = if (i <= 1) "%.0f %s" else "%.1f %s"
    return String.format(Locale.getDefault(), patron, valor, unidades[i])
}

@Composable
fun StorageScreen(viewModel: StorageViewModel) {
    val state by viewModel.state.collectAsState()

    // Dispara la carga la primera vez que se muestra la pantalla
    LaunchedEffect(Unit) { viewModel.cargar() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Mis archivos", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        when (val s = state) {
            is UiState.Loading -> CircularProgressIndicator()
            is UiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            is UiState.Success -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.data) { archivo -> ArchivoItem(archivo) }
            }
            UiState.Idle -> {}
        }
    }
}

@Composable
private fun ArchivoItem(archivo: ArchivoDto) {
    ListItem(
        headlineContent = {
            Text(archivo.nombre, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            val detalle = if (archivo.tipo == "directorio") "Carpeta"
                            else "${formatearTamano(archivo.tamanoBytes)} · v${archivo.version}"
            Text(detalle)
        },
        leadingContent = {
            Text(if (archivo.tipo == "directorio") "📁" else "📄")
        }
    )
    HorizontalDivider()
}