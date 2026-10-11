package com.fktm.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF4DD0B1)
private val TealDark = Color(0xFF0F6E5C)
private val Danger = Color(0xFFE5534B)

private val DarkScheme = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF06231D),
    secondary = TealDark,
    error = Danger,
    background = Color(0xFF101418),
    surface = Color(0xFF171C21),
    onBackground = Color(0xFFE6EAEE),
    onSurface = Color(0xFFE6EAEE),
)

private val LightScheme = lightColorScheme(
    primary = TealDark,
    onPrimary = Color.White,
    error = Danger,
)

@Composable
fun FktmTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
