package com.upbcientifica.mobile.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.upbcientifica.mobile.data.streaming.ContenidoDto
import com.upbcientifica.mobile.data.streaming.FotoDto
import com.upbcientifica.mobile.data.streaming.OrdenMedios
import com.upbcientifica.mobile.ui.components.*
import com.upbcientifica.mobile.ui.state.UiState
import com.upbcientifica.mobile.ui.viewmodel.StreamingViewModel
import kotlin.math.absoluteValue

/** Pares de colores para las miniaturas; se elige uno por video de forma estable. */
private val DegradadosMiniatura = listOf(
    Color(0xFF1A5BC0) to Color(0xFF4FD8E3),
    Color(0xFF7A4E9C) to Color(0xFFE37400),
    Color(0xFF006970) to Color(0xFF9AE6B4),
    Color(0xFFC5221F) to Color(0xFFF6AE2D),
    Color(0xFF2E3A59) to Color(0xFF8E9CC9),
)

private enum class Seccion(val etiqueta: String) { VIDEOS("Videos"), FOTOS("Fotos") }

/** Etiqueta de cada orden. MAS_VISTOS solo existe en videos (las fotos no se "reproducen"). */
private fun OrdenMedios.etiqueta() = when (this) {
    OrdenMedios.RECIENTES -> "Recientes"
    OrdenMedios.MAS_VISTOS -> "Más vistos"
    OrdenMedios.ALFABETICO -> "Título (A-Z)"
}

/** Lo que se puede borrar desde esta pantalla. */
private sealed interface Medio {
    val titulo: String
    data class Video(val dto: ContenidoDto) : Medio { override val titulo get() = dto.titulo }
    data class Foto(val dto: FotoDto) : Medio { override val titulo get() = dto.titulo }
}

/** "2026-10-08T14:03:00.000Z" -> "8 oct 2026". Si no se puede leer, se omite. */
private fun fechaCorta(iso: String?): String? = runCatching {
    val formato = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    val fecha = formato.parse(iso!!.take(19))!!
    java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("es")).format(fecha)
}.getOrNull()

