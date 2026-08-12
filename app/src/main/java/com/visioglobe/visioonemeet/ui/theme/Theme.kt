package com.visioglobe.visioonemeet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = VisioBlue,
    secondary = VisioBlueLight,
)

private val DarkColors = darkColorScheme(
    primary = VisioBlueLight,
    secondary = VisioBlue,
)

@Composable
fun VisioOneMeetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
