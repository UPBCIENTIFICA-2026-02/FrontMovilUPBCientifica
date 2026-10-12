package com.upbcientifica.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.upbcientifica.mobile.data.sharedfile.NivelPermiso
import com.upbcientifica.mobile.data.sharedfile.TipoDestinatario
import com.upbcientifica.mobile.data.storage.ArchivoDto
import com.upbcientifica.mobile.ui.components.*
import com.upbcientifica.mobile.ui.state.UiState
import com.upbcientifica.mobile.ui.viewmodel.Carpeta
import com.upbcientifica.mobile.ui.viewmodel.StorageViewModel
import com.upbcientifica.mobile.ui.viewmodel.tipoVisible

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(viewModel: StorageViewModel, accionesCuenta: @Composable () -> Unit) {
    val state by viewModel.state.collectAsState()
    val ruta by viewModel.ruta.collectAsState()
    val operacion by viewModel.operacion.collectAsState()
    val visor by viewModel.visor.collectAsState()
    val cuota by viewModel.cuota.collectAsState()

    var consulta by rememberSaveable { mutableStateOf("") }
    var orden by rememberSaveable { mutableStateOf(Orden.NOMBRE_ASC) }
    var enCuadricula by rememberSaveable { mutableStateOf(false) }
    var detalle by remember { mutableStateOf<ArchivoDto?>(null) }
    var menuNuevo by remember { mutableStateOf(false) }
    var dialogoCarpeta by remember { mutableStateOf(false) }
    var porEliminar by remember { mutableStateOf<ArchivoDto?>(null) }
    var porDescargar by remember { mutableStateOf<ArchivoDto?>(null) }
    var porCompartir by remember { mutableStateOf<ArchivoDto?>(null) }
    // Archivo abierto en el visor, para su papelera.
    var abierto by remember { mutableStateOf<ArchivoDto?>(null) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.avisos.collect { snackbar.showSnackbar(it) } }

    // Selector de archivos del sistema: no pide permisos de almacenamiento.
    val elegirArchivos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.subir(uris)
    }
    // "Guardar como" del sistema: el usuario elige dónde queda la copia.
    val guardarComo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destino ->
        val archivo = porDescargar
        porDescargar = null
        if (destino != null && archivo != null) viewModel.descargar(archivo, destino)
    }

    LaunchedEffect(Unit) { viewModel.cargarSiHaceFalta() }

    // Atrás sube de carpeta antes de salir de la pestaña, como en Drive.
    BackHandler(enabled = ruta.size > 1) { viewModel.subirNivel() }

    // Al cambiar de carpeta, la búsqueda anterior ya no tiene sentido.
    LaunchedEffect(ruta.size) { consulta = "" }

    /** Tocar un archivo: imágenes y videos se abren en el visor; lo demás, en detalles. */
    val alTocarArchivo: (ArchivoDto) -> Unit = { archivo ->
        if (tipoVisible(archivo.nombre) != null) {
            abierto = archivo
            viewModel.abrir(archivo)
        } else detalle = archivo
    }

    Scaffold(
        // Los insets ya los aplica el Scaffold de MainScreen.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { menuNuevo = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Nuevo") },
                expanded = operacion == null
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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
                IconButton(onClick = { elegirArchivos.launch(arrayOf("*/*")) }, enabled = operacion == null) {
                    Icon(Icons.Rounded.UploadFile, contentDescription = "Subir archivos")
                }
                IconButton(onClick = { dialogoCarpeta = true }, enabled = operacion == null) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = "Nueva carpeta")
                }
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

            // Subidas, descargas... : barra indeterminada con el texto de lo que pasa.
            operacion?.let { texto ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        texto,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }

            cuota?.takeIf { it.cuota > 0 }?.let { c ->
                Text(
                    "${formatearTamano(c.usados)} usados de ${formatearTamano(c.cuota)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
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
                            mensaje = "Toca \"Nuevo\" para subir archivos o crear una carpeta."
                        )
                        visibles.isEmpty() -> EstadoVacio(
                            icono = Icons.Outlined.SearchOff,
                            titulo = "Sin resultados",
                            mensaje = "Ningún elemento coincide con \"${consulta.trim()}\"."
                        )
                        enCuadricula -> CuadriculaArchivos(
                            carpetas, archivos,
                            onCarpeta = viewModel::abrirCarpeta,
                            onArchivo = alTocarArchivo,
                            onDetalles = { detalle = it }
                        )
                        else -> ListaArchivos(
                            carpetas, archivos,
                            onCarpeta = viewModel::abrirCarpeta,
                            onArchivo = alTocarArchivo,
                            onDetalles = { detalle = it }
                        )
                    }
                }
            }
        }
    }

    if (menuNuevo) {
        ModalBottomSheet(onDismissRequest = { menuNuevo = false }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Nuevo en ${ruta.last().nombre}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                ListItem(
                    headlineContent = { Text("Subir archivos") },
                    supportingContent = { Text("Elige uno o varios archivos del teléfono") },
                    leadingContent = { Icon(Icons.Rounded.UploadFile, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = {
                        menuNuevo = false
                        elegirArchivos.launch(arrayOf("*/*"))
                    })
                )
                ListItem(
                    headlineContent = { Text("Nueva carpeta") },
                    leadingContent = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = {
                        menuNuevo = false
                        dialogoCarpeta = true
                    })
                )
            }
        }
    }

    if (dialogoCarpeta) {
        DialogoNuevaCarpeta(
            onCrear = { nombre ->
                dialogoCarpeta = false
                viewModel.crearCarpeta(nombre)
            },
            onCancelar = { dialogoCarpeta = false }
        )
    }

    porEliminar?.let { archivo ->
        AlertDialog(
            onDismissRequest = { porEliminar = null },
            title = { Text("¿Eliminar \"${archivo.nombre}\"?") },
            text = { Text("Se borrará de Mis archivos. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        porEliminar = null
                        if (abierto?.id == archivo.id) {
                            abierto = null
                            viewModel.cerrarVisor()
                        }
                        viewModel.eliminar(archivo)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { porEliminar = null }) { Text("Cancelar") } }
        )
    }

    detalle?.let { archivo ->
        HojaDetalles(
            nombre = archivo.nombre,
            tipo = tipoArchivo(archivo.nombre, archivo.esCarpeta),
            filas = buildList {
                if (!archivo.esCarpeta) {
                    add("Tamaño" to formatearTamano(archivo.tamanoBytes))
                    add("Versión" to "v${archivo.version}")
                }
                add("Ubicación" to ruta.joinToString(" / ") { it.nombre })
                add("Identificador" to archivo.id)
            },
            onCerrar = { detalle = null },
            acciones = {
                if (archivo.esCarpeta) {
                    Button(
                        onClick = { detalle = null; viewModel.abrirCarpeta(archivo) },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Abrir carpeta") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { detalle = null; porCompartir = archivo },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Compartir carpeta")
                    }
                    return@HojaDetalles
                }
                if (tipoVisible(archivo.nombre) != null) {
                    Button(
                        onClick = { detalle = null; abierto = archivo; viewModel.abrir(archivo) },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.OpenInFull, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = { detalle = null; porCompartir = archivo },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Compartir")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        detalle = null
                        porDescargar = archivo
                        guardarComo.launch(archivo.nombre)
                    },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Descargar al teléfono")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { detalle = null; porEliminar = archivo },
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Eliminar")
                }
            }
        )
    }

    porCompartir?.let { archivo ->
        DialogoCompartir(
            archivo = archivo,
            onCompartir = { tipo, destinatario, nivel ->
                porCompartir = null
                viewModel.compartir(archivo, tipo, destinatario, nivel)
            },
            onCancelar = { porCompartir = null }
        )
    }

    visor?.let {
        VisorMedia(
            it,
            onCerrar = { abierto = null; viewModel.cerrarVisor() },
            onEliminar = abierto?.let { archivo -> { porEliminar = archivo } }
        )
    }
}

