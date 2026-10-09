package com.upbcientifica.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.upbcientifica.mobile.data.storage.ArchivoDto
import com.upbcientifica.mobile.ui.components.*
import com.upbcientifica.mobile.ui.state.UiState
import com.upbcientifica.mobile.ui.viewmodel.Carpeta
import com.upbcientifica.mobile.ui.viewmodel.StorageViewModel

private enum class Orden(val etiqueta: String) {
    NOMBRE_ASC("Nombre (A-Z)"),
    NOMBRE_DESC("Nombre (Z-A)"),
    TAMANO_DESC("Tamaño (mayor primero)"),
    TAMANO_ASC("Tamaño (menor primero)"),
}

private val ArchivoDto.esCarpeta get() = tipo == "directorio"

private fun ordenar(lista: List<ArchivoDto>, orden: Orden): List<ArchivoDto> {
    val porNombre = compareBy<ArchivoDto, String>(String.CASE_INSENSITIVE_ORDER) { it.nombre }
    val comparador = when (orden) {
        Orden.NOMBRE_ASC -> porNombre
        Orden.NOMBRE_DESC -> porNombre.reversed()
        Orden.TAMANO_DESC -> compareByDescending<ArchivoDto> { it.tamanoBytes }.then(porNombre)
        Orden.TAMANO_ASC -> compareBy<ArchivoDto> { it.tamanoBytes }.then(porNombre)
    }
    return lista.sortedWith(comparador)
}

@Composable
fun StorageScreen(viewModel: StorageViewModel, accionesCuenta: @Composable () -> Unit) {
    val state by viewModel.state.collectAsState()
    val ruta by viewModel.ruta.collectAsState()

    var consulta by rememberSaveable { mutableStateOf("") }
    var orden by rememberSaveable { mutableStateOf(Orden.NOMBRE_ASC) }
    var enCuadricula by rememberSaveable { mutableStateOf(false) }
    var detalle by remember { mutableStateOf<ArchivoDto?>(null) }

    LaunchedEffect(Unit) { viewModel.cargarSiHaceFalta() }

    // Atrás sube de carpeta antes de salir de la pestaña, como en Drive.
    BackHandler(enabled = ruta.size > 1) { viewModel.subirNivel() }

    // Al cambiar de carpeta, la búsqueda anterior ya no tiene sentido.
    LaunchedEffect(ruta.size) { consulta = "" }

    Column(Modifier.fillMaxSize()) {
        BarraBusqueda(
            consulta = consulta,
            onConsultaChange = { consulta = it },
            placeholder = "Buscar en ${ruta.last().nombre}",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            finalContenido = { accionesCuenta() }
        )

        MigasDePan(ruta, onNivel = viewModel::irANivel)

        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectorOrden(orden, onOrden = { orden = it })
            Spacer(Modifier.weight(1f))
            IconButton(onClick = viewModel::recargar) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Actualizar")
            }
            IconButton(onClick = { enCuadricula = !enCuadricula }) {
                Icon(
                    if (enCuadricula) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                    contentDescription = if (enCuadricula) "Ver como lista" else "Ver como cuadrícula"
                )
            }
        }

        when (val s = state) {
            UiState.Idle, UiState.Loading -> EsqueletoLista()
            is UiState.Error -> EstadoError(s.message, onReintentar = viewModel::recargar)
            is UiState.Success -> {
                val visibles = ordenar(
                    s.data.filter { it.nombre.contains(consulta.trim(), ignoreCase = true) },
                    orden
                )
                val carpetas = visibles.filter { it.esCarpeta }
                val archivos = visibles.filterNot { it.esCarpeta }

                when {
                    s.data.isEmpty() -> EstadoVacio(
                        icono = Icons.Outlined.FolderOpen,
                        titulo = "Esta carpeta está vacía",
                        mensaje = "Los archivos que subas a esta carpeta aparecerán aquí."
                    )
                    visibles.isEmpty() -> EstadoVacio(
                        icono = Icons.Outlined.SearchOff,
                        titulo = "Sin resultados",
                        mensaje = "Ningún elemento coincide con \"${consulta.trim()}\"."
                    )
                    enCuadricula -> CuadriculaArchivos(
                        carpetas, archivos,
                        onCarpeta = viewModel::abrirCarpeta,
                        onArchivo = { detalle = it }
                    )
                    else -> ListaArchivos(
                        carpetas, archivos,
                        onCarpeta = viewModel::abrirCarpeta,
                        onArchivo = { detalle = it }
                    )
                }
            }
        }
    }

    detalle?.let { archivo ->
        HojaDetalles(
            nombre = archivo.nombre,
            tipo = tipoArchivo(archivo.nombre, archivo.esCarpeta),
            filas = listOf(
                "Tamaño" to formatearTamano(archivo.tamanoBytes),
                "Versión" to "v${archivo.version}",
                "Ubicación" to ruta.joinToString(" / ") { it.nombre },
                "Identificador" to archivo.id,
            ),
            onCerrar = { detalle = null }
        )
    }
}

