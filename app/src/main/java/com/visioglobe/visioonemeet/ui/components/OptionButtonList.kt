package com.visioglobe.visioonemeet.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One full-width button per option (value, label), the [current] one rendered as a filled,
 * disabled [Button] (tapping it again would be a no-op), every other one as an [OutlinedButton]
 * calling [onSelect]. Used by `floor-selector`, `runtime-locale` and `explore-mode`.
 */
@Composable
fun CurrentOptionButtons(
    options: List<Pair<String, String>>,
    current: String?,
    onSelect: (String) -> Unit,
) {
    options.forEach { (value, label) ->
        val buttonModifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
        if (value == current) {
            Button(onClick = {}, enabled = false, modifier = buttonModifier) { Text(label) }
        } else {
            OutlinedButton(onClick = { onSelect(value) }, modifier = buttonModifier) { Text(label) }
        }
    }
}
