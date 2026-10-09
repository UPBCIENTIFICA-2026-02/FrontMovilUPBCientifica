package com.upbcientifica.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.upbcientifica.mobile.data.filesync.EstadoSincronizacion
import com.upbcientifica.mobile.ui.components.EncabezadoSeccion
import com.upbcientifica.mobile.ui.components.formatearHace
import com.upbcientifica.mobile.ui.theme.exito
import java.util.Calendar

/** Un tile por cada servicio del cliente móvil; cada uno lleva a su pestaña. */
private data class Servicio(
    val nombre: String,
    val descripcion: String,
    val icono: ImageVector,
    val pestana: Pestana,
)

private val servicios = listOf(
    Servicio("Mis archivos", "Tu almacenamiento personal", Icons.Rounded.Folder, Pestana.ARCHIVOS),
    Servicio("Compartidos", "Lo que otros compartieron contigo", Icons.Outlined.FolderShared, Pestana.COMPARTIDOS),
    Servicio("Streaming", "Videos y material audiovisual", Icons.Outlined.PlayCircle, Pestana.STREAMING),
    Servicio("Cómputo HPC", "Lanza y sigue trabajos en el clúster", Icons.Outlined.Memory, Pestana.HPC),
)

private fun saludo(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Buenos días"
    in 12..18 -> "Buenas tardes"
    else -> "Buenas noches"
}

@Composable
fun HomeScreen(
    usuario: String,
    estadoSync: EstadoSincronizacion,
    onNavigate: (Pestana) -> Unit,
    accionesCuenta: @Composable () -> Unit
) {
    // Cada servicio tiene su par de colores del tema para diferenciarlos sin romper el modo oscuro.
    val colores = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
        MaterialTheme.colorScheme.inverseSurface to MaterialTheme.colorScheme.inverseOnSurface,
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "UPB-Científica",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                accionesCuenta()
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 16.dp)) {
                Text("${saludo()}, $usuario", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "¿Qué quieres hacer hoy?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) { FranjaSincronizacion(estadoSync) }

        item(span = { GridItemSpan(maxLineSpan) }) {
            EncabezadoSeccion("Servicios", Modifier.offset(x = (-16).dp))
        }
        items(servicios.size) { i ->
            val servicio = servicios[i]
            val (fondo, contenido) = colores[i % colores.size]
            TarjetaServicio(servicio, fondo, contenido, onClick = { onNavigate(servicio.pestana) })
        }
    }
}

/** Resumen en texto del mismo estado que muestra el icono de la barra superior. */
@Composable
private fun FranjaSincronizacion(estado: EstadoSincronizacion) {
    val (icono, color, titulo, detalle) = when (estado) {
        is EstadoSincronizacion.AlDia -> Cuarteto(
            Icons.Outlined.CloudDone, MaterialTheme.colorScheme.exito,
            "Todo sincronizado", "Última sincronización ${formatearHace(estado.ultimaSincronizacion)}"
        )
        is EstadoSincronizacion.Sincronizando -> Cuarteto(
            Icons.Outlined.CloudSync, MaterialTheme.colorScheme.primary,
            "Sincronizando…",
            if (estado.pendientes > 0) "Faltan ${estado.pendientes} cambios por subir" else "Buscando cambios"
        )
        EstadoSincronizacion.SinConexion -> Cuarteto(
            Icons.Outlined.CloudOff, MaterialTheme.colorScheme.error,
            "Sin conexión", "Los cambios se subirán cuando vuelva la red"
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Column {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icono, contentDescription = null, tint = color)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(titulo, style = MaterialTheme.typography.titleSmall)
                    Text(
                        detalle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (estado is EstadoSincronizacion.Sincronizando) {
                LinearProgressIndicator(
                    progress = { estado.progreso },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

private data class Cuarteto(val icono: ImageVector, val color: Color, val titulo: String, val detalle: String)

@Composable
private fun TarjetaServicio(servicio: Servicio, fondo: Color, contenido: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(148.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = fondo, contentColor = contenido)
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(contenido.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(servicio.icono, contentDescription = null)
            }
            Spacer(Modifier.weight(1f))
            Text(servicio.nombre, style = MaterialTheme.typography.titleMedium)
            Text(
                servicio.descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = contenido.copy(alpha = 0.8f),
                maxLines = 2
            )
        }
    }
}
