package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.visioglobe.visioonemeet.ui.components.UiText
import com.visioglobe.visioonemeet.ui.components.resolve
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState
import org.json.JSONObject

// 3d-icon-marker — JS side: web/src/features/3d-icon-marker.js. See docs/features/3d-icon-marker.md.
// The placement result below is also used by custom-text-label.

/**
 * The single image id this demo places under on the JS side — only one icon at a time, a
 * demo-side simplification, not an SDK limitation.
 */
private const val ICON_MARKER_IMAGE_ID = "demo-icon"

/** The SDK's three `OrientationType` values, paired with a localized label. */
private val ICON_MARKER_ORIENTATIONS = listOf(
    "fixed" to R.string.orientation_fixed,
    "flat" to R.string.orientation_flat,
    "facing" to R.string.orientation_facing,
)

/**
 * Places the demo icon on [anchorId] (`venue.createImage`, owned by the anchor POI itself, at its
 * own position). The result arrives asynchronously via `AndroidBridge.onIconImagePlaced`, echoing
 * [requestId] back.
 */
private fun WebView.placeIconImage(requestId: Int, anchorId: String, orientationType: String) =
    callBridge("placeIconImage", requestId, ICON_MARKER_IMAGE_ID, anchorId, orientationType)

/** Switches the placed icon's orientation live (`venue.updateImage`). Fire-and-forget. */
private fun WebView.updateIconImageOrientation(orientationType: String) =
    callBridge("updateIconImageOrientation", ICON_MARKER_IMAGE_ID, orientationType)

/** Removes the placed icon (`venue.removeImage`). Fire-and-forget. */
private fun WebView.removeIconImage() = callBridge("removeIconImage", ICON_MARKER_IMAGE_ID)

/**
 * Result of a `placeIconImage` or `placeTextLabel` call, carried by
 * `AndroidBridge.onIconImagePlaced` / `AndroidBridge.onTextLabelPlaced`. [status] is one of
 * `"placed"` ([poiId] then non-null), `"text-required"` (text label only), `"anchor-not-found"`,
 * `"anchor-has-no-position"`, or `"error"` ([message] carrying its text).
 */
internal data class PlacementResult(
    val status: String,
    val poiId: String? = null,
    val message: String? = null,
)

/** Parses the JSON object argument of `onIconImagePlaced`/`onTextLabelPlaced`, e.g. `{"status":"placed","poiId":"…"}`. */
internal fun parsePlacementPayload(json: String): PlacementResult {
    val root = JSONObject(json)
    return PlacementResult(
        status = root.getString("status"),
        poiId = if (root.has("poiId")) root.getString("poiId") else null,
        message = if (root.has("message")) root.getString("message") else null,
    )
}

/** The error to show for a failed [PlacementResult], or null when it was placed. */
internal fun PlacementResult.errorUiText(@StringRes fallbackRes: Int): UiText? = when (status) {
    "placed" -> null
    "text-required" -> UiText(R.string.text_required)
    "anchor-not-found" -> UiText(R.string.poi_not_found)
    "anchor-has-no-position" -> UiText(R.string.poi_has_no_position)
    else -> message?.let { UiText(R.string.raw_message, listOf(it)) } ?: UiText(fallbackRes)
}

/**
 * Screen-level state of `3d-icon-marker`: the fields, and the POI the icon is placed on, so after a
 * close/reopen "Remove icon" and the live orientation chips still act on the icon on the map.
 * [onPlaced] is fed every `onIconImagePlaced` response at screen level; on success it moves the
 * camera to the anchor POI via goto-poi's `goToPlace` (from the default whole-venue view the indoor
 * floor, and so the icon, isn't shown).
 */
@Stable
private class IconMarkerSession {
    var anchorId by mutableStateOf(DemoMap.ANCHOR_POI_ID)
    var orientation by mutableStateOf("facing")
    var placedOnPoiId by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set
    private var pendingRequestId: Int? = null

    fun place(webView: WebView?) {
        error = null
        val requestId = nextBridgeRequestId()
        pendingRequestId = requestId
        webView?.placeIconImage(requestId, anchorId.trim(), orientation)
    }

    fun selectOrientation(webView: WebView?, value: String) {
        orientation = value
        if (placedOnPoiId != null) webView?.updateIconImageOrientation(value)
    }

    fun remove(webView: WebView?) {
        webView?.removeIconImage()
        webView?.clearPlace()
        placedOnPoiId = null
        error = null
    }

    fun onPlaced(webView: WebView?, event: BridgeEvent.IconImagePlaced) {
        if (event.requestId != pendingRequestId) return
        pendingRequestId = null
        val response = parsePlacementPayload(event.resultJson)
        error = response.errorUiText(R.string.icon_place_failed)
        if (response.status == "placed") {
            placedOnPoiId = response.poiId
            response.poiId?.let { webView?.goToPlace(it) }
        }
    }
}

@Composable
fun IconMarker3dScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val session = remember { IconMarkerSession() }
    OnBridgeEvent<BridgeEvent.IconImagePlaced>(map) { session.onPlaced(map.webView, it) }
    FeatureMapScreen(map, Feature.IconMarker3d, onBack) {
        IconMarker3dSheet(session, map.webView)
    }
}

/**
 * An "Anchor POI ID" field, one chip per [ICON_MARKER_ORIENTATIONS] entry (default "Facing
 * camera"), Place icon/Remove icon buttons and a status line. Place calls [placeIconImage];
 * "POI not found"/"no position" outcomes surface as normal states. Changing the orientation while
 * an icon is placed calls [updateIconImageOrientation] live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconMarker3dSheet(session: IconMarkerSession, webView: WebView?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = session.anchorId,
            onValueChange = { session.anchorId = it },
            label = { Text(stringResource(R.string.field_anchor_poi_id)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = stringResource(R.string.orientation_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            ICON_MARKER_ORIENTATIONS.forEach { (value, labelRes) ->
                FilterChip(
                    selected = value == session.orientation,
                    onClick = { session.selectOrientation(webView, value) },
                    label = { Text(stringResource(labelRes)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { session.place(webView) }, enabled = session.anchorId.isNotBlank()) {
                Text(stringResource(R.string.icon_place))
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = { session.remove(webView) }, enabled = session.placedOnPoiId != null) {
                Text(stringResource(R.string.icon_remove))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val error = session.error
        if (error != null) {
            Text(text = error.resolve(), color = MaterialTheme.colorScheme.error)
        } else {
            Text(
                text = session.placedOnPoiId?.let { stringResource(R.string.icon_placed_on, it) }
                    ?: stringResource(R.string.icon_none),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
