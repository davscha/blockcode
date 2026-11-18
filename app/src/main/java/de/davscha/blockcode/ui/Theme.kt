package de.davscha.blockcode.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

object C {
    val bg = Color(0xFF14161B)
    val panel = Color(0xFF171A20)
    val panel2 = Color(0xFF1F232B)
    val codeBg = Color(0xFF111318)
    val termBg = Color(0xFF0B0C10)
    val border = Color(0xFF2A2E37)
    val border2 = Color(0xFF3A3F4B)
    val text = Color(0xFFE8EAF0)
    val muted = Color(0xFF9AA1AE)
    val teal = Color(0xFF3DD9C1)
    val orange = Color(0xFFF5A524)
    val violet = Color(0xFFB79CFF)
    val error = Color(0xFFFF8A80)
    val highlight = Color(0xFF233552)
}

val Mono = FontFamily.Monospace

@Composable
fun BlockcodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.teal,
            onPrimary = Color(0xFF0D1F1C),
            secondary = C.orange,
            background = C.bg,
            surface = C.panel2,
            onSurface = C.text,
            onBackground = C.text,
            surfaceVariant = C.panel,
            outline = C.border2,
        ),
        content = content,
    )
}
