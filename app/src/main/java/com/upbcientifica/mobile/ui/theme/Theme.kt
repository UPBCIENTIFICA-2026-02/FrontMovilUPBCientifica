package com.upbcientifica.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Paleta de la app. Todo color de la UI debe salir de aquí (vía
 * MaterialTheme.colorScheme) para que el modo oscuro funcione solo.
 * Para cambiar la identidad visual basta con tocar estos valores.
 */
private val Azul10 = Color(0xFF001A41)
private val Azul20 = Color(0xFF002E69)
private val Azul30 = Color(0xFF004494)
private val Azul40 = Color(0xFF1A5BC0)
private val Azul80 = Color(0xFFAFC6FF)
private val Azul90 = Color(0xFFD9E2FF)
private val Azul95 = Color(0xFFEDF0FF)

private val Teal30 = Color(0xFF004F54)
private val Teal40 = Color(0xFF006970)
private val Teal80 = Color(0xFF4FD8E3)
private val Teal90 = Color(0xFF9AF0F9)

private val Neutro6 = Color(0xFF111318)
private val Neutro10 = Color(0xFF191C21)
private val Neutro12 = Color(0xFF1D2025)
private val Neutro17 = Color(0xFF272A2F)
private val Neutro22 = Color(0xFF32353A)
private val Neutro90 = Color(0xFFE1E2E9)
private val Neutro94 = Color(0xFFEDEDF4)
private val Neutro96 = Color(0xFFF2F3FA)
private val Neutro98 = Color(0xFFF9F9FF)

private val ColoresClaros = lightColorScheme(
    primary = Azul40,
    onPrimary = Color.White,
    primaryContainer = Azul90,
    onPrimaryContainer = Azul10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Color(0xFF002022),
    tertiary = Color(0xFF7A4E9C),
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF2F004C),
    background = Neutro98,
    onBackground = Neutro10,
    surface = Neutro98,
    onSurface = Neutro10,
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Neutro96,
    surfaceContainer = Neutro94,
    surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Neutro90,
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val ColoresOscuros = darkColorScheme(
    primary = Azul80,
    onPrimary = Azul20,
    primaryContainer = Azul30,
    onPrimaryContainer = Azul90,
    secondary = Teal80,
    onSecondary = Color(0xFF00363A),
    secondaryContainer = Teal30,
    onSecondaryContainer = Teal90,
    tertiary = Color(0xFFE0B6FF),
    tertiaryContainer = Color(0xFF603681),
    onTertiaryContainer = Color(0xFFF2DAFF),
    background = Neutro6,
    onBackground = Neutro90,
    surface = Neutro6,
    onSurface = Neutro90,
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Neutro10,
    surfaceContainer = Neutro12,
    surfaceContainerHigh = Neutro17,
    surfaceContainerHighest = Neutro22,
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Solo se ajustan pesos y tracking; los tamaños son los de Material 3. */
private val Tipografia = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/**
 * Verde para estados correctos (trabajo completado, clúster en línea).
 * Material 3 no trae un rol "success", así que se deduce del tema activo.
 */
val ColorScheme.exito: Color
    get() = if (surface.luminance() < 0.5f) Color(0xFF81C995) else Color(0xFF188038)

/** Etiqueta en mayúsculas pequeñas para encabezados de sección ("CARPETAS"). */
val EstiloEncabezadoSeccion = TextStyle(
    fontSize = 12.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.8.sp,
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun UpbCientificaTheme(
    oscuro: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (oscuro) ColoresOscuros else ColoresClaros,
        typography = Tipografia,
        shapes = Formas,
        content = content
    )
}
