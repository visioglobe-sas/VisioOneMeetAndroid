package com.visioglobe.visioonemeet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt

/** Parses a `#RRGGBB` color, the format of every color preset in this app. */
fun parseHexColor(hex: String): Color = Color(hex.toColorInt())

/**
 * One color choice chip. The swatch gets an outline in the panel's content color, so dark (Black)
 * and light (White/Yellow) swatches stay visible on either a dark or a light panel.
 */
@Composable
fun ColorChip(hex: String, name: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(name) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(hex))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
            )
        },
        modifier = Modifier.padding(end = 8.dp),
    )
}
