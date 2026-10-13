package com.upbcientifica.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.upbcientifica.mobile.data.hpc.EstadoCluster
import com.upbcientifica.mobile.data.hpc.EstadoTrabajo
import com.upbcientifica.mobile.data.hpc.SolicitudTrabajo
import com.upbcientifica.mobile.data.hpc.TrabajoHpc
import com.upbcientifica.mobile.ui.components.*
import com.upbcientifica.mobile.ui.state.UiState
import com.upbcientifica.mobile.ui.theme.exito
import com.upbcientifica.mobile.ui.viewmodel.HpcViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Cada cuánto se vuelve a preguntar por el estado mientras la pestaña está abierta. */
private const val INTERVALO_CONSULTA_MS = 3_000L

private enum class Filtro(val etiqueta: String) { TODOS("Todos"), ACTIVOS("Activos"), FINALIZADOS("Finalizados") }

private val PRIORIDADES = listOf("BAJA" to "Baja", "NORMAL" to "Normal", "ALTA" to "Alta")

private val TrabajoHpc.activo
    get() = estado == EstadoTrabajo.PENDIENTE || estado == EstadoTrabajo.EN_COLA ||
        estado == EstadoTrabajo.EJECUTANDO || estado == EstadoTrabajo.GUARDANDO

private val TrabajoHpc.reintentable get() = estado == EstadoTrabajo.FALLIDO || estado == EstadoTrabajo.FALLIDO_TIMEOUT

private val TrabajoHpc.conLogs get() = estado != EstadoTrabajo.PENDIENTE && estado != EstadoTrabajo.EN_COLA

/** job-api usa UUID: en pantalla basta con el principio, como un hash corto de git. */
private val TrabajoHpc.idCorto get() = id.take(8)

/** Etiqueta, icono y color de cada estado; un solo sitio para que lista y detalle coincidan. */
private data class AspectoEstado(val etiqueta: String, val icono: ImageVector, val color: Color)

@Composable
private fun aspecto(estado: EstadoTrabajo): AspectoEstado = when (estado) {
    EstadoTrabajo.PENDIENTE -> AspectoEstado("Pendiente", Icons.Rounded.HourglassTop, MaterialTheme.colorScheme.tertiary)
    EstadoTrabajo.EN_COLA -> AspectoEstado("En cola", Icons.Rounded.HourglassTop, MaterialTheme.colorScheme.tertiary)
    EstadoTrabajo.EJECUTANDO -> AspectoEstado("Ejecutando", Icons.Rounded.Sync, MaterialTheme.colorScheme.primary)
    EstadoTrabajo.GUARDANDO -> AspectoEstado("Guardando resultado", Icons.Rounded.CloudUpload, MaterialTheme.colorScheme.primary)
    EstadoTrabajo.COMPLETADO -> AspectoEstado("Completado", Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.exito)
    EstadoTrabajo.FALLIDO -> AspectoEstado("Fallido", Icons.Rounded.Error, MaterialTheme.colorScheme.error)
    EstadoTrabajo.FALLIDO_TIMEOUT -> AspectoEstado("Tiempo agotado", Icons.Rounded.TimerOff, MaterialTheme.colorScheme.error)
    EstadoTrabajo.CANCELADO -> AspectoEstado("Cancelado", Icons.Rounded.Block, MaterialTheme.colorScheme.outline)
}

