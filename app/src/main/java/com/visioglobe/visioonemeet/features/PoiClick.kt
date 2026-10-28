package com.visioglobe.visioonemeet.features

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.visioglobe.visioonemeet.R
import com.visioglobe.visioonemeet.bridge.BridgeEvent
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.OnBridgeEvent
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState
import org.json.JSONArray

// poi-click — JS side: web/src/features/poi-click.js. See docs/features/poi-click.md.

/** A single POI carried by the `AndroidBridge.onPoiClick` payload. */
data class PoiClickInfo(val id: String, val name: String)

/**
 * Parses the JSON array sent by the page's `poiclick` forwarder, e.g.
 * `[{"id":"poi-42","name":"Meeting room A"}]` — one entry per POI under the tap (almost always
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
 * The page always forwards the SDK's `poiclick` event, but only this screen collects it: each
 * click auto-opens the controls panel (same as tapping the FAB) on the tapped POI(s). Other
 * screens ignore the event, so tapping a POI there has no side effect. The FAB remains available
 * too, so reopening the panel re-shows the last tapped POI.
 */
@Composable
fun PoiClickScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    var lastPoiClick by remember { mutableStateOf<List<PoiClickInfo>>(emptyList()) }
    OnBridgeEvent<BridgeEvent.PoiClick>(map) { event ->
        lastPoiClick = parsePoiClickPayload(event.poisJson)
        map.isSheetOpen = true
    }
    FeatureMapScreen(map, Feature.PoiClick, onBack) {
        PoiClickSheet(lastPoiClick)
    }
}

/** Displays the POI(s) tapped on the map — no manual control here, unlike the other panels. */
@Composable
private fun PoiClickSheet(pois: List<PoiClickInfo>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        if (pois.isEmpty()) {
            Text(stringResource(R.string.poi_click_empty))
            return@Column
        }
        pois.forEachIndexed { index, poi ->
            if (index > 0) Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = poi.name.ifBlank { poi.id },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = stringResource(R.string.poi_click_id, poi.id), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
