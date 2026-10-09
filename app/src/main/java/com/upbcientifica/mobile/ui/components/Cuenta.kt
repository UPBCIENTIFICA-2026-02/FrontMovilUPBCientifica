package com.upbcientifica.mobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Avatar del usuario que abre un menú con su nombre y "Cerrar sesión". */
@Composable
fun MenuCuenta(usuario: String, onCerrarSesion: () -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { abierto = true }) {
            Avatar(usuario, tamano = 32.dp)
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp).widthIn(min = 200.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(usuario, tamano = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        usuario,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Sesión iniciada",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Cerrar sesión") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
                onClick = {
                    abierto = false
                    confirmar = true
                }
            )
        }
    }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            icon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
            title = { Text("¿Cerrar sesión?") },
            text = { Text("Tendrás que volver a ingresar tu usuario y contraseña.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmar = false
                    onCerrarSesion()
                }) { Text("Cerrar sesión") }
            },
            dismissButton = {
                TextButton(onClick = { confirmar = false }) { Text("Cancelar") }
            }
        )
    }
}

/**
 * Hoja inferior con la ficha de un elemento (nombre, tipo y pares
 * etiqueta/valor), como el panel "Detalles" de Drive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaDetalles(
    nombre: String,
    tipo: TipoArchivo,
    filas: List<Pair<String, String>>,
    onCerrar: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onCerrar) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconoTipoArchivo(tipo, tamano = 48.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(nombre, style = MaterialTheme.typography.titleMedium)
                    Text(
                        tipo.etiqueta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(
                "Detalles",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            filas.forEach { (etiqueta, valor) ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        etiqueta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(valor, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
