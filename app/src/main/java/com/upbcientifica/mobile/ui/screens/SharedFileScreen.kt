package com.upbcientifica.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.upbcientifica.mobile.data.sharedfile.NivelPermiso
import com.upbcientifica.mobile.data.sharedfile.RelacionCompartidoDto
import com.upbcientifica.mobile.data.sharedfile.TipoDestinatario
import com.upbcientifica.mobile.data.storage.ArchivoDto
import com.upbcientifica.mobile.ui.components.*
import com.upbcientifica.mobile.ui.state.UiState
import com.upbcientifica.mobile.ui.viewmodel.Carpeta
import com.upbcientifica.mobile.ui.viewmodel.SharedFileViewModel
import com.upbcientifica.mobile.ui.viewmodel.tipoVisible

private enum class SeccionCompartidos(val etiqueta: String) { CONMIGO("Conmigo"), QUE_COMPARTO("Que comparto") }

/** Un elemento que se puede abrir o descargar desde esta pestaña. */
private data class Elemento(val id: String, val nombre: String, val esCarpeta: Boolean)

private fun RelacionCompartidoDto.destinatarioLegible() =
    if (destinatarioTipo == TipoDestinatario.GRUPO) "grupo $destinatarioId" else destinatarioId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedFileScreen(viewModel: SharedFileViewModel, accionesCuenta: @Composable () -> Unit) {
    val conmigo by viewModel.state.collectAsState()
    val queComparto by viewModel.queComparto.collectAsState()
    val ruta by viewModel.ruta.collectAsState()
    val contenido by viewModel.contenido.collectAsState()
    val visor by viewModel.visor.collectAsState()

    var consulta by rememberSaveable { mutableStateOf("") }
    var seccion by rememberSaveable { mutableStateOf(SeccionCompartidos.CONMIGO) }
    var detalle by remember { mutableStateOf<RelacionCompartidoDto?>(null) }
    var archivoDetalle by remember { mutableStateOf<Elemento?>(null) }
    var porDescargar by remember { mutableStateOf<Elemento?>(null) }
    var porRevocar by remember { mutableStateOf<RelacionCompartidoDto?>(null) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.avisos.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.cargarSiHaceFalta() }

    val guardarComo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destino ->
        val e = porDescargar
        porDescargar = null
        if (destino != null && e != null) viewModel.descargar(e.id, e.nombre, destino)
    }

    // Atrás sale de la carpeta compartida antes de salir de la pestaña.
    BackHandler(enabled = ruta.isNotEmpty()) { viewModel.subirNivel() }

    /** Tocar: carpeta -> se abre aquí mismo; imagen/video -> visor; otro -> detalles con descargar. */
    val tocar: (Elemento) -> Unit = { e ->
        when {
            e.esCarpeta -> viewModel.abrirCarpeta(e.id, e.nombre)
            tipoVisible(e.nombre) != null -> viewModel.abrir(e.id, e.nombre)
            else -> archivoDetalle = e
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BarraBusqueda(
                consulta = consulta,
                onConsultaChange = { consulta = it },
                placeholder = "Buscar en Compartidos",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                finalContenido = { accionesCuenta() }
            )

            if (ruta.isNotEmpty()) {
                // Dentro de una carpeta compartida: migas de pan y su contenido.
                MigasCompartidos(ruta, onNivel = viewModel::irANivel)
                ContenidoCarpeta(contenido, consulta.trim(), onReintentar = viewModel::cargarCarpeta, onTocar = tocar,
                    onDetalles = { archivoDetalle = it })
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Compartidos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.cargar(); viewModel.cargarQueComparto() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Actualizar")
                    }
                }
                TabRow(selectedTabIndex = seccion.ordinal, containerColor = MaterialTheme.colorScheme.surface) {
                    SeccionCompartidos.entries.forEach { s ->
                        Tab(selected = seccion == s, onClick = { seccion = s }, text = { Text(s.etiqueta) })
                    }
                }
                ListaRelaciones(
                    estado = if (seccion == SeccionCompartidos.CONMIGO) conmigo else queComparto,
                    conmigo = seccion == SeccionCompartidos.CONMIGO,
                    q = consulta.trim(),
                    onReintentar = if (seccion == SeccionCompartidos.CONMIGO) viewModel::cargar else viewModel::cargarQueComparto,
                    onTocar = { r ->
                        if (seccion == SeccionCompartidos.CONMIGO) tocar(Elemento(r.elementoId, r.nombre, r.esDirectorio))
                        else detalle = r
                    },
                    onDetalles = { detalle = it }
                )
            }
        }
    }

    // Detalles de una relación (de la lista).
    detalle?.let { r ->
        val conmigoSel = seccion == SeccionCompartidos.CONMIGO
        var nivel by remember(r.compartidoId) { mutableStateOf(r.nivelPermiso) }
        HojaDetalles(
            nombre = r.nombre,
            tipo = tipoArchivo(r.nombre, r.esDirectorio),
            filas = buildList {
                add("Propietario" to r.propietario)
                if (conmigoSel) add("Compartido por" to r.compartidoPor) else add("Compartido con" to r.destinatarioLegible())
                add("Permiso" to r.nivelPermiso.etiqueta)
                r.fechaExpiracion?.let { add("Caduca" to it.take(10)) }
                if (!r.esDirectorio) add("Tamaño" to formatearTamano(r.tamanoBytes))
            },
            onCerrar = { detalle = null },
            acciones = {
                if (conmigoSel) {
                    Button(
                        onClick = { detalle = null; tocar(Elemento(r.elementoId, r.nombre, r.esDirectorio)) },
                        shape = CircleShape, modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.OpenInFull, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir")
                    }
                    if (!r.esDirectorio) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                detalle = null
                                porDescargar = Elemento(r.elementoId, r.nombre, false)
                                guardarComo.launch(r.nombre)
                            },
                            shape = CircleShape, modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Descargar al teléfono")
                        }
                    }
                } else {
                    // ActualizarPermisosCompartidos
                    Text("Cambiar permiso", style = MaterialTheme.typography.labelLarge)
                    NivelPermiso.entries.forEach { opcion ->
                        Row(Modifier.fillMaxWidth().clickable { nivel = opcion }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = nivel == opcion, onClick = { nivel = opcion })
                            Text(opcion.etiqueta)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { detalle = null; viewModel.cambiarNivel(r, nivel) },
                        enabled = nivel != r.nivelPermiso, shape = CircleShape, modifier = Modifier.fillMaxWidth()
                    ) { Text("Guardar permiso") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { detalle = null; porRevocar = r },
                        shape = CircleShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.PersonRemove, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Dejar de compartir")
                    }
                }
            }
        )
    }

    // Detalles de un archivo dentro de una carpeta compartida (o que no se puede previsualizar).
    archivoDetalle?.let { e ->
        HojaDetalles(
            nombre = e.nombre,
            tipo = tipoArchivo(e.nombre, false),
            filas = listOf("Identificador" to e.id),
            onCerrar = { archivoDetalle = null },
            acciones = {
                if (tipoVisible(e.nombre) != null) {
                    Button(
                        onClick = { archivoDetalle = null; viewModel.abrir(e.id, e.nombre) },
                        shape = CircleShape, modifier = Modifier.fillMaxWidth()
                    ) { Text("Abrir") }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = { archivoDetalle = null; porDescargar = e; guardarComo.launch(e.nombre) },
                    shape = CircleShape, modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Descargar al teléfono")
                }
            }
        )
    }

    porRevocar?.let { r ->
        AlertDialog(
            onDismissRequest = { porRevocar = null },
            title = { Text("¿Dejar de compartir?") },
            text = { Text("${r.destinatarioLegible()} perderá el acceso a \"${r.nombre}\" de inmediato.") },
            confirmButton = {
                TextButton(
                    onClick = { porRevocar = null; viewModel.revocar(r) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Dejar de compartir") }
            },
            dismissButton = { TextButton(onClick = { porRevocar = null }) { Text("Cancelar") } }
        )
    }

    visor?.let { VisorMedia(it, onCerrar = viewModel::cerrarVisor) }
}

@Composable
private fun ListaRelaciones(
    estado: UiState<List<RelacionCompartidoDto>>,
    conmigo: Boolean,
    q: String,
    onReintentar: () -> Unit,
    onTocar: (RelacionCompartidoDto) -> Unit,
    onDetalles: (RelacionCompartidoDto) -> Unit,
) {
    when (estado) {
        UiState.Idle, UiState.Loading -> EsqueletoLista()
        is UiState.Error -> EstadoError(estado.message, onReintentar = onReintentar)
        is UiState.Success -> {
            val visibles = estado.data.filter {
                it.nombre.contains(q, true) || it.propietario.contains(q, true) || it.destinatarioId.contains(q, true)
            }
            when {
                estado.data.isEmpty() -> EstadoVacio(
                    icono = if (conmigo) Icons.Outlined.FolderShared else Icons.Outlined.Share,
                    titulo = if (conmigo) "Nada compartido contigo" else "No compartes nada",
                    mensaje = if (conmigo) "Cuando alguien comparta un archivo o carpeta contigo, aparecerá aquí."
                              else "Comparte desde Mis archivos con el botón Compartir de cada archivo o carpeta."
                )
                visibles.isEmpty() -> EstadoVacio(
                    icono = Icons.Outlined.SearchOff,
                    titulo = "Sin resultados",
                    mensaje = "Nada coincide con \"$q\"."
                )
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(visibles, key = { it.compartidoId }) { r ->
                        ListItem(
                            modifier = Modifier.clickable { onTocar(r) },
                            headlineContent = { Text(r.nombre, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val persona = if (conmigo) r.propietario else r.destinatarioId
                                    Avatar(persona, tamano = 18.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (conmigo) persona else "con ${r.destinatarioLegible()}",
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            leadingContent = { IconoTipoArchivo(tipoArchivo(r.nombre, r.esDirectorio)) },
                            trailingContent = {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.clickable { onDetalles(r) }
                                ) {
                                    Text(
                                        r.nivelPermiso.etiqueta,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContenidoCarpeta(
    estado: UiState<List<ArchivoDto>>,
    q: String,
    onReintentar: () -> Unit,
    onTocar: (Elemento) -> Unit,
    onDetalles: (Elemento) -> Unit,
) {
    when (estado) {
        UiState.Idle, UiState.Loading -> EsqueletoLista()
        is UiState.Error -> EstadoError(estado.message, onReintentar = onReintentar)
        is UiState.Success -> {
            val visibles = estado.data.filter { it.nombre.contains(q, true) }
                .sortedWith(compareByDescending<ArchivoDto> { it.tipo == "directorio" }.thenBy { it.nombre.lowercase() })
            if (visibles.isEmpty()) {
                EstadoVacio(
                    icono = Icons.Outlined.FolderOpen,
                    titulo = if (estado.data.isEmpty()) "Esta carpeta está vacía" else "Sin resultados",
                    mensaje = if (estado.data.isEmpty()) "Todavía no hay nada aquí." else "Nada coincide con \"$q\"."
                )
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(visibles, key = { it.id }) { a ->
                        val esCarpeta = a.tipo == "directorio"
                        val e = Elemento(a.id, a.nombre, esCarpeta)
                        ListItem(
                            modifier = Modifier.clickable { onTocar(e) },
                            headlineContent = { Text(a.nombre, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(if (esCarpeta) "Carpeta" else formatearTamano(a.tamanoBytes)) },
                            leadingContent = { IconoTipoArchivo(tipoArchivo(a.nombre, esCarpeta)) },
                            trailingContent = if (esCarpeta) {
                                { Icon(Icons.Rounded.ChevronRight, contentDescription = null) }
                            } else {
                                { IconButton(onClick = { onDetalles(e) }) { Icon(Icons.Rounded.Download, contentDescription = "Descargar") } }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                    }
                }
            }
        }
    }
}

/** "Compartidos > Carpeta > Subcarpeta". El primer nivel vuelve a la lista. */
@Composable
private fun MigasCompartidos(ruta: List<Carpeta>, onNivel: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        item {
            TextButton(onClick = { onNivel(-1) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Compartidos", style = MaterialTheme.typography.titleMedium)
            }
        }
        itemsIndexed(ruta) { i, carpeta ->
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
            TextButton(
                onClick = { onNivel(i) },
                enabled = i != ruta.lastIndex,
                contentPadding = PaddingValues(horizontal = 8.dp),
                colors = ButtonDefaults.textButtonColors(disabledContentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Text(
                    carpeta.nombre,
                    style = if (i == ruta.lastIndex) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }
        }
    }
}
