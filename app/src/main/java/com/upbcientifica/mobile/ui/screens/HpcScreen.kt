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
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Sync
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

private val TrabajoHpc.activo get() = estado == EstadoTrabajo.EN_COLA || estado == EstadoTrabajo.EJECUTANDO

/** job-api usa UUID: en pantalla basta con el principio, como un hash corto de git. */
private val TrabajoHpc.idCorto get() = id.take(8)

/** Etiqueta, icono y color de cada estado; un solo sitio para que lista y detalle coincidan. */
private data class AspectoEstado(val etiqueta: String, val icono: ImageVector, val color: Color)

@Composable
private fun aspecto(estado: EstadoTrabajo): AspectoEstado = when (estado) {
    EstadoTrabajo.EN_COLA -> AspectoEstado("En cola", Icons.Rounded.HourglassTop, MaterialTheme.colorScheme.tertiary)
    EstadoTrabajo.EJECUTANDO -> AspectoEstado("Ejecutando", Icons.Rounded.Sync, MaterialTheme.colorScheme.primary)
    EstadoTrabajo.COMPLETADO -> AspectoEstado("Completado", Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.exito)
    EstadoTrabajo.FALLIDO -> AspectoEstado("Fallido", Icons.Rounded.Error, MaterialTheme.colorScheme.error)
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

    val snackbar = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()

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
                    val visibles = s.data.trabajos
                        .filter {
                            when (filtro) {
                                Filtro.TODOS -> true
                                Filtro.ACTIVOS -> it.activo
                                Filtro.FINALIZADOS -> !it.activo
                            }
                        }
                        .filter { it.nombre.contains(q, ignoreCase = true) || it.id.contains(q) }

                    LazyColumn(
                        Modifier.fillMaxSize(),
                        // Espacio al final para que el FAB no tape la última tarjeta.
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "cluster") {
                            val cluster = s.data.cluster
                            if (cluster != null) TarjetaCluster(cluster) else TarjetaClusterSinEstado(s.data.trabajos)
                        }

                        item(key = "filtros") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(Filtro.entries) { opcion ->
                                    val cuantos = s.data.trabajos.count {
                                        when (opcion) {
                                            Filtro.TODOS -> true
                                            Filtro.ACTIVOS -> it.activo
                                            Filtro.FINALIZADOS -> !it.activo
                                        }
                                    }
                                    FilterChip(
                                        selected = filtro == opcion,
                                        onClick = { filtro = opcion },
                                        label = { Text("${opcion.etiqueta} · $cuantos") }
                                    )
                                }
                            }
                        }

                        if (visibles.isEmpty()) {
                            item(key = "vacio") {
                                EstadoVacio(
                                    icono = if (q.isEmpty()) Icons.Outlined.Dns else Icons.Outlined.SearchOff,
                                    titulo = if (q.isEmpty()) "No hay trabajos aquí" else "Sin resultados",
                                    mensaje = if (q.isEmpty()) "Envía un trabajo al clúster con \"Nuevo trabajo\"."
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
                    formularioAbierto = false
                    alcance.launch {
                        snackbar.showSnackbar(
                            resultado.fold(
                                onSuccess = { "Trabajo #${it.idCorto} enviado al clúster" },
                                onFailure = { "No se pudo enviar: ${it.message}" }
                            )
                        )
                    }
                }
            }
        )
    }

    detalle?.let { elegido ->
        // Se busca la versión más reciente, para que el progreso siga avanzando con la hoja abierta.
        val trabajo = (state as? UiState.Success)?.data?.trabajos?.find { it.id == elegido.id } ?: elegido
        val a = aspecto(trabajo.estado)
        val progreso = trabajo.progreso
        HojaDetalles(
            nombre = trabajo.nombre,
            tipo = TipoArchivo(a.etiqueta, a.icono, a.color),
            filas = buildList {
                add("Estado" to if (trabajo.estado == EstadoTrabajo.EJECUTANDO && progreso != null)
                    "${a.etiqueta} · ${(progreso * 100).roundToInt()} %" else a.etiqueta)
                add("Comando" to trabajo.comando)
                add("Prioridad" to "${trabajo.prioridad} de 10")
                trabajo.recursos?.let { r ->
                    add("Recursos" to "${r.nodos} nodos × ${r.cpusPorNodo} CPU · ${r.memoriaGb} GB RAM")
                    add("Tiempo máximo" to formatearMinutos(r.tiempoMaximoMin))
                }
                trabajo.resultado?.takeIf { it.isNotBlank() }?.let { add("Salida" to it) }
                trabajo.mensajeError?.takeIf { it.isNotBlank() }?.let { add("Error" to it) }
                add("Enviado" to formatearHace(trabajo.enviadoEn))
                add("Identificador" to trabajo.id)
            },
            onCerrar = { detalle = null },
            acciones = if (!trabajo.activo) null else {
                {
                    OutlinedButton(
                        onClick = {
                            viewModel.cancelarTrabajo(trabajo.id) { resultado ->
                                detalle = null
                                alcance.launch {
                                    snackbar.showSnackbar(
                                        resultado.fold(
                                            onSuccess = { "Trabajo #${trabajo.idCorto} cancelado" },
                                            onFailure = { "No se pudo cancelar: ${it.message}" }
                                        )
                                    )
                                }
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cancelar trabajo") }
                }
            }
        )
    }
}

@Composable
private fun TarjetaCluster(cluster: EstadoCluster) {
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
                    Icon(
                        Icons.Rounded.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Clúster UPB", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${cluster.nodosActivos} de ${cluster.nodosTotales} nodos en uso",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Punto verde + "En línea", como el estado de un servicio.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.exito))
                    Spacer(Modifier.width(6.dp))
                    Text("En línea", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.exito)
                }
            }

            Spacer(Modifier.height(20.dp))
            Medidor("CPU", cluster.usoCpu)
            Spacer(Modifier.height(12.dp))
            Medidor("GPU", cluster.usoGpu)
            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth()) {
                Cifra("Nodos activos", "${cluster.nodosActivos}/${cluster.nodosTotales}", Modifier.weight(1f))
                Cifra("En cola", cluster.trabajosEnCola.toString(), Modifier.weight(1f))
                Cifra("Libres", (cluster.nodosTotales - cluster.nodosActivos).toString(), Modifier.weight(1f))
            }
        }
    }
}

