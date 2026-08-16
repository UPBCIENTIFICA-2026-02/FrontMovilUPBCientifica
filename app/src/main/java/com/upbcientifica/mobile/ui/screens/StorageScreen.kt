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
                            else "${archivo.tamanoBytes / 1000} KB · v${archivo.version}"
            Text(detalle)
        },
        leadingContent = {
            Text(if (archivo.tipo == "directorio") "📁" else "📄")
        }
    )
    HorizontalDivider()
}