package com.visioglobe.visioonemeet.ui

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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

/**
 * Centers/zooms the camera on the POI matching `placeId` by calling `window.MapBridge.goToPlace`
 * in the WebView (`view.goToPOI` under the hood). See docs/features/goto-poi.md.
 */
private fun WebView.goToPlace(placeId: String) {
    // JSONObject.quote() JSON-encodes the single string argument (quoting + escaping) —
    // JSONArray().put(placeId) would wrap it in `[...]`, which is wrong here since
    // MapBridge.goToPlace takes a bare placeId string, not an array (unlike updateOccupancy).
    val script = "window.MapBridge.goToPlace(${JSONObject.quote(placeId)})"
    evaluateJavascript(script, null)
}

/**
 * Reverts the highlight applied by [goToPlace], without moving the camera back. See
 * docs/features/goto-poi.md.
 */
private fun WebView.clearPlace() {
    evaluateJavascript("window.MapBridge.clearPlace()", null)
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

/**
 * FAB-triggered control for `goto-poi`: a Place ID field plus "Go" (centers the camera on that
 * POI via [WebView.goToPlace]) and "Clear" (undoes the highlight via [WebView.clearPlace], camera
 * stays put) — same UX as the React Native sibling's `GoToPoiOverlay`. See docs/features/goto-poi.md.
 */
@Composable
fun GoToPoiOverlay(webView: WebView?) {
    var placeId by remember { mutableStateOf("") }

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
            onClick = { webView?.goToPlace(placeId.trim()) },
            enabled = placeId.isNotBlank(),
        ) {
            Text("Go")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = { webView?.clearPlace() }) {
            Text("Clear")
        }
    }
}

/** A single POI carried by the `AndroidBridge.onPoiClick` payload. See docs/features/poi-click.md. */
data class PoiClickInfo(val id: String, val name: String)

/**
 * Parses the JSON array emitted by `window.MapBridge`'s `poiclick` forwarder in `web/src/main.js`,
 * e.g. `[{"id":"poi-42","name":"Meeting room A"}]` — one entry per POI under the tap (almost always
 * a single one). `name` falls back to an empty string when the venue has no translation for it,
 * never throwing, so a missing translation never crashes the bridge callback.
 */
internal fun parsePoiClickPayload(json: String): List<PoiClickInfo> {
    val array = JSONArray(json)
    return List(array.length()) { index ->
        val entry = array.getJSONObject(index)
        PoiClickInfo(id = entry.getString("id"), name = entry.optString("name", ""))
    }
}

/**
 * Displays the POI(s) tapped on the map, driven entirely by the `poiclick` SDK event relayed
 * through the native<->JS bridge — there is no manual control here, unlike the other overlays.
 * [FeatureMapScreen] auto-opens the modal bottom sheet hosting this content as soon as a click
 * arrives; the FAB remains available too, so re-opening the sheet re-shows the last tapped POI.
 */
@Composable
fun PoiClickOverlay(pois: List<PoiClickInfo>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        if (pois.isEmpty()) {
            Text("Tap a POI on the map to see its details.")
            return@Column
        }
        pois.forEachIndexed { index, poi ->
            if (index > 0) Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = poi.name.ifBlank { poi.id },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = "ID: ${poi.id}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
