package com.upbcientifica.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.VideoLibrary
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.upbcientifica.mobile.data.streaming.ContenidoDto
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

@Composable
fun StreamingScreen(viewModel: StreamingViewModel, accionesCuenta: @Composable () -> Unit) {
    val state by viewModel.state.collectAsState()
    var consulta by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.cargarSiHaceFalta() }

    Column(Modifier.fillMaxSize()) {
        BarraBusqueda(
            consulta = consulta,
            onConsultaChange = { consulta = it },
            placeholder = "Buscar videos",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            finalContenido = { accionesCuenta() }
        )

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Streaming", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = viewModel::cargar) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Actualizar")
            }
        }

        when (val s = state) {
            UiState.Idle, UiState.Loading -> EsqueletoLista()
            is UiState.Error -> EstadoError(s.message, onReintentar = viewModel::cargar)
            is UiState.Success -> {
                val q = consulta.trim()
                val visibles = s.data.filter { it.titulo.contains(q, ignoreCase = true) }
                when {
                    s.data.isEmpty() -> EstadoVacio(
                        icono = Icons.Outlined.VideoLibrary,
                        titulo = "El catálogo está vacío",
                        mensaje = "Todavía no hay contenido disponible para reproducir."
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
                        items(visibles, key = { it.id }) { TarjetaVideo(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaVideo(video: ContenidoDto) {
    val (desde, hasta) = DegradadosMiniatura[video.id.hashCode().absoluteValue % DegradadosMiniatura.size]
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.medium)
                .background(Brush.linearGradient(listOf(desde, hasta))),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White)
            }
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
        Spacer(Modifier.height(8.dp))
        Text(
            video.titulo,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
