package com.visioglobe.visioonemeet.features

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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.visioglobe.visioonemeet.R
import com.visioglobe.visioonemeet.bridge.callBridge
import com.visioglobe.visioonemeet.model.DemoMap
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject

// occupancy-simulated — JS side: web/src/features/occupancy-simulated.js.
// See docs/features/occupancy-simulated.md.

// Stand-in for a real occupancy sensor feed: cycles a POI's surface through
// these colors on a timer.
private val OCCUPANCY_COLORS = listOf("#2ECC71", "#F1C40F", "#E74C3C")
private const val OCCUPANCY_INTERVAL_MS = 2500L

/**
 * Recolors [planId]'s surfaces (`venue.updateSurface`). `color: null` resets the surface to its
 * normal appearance rather than a hardcoded default.
 */
private fun WebView.updateOccupancy(planId: String, color: String?) {
    val entry = JSONObject().apply {
        put("planId", planId)
        put("color", color ?: JSONObject.NULL)
    }
    callBridge("updateOccupancy", JSONArray().put(entry))
}

/**
 * Screen-level state of `occupancy-simulated`: the place id and whether the simulation runs. The
 * loop itself is [OccupancySimulationHost], composed at screen level, so the simulation keeps
 * running with the panel closed (the visitor closes it to watch the map).
 */
@Stable
private class OccupancySession(initialPlaceId: String) {
    var placeId by mutableStateOf(initialPlaceId)
    var isRunning by mutableStateOf(false)
}

/**
 * The occupancy loop: while [OccupancySession.isRunning], cycles the place's surface through
 * [OCCUPANCY_COLORS]. Stopping it — or leaving the screen, which cancels this effect — resets the
 * surface in the `finally` block rather than leaving it on the last simulated color.
 */
@Composable
private fun OccupancySimulationHost(session: OccupancySession, webView: WebView?) {
    LaunchedEffect(session.isRunning, webView) {
        val view = webView ?: return@LaunchedEffect
        if (!session.isRunning) return@LaunchedEffect
        val targetPlaceId = session.placeId.trim()
        var colorIndex = 0
        view.updateOccupancy(targetPlaceId, OCCUPANCY_COLORS[colorIndex])
        try {
            while (isActive) {
                delay(OCCUPANCY_INTERVAL_MS)
                colorIndex = (colorIndex + 1) % OCCUPANCY_COLORS.size
                view.updateOccupancy(targetPlaceId, OCCUPANCY_COLORS[colorIndex])
            }
        } finally {
            view.updateOccupancy(targetPlaceId, null)
        }
    }
}

@Composable
fun OccupancySimulatedScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val session = remember { OccupancySession(DemoMap.OCCUPANCY_PLACE_ID) }
    OccupancySimulationHost(session, map.webView)
    FeatureMapScreen(map, Feature.OccupancySimulated, onBack) {
        OccupancySimulationSheet(session)
    }
}

@Composable
private fun OccupancySimulationSheet(session: OccupancySession) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = session.placeId,
            onValueChange = { session.placeId = it },
            label = { Text(stringResource(R.string.field_place_id)) },
            singleLine = true,
            enabled = !session.isRunning,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = { session.isRunning = !session.isRunning },
            enabled = session.isRunning || session.placeId.isNotBlank(),
        ) {
            Text(
                stringResource(
                    if (session.isRunning) R.string.occupancy_stop else R.string.occupancy_start,
                ),
            )
        }
    }
}
