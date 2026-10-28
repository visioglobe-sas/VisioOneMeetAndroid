package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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
import com.visioglobe.visioonemeet.bridge.nextBridgeRequestId
import com.visioglobe.visioonemeet.model.DemoMap
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.OnBridgeEvent
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState
import org.json.JSONObject

// custom-data — JS side: web/src/features/custom-data.js. See docs/features/custom-data.md.

/**
 * Reloads CustomData from the server and reads the given POI's CustomData in one round trip
 * (`venue.refreshCustomData()` then `venue.getPOICustomData(poi)`). The result arrives
 * asynchronously via `AndroidBridge.onCustomDataLoaded`, echoing [requestId] back.
 */
private fun WebView.loadCustomData(requestId: Int, placeId: String) = callBridge("loadCustomData", requestId, placeId)

/**
 * Parses the JSON argument of `AndroidBridge.onCustomDataLoaded`, e.g. `{"price":"12€"}`, `{}` (no
 * CustomData for that POI), or the literal string `"null"` (`JSON.stringify(null)` on the JS side,
 * meaning the POI id didn't resolve at all). `venue.getPOICustomData()` itself never returns
 * null/undefined, only `{}`, so that distinction is made on the JS side, not by the SDK.
 */
internal fun parseCustomDataPayload(json: String): Map<String, String>? {
    if (json == "null") return null
    val root = JSONObject(json)
    return root.keys().asSequence().associateWith { key -> root.getString(key) }
}

/**
 * Screen-level state of `custom-data`: the place id and the last loaded result, fed every
 * `onCustomDataLoaded` response at screen level, so a response arriving while the panel is closed
 * is not lost.
 */
@Stable
private class CustomDataSession(initialPlaceId: String) {
    var placeId by mutableStateOf(initialPlaceId)

    /** `false` until a load completes; then [customData] holds its result (`null` = POI not found). */
    var hasResult by mutableStateOf(false)
        private set
    var customData by mutableStateOf<Map<String, String>?>(null)
        private set
    private var pendingRequestId: Int? = null

    fun load(webView: WebView?, targetPlaceId: String) {
        hasResult = false
        val requestId = nextBridgeRequestId()
        pendingRequestId = requestId
        webView?.loadCustomData(requestId, targetPlaceId)
    }

    fun onLoaded(event: BridgeEvent.CustomDataLoaded) {
        if (event.requestId != pendingRequestId) return
        pendingRequestId = null
        customData = parseCustomDataPayload(event.customDataJson)
        hasResult = true
    }
}

/**
 * Loads its own map ([DemoMap.CUSTOM_DATA_MAP_HASH]): [DemoMap.MAP_HASH] has no CustomData
 * published. A Place ID field plus a single "Load" button that calls [loadCustomData] — combining
 * `venue.refreshCustomData()` and `venue.getPOICustomData()` into one round trip. Above the field,
 * one chip per [DemoMap.CUSTOM_DATA_SAMPLE_PLACE_IDS] entry fills it and loads it right away. All
 * three outcomes are normal states, not errors: every key/value pair, "No custom data for this
 * POI" (empty map) or "POI not found" (`null`).
 */
@Composable
fun CustomDataScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val session = remember { CustomDataSession(DemoMap.CUSTOM_DATA_PLACE_ID) }
    OnBridgeEvent<BridgeEvent.CustomDataLoaded>(map) { session.onLoaded(it) }
    FeatureMapScreen(map, Feature.CustomData, onBack, mapHash = DemoMap.CUSTOM_DATA_MAP_HASH) {
        CustomDataSheet(session, map.webView)
    }
}

@Composable
private fun CustomDataSheet(session: CustomDataSession, webView: WebView?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.custom_data_samples_label), style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            DemoMap.CUSTOM_DATA_SAMPLE_PLACE_IDS.forEach { sampleId ->
                OutlinedButton(
                    onClick = {
                        session.placeId = sampleId
                        session.load(webView, sampleId)
                    },
                    modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                ) {
                    Text(sampleId)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = session.placeId,
                onValueChange = { session.placeId = it },
                label = { Text(stringResource(R.string.field_place_id)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { session.load(webView, session.placeId.trim()) },
                enabled = session.placeId.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_load))
            }
        }
        if (session.hasResult) {
            Spacer(modifier = Modifier.height(12.dp))
            val data = session.customData
            when {
                data == null -> Text(stringResource(R.string.poi_not_found), color = MaterialTheme.colorScheme.error)
                data.isEmpty() -> Text(stringResource(R.string.custom_data_empty))
                else -> data.forEach { (key, value) ->
                    Text(text = "$key: $value", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
