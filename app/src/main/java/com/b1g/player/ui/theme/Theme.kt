package com.b1g.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark by default: the app is used in living rooms and full-screen video sits
// better against a dark chrome than a light one.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF00232F),
    secondary = Color(0xFF80DEEA),
    background = Color(0xFF0E1216),
    surface = Color(0xFF161B21),
    surfaceVariant = Color(0xFF1F262E),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00668B),
    secondary = Color(0xFF4C616C),
)

@Composable
fun B1gPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
