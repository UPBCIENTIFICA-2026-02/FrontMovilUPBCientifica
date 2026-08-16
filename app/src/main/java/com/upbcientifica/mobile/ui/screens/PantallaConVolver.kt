package com.upbcientifica.mobile.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Envoltorio simple: barra superior con título y flecha de volver al Home.
 * Para el MVP esto basta; más adelante se puede migrar a Navigation-Compose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaConVolver(
    titulo: String,
    onVolver: () -> Unit,
    contenido: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titulo) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            contenido()
        }
    }
}