@Composable
fun HpcScreen(viewModel: HpcViewModel, accionesCuenta: @Composable () -> Unit) {
    val state by viewModel.state.collectAsState()
    val enviando by viewModel.enviando.collectAsState()

    var consulta by rememberSaveable { mutableStateOf("") }
    var filtro by rememberSaveable { mutableStateOf(Filtro.TODOS) }
    var formularioAbierto by rememberSaveable { mutableStateOf(false) }
    var detalle by remember { mutableStateOf<TrabajoHpc?>(null) }
    var logs by remember { mutableStateOf<Pair<String, String?>?>(null) }  // (nombre, texto; null = cargando)

    val snackbar = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()
    fun avisar(texto: String) = alcance.launch { snackbar.showSnackbar(texto) }

    // Consulta periódica (ConsultarEstadoTrabajo / ObtenerEstadoCluster) solo
    // mientras la pestaña está en pantalla; se cancela sola al salir.
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.actualizar()
            delay(INTERVALO_CONSULTA_MS)
        }
    }

    Scaffold(
        // Los insets ya los aplica el Scaffold de MainScreen.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { formularioAbierto = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Nuevo trabajo") }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BarraBusqueda(
                consulta = consulta,
                onConsultaChange = { consulta = it },
                placeholder = "Buscar trabajos",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                finalContenido = { accionesCuenta() }
            )

            when (val s = state) {
                UiState.Idle, UiState.Loading -> EsqueletoLista()
                is UiState.Error -> EstadoError(s.message, onReintentar = viewModel::actualizar)
                is UiState.Success -> {
                    val q = consulta.trim()
                    val pasa = { t: TrabajoHpc, f: Filtro ->
                        when (f) {
                            Filtro.TODOS -> true
                            Filtro.ACTIVOS -> t.activo
                            Filtro.FINALIZADOS -> !t.activo
                        }
                    }
                    val visibles = s.data.trabajos
                        .filter { pasa(it, filtro) }
                        .filter {
                            it.nombre.contains(q, ignoreCase = true) || it.id.contains(q) ||
                                it.rutaCodigo.contains(q, ignoreCase = true)
                        }

                    LazyColumn(
                        Modifier.fillMaxSize(),
                        // Espacio al final para que el FAB no tape la última tarjeta.
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "cluster") { TarjetaCluster(s.data.cluster) }

                        item(key = "filtros") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(Filtro.entries) { opcion ->
                                    FilterChip(
                                        selected = filtro == opcion,
                                        onClick = { filtro = opcion },
                                        label = { Text("${opcion.etiqueta} · ${s.data.trabajos.count { pasa(it, opcion) }}") }
                                    )
                                }
                            }
                        }

                        if (visibles.isEmpty()) {
                            item(key = "vacio") {
                                EstadoVacio(
                                    icono = if (q.isEmpty()) Icons.Outlined.Dns else Icons.Outlined.SearchOff,
                                    titulo = if (q.isEmpty()) "No hay trabajos aquí" else "Sin resultados",
                                    mensaje = if (q.isEmpty()) "Envía tu código y tus datos al clúster con \"Nuevo trabajo\"."
                                              else "Ningún trabajo coincide con \"$q\".",
                                    modifier = Modifier.height(320.dp)
                                )
                            }
                        } else {
                            items(visibles, key = { it.id }) { trabajo ->
                                TarjetaTrabajo(trabajo, onClick = { detalle = trabajo })
                            }
                        }
                    }
                }
            }
        }
    }

    if (formularioAbierto) {
        HojaNuevoTrabajo(
            enviando = enviando,
            onCerrar = { formularioAbierto = false },
            onEnviar = { solicitud ->
                viewModel.enviarTrabajo(solicitud) { resultado ->
                    resultado.fold(
                        onSuccess = {
                            formularioAbierto = false
                            avisar("Trabajo #${it.idCorto} enviado al clúster")
                        },
                        // El formulario se queda abierto para corregir (p. ej. HPC-001: el código no existe).
                        onFailure = { avisar("No se pudo enviar: ${it.message}") }
                    )
                }
            }
        )
    }

    detalle?.let { elegido ->
        // Se busca la versión más reciente, para que el estado siga avanzando con la hoja abierta.
        val trabajo = (state as? UiState.Success)?.data?.trabajos?.find { it.id == elegido.id } ?: elegido
        val a = aspecto(trabajo.estado)
        HojaDetalles(
            nombre = trabajo.nombre,
            tipo = TipoArchivo(a.etiqueta, a.icono, a.color),
            filas = buildList {
                add("Estado" to if (trabajo.estado == EstadoTrabajo.EN_COLA && trabajo.posicionCola > 0)
                    "${a.etiqueta} · posición ${trabajo.posicionCola}" else a.etiqueta)
                add("Código" to trabajo.rutaCodigo)
                add("Dataset" to (trabajo.rutaDataset ?: "—"))
                add("Recursos" to "${trabajo.procesos} procesos MPI · máximo ${formatearMinutos((trabajo.tiempoMaximoS / 60).coerceAtLeast(1))}" +
                    " · prioridad ${trabajo.prioridad.lowercase()}")
                if (trabajo.nodos.isNotEmpty()) add("Nodos" to trabajo.nodos.joinToString(", "))
                trabajo.rutaResultado?.let { add("Resultado en tu Home" to it + if (trabajo.incompleto) " (incompleto)" else "") }
                trabajo.mensajeError?.takeIf { it.isNotBlank() }?.let {
                    add((if (trabajo.estado == EstadoTrabajo.COMPLETADO) "Nota" else "Detalle") to it)
                }
                add("Enviado" to formatearHace(trabajo.enviadoEn))
                add("Identificador" to trabajo.id)
            },
            onCerrar = { detalle = null },
            acciones = if (!trabajo.conLogs && !trabajo.activo) null else {
                {
                    if (trabajo.conLogs) {
                        OutlinedButton(
                            onClick = {
                                logs = trabajo.nombre to null
                                viewModel.logs(trabajo.id) { r ->
                                    logs = trabajo.nombre to r.fold({ it.ifBlank { "(sin salida)" } }, { "No se pudieron leer: ${it.message}" })
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Ver registros") }
                    }
                    if (trabajo.reintentable) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.reintentarTrabajo(trabajo.id) { r ->
                                    detalle = null
                                    avisar(r.fold({ "Reintento #${it.first.take(8)} enviado. ${it.second.orEmpty()}" },
                                        { "No se pudo reintentar: ${it.message}" }))
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Reintentar") }
                    }
                    if (trabajo.activo) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.cancelarTrabajo(trabajo.id) { r ->
                                    detalle = null
                                    avisar(r.fold({ "Trabajo #${trabajo.idCorto} cancelado" }, { "No se pudo cancelar: ${it.message}" }))
                                }
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cancelar trabajo") }
                    }
                }
            }
        )
    }

    logs?.let { (nombre, texto) ->
        AlertDialog(
            onDismissRequest = { logs = null },
            confirmButton = { TextButton(onClick = { logs = null }) { Text("Cerrar") } },
            title = { Text("Registros · $nombre", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                if (texto == null) {
                    CircularProgressIndicator()
                } else {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        Text(texto, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                }
            }
        )
    }
}

@Composable
private fun TarjetaCluster(cluster: EstadoCluster?) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Clúster UPB · malla Grid", style = MaterialTheme.typography.titleMedium)
                    Text(
                        cluster?.let { "${it.procesosEnUso} de ${it.procesosTotales} procesos en uso · ${it.nodosTotales} nodos" }
                            ?: "No se pudo consultar el estado del clúster",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (cluster != null) {
                    val (texto, color) = when {
                        !cluster.ejecutorConectado -> "Sin job-runner" to MaterialTheme.colorScheme.error
                        cluster.nodosEnFalla > 0 -> "${cluster.nodosEnFalla} en falla" to MaterialTheme.colorScheme.tertiary
                        else -> "En línea" to MaterialTheme.colorScheme.exito
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(6.dp))
                        Text(texto, style = MaterialTheme.typography.labelMedium, color = color)
                    }
                }
            }
            if (cluster == null) return@Column

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("Procesos MPI en uso", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(
                    "${(cluster.uso * 100).roundToInt()} %",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { cluster.uso },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth()) {
                Cifra("Nodos disponibles", "${cluster.nodosDisponibles}/${cluster.nodosTotales}", Modifier.weight(1f))
                Cifra("En cola", cluster.trabajosEnCola.toString(), Modifier.weight(1f))
                Cifra("Ejecutando", cluster.trabajosEjecutando.toString(), Modifier.weight(1f))
            }
            if (cluster.nodos.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    cluster.nodos.joinToString("  ·  ") {
                        "${it.id}: ${it.carga}/${it.capacidad}" + (if (it.externo) " externo" else "") +
                            (if (it.estado == "EN_FALLA") " en falla" else "")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Cifra(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(valor, style = MaterialTheme.typography.titleLarge)
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TarjetaTrabajo(trabajo: TrabajoHpc, onClick: () -> Unit) {
    val a = aspecto(trabajo.estado)
    OutlinedCard(onClick = onClick, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(a.color.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(a.icono, contentDescription = null, tint = a.color, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(trabajo.nombre, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "#${trabajo.idCorto} · ${formatearHace(trabajo.enviadoEn)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(color = a.color.copy(alpha = 0.14f), shape = CircleShape) {
                    Text(
                        a.etiqueta,
                        style = MaterialTheme.typography.labelSmall,
                        color = a.color,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (trabajo.activo) {
                // OpenMPI no informa el porcentaje de avance: barra indeterminada.
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = a.color,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                trabajo.rutaCodigo + (trabajo.rutaDataset?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildList {
                    add("${trabajo.procesos} procesos")
                    add("prioridad ${trabajo.prioridad.lowercase()}")
                    if (trabajo.estado == EstadoTrabajo.EJECUTANDO && trabajo.nodos.isNotEmpty()) add("en ${trabajo.nodos.joinToString(", ")}")
                    trabajo.rutaResultado?.let { add("resultado: $it") }
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            trabajo.mensajeError?.takeIf { it.isNotBlank() && trabajo.estado != EstadoTrabajo.COMPLETADO }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaNuevoTrabajo(
    enviando: Boolean,
    onCerrar: () -> Unit,
    onEnviar: (SolicitudTrabajo) -> Unit
) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var codigo by rememberSaveable { mutableStateOf("") }
    var dataset by rememberSaveable { mutableStateOf("") }
    var procesos by rememberSaveable { mutableIntStateOf(4) }
    var minutos by rememberSaveable { mutableStateOf("10") }
    var prioridad by rememberSaveable { mutableStateOf("NORMAL") }
    var parametros by rememberSaveable { mutableStateOf("") }

    val minutosValidos = minutos.toIntOrNull()?.takeIf { it >= 1 }
    val valido = nombre.isNotBlank() && codigo.isNotBlank() && minutosValidos != null

    ModalBottomSheet(
        onDismissRequest = { if (!enviando) onCerrar() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Nuevo trabajo", style = MaterialTheme.typography.titleLarge)
            Text(
                "Tu código se compila y se ejecuta con MPI en el clúster. El resultado queda en \"Resultados HPC\" de tu Home.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = nombre, onValueChange = { nombre = it },
                label = { Text("Nombre del trabajo") }, placeholder = { Text("Simulación de fluidos") },
                singleLine = true, enabled = !enviando, shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            CampoRuta("Código fuente", codigo, { codigo = it }, "/Proyectos/simulacion.c",
                "Ruta en tu Home: .c, .cpp o .py (mpi4py)", !enviando)
            Spacer(Modifier.height(12.dp))
            CampoRuta("Dataset (opcional)", dataset, { dataset = it }, "/Proyectos/datos.csv",
                "Ruta en tu Home; se pasa al programa como primer argumento", !enviando)

            Spacer(Modifier.height(20.dp))
            Contador("Procesos MPI", procesos, rango = 1..64, habilitado = !enviando, onCambio = { procesos = it })
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = minutos, onValueChange = { minutos = it.filter(Char::isDigit).take(5) },
                label = { Text("Tiempo máximo (minutos)") }, singleLine = true, enabled = !enviando,
                isError = minutosValidos == null, shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            Text("Prioridad", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRIORIDADES.forEach { (valor, texto) ->
                    FilterChip(selected = prioridad == valor, onClick = { prioridad = valor }, enabled = !enviando,
                        label = { Text(texto) })
                }
            }
            Text(
                "Alta requiere el permiso de administración del clúster.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = parametros, onValueChange = { parametros = it },
                label = { Text("Parámetros (opcional)") }, placeholder = { Text("iteraciones=1000, tolerancia=0.01") },
                singleLine = true, enabled = !enviando, shape = MaterialTheme.shapes.small,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val params = parametros.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        .associate { p -> p.substringBefore("=").trim() to p.substringAfter("=", "").trim() }
                    onEnviar(SolicitudTrabajo(nombre.trim(), codigo.trim(), dataset.trim().ifBlank { null },
                        procesos, (minutosValidos ?: 1) * 60, prioridad, params))
                },
                enabled = valido && !enviando,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (enviando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Enviando…")
                } else {
                    Text("Enviar al clúster")
                }
            }
        }
    }
}

@Composable
private fun CampoRuta(etiqueta: String, valor: String, onCambio: (String) -> Unit, ejemplo: String, ayuda: String, habilitado: Boolean) {
    OutlinedTextField(
        value = valor, onValueChange = onCambio,
        label = { Text(etiqueta) }, placeholder = { Text(ejemplo) }, supportingText = { Text(ayuda) },
        singleLine = true, enabled = habilitado, shape = MaterialTheme.shapes.small,
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Contador(etiqueta: String, valor: Int, rango: IntRange, habilitado: Boolean, onCambio: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = { onCambio(valor - 1) },
            enabled = habilitado && valor > rango.first
        ) { Icon(Icons.Rounded.Remove, contentDescription = "Menos $etiqueta") }
        Text(
            valor.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.widthIn(min = 40.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        FilledTonalIconButton(
            onClick = { onCambio(valor + 1) },
            enabled = habilitado && valor < rango.last
        ) { Icon(Icons.Rounded.Add, contentDescription = "Más $etiqueta") }
    }
}
