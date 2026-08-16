package com.upbcientifica.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.upbcientifica.mobile.ui.state.UiState
import com.upbcientifica.mobile.ui.viewmodel.SharedFileViewModel

@Composable
fun SharedFileScreen(viewModel: SharedFileViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.cargar() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        when (val s = state) {
            is UiState.Loading -> CircularProgressIndicator()
            is UiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            is UiState.Success -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.data) { recurso ->
                    ListItem(
                        headlineContent = { Text(recurso.nombreArchivo) },
                        supportingContent = {
                            Text("De: ${recurso.propietario} · Permiso: ${recurso.permisoUnix}")
                        },
                        leadingContent = { Text("🔗") }
                    )
                    HorizontalDivider()
                }
            }
            UiState.Idle -> {}
        }
    }
}