@Composable
fun StreamingScreen(viewModel: StreamingViewModel, accionesCuenta: @Composable () -> Unit) {
    val videos by viewModel.state.collectAsState()
    val fotos by viewModel.fotos.collectAsState()
    val visor by viewModel.visor.collectAsState()
    val indexando by viewModel.indexando.collectAsState()
    val ordenVideos by viewModel.ordenVideos.collectAsState()
    val ordenFotos by viewModel.ordenFotos.collectAsState()
    var menuOrden by remember { mutableStateOf(false) }

    var consulta by rememberSaveable { mutableStateOf("") }
    var seccion by rememberSaveable { mutableStateOf(Seccion.VIDEOS) }
    // Qué muestra el visor (para su papelera) y qué espera confirmación de borrado.
    var abierto by remember { mutableStateOf<Medio?>(null) }
    var porEliminar by remember { mutableStateOf<Medio?>(null) }

    val abrir: (Medio) -> Unit = { medio ->
        abierto = medio
        when (medio) {
            is Medio.Video -> viewModel.reproducir(medio.dto)
            is Medio.Foto -> viewModel.verFoto(medio.dto)
        }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.avisos.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.cargarSiHaceFalta() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BarraBusqueda(
                consulta = consulta,
                onConsultaChange = { consulta = it },
                placeholder = if (seccion == Seccion.VIDEOS) "Buscar videos" else "Buscar fotos",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                finalContenido = { accionesCuenta() }
            )

            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Streaming", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                // Ordenar: lo aplica node-media (ordenPor del Contrato_Streaming).
                Box {
                    IconButton(onClick = { menuOrden = true }) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Ordenar")
                    }
                    DropdownMenu(expanded = menuOrden, onDismissRequest = { menuOrden = false }) {
                        val actual = if (seccion == Seccion.VIDEOS) ordenVideos else ordenFotos
                        val opciones = if (seccion == Seccion.VIDEOS) OrdenMedios.entries
                        else OrdenMedios.entries - OrdenMedios.MAS_VISTOS
                        Text(
                            "Ordenar por",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                        opciones.forEach { o ->
                            DropdownMenuItem(
                                text = { Text(o.etiqueta()) },
                                trailingIcon = { if (o == actual) RadioButton(selected = true, onClick = null) },
                                onClick = {
                                    menuOrden = false
                                    if (seccion == Seccion.VIDEOS) viewModel.ordenarVideos(o) else viewModel.ordenarFotos(o)
                                }
                            )
                        }
                    }
                }
                // node-media busca lo nuevo solo al cargar; este botón lo fuerza al momento.
                if (indexando) {
                    CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = viewModel::indexar) {
                        Icon(Icons.Rounded.ImageSearch, contentDescription = "Buscar fotos y videos nuevos en Mis archivos")
                    }
                }
                IconButton(onClick = { viewModel.cargar(); viewModel.cargarFotos() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Actualizar")
                }
            }

            TabRow(selectedTabIndex = seccion.ordinal, containerColor = MaterialTheme.colorScheme.surface) {
                Seccion.entries.forEach { s ->
                    Tab(selected = seccion == s, onClick = { seccion = s }, text = { Text(s.etiqueta) })
                }
            }

            val q = consulta.trim()
            when (seccion) {
                Seccion.VIDEOS -> Videos(videos, q, viewModel, onAbrir = abrir, onEliminar = { porEliminar = it })
                Seccion.FOTOS -> Fotos(fotos, q, viewModel, onAbrir = abrir, onEliminar = { porEliminar = it })
            }
        }
    }

    visor?.let {
        VisorMedia(
            it,
            onCerrar = { abierto = null; viewModel.cerrarVisor() },
            onEliminar = abierto?.let { medio -> { porEliminar = medio } }
        )
    }

    porEliminar?.let { medio ->
        AlertDialog(
            onDismissRequest = { porEliminar = null },
            title = { Text("¿Eliminar \"${medio.titulo}\"?") },
            text = {
                Text("Se borrará también de Mis archivos, no solo de " +
                    (if (medio is Medio.Video) "Streaming" else "Fotos") + ". Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        porEliminar = null
                        when (medio) {
                            is Medio.Video -> viewModel.eliminarVideo(medio.dto)
                            is Medio.Foto -> viewModel.eliminarFoto(medio.dto)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { porEliminar = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun Videos(
    estado: UiState<List<ContenidoDto>>,
    q: String,
    viewModel: StreamingViewModel,
    onAbrir: (Medio) -> Unit,
    onEliminar: (Medio) -> Unit
) {
    when (estado) {
        UiState.Idle, UiState.Loading -> EsqueletoLista()
        is UiState.Error -> EstadoError(estado.message, onReintentar = viewModel::cargar)
        is UiState.Success -> {
            val visibles = estado.data.filter { it.titulo.contains(q, ignoreCase = true) }
            when {
                estado.data.isEmpty() -> EstadoVacio(
                    icono = Icons.Outlined.VideoLibrary,
                    titulo = "No hay videos",
                    mensaje = "Los videos de Mis archivos y los que te compartan aparecen aquí."
                )
                visibles.isEmpty() -> EstadoVacio(
                    icono = Icons.Outlined.SearchOff,
                    titulo = "Sin resultados",
                    mensaje = "Ningún video coincide con \"$q\"."
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 170.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(visibles, key = { it.id }) { video ->
                        TarjetaVideo(
                            video, viewModel,
                            onClick = { onAbrir(Medio.Video(video)) },
                            // Solo el dueño puede borrarlo: no se ofrece en lo compartido.
                            onEliminar = if (video.compartido) null else ({ onEliminar(Medio.Video(video)) })
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Fotos(
    estado: UiState<List<FotoDto>>,
    q: String,
    viewModel: StreamingViewModel,
    onAbrir: (Medio) -> Unit,
    onEliminar: (Medio) -> Unit
) {
    when (estado) {
        UiState.Idle, UiState.Loading -> EsqueletoLista()
        is UiState.Error -> EstadoError(estado.message, onReintentar = viewModel::cargarFotos)
        is UiState.Success -> {
            val visibles = estado.data.filter { it.titulo.contains(q, ignoreCase = true) }
            when {
                estado.data.isEmpty() -> EstadoVacio(
                    icono = Icons.Outlined.PhotoLibrary,
                    titulo = "No hay fotos",
                    mensaje = "Las fotos de Mis archivos y las que te compartan aparecen aquí."
                )
                visibles.isEmpty() -> EstadoVacio(
                    icono = Icons.Outlined.SearchOff,
                    titulo = "Sin resultados",
                    mensaje = "Ninguna foto coincide con \"$q\"."
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(visibles, key = { it.id }) { foto ->
                        TarjetaFoto(
                            foto, viewModel,
                            onClick = { onAbrir(Medio.Foto(foto)) },
                            onEliminar = if (foto.compartido) null else ({ onEliminar(Medio.Foto(foto)) })
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TarjetaVideo(
    video: ContenidoDto,
    viewModel: StreamingViewModel,
    onClick: () -> Unit,
    onEliminar: (() -> Unit)?
) {
    val (desde, hasta) = DegradadosMiniatura[video.id.hashCode().absoluteValue % DegradadosMiniatura.size]
    var menu by remember { mutableStateOf(false) }
    // La vista previa la genera node-media; mientras llega (o si no hay) se ve el degradado.
    val miniatura by produceState<ImageBitmap?>(initialValue = null, video.id) { value = viewModel.miniatura(video) }
    // Tocar reproduce; mantener pulsado (o el menú ⋮) ofrece eliminar.
    Column(Modifier.clip(MaterialTheme.shapes.medium).combinedClickable(onClick = onClick, onLongClick = onEliminar)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.medium)
                .background(Brush.linearGradient(listOf(desde, hasta))),
            contentAlignment = Alignment.Center
        ) {
            miniatura?.let {
                Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Reproducir", tint = Color.White)
            }
            if (video.duracionSegundos > 0) {
                Text(
                    formatearDuracion(video.duracionSegundos),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(
                    video.titulo,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (video.compartido) {
                    Text(
                        "Compartido por ${video.propietario}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                fechaCorta(video.fecha)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onEliminar != null) Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Más opciones")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = { menu = false; onEliminar() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TarjetaFoto(foto: FotoDto, viewModel: StreamingViewModel, onClick: () -> Unit, onEliminar: (() -> Unit)?) {
    // La miniatura se pide con el token al aparecer en pantalla y queda en caché.
    val miniatura by produceState<ImageBitmap?>(initialValue = null, foto.id) { value = viewModel.miniatura(foto) }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            // Tocar abre; mantener pulsado ofrece eliminar (también desde la papelera del visor).
            .combinedClickable(onClick = onClick, onLongClick = onEliminar),
        contentAlignment = Alignment.Center
    ) {
        val imagen = miniatura
        if (imagen != null) {
            Image(imagen, contentDescription = foto.titulo, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.Image, contentDescription = foto.titulo, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (foto.compartido) {
            // Insignia de "compartida contigo", como en Drive.
            Icon(
                Icons.Rounded.People,
                contentDescription = "Compartida por ${foto.propietario}",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(3.dp)
                    .size(14.dp)
            )
        }
    }
}

