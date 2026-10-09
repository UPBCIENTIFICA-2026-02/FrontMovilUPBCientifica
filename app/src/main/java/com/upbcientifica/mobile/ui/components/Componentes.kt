package com.upbcientifica.mobile.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue

/** Colores fijos para avatares: legibles con texto blanco en ambos temas. */
private val ColoresAvatar = listOf(
    Color(0xFF1A73E8), Color(0xFF188038), Color(0xFFD93025), Color(0xFFE37400),
    Color(0xFF9334E6), Color(0xFF007B83), Color(0xFFC5221F), Color(0xFF5F6368),
)

/** Círculo con la inicial; el color depende del texto, así es estable entre pantallas. */
@Composable
fun Avatar(texto: String, modifier: Modifier = Modifier, tamano: Dp = 36.dp) {
    val inicial = texto.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val color = ColoresAvatar[texto.lowercase().hashCode().absoluteValue % ColoresAvatar.size]
    Box(
        modifier = modifier.size(tamano).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            inicial,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (tamano.value * 0.42f).sp
        )
    }
}

/** Buscador en forma de píldora, al estilo de la barra superior de Drive. */
@Composable
fun BarraBusqueda(
    consulta: String,
    onConsultaChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    finalContenido: @Composable RowScope.() -> Unit = {}
) {
    val foco = LocalFocusManager.current
    Surface(
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (consulta.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = consulta,
                    onValueChange = onConsultaChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { foco.clearFocus() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (consulta.isNotEmpty()) {
                IconButton(onClick = { onConsultaChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Borrar búsqueda")
                }
            }
            finalContenido()
        }
    }
}

/** Encabezado de sección en mayúsculas pequeñas ("CARPETAS", "ARCHIVOS"). */
@Composable
fun EncabezadoSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto.uppercase(),
        style = com.upbcientifica.mobile.ui.theme.EstiloEncabezadoSeccion,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** Mensaje centrado con icono para listas vacías o búsquedas sin resultados. */
@Composable
fun EstadoVacio(
    icono: ImageVector,
    titulo: String,
    mensaje: String,
    modifier: Modifier = Modifier,
    accion: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icono,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(titulo, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            mensaje,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (accion != null) {
            Spacer(Modifier.height(20.dp))
            accion()
        }
    }
}

/** Error de red con botón para reintentar. */
@Composable
fun EstadoError(mensaje: String, onReintentar: () -> Unit, modifier: Modifier = Modifier) {
    EstadoVacio(
        icono = Icons.Outlined.CloudOff,
        titulo = "No se pudo cargar",
        mensaje = mensaje,
        modifier = modifier,
        accion = { FilledTonalButton(onClick = onReintentar) { Text("Reintentar") } }
    )
}

/**
 * Filas "fantasma" que pulsan mientras llega la respuesta. Da idea de la forma
 * del contenido y se percibe más rápido que un spinner suelto.
 */
@Composable
fun EsqueletoLista(modifier: Modifier = Modifier, filas: Int = 8) {
    val transicion = rememberInfiniteTransition(label = "esqueleto")
    val alfa by transicion.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alfa"
    )
    val color = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier.fillMaxWidth().alpha(alfa).padding(top = 8.dp)) {
        repeat(filas) { i ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(color))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier.fillMaxWidth(if (i % 2 == 0) 0.7f else 0.5f).height(14.dp)
                            .clip(CircleShape).background(color)
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth(0.3f).height(10.dp)
                            .clip(CircleShape).background(color)
                    )
                }
            }
        }
    }
}
