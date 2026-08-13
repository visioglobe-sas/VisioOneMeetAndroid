package com.visioglobe.visioonemeet.ui

import android.webkit.WebView
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.visioglobe.visioonemeet.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject

// Stand-in for a real occupancy sensor feed: cycles a POI's surface through
// these colors on a timer. See docs/features/occupancy-simulated.md.
private val OCCUPANCY_COLORS = listOf("#2ECC71", "#F1C40F", "#E74C3C")
private const val OCCUPANCY_INTERVAL_MS = 2500L

/**
 * Calls `window.MapBridge.updateOccupancy` in the WebView. `color: null` resets the surface to
 * its normal appearance rather than a hardcoded default.
 *
 * Arguments are JSON-encoded via [org.json] before being interpolated into the generated script
 * — never raw string concatenation, to avoid JS injection (same rule as the other platforms'
 * native<->JS bridges, see docs/COMMUNICATION_GUIDE.md equivalents).
 */
private fun WebView.updateOccupancy(planId: String, color: String?) {
    val entry = JSONObject().apply {
        put("planId", planId)
        put("color", color ?: JSONObject.NULL)
    }
    val script = "window.MapBridge.updateOccupancy(${JSONArray().put(entry)})"
    evaluateJavascript(script, null)
}

/**
 * Recenters the camera on the whole venue by calling `view.goToGlobal()` in the WebView. See
 * docs/features/reset-view.md.
 */
private fun WebView.goToGlobal() {
    evaluateJavascript("window.MapBridge.goToGlobal()", null)
}

@Composable
fun ResetViewOverlay(webView: WebView?) {
    Button(
        onClick = { webView?.goToGlobal() },
        modifier = Modifier.padding(16.dp),
    ) {
        Text(stringResource(R.string.feature_reset_view_title))
    }
}

@Composable
fun OccupancySimulationOverlay(webView: WebView?) {
    var placeId by remember { mutableStateOf("") }
    var simulatingOccupancy by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = placeId,
            onValueChange = { placeId = it },
            label = { Text("Place ID") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = { simulatingOccupancy = !simulatingOccupancy },
            enabled = simulatingOccupancy || placeId.isNotBlank(),
        ) {
            Text(if (simulatingOccupancy) "Stop occupancy simulation" else "Simulate occupancy")
        }
    }

    LaunchedEffect(simulatingOccupancy, placeId, webView) {
        val targetPlaceId = placeId.trim()
        val view = webView
        if (!simulatingOccupancy || targetPlaceId.isEmpty() || view == null) return@LaunchedEffect

        var colorIndex = 0
        view.updateOccupancy(targetPlaceId, OCCUPANCY_COLORS[colorIndex])
        try {
            while (isActive) {
                delay(OCCUPANCY_INTERVAL_MS)
                colorIndex = (colorIndex + 1) % OCCUPANCY_COLORS.size
                view.updateOccupancy(targetPlaceId, OCCUPANCY_COLORS[colorIndex])
            }
        } finally {
            // Reset the surface rather than leaving it stuck on the last simulated color.
            view.updateOccupancy(targetPlaceId, null)
        }
    }
}
