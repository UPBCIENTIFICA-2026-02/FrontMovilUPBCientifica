package com.upbcientifica.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Un tile por cada servicio ya soportado en el cliente móvil. HPC no aparece
 * aquí todavía — ver README.md sobre el bloqueo de RMI en Android/iOS.
 */
private data class ServiceTile(val nombre: String, val ruta: String)

private val serviciosDisponibles = listOf(
    ServiceTile("Mis archivos", "storage"),
    ServiceTile("Compartidos conmigo", "shared_file"),
    ServiceTile("Streaming", "streaming"),
    ServiceTile("Sincronización", "file_sync"),
)

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Servicios", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(serviciosDisponibles) { servicio ->
                ElevatedCard(
                    onClick = { onNavigate(servicio.ruta) },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                ) {
                    Box(Modifier.fillMaxSize().padding(12.dp)) {
                        Text(servicio.nombre, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