@Composable
private fun MigasDePan(ruta: List<Carpeta>, onNivel: (Int) -> Unit) {
    val estado = rememberLazyListState()
    // Mantiene visible la carpeta actual cuando la ruta es más ancha que la pantalla.
    LaunchedEffect(ruta.size) { estado.animateScrollToItem(ruta.lastIndex) }

    LazyRow(
        state = estado,
        contentPadding = PaddingValues(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(ruta) { i, carpeta ->
            val actual = i == ruta.lastIndex
            if (i > 0) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            TextButton(
                onClick = { onNivel(i) },
                enabled = !actual,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    carpeta.nombre,
                    style = if (actual) MaterialTheme.typography.titleLarge
                            else MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SelectorOrden(orden: Orden, onOrden: (Orden) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { abierto = true }) {
            Text(
                if (orden == Orden.NOMBRE_ASC || orden == Orden.NOMBRE_DESC) "Nombre" else "Tamaño",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                if (orden == Orden.NOMBRE_ASC || orden == Orden.TAMANO_ASC) Icons.Rounded.ArrowUpward
                else Icons.Rounded.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            Text(
                "Ordenar por",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Orden.entries.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(opcion.etiqueta) },
                    trailingIcon = {
                        if (opcion == orden) Icon(Icons.Rounded.Check, contentDescription = "Seleccionado")
                    },
                    onClick = {
                        onOrden(opcion)
                        abierto = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ListaArchivos(
    carpetas: List<ArchivoDto>,
    archivos: List<ArchivoDto>,
    onCarpeta: (ArchivoDto) -> Unit,
    onArchivo: (ArchivoDto) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (carpetas.isNotEmpty()) {
            item(key = "h-carpetas") { EncabezadoSeccion("Carpetas") }
            items(carpetas, key = { it.id }) { FilaArchivo(it, onClick = { onCarpeta(it) }) }
        }
        if (archivos.isNotEmpty()) {
            item(key = "h-archivos") { EncabezadoSeccion("Archivos") }
            items(archivos, key = { it.id }) { FilaArchivo(it, onClick = { onArchivo(it) }) }
        }
    }
}

@Composable
private fun FilaArchivo(archivo: ArchivoDto, onClick: () -> Unit) {
    val tipo = tipoArchivo(archivo.nombre, archivo.esCarpeta)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(archivo.nombre, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                if (archivo.esCarpeta) "Carpeta"
                else "${formatearTamano(archivo.tamanoBytes)} · v${archivo.version}"
            )
        },
        leadingContent = { IconoTipoArchivo(tipo) },
        trailingContent = if (archivo.esCarpeta) {
            { Icon(Icons.Rounded.ChevronRight, contentDescription = null) }
        } else null,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
private fun CuadriculaArchivos(
    carpetas: List<ArchivoDto>,
    archivos: List<ArchivoDto>,
    onCarpeta: (ArchivoDto) -> Unit,
    onArchivo: (ArchivoDto) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (carpetas.isNotEmpty()) {
            item(key = "h-carpetas", span = { GridItemSpan(maxLineSpan) }) {
                EncabezadoSeccion("Carpetas", Modifier.offset(x = (-16).dp))
            }
            items(carpetas, key = { it.id }) { carpeta ->
                // Las carpetas van compactas, como en Drive: solo icono y nombre.
                OutlinedCard(onClick = { onCarpeta(carpeta) }) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconoTipoArchivo(tipoArchivo(carpeta.nombre, true), tamano = 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            carpeta.nombre,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        if (archivos.isNotEmpty()) {
            item(key = "h-archivos", span = { GridItemSpan(maxLineSpan) }) {
                EncabezadoSeccion("Archivos", Modifier.offset(x = (-16).dp))
            }
            items(archivos, key = { it.id }) { archivo ->
                val tipo = tipoArchivo(archivo.nombre, false)
                OutlinedCard(onClick = { onArchivo(archivo) }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.4f)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = tipo.color.copy(alpha = 0.10f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxSize()
                        ) {}
                        Icon(
                            tipo.icono,
                            contentDescription = tipo.etiqueta,
                            tint = tipo.color,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                        Text(
                            archivo.nombre,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            formatearTamano(archivo.tamanoBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