/** CompartirArchivo (Contrato_SharedFile): a quién (usuario o grupo) y con qué nivel. */
@Composable
private fun DialogoCompartir(
    archivo: ArchivoDto,
    onCompartir: (TipoDestinatario, String, NivelPermiso) -> Unit,
    onCancelar: () -> Unit
) {
    var tipo by rememberSaveable { mutableStateOf(TipoDestinatario.USUARIO) }
    var destinatario by rememberSaveable { mutableStateOf("") }
    var nivel by rememberSaveable { mutableStateOf(NivelPermiso.LECTURA) }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (archivo.esCarpeta) "Compartir la carpeta \"${archivo.nombre}\"" else "Compartir \"${archivo.nombre}\"") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tipo == TipoDestinatario.USUARIO,
                        onClick = { tipo = TipoDestinatario.USUARIO },
                        label = { Text("Usuario") }
                    )
                    FilterChip(
                        selected = tipo == TipoDestinatario.GRUPO,
                        onClick = { tipo = TipoDestinatario.GRUPO },
                        label = { Text("Grupo") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = destinatario,
                    onValueChange = { destinatario = it },
                    label = { Text(if (tipo == TipoDestinatario.USUARIO) "Usuario (p. ej. investigador)" else "Grupo (p. ej. investigadores)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Permiso", style = MaterialTheme.typography.labelLarge)
                NivelPermiso.entries.forEach { opcion ->
                    Row(
                        Modifier.fillMaxWidth().clickable { nivel = opcion },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = nivel == opcion, onClick = { nivel = opcion })
                        Text(opcion.etiqueta)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCompartir(tipo, destinatario, nivel) }, enabled = destinatario.isNotBlank()) {
                Text("Compartir")
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

@Composable
private fun DialogoNuevaCarpeta(onCrear: (String) -> Unit, onCancelar: () -> Unit) {
    var nombre by rememberSaveable { mutableStateOf("Carpeta sin título") }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Nueva carpeta") },
        text = {
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (nombre.isNotBlank()) onCrear(nombre) })
            )
        },
        confirmButton = { TextButton(onClick = { onCrear(nombre) }, enabled = nombre.isNotBlank()) { Text("Crear") } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
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
    onArchivo: (ArchivoDto) -> Unit,
    onDetalles: (ArchivoDto) -> Unit
) {
    // Espacio al final para que el botón "Nuevo" no tape la última fila.
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        if (carpetas.isNotEmpty()) {
            item(key = "h-carpetas") { EncabezadoSeccion("Carpetas") }
            items(carpetas, key = { it.id }) { FilaArchivo(it, onClick = { onCarpeta(it) }, onDetalles = { onDetalles(it) }) }
        }
        if (archivos.isNotEmpty()) {
            item(key = "h-archivos") { EncabezadoSeccion("Archivos") }
            items(archivos, key = { it.id }) {
                FilaArchivo(it, onClick = { onArchivo(it) }, onDetalles = { onDetalles(it) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilaArchivo(archivo: ArchivoDto, onClick: () -> Unit, onDetalles: (() -> Unit)?) {
    val tipo = tipoArchivo(archivo.nombre, archivo.esCarpeta)
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onDetalles),
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
        trailingContent = when {
            onDetalles != null -> { { IconButton(onClick = onDetalles) { Icon(Icons.Rounded.MoreVert, contentDescription = "Más opciones") } } }
            archivo.esCarpeta -> { { Icon(Icons.Rounded.ChevronRight, contentDescription = null) } }
            else -> null
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CuadriculaArchivos(
    carpetas: List<ArchivoDto>,
    archivos: List<ArchivoDto>,
    onCarpeta: (ArchivoDto) -> Unit,
    onArchivo: (ArchivoDto) -> Unit,
    onDetalles: (ArchivoDto) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (carpetas.isNotEmpty()) {
            item(key = "h-carpetas", span = { GridItemSpan(maxLineSpan) }) {
                EncabezadoSeccion("Carpetas", Modifier.offset(x = (-16).dp))
            }
            items(carpetas, key = { it.id }) { carpeta ->
                // Las carpetas van compactas, como en Drive: solo icono y nombre.
                OutlinedCard(
                    Modifier.combinedClickable(onClick = { onCarpeta(carpeta) }, onLongClick = { onDetalles(carpeta) })
                ) {
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
                // Tocar abre; mantener pulsado muestra detalles y acciones.
                OutlinedCard(
                    Modifier.combinedClickable(onClick = { onArchivo(archivo) }, onLongClick = { onDetalles(archivo) })
                ) {
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
                    Row(
                        Modifier.padding(start = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
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
                        IconButton(onClick = { onDetalles(archivo) }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Más opciones")
                        }
                    }
                }
            }
        }
    }
}
