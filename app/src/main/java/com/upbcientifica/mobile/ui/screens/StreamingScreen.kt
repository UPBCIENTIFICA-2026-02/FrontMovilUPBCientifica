package com.upbcientifica.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.upbcientifica.mobile.ui.state.UiState
import com.upbcientifica.mobile.ui.viewmodel.StreamingViewModel

@Composable
fun StreamingScreen(viewModel: StreamingViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.cargar() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Streaming", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        when (val s = state) {
            is UiState.Loading -> CircularProgressIndicator()
            is UiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            is UiState.Success -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.data) { video ->
                    ListItem(
                        headlineContent = { Text(video.titulo) },
                        supportingContent = {
                            Text("${video.duracionSegundos / 60} min")
                        },
                        leadingContent = { Text("▶️") }
                    )
                    HorizontalDivider()
                }
            }
            UiState.Idle -> {}
        }
    }
}