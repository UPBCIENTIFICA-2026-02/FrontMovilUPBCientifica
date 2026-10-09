package com.upbcientifica.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Icono y color de cada tipo de archivo, deducidos de la extensión. Los
 * colores son fijos (no del tema) porque identifican el tipo, como en Drive:
 * un PDF es rojo tanto en modo claro como en oscuro.
 */
data class TipoArchivo(val etiqueta: String, val icono: ImageVector, val color: Color)

private val Carpeta = TipoArchivo("Carpeta", Icons.Rounded.Folder, Color(0xFF5F6B7A))
private val Pdf = TipoArchivo("PDF", Icons.Rounded.PictureAsPdf, Color(0xFFD93025))
private val Hoja = TipoArchivo("Hoja de cálculo", Icons.Rounded.TableChart, Color(0xFF188038))
private val Documento = TipoArchivo("Documento", Icons.Rounded.Description, Color(0xFF1A73E8))
private val Presentacion = TipoArchivo("Presentación", Icons.Rounded.Slideshow, Color(0xFFE37400))
private val Imagen = TipoArchivo("Imagen", Icons.Rounded.Image, Color(0xFFC5221F))
private val Video = TipoArchivo("Video", Icons.Rounded.Movie, Color(0xFFD93025))
private val Audio = TipoArchivo("Audio", Icons.Rounded.AudioFile, Color(0xFF9334E6))
private val Comprimido = TipoArchivo("Comprimido", Icons.Rounded.FolderZip, Color(0xFF795548))
private val Codigo = TipoArchivo("Código", Icons.Rounded.Code, Color(0xFF007B83))
private val Datos = TipoArchivo("Datos", Icons.Rounded.DataObject, Color(0xFF007B83))
private val Generico = TipoArchivo("Archivo", Icons.Rounded.InsertDriveFile, Color(0xFF5F6368))

fun tipoArchivo(nombre: String, esCarpeta: Boolean): TipoArchivo {
    if (esCarpeta) return Carpeta
    return when (nombre.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "pdf" -> Pdf
        "csv", "tsv", "xls", "xlsx", "ods" -> Hoja
        "doc", "docx", "odt", "txt", "md", "rtf", "tex" -> Documento
        "ppt", "pptx", "odp" -> Presentacion
        "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "tif", "tiff" -> Imagen
        "mp4", "mkv", "mov", "avi", "webm" -> Video
        "mp3", "wav", "flac", "ogg", "m4a" -> Audio
        "zip", "rar", "7z", "tar", "gz", "tgz", "bz2" -> Comprimido
        "py", "java", "kt", "c", "cpp", "h", "js", "ts", "r", "m", "sh", "ipynb" -> Codigo
        "json", "xml", "yaml", "yml", "h5", "hdf5", "nc", "parquet", "mat", "npy" -> Datos
        else -> Generico
    }
}

/** Icono del tipo sobre un cuadrado redondeado con el color del tipo muy tenue. */
@Composable
fun IconoTipoArchivo(tipo: TipoArchivo, modifier: Modifier = Modifier, tamano: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(tamano)
            .clip(MaterialTheme.shapes.small)
            .background(tipo.color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            tipo.icono,
            contentDescription = tipo.etiqueta,
            tint = tipo.color,
            modifier = Modifier.size(tamano * 0.58f)
        )
    }
}

/** 1536 -> "1.5 KB". Sin decimales para B y KB: "512 B" se lee mejor que "512,0 B". */
fun formatearTamano(bytes: Long): String {
    val unidades = listOf("B", "KB", "MB", "GB", "TB")
    var valor = bytes.toDouble()
    var i = 0
    while (valor >= 1024 && i < unidades.lastIndex) {
        valor /= 1024
        i++
    }
    val patron = if (i <= 1) "%.0f %s" else "%.1f %s"
    return String.format(Locale.getDefault(), patron, valor, unidades[i])
}

/** 3725 -> "1:02:05"; 95 -> "1:35". Formato de reproductor de video. */
fun formatearDuracion(segundos: Int): String {
    val h = segundos / 3600
    val m = (segundos % 3600) / 60
    val s = segundos % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%d:%02d", m, s)
}

/** Marca de tiempo (ms) -> "hace un momento", "hace 5 min", "hace 2 h", "hace 3 días". */
fun formatearHace(instante: Long, ahora: Long = System.currentTimeMillis()): String {
    val segundos = ((ahora - instante) / 1000).coerceAtLeast(0)
    return when {
        segundos < 60 -> "hace un momento"
        segundos < 3600 -> "hace ${segundos / 60} min"
        segundos < 86_400 -> "hace ${segundos / 3600} h"
        else -> (segundos / 86_400).let { if (it == 1L) "hace 1 día" else "hace $it días" }
    }
}

/** 90 -> "1 h 30 min"; 45 -> "45 min". */
fun formatearMinutos(minutos: Int): String {
    val h = minutos / 60
    val m = minutos % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}