/**
 * El servidor real (job-api) no informa todavía el estado del clúster
 * (Contrato_HPC: GET /cluster/estado). En vez de inventar cifras se muestra
 * lo que sí se sabe: cómo van los trabajos propios.
 */
@Composable
private fun TarjetaClusterSinEstado(trabajos: List<TrabajoHpc>) {
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
                    Text("Clúster UPB", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "El servidor aún no informa la carga de los nodos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth()) {
                Cifra("Ejecutando", trabajos.count { it.estado == EstadoTrabajo.EJECUTANDO }.toString(), Modifier.weight(1f))
                Cifra("En cola", trabajos.count { it.estado == EstadoTrabajo.EN_COLA }.toString(), Modifier.weight(1f))
                Cifra("Terminados", trabajos.count { !it.activo }.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Medidor(etiqueta: String, valor: Float) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(etiqueta, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                "${(valor * 100).roundToInt()} %",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { valor },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
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
                    Text(
                        trabajo.nombre,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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

            val progreso = trabajo.progreso
            if (trabajo.estado == EstadoTrabajo.EJECUTANDO && progreso != null) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { progreso },
                        modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${(progreso * 100).roundToInt()} %",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (trabajo.activo) {
                // En cola, o ejecutando sin porcentaje conocido (job-api no lo informa).
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = a.color,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                trabajo.comando,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                trabajo.recursos?.let { "${it.nodos} nodos · ${it.nodos * it.cpusPorNodo} CPU · ${it.memoriaGb} GB" }
                    ?: "Prioridad ${trabajo.prioridad}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HojaNuevoTrabajo(
    enviando: Boolean,
    onCerrar: () -> Unit,
    onEnviar: (SolicitudTrabajo) -> Unit
) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var comando by rememberSaveable { mutableStateOf("") }
    var prioridad by rememberSaveable { mutableIntStateOf(5) }

    val valido = nombre.isNotBlank() && comando.isNotBlank()

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
                "Se enviará a la cola del clúster UPB",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre del trabajo") },
                placeholder = { Text("Simulación de fluidos") },
                singleLine = true,
                enabled = !enviando,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = comando,
                onValueChange = { comando = it },
                label = { Text("Comando") },
                placeholder = { Text("mpirun -np 4 ./simulacion") },
                supportingText = { Text("Se ejecuta tal cual en el clúster") },
                singleLine = true,
                enabled = !enviando,
                shape = MaterialTheme.shapes.small,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))
            Contador("Prioridad", prioridad, rango = 1..10, habilitado = !enviando, onCambio = { prioridad = it })
            Text(
                "1 es la más baja y 10 la más alta: el gestor atiende primero los de mayor prioridad.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    onEnviar(SolicitudTrabajo(nombre.trim(), comando.trim(), prioridad))
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
