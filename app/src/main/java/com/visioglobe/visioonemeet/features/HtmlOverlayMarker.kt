package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.visioglobe.visioonemeet.model.DemoMap
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.OnBridgeEvent
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState
import org.json.JSONObject

// html-overlay-marker — JS side: web/src/features/html-overlay-marker.js.
// See docs/features/html-overlay-marker.md.

/**
 * Pins the demo HTML badge on [anchorId] (`venue.createMarker`, with a `div` element the bridge JS builds). The
 * outcome comes back via `AndroidBridge.onHtmlMarkerStateChanged`, like every other badge state
 * change.
 */
private fun WebView.placeHtmlMarker(anchorId: String, text: String) = callBridge("placeHtmlMarker", anchorId, text)

/**
 * Replaces the placed badge's text — a plain DOM edit on the marker's element, no SDK call.
 */
private fun WebView.updateHtmlMarkerText(text: String) = callBridge("updateHtmlMarkerText", text)

/**
 * Shows/hides the placed badge (`venue.updateMarker(marker, { isVisible })`).
 */
private fun WebView.setHtmlMarkerVisible(isVisible: Boolean) = callBridge("setHtmlMarkerVisible", isVisible)

/**
 * Removes the placed badge (`venue.removeMarker`). A no-op on the JS side if nothing is placed.
 */
private fun WebView.removeHtmlMarker() = callBridge("removeHtmlMarker")

/**
 * The badge's whole state, as reported by `AndroidBridge.onHtmlMarkerStateChanged` after every
 * `html-overlay-marker` command and every tap on the badge. [status] names what just happened:
 * `"placed"`, `"tapped"`, `"text-updated"`, `"hidden"`, `"shown"`, `"removed"`,
 * `"text-required"`, `"anchor-not-found"`, `"anchor-has-no-position"` or `"error"` ([message]
 * carrying its text). [placedOnPoiId] is null while no badge is placed; [tapCount] counts taps on
 * the badge since it was last placed.
 */
private data class HtmlMarkerState(
    val status: String,
    val placedOnPoiId: String?,
    val isVisible: Boolean,
    val tapCount: Int,
    val message: String? = null,
)

/** Parses the JSON object argument of `AndroidBridge.onHtmlMarkerStateChanged`. */
private fun parseHtmlMarkerStatePayload(json: String): HtmlMarkerState {
    val root = JSONObject(json)
    return HtmlMarkerState(
        status = root.getString("status"),
        placedOnPoiId = if (root.isNull("placedOnPoiId")) null else root.getString("placedOnPoiId"),
        isVisible = root.getBoolean("isVisible"),
        tapCount = root.getInt("tapCount"),
        message = if (root.has("message")) root.getString("message") else null,
    )
}

/**
 * Screen-level values of the `html-overlay-marker` fields, kept across a close/reopen. The badge's
 * own state lives in the web page and comes back as [HtmlMarkerState].
 */
@Stable
private class HtmlMarkerFieldsSession(initialText: String) {
    var anchorId by mutableStateOf(DemoMap.ANCHOR_POI_ID)
    var text by mutableStateOf(initialText)
}

/**
 * The badge's state is the page's, pushed after every command and every tap via
 * `AndroidBridge.onHtmlMarkerStateChanged` and kept here, at screen level, because the visitor has
 * to close the panel to tap the badge on the map: the reopened panel renders from this snapshot.
 */
@Composable
fun HtmlOverlayMarkerScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val defaultText = stringResource(R.string.html_marker_default_text)
    val fields = remember { HtmlMarkerFieldsSession(defaultText) }
    var htmlMarkerState by remember { mutableStateOf<HtmlMarkerState?>(null) }
    OnBridgeEvent<BridgeEvent.HtmlMarkerStateChanged>(map) { htmlMarkerState = parseHtmlMarkerStatePayload(it.stateJson) }
    FeatureMapScreen(map, Feature.HtmlOverlayMarker, onBack) {
        HtmlOverlayMarkerSheet(fields, map.webView, htmlMarkerState)
    }
}

/**
 * "Anchor POI ID" and "Badge text" fields, Place
 * badge/Update text/Hide-Show badge/Remove badge buttons and a status line. Everything except the
 * two fields is rendered from [htmlMarkerState] — the JS side is the source of truth (validation
 * included), and the panel has to be closed to tap the badge on the map. The badge's taps reach
 * native as a `"tapped"` state with the running [HtmlMarkerState.tapCount].
 */
@Composable
private fun HtmlOverlayMarkerSheet(fields: HtmlMarkerFieldsSession, webView: WebView?, htmlMarkerState: HtmlMarkerState?) {
    val placedOnPoiId = htmlMarkerState?.placedOnPoiId
    val isVisible = htmlMarkerState?.isVisible ?: true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = fields.anchorId,
            onValueChange = { fields.anchorId = it },
            label = { Text(stringResource(R.string.field_anchor_poi_id)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = fields.text,
            onValueChange = { fields.text = it },
            label = { Text(stringResource(R.string.field_badge_text)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { webView?.placeHtmlMarker(fields.anchorId.trim(), fields.text) },
                enabled = fields.anchorId.isNotBlank(),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text(stringResource(R.string.badge_place))
            }
            OutlinedButton(
                onClick = { webView?.updateHtmlMarkerText(fields.text) },
                enabled = placedOnPoiId != null,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text(stringResource(R.string.action_update_text))
            }
            OutlinedButton(
                onClick = { webView?.setHtmlMarkerVisible(!isVisible) },
                enabled = placedOnPoiId != null,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text(stringResource(if (isVisible) R.string.badge_hide else R.string.badge_show))
            }
            OutlinedButton(
                onClick = { webView?.removeHtmlMarker() },
                enabled = placedOnPoiId != null,
            ) {
                Text(stringResource(R.string.badge_remove))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val errorMessage = when (htmlMarkerState?.status) {
            "text-required" -> stringResource(R.string.text_required)
            "anchor-not-found" -> stringResource(R.string.poi_not_found)
            "anchor-has-no-position" -> stringResource(R.string.poi_has_no_position)
            "error" -> htmlMarkerState.message ?: stringResource(R.string.badge_place_failed)
            else -> null
        }
        if (errorMessage != null) {
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        } else {
            val statusMessage = when (htmlMarkerState?.status) {
                "placed" -> stringResource(R.string.badge_placed_on, placedOnPoiId.orEmpty())
                "tapped" -> stringResource(R.string.badge_tapped, htmlMarkerState.tapCount)
                "text-updated" -> stringResource(R.string.text_updated)
                "hidden" -> stringResource(R.string.badge_hidden)
                "shown" -> stringResource(R.string.badge_shown)
                else -> stringResource(R.string.badge_none)
            }
            Text(text = statusMessage, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
