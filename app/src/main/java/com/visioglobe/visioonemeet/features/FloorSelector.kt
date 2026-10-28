package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.visioglobe.visioonemeet.bridge.callBridge
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.FeatureMapState
import com.visioglobe.visioonemeet.ui.OnBridgeEvent
import com.visioglobe.visioonemeet.ui.components.CurrentOptionButtons
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState
import org.json.JSONObject

// floor-selector — JS side: web/src/features/floor-selector.js. See docs/features/floor-selector.md.
// Also used by native-ui-replacement.

/** Moves the camera to [floorId] within [buildingId] (`view.goToFloor`). */
private fun WebView.goToFloor(buildingId: String, floorId: String) = callBridge("goToFloor", buildingId, floorId)

/** A single floor of the exposed building, carried by the `AndroidBridge.onFloorsReady` payload. */
data class FloorInfo(val id: String, val name: String, val levelIndex: Int)

/**
 * Snapshot of the floor-selector state, seeded once by `AndroidBridge.onFloorsReady` and kept in
 * sync afterwards by `AndroidBridge.onFloorChanged`. [floors] belongs to a single building —
 * today always the venue's first one, see `web/src/features/floor-selector.js` — and
 * [currentFloorId] reflects the SDK's actual current floor, whether it changed via this panel's
 * buttons or the SDK's own default floor-selector widget.
 */
data class FloorSelectorState(
    val buildingId: String? = null,
    val floors: List<FloorInfo> = emptyList(),
    val currentFloorId: String? = null,
)

/**
 * Parses the JSON object the page sends once the view exists, e.g.
 * `{"buildingId":"b1","currentFloorId":"f0","floors":[{"id":"f0","name":"Ground floor","levelIndex":0}]}`.
 * `name` falls back to an empty string when the venue has no translation for it, same convention
 * as `parsePoiClickPayload`.
 */
internal fun parseFloorsReadyPayload(json: String): FloorSelectorState {
    val root = JSONObject(json)
    val floorsArray = root.getJSONArray("floors")
    val floors = List(floorsArray.length()) { index ->
        val entry = floorsArray.getJSONObject(index)
        FloorInfo(
            id = entry.getString("id"),
            name = entry.optString("name", ""),
            levelIndex = entry.getInt("levelIndex"),
        )
    }
    return FloorSelectorState(
        // `opt(...) as? String` (rather than `optString`) treats a missing key and an explicit
        // JSON `null` (e.g. `currentFloorId` when the SDK has no current floor yet) the same way:
        // JSONObject.NULL is not a String instance, so the cast yields null in both cases.
        buildingId = root.opt("buildingId") as? String,
        floors = floors,
        currentFloorId = root.opt("currentFloorId") as? String,
    )
}

/**
 * The floor-selector state for the calling screen, fed by `onFloorsReady` (pushed once, right
 * after the map loads, so this must be called from the start) and `onFloorChanged`.
 */
@Composable
internal fun rememberFloorSelectorState(map: FeatureMapState): FloorSelectorState {
    var state by remember { mutableStateOf(FloorSelectorState()) }
    OnBridgeEvent<BridgeEvent.FloorsReady>(map) { state = parseFloorsReadyPayload(it.floorsJson) }
    OnBridgeEvent<BridgeEvent.FloorChanged>(map) { state = state.copy(currentFloorId = it.floorId) }
    return state
}

@Composable
fun FloorSelectorScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val floorSelector = rememberFloorSelectorState(map)
    FeatureMapScreen(map, Feature.FloorSelector, onBack) {
        FloorSelectorSheet(map.webView, floorSelector)
    }
}

/**
 * One button per floor of the current building, highest first, calling [goToFloor] on tap, the
 * current floor (per [FloorSelectorState.currentFloorId]) highlighted. Demonstrates driving
 * `view.goToFloor` from native code — the SDK's own default floor-selector widget (visible on the
 * map with no app code at all) already offers this to the end user.
 */
@Composable
internal fun FloorSelectorSheet(webView: WebView?, floorSelector: FloorSelectorState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        if (floorSelector.floors.isEmpty()) {
            Text(stringResource(R.string.floor_selector_unavailable))
            return@Column
        }
        val buildingId = floorSelector.buildingId
        CurrentOptionButtons(
            options = floorSelector.floors.sortedByDescending { it.levelIndex }.map { it.id to it.name },
            current = floorSelector.currentFloorId,
            onSelect = { floorId -> if (buildingId != null) webView?.goToFloor(buildingId, floorId) },
        )
    }
}
