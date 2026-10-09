package com.upbcientifica.mobile.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.upbcientifica.mobile.data.filesync.EstadoSincronizacion
import com.upbcientifica.mobile.ui.theme.exito

/**
 * Icono de estado de File Sync para la barra superior. Es solo informativo
 * (no se puede pulsar): ocupa lo mismo que un IconButton para alinearse con
 * el avatar, y TalkBack lee el estado en voz alta.
 */
@Composable
fun IndicadorSincronizacion(estado: EstadoSincronizacion, modifier: Modifier = Modifier) {
    val descripcion = when (estado) {
        is EstadoSincronizacion.AlDia -> "Archivos sincronizados"
        is EstadoSincronizacion.Sincronizando ->
            if (estado.pendientes > 0) "Sincronizando, faltan ${estado.pendientes} cambios"
            else "Sincronizando"
        EstadoSincronizacion.SinConexion -> "Sin conexión, la sincronización está en pausa"
    }

    Box(
        modifier = modifier.size(48.dp).semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center
    ) {
        // Crossfade por tipo de estado (no por valor), para no reanimar con cada avance de progreso.
        Crossfade(targetState = estado::class, label = "sync") { tipo ->
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                when (tipo) {
                    EstadoSincronizacion.AlDia::class -> Icon(
                        Icons.Outlined.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.exito
                    )
                    EstadoSincronizacion.SinConexion::class -> Icon(
                        Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    else -> Sincronizando(
                        (estado as? EstadoSincronizacion.Sincronizando)?.progreso ?: 0f
                    )
                }
            }
        }
    }
}

@Composable
private fun Sincronizando(progreso: Float) {
    val progresoAnimado by animateFloatAsState(progreso, tween(400), label = "progreso")
    val pulso by rememberInfiniteTransition(label = "pulso").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alfa"
    )
    CircularProgressIndicator(
        progress = { progresoAnimado },
        modifier = Modifier.size(34.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    )
    Icon(
        Icons.Outlined.CloudSync,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp).alpha(pulso)
    )
}
