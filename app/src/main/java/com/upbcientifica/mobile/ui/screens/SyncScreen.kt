package com.upbcientifica.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.upbcientifica.mobile.data.filesync.ConfigSync
import com.upbcientifica.mobile.data.filesync.EstadoSincronizacion
import com.upbcientifica.mobile.data.filesync.Evento
import com.upbcientifica.mobile.data.filesync.ModoDisparo
import com.upbcientifica.mobile.data.filesync.Programacion
import com.upbcientifica.mobile.data.filesync.Resumen
import com.upbcientifica.mobile.data.filesync.TipoEvento
import com.upbcientifica.mobile.ui.components.EncabezadoSeccion
import com.upbcientifica.mobile.ui.components.formatearHace
import com.upbcientifica.mobile.ui.theme.exito
import com.upbcientifica.mobile.ui.viewmodel.SyncViewModel
import java.util.Locale

/**
 * File Sync: qué carpeta del teléfono se respalda, dónde queda en el Home,
 * cuándo se sincroniza (al llegar al Wi-Fi del servidor y/o en un horario) y
 * qué pasó en la última sincronización.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(viewModel: SyncViewModel, onVolver: () -> Unit) {
    val estado by viewModel.estado.collectAsState()
    val config by viewModel.config.collectAsState()
    var editarRemota by remember { mutableStateOf(false) }
    var elegirHora by remember { mutableStateOf(false) }

    BackHandler(onBack = onVolver)

    val elegirCarpeta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { arbol ->
        if (arbol != null) viewModel.elegirCarpeta(arbol)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Sincronización") },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver") }
                },
                windowInsets = WindowInsets(0),
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                TarjetaEstado(
                    estado = estado,
                    configurada = config.carpetaUri != null,
                    onSincronizar = viewModel::sincronizarAhora,
                    onElegir = { elegirCarpeta.launch(null) },
                )
            }

            item { EncabezadoSeccion("Qué se respalda") }
            item {
                Fila(
                    icono = Icons.Outlined.PhoneAndroid,
                    titulo = "Carpeta del teléfono",
                    detalle = config.carpetaNombre ?: "Sin elegir",
                    accion = if (config.carpetaUri == null) "Elegir" else "Cambiar",
                    onClick = { elegirCarpeta.launch(null) },
                )
            }
            item {
                Fila(
                    icono = Icons.Outlined.CloudUpload,
                    titulo = "Se guarda en tu Home, en la carpeta",
                    detalle = config.carpetaRemota,
                    accion = "Cambiar",
                    onClick = { editarRemota = true },
                )
            }

            item { EncabezadoSeccion("Cuándo se sincroniza") }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Wifi, contentDescription = null) },
                    headlineContent = { Text("Al conectarse al Wi-Fi del servidor") },
                    supportingContent = {
                        Text("En cuanto el teléfono llega a la red local donde está el servidor, aunque la app esté cerrada.")
                    },
                    trailingContent = {
                        Switch(checked = config.alConectarWifi, onCheckedChange = viewModel::alConectarWifi)
                    },
                    modifier = Modifier.clickable { viewModel.alConectarWifi(!config.alConectarWifi) }
                )
            }
            item {
                OpcionesProgramacion(
                    config = config,
                    onElegir = { p ->
                        if (p == Programacion.DIARIA) elegirHora = true
                        else viewModel.programar(p, config.horaDiaria, config.minutoDiario)
                    },
                )
            }
            item {
                Text(
                    "La sincronización programada también espera a estar en el Wi-Fi del servidor: no gasta datos móviles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            config.ultimoResumen?.let { r ->
                item { EncabezadoSeccion("Última sincronización") }
                item { ResumenUltima(r) }
                items(r.eventos.asReversed()) { FilaEvento(it, config.carpetaRemota) }
            }
        }
    }

    if (editarRemota) {
        DialogoCarpetaRemota(
            actual = config.carpetaRemota,
            onAceptar = { viewModel.cambiarCarpetaRemota(it); editarRemota = false },
            onCancelar = { editarRemota = false },
        )
    }
    if (elegirHora) {
        DialogoHora(
            hora = config.horaDiaria, minuto = config.minutoDiario,
            onAceptar = { h, m -> viewModel.programar(Programacion.DIARIA, h, m); elegirHora = false },
            onCancelar = { elegirHora = false },
        )
    }
}

private data class Aspecto(val icono: ImageVector, val color: Color, val titulo: String, val detalle: String)

@Composable
private fun TarjetaEstado(
    estado: EstadoSincronizacion,
    configurada: Boolean,
    onSincronizar: () -> Unit,
    onElegir: () -> Unit,
) {
    val esquema = MaterialTheme.colorScheme
    val a = when (estado) {
        EstadoSincronizacion.SinConfigurar -> Aspecto(
            Icons.Outlined.CloudQueue, esquema.onSurfaceVariant, "Sin configurar",
            "Elige una carpeta del teléfono para tener una copia en el servidor."
        )
        is EstadoSincronizacion.Sincronizando -> Aspecto(
            Icons.Outlined.CloudSync, esquema.primary, "Sincronizando…",
            when {
                estado.pendientes > 0 -> "Faltan ${estado.pendientes} · ${estado.detalle}"
                else -> estado.detalle.ifBlank { "Buscando cambios" }
            }
        )
        is EstadoSincronizacion.AlDia -> Aspecto(
            Icons.Outlined.CloudDone, esquema.exito,
            if (estado.ultimaSincronizacion == 0L) "Lista para sincronizar" else "Todo sincronizado",
            buildString {
                append(if (estado.ultimaSincronizacion == 0L) "Aún no se ha sincronizado" else "Última: ${formatearHace(estado.ultimaSincronizacion)}")
                if (estado.conflictos > 0) append(" · ${estado.conflictos} conflicto(s): se guardaron ambas versiones")
            }
        )
        EstadoSincronizacion.SinConexion -> Aspecto(
            Icons.Outlined.WifiOff, esquema.onSurfaceVariant, "Esperando la red del servidor",
            "Se sincronizará sola al conectarte al Wi-Fi donde está el servidor."
        )
        is EstadoSincronizacion.Error -> Aspecto(Icons.Outlined.SyncProblem, esquema.error, "No se pudo sincronizar", estado.mensaje)
    }
    val sincronizando = estado is EstadoSincronizacion.Sincronizando

    Surface(
        color = esquema.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        Column {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(a.icono, contentDescription = null, tint = a.color, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.titulo, style = MaterialTheme.typography.titleMedium)
                    Text(a.detalle, style = MaterialTheme.typography.bodyMedium, color = esquema.onSurfaceVariant,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            if (estado is EstadoSincronizacion.Sincronizando) {
                if (estado.pendientes > 0) LinearProgressIndicator(progress = { estado.progreso }, Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                if (configurada) {
                    Button(onClick = onSincronizar, enabled = !sincronizando) {
                        Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sincronizar ahora")
                    }
                } else {
                    Button(onClick = onElegir) { Text("Elegir carpeta") }
                }
            }
        }
    }
}

@Composable
private fun Fila(icono: ImageVector, titulo: String, detalle: String, accion: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icono, contentDescription = null) },
        overlineContent = { Text(titulo) },
        headlineContent = { Text(detalle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = { TextButton(onClick = onClick) { Text(accion) } },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun OpcionesProgramacion(config: ConfigSync, onElegir: (Programacion) -> Unit) {
    val opciones = listOf(
        Programacion.NINGUNA to "Sin horario",
        Programacion.CADA_HORA to "Cada hora",
        Programacion.DIARIA to "Todos los días a las " +
            String.format(Locale.ROOT, "%02d:%02d", config.horaDiaria, config.minutoDiario),
    )
    Column {
        ListItem(
            leadingContent = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
            headlineContent = { Text("Horario programado") },
        )
        opciones.forEach { (p, texto) ->
            Row(
                Modifier.fillMaxWidth()
                    .selectable(selected = config.programacion == p, onClick = { onElegir(p) }, role = Role.RadioButton)
                    .padding(start = 56.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = config.programacion == p, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(texto, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (p == Programacion.DIARIA && config.programacion == p) {
                    TextButton(onClick = { onElegir(p) }) { Text("Cambiar hora") }
                }
            }
        }
    }
}

@Composable
private fun ResumenUltima(r: Resumen) {
    val modo = when (r.modo) {
        ModoDisparo.MANUAL -> "manual"
        ModoDisparo.AUTOMATICO_AL_CONECTAR -> "al conectar el Wi-Fi"
        ModoDisparo.PROGRAMADO -> "programada"
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("${formatearHace(r.fecha).replaceFirstChar { it.uppercase() }} · $modo",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        r.mensajeError?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        if (r.mensajeError == null) {
            val partes = listOfNotNull(
                "${r.subidos} subido(s)",
                "${r.bajados} bajado(s)",
                "${r.borrados} borrado(s)".takeIf { r.borrados > 0 },
                "${r.conflictos} conflicto(s)".takeIf { r.conflictos > 0 },
                "${r.errores} con error".takeIf { r.errores > 0 },
            )
            Text(if (r.eventos.isEmpty()) "Sin cambios: todo estaba al día" else partes.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FilaEvento(e: Evento, carpetaRemota: String) {
    val esquema = MaterialTheme.colorScheme
    val (icono, color, texto) = when (e.tipo) {
        TipoEvento.SUBIDO -> Triple(Icons.Outlined.CloudUpload, esquema.primary, "Subido al servidor")
        TipoEvento.BAJADO -> Triple(Icons.Outlined.CloudDownload, esquema.primary, "Bajado al teléfono")
        TipoEvento.BORRADO_EN_TELEFONO -> Triple(Icons.Outlined.Delete, esquema.onSurfaceVariant, "Borrado en el teléfono (se borró en el servidor)")
        TipoEvento.BORRADO_EN_SERVIDOR -> Triple(Icons.Outlined.Delete, esquema.onSurfaceVariant, "Borrado en el servidor (se borró en el teléfono)")
        TipoEvento.CONFLICTO -> Triple(Icons.Outlined.CallSplit, esquema.tertiary, "Conflicto: tu versión quedó como «${e.detalle}»")
        TipoEvento.ERROR -> Triple(Icons.Outlined.ErrorOutline, esquema.error, e.detalle ?: "Error")
    }
    ListItem(
        leadingContent = { Icon(icono, contentDescription = null, tint = color) },
        headlineContent = { Text(e.ruta.removePrefix("$carpetaRemota/"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(texto, maxLines = 2, overflow = TextOverflow.Ellipsis) },
    )
}

@Composable
private fun DialogoCarpetaRemota(actual: String, onAceptar: (String) -> Unit, onCancelar: () -> Unit) {
    var nombre by remember { mutableStateOf(actual) }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Carpeta en tu Home") },
        text = {
            Column {
                OutlinedTextField(value = nombre, onValueChange = { nombre = it }, singleLine = true, label = { Text("Nombre") })
                Spacer(Modifier.height(8.dp))
                Text(
                    "Es la carpeta que verás en «Mis archivos». Si la cambias, la próxima sincronización empieza una copia nueva allí.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAceptar(nombre) }, enabled = nombre.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoHora(hora: Int, minuto: Int, onAceptar: (Int, Int) -> Unit, onCancelar: () -> Unit) {
    val estado = rememberTimePickerState(initialHour = hora, initialMinute = minuto, is24Hour = true)
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Sincronizar todos los días a las") },
        text = { TimePicker(state = estado) },
        confirmButton = { TextButton(onClick = { onAceptar(estado.hour, estado.minute) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}
