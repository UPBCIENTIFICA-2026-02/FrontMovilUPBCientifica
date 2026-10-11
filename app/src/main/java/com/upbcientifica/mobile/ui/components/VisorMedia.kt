package com.upbcientifica.mobile.ui.components

import android.graphics.BitmapFactory
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.upbcientifica.mobile.data.FuenteVideo

/**
 * Qué está mostrando el visor. Imágenes y videos se ven DENTRO de la app:
 * nada de pasarle el archivo a otra aplicación ni abrir un enlace en el
 * navegador (el token no debe salir de la app).
 */
sealed interface EstadoVisor {
    val titulo: String

    data class Cargando(override val titulo: String) : EstadoVisor
    data class Imagen(override val titulo: String, val imagen: ImageBitmap) : EstadoVisor
    data class Video(override val titulo: String, val fuente: FuenteVideo) : EstadoVisor
    data class Error(override val titulo: String, val mensaje: String) : EstadoVisor
}

/**
 * Convierte bytes en imagen reduciéndola si hace falta: una foto de cámara de
 * 48 MP decodificada a tamaño completo ocupa ~190 MB y tumba la app.
 */
fun decodificarImagen(bytes: ByteArray, ladoMaximo: Int = 2048): ImageBitmap? {
    val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
    if (medidas.outWidth <= 0 || medidas.outHeight <= 0) return null
    var muestreo = 1
    while (maxOf(medidas.outWidth, medidas.outHeight) / muestreo > ladoMaximo) muestreo *= 2
    val opciones = BitmapFactory.Options().apply { inSampleSize = muestreo }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opciones)?.asImageBitmap()
}

/**
 * Visor a pantalla completa, fondo negro, con el título arriba y un botón de
 * cerrar. Con [onEliminar] muestra además una papelera (la confirmación la
 * pide quien llama).
 */
@Composable
fun VisorMedia(estado: EstadoVisor, onCerrar: () -> Unit, onEliminar: (() -> Unit)? = null) {
    Dialog(
        onDismissRequest = onCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when (estado) {
                is EstadoVisor.Cargando -> CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
                is EstadoVisor.Imagen -> ImagenConZoom(estado.imagen, estado.titulo)
                is EstadoVisor.Video -> Reproductor(estado.fuente)
                is EstadoVisor.Error -> Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Rounded.BrokenImage, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(estado.mensaje, color = Color.White, textAlign = TextAlign.Center)
                }
            }

            // Barra superior semitransparente, para que se lea también sobre fotos claras.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCerrar) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = Color.White)
                }
                Text(
                    estado.titulo,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (onEliminar != null) {
                    IconButton(onClick = onEliminar) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Eliminar", tint = Color.White)
                    }
                }
            }
        }
    }
}

/** Pellizcar para ampliar, arrastrar para mover, doble toque para volver al tamaño original. */
@Composable
private fun ImagenConZoom(imagen: ImageBitmap, descripcion: String) {
    var escala by remember { mutableFloatStateOf(1f) }
    var desplazamientoX by remember { mutableFloatStateOf(0f) }
    var desplazamientoY by remember { mutableFloatStateOf(0f) }

    Image(
        bitmap = imagen,
        contentDescription = descripcion,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    escala = 1f; desplazamientoX = 0f; desplazamientoY = 0f
                })
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, mover, zoom, _ ->
                    escala = (escala * zoom).coerceIn(1f, 6f)
                    if (escala == 1f) {
                        desplazamientoX = 0f; desplazamientoY = 0f
                    } else {
                        desplazamientoX += mover.x
                        desplazamientoY += mover.y
                    }
                }
            }
            .graphicsLayer {
                scaleX = escala
                scaleY = escala
                translationX = desplazamientoX
                translationY = desplazamientoY
            }
    )
}

/**
 * ExoPlayer con los controles estándar (reproducir, pausa, barra de tiempo).
 * Manda el token en cada petición y pide el video por rangos, así que se
 * puede saltar en la línea de tiempo sin descargarlo entero.
 */
@OptIn(UnstableApi::class)
@Composable
private fun Reproductor(fuente: FuenteVideo) {
    val contexto = LocalContext.current
    val reproductor = remember(fuente) {
        val http = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(fuente.cabeceras)
            .setAllowCrossProtocolRedirects(false)
        ExoPlayer.Builder(contexto)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(fuente.url))
                prepare()
                playWhenReady = true
            }
    }
    // Al cerrar el visor se libera el decodificador y la conexión.
    DisposableEffect(reproductor) { onDispose { reproductor.release() } }

    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { player = reproductor } },
        modifier = Modifier.fillMaxSize()
    )
}
