package com.upbcientifica.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.upbcientifica.mobile.data.sharedfile.RecursoCompartidoDto
import com.upbcientifica.mobile.ui.viewmodel.SharedFileViewModel
import com.upbcientifica.mobile.ui.components.*
import com.upbcientifica.mobile.ui.state.UiState

/** "rw-" -> "Puede editar". Se traduce el permiso Unix a algo que entienda cualquiera. */
private fun describirPermiso(permisoUnix: String): String = when {
    'w' in permisoUnix -> "Puede editar"
    'r' in permisoUnix -> "Solo lectura"
    'x' in permisoUnix -> "Solo ejecución"
    else -> "Sin acceso"
}

@Composable
fun SharedFileScreen(viewModel: SharedFileViewModel, accionesCuenta: @Composable () -> Unit) {
    val state by viewModel.state.collectAsState()
    var consulta by rememberSaveable { mutableStateOf("") }
    var detalle by remember { mutableStateOf<RecursoCompartidoDto?>(null) }

    LaunchedEffect(Unit) { viewModel.cargarSiHaceFalta() }

    Column(Modifier.fillMaxSize()) {
        BarraBusqueda(
            consulta = consulta,
            onConsultaChange = { consulta = it },
            placeholder = "Buscar en Compartidos",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            finalContenido = { accionesCuenta() }
        )

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Compartidos conmigo",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = viewModel::cargar) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Actualizar")
            }
        }

        when (val s = state) {
            UiState.Idle, UiState.Loading -> EsqueletoLista()
            is UiState.Error -> EstadoError(s.message, onReintentar = viewModel::cargar)
            is UiState.Success -> {
                val q = consulta.trim()
                val visibles = s.data.filter {
                    it.nombreArchivo.contains(q, ignoreCase = true) ||
                        it.propietario.contains(q, ignoreCase = true)
                }
                when {
                    s.data.isEmpty() -> EstadoVacio(
                        icono = Icons.Outlined.FolderShared,
                        titulo = "Nada compartido todavía",
                        mensaje = "Cuando alguien comparta un archivo o carpeta contigo, aparecerá aquí."
                    )
                    visibles.isEmpty() -> EstadoVacio(
                        icono = Icons.Outlined.SearchOff,
                        titulo = "Sin resultados",
                        mensaje = "Nada coincide con \"$q\" en el nombre ni en el propietario."
                    )
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(visibles, key = { it.compartidoId }) { recurso ->
                            FilaCompartido(recurso, onClick = { detalle = recurso })
                        }
                    }
                }
            }
        }
    }

    detalle?.let { recurso ->
        HojaDetalles(
            nombre = recurso.nombreArchivo,
            tipo = tipoArchivo(recurso.nombreArchivo, esCarpeta = false),
            filas = listOf(
                "Propietario" to recurso.propietario,
                "Tu acceso" to "${describirPermiso(recurso.permisoUnix)} (${recurso.permisoUnix})",
                "Identificador" to recurso.archivoId,
            ),
            onCerrar = { detalle = null }
        )
    }
}

@Composable
private fun FilaCompartido(recurso: RecursoCompartidoDto, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(recurso.nombreArchivo, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(recurso.propietario, tamano = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(recurso.propietario, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = { IconoTipoArchivo(tipoArchivo(recurso.nombreArchivo, esCarpeta = false)) },
        trailingContent = {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    describirPermiso(recurso.permisoUnix),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}
