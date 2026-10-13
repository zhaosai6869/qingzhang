package com.qingxi.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.qingxi.ledger.data.settings.ThemePalette

val ExpenseRed = Color(0xFFE2574C)

private fun Color.blend(target: Color, fraction: Float): Color = Color(
    red = red + (target.red - red) * fraction,
    green = green + (target.green - green) * fraction,
    blue = blue + (target.blue - blue) * fraction,
    alpha = alpha
)

private fun argb(value: Long): Color = Color(value.toInt())

private fun lightScheme(palette: ThemePalette) = lightColorScheme(
    primary = argb(palette.argb),
    onPrimary = Color.White,
    primaryContainer = argb(palette.argb).blend(Color.White, 0.85f),
    onPrimaryContainer = argb(palette.argb).blend(Color.Black, 0.55f),
    secondary = argb(palette.argb).blend(Color.Gray, 0.35f),
    onSecondary = Color.White,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF15181E),
    surface = Color.White,
    onSurface = Color(0xFF15181E),
    surfaceVariant = Color(0xFFEDF0F5),
    onSurfaceVariant = Color(0xFF5A6272),
    outline = Color(0xFFB6BDC9),
    outlineVariant = Color(0xFFE1E5EB)
)

private fun darkScheme(palette: ThemePalette) = darkColorScheme(
    primary = argb(palette.argb).blend(Color.White, 0.45f),
    onPrimary = Color(0xFF08183F),
    primaryContainer = argb(palette.argb).blend(Color.Black, 0.45f),
    onPrimaryContainer = argb(palette.argb).blend(Color.White, 0.80f),
    secondary = argb(palette.argb).blend(Color.White, 0.25f),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE6E9EF),
    surface = Color(0xFF171A20),
    onSurface = Color(0xFFE6E9EF),
    surfaceVariant = Color(0xFF23272F),
    onSurfaceVariant = Color(0xFFAEB6C2),
    outline = Color(0xFF4A5058),
    outlineVariant = Color(0xFF2C313A)
)

@Composable
fun QingJiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: ThemePalette = ThemePalette.BLUE,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme(palette) else lightScheme(palette),
        content = content
    )
}
