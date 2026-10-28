package com.visioglobe.visioonemeet.features

import androidx.annotation.StringRes
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.visioglobe.visioonemeet.ui.components.ColorChip
import com.visioglobe.visioonemeet.ui.components.UiText
import com.visioglobe.visioonemeet.ui.components.resolve
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState
import org.json.JSONObject

// custom-text-label — JS side: web/src/features/custom-text-label.js.
// See docs/features/custom-text-label.md.

/** Font colors offered for the label (hex — the SDK only accepts hex / `rgb()` / `hsl()`). */
private val TEXT_LABEL_COLORS = listOf(
    "#E53935" to R.string.color_red,
    "#43A047" to R.string.color_green,
    "#1E88E5" to R.string.color_blue,
    "#212121" to R.string.color_dark,
)

/** A label size preset: [width]×[height] in meters, always sent together to the SDK. */
private data class TextLabelSize(@StringRes val labelRes: Int, val width: Int, val height: Int)

private val TEXT_LABEL_SIZES = listOf(
    TextLabelSize(R.string.size_small, width = 10, height = 2),
    TextLabelSize(R.string.size_medium, width = 16, height = 3),
    TextLabelSize(R.string.size_large, width = 22, height = 4),
)

/** Rotations offered for the label, in degrees (create-only in the SDK, see the doc). */
private val TEXT_LABEL_ROTATIONS = listOf(0, 45, 90)

/**
 * Places the demo text label on [anchorId] (`venue.createLabel`, owned by the anchor POI, at its position offset by
 * ~4 m). The result arrives asynchronously via `AndroidBridge.onTextLabelPlaced`, echoing
 * [requestId] back.
 */
private fun WebView.placeTextLabel(
    requestId: Int,
    anchorId: String,
    text: String,
    color: String,
    size: TextLabelSize,
    rotation: Int,
) {
    val options = JSONObject()
        .put("text", text)
        .put("color", color)
        .put("width", size.width)
        .put("height", size.height)
        .put("rotation", rotation)
    callBridge("placeTextLabel", requestId, anchorId, options)
}

/**
 * Restyles the placed label live (`venue.updateLabel`) with whatever subset of text/color/width+height [options] carries. Fire-and-forget.
 */
private fun WebView.updateTextLabel(options: JSONObject) = callBridge("updateTextLabel", options)

/**
 * Changes the placed label's rotation; the page removes
 * and recreates it (rotation can't go through `venue.updateLabel`). Fire-and-forget.
 */
private fun WebView.setTextLabelRotation(rotation: Int) = callBridge("setTextLabelRotation", rotation)

/**
 * Removes the placed label (`venue.removeLabel`). Fire-and-forget, and a no-op on the JS side if
 * nothing is placed.
 */
private fun WebView.removeTextLabel() = callBridge("removeTextLabel")

/**
 * Screen-level state of `custom-text-label`: the fields and style, and the POI the label is placed
 * on, so after a close/reopen Update text/Remove label and the live style controls still act on
 * the label on the map. [onPlaced] is fed every `onTextLabelPlaced` response at screen level; on
 * success it moves the camera to the anchor POI via goto-poi's `goToPlace` (a label only renders
 * while its POI's floor is displayed). The JS side validates text, anchor and position before
 * touching the label already placed, so a failed place leaves it on the map.
 */
@Stable
private class TextLabelSession(initialText: String) {
    var anchorId by mutableStateOf(DemoMap.ANCHOR_POI_ID)
    var text by mutableStateOf(initialText)
    var color by mutableStateOf(TEXT_LABEL_COLORS.first().first)
        private set
    var sizeIndex by mutableIntStateOf(1)
        private set
    var rotation by mutableIntStateOf(TEXT_LABEL_ROTATIONS.first())
        private set
    var placedOnPoiId by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set
    var status by mutableStateOf<UiText?>(null)
        private set
    private var pendingRequestId: Int? = null

    fun place(webView: WebView?) {
        status = null
        error = null
        val requestId = nextBridgeRequestId()
        pendingRequestId = requestId
        webView?.placeTextLabel(requestId, anchorId.trim(), text.trim(), color, TEXT_LABEL_SIZES[sizeIndex], rotation)
    }

    fun updateText(webView: WebView?) {
        error = null
        status = UiText(R.string.text_updated)
        webView?.updateTextLabel(JSONObject().put("text", text.trim()))
    }

    fun selectColor(webView: WebView?, hex: String) {
        color = hex
        if (placedOnPoiId != null) webView?.updateTextLabel(JSONObject().put("color", hex))
    }

    fun selectSize(webView: WebView?, index: Int) {
        sizeIndex = index
        val size = TEXT_LABEL_SIZES[index]
        if (placedOnPoiId != null) {
            webView?.updateTextLabel(JSONObject().put("width", size.width).put("height", size.height))
        }
    }

    fun selectRotation(webView: WebView?, degrees: Int) {
        rotation = degrees
        if (placedOnPoiId != null) webView?.setTextLabelRotation(degrees)
    }

    fun remove(webView: WebView?) {
        webView?.removeTextLabel()
        webView?.clearPlace()
        placedOnPoiId = null
        error = null
        status = UiText(R.string.label_removed)
    }

    fun onPlaced(webView: WebView?, event: BridgeEvent.TextLabelPlaced) {
        if (event.requestId != pendingRequestId) return
        pendingRequestId = null
        val response = parsePlacementPayload(event.resultJson)
        error = response.errorUiText(R.string.label_place_failed)
        if (response.status == "placed") {
            placedOnPoiId = response.poiId
            status = null
            response.poiId?.let { webView?.goToPlace(it) }
        }
    }
}

@Composable
fun CustomTextLabelScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val defaultText = stringResource(R.string.custom_text_label_default_text)
    val session = remember { TextLabelSession(defaultText) }
    OnBridgeEvent<BridgeEvent.TextLabelPlaced>(map) { session.onPlaced(map.webView, it) }
    FeatureMapScreen(map, Feature.CustomTextLabel, onBack) {
        CustomTextLabelSheet(session, map.webView)
    }
}

/**
 * "Anchor POI ID" and "Text" fields, one chip per
 * [TEXT_LABEL_COLORS] entry, segmented [TEXT_LABEL_SIZES]/[TEXT_LABEL_ROTATIONS] controls, Place
 * label/Update text/Remove label buttons and a status line, bound to the screen-level
 * [TextLabelSession]. Place calls [placeTextLabel]. While a label is placed, color/size changes
 * apply live via [updateTextLabel] (width and height always together) and a rotation change
 * recreates it via [setTextLabelRotation]. Text is validated on the JS side only
 * (`"text-required"`).
 */
@Composable
private fun CustomTextLabelSheet(session: TextLabelSession, webView: WebView?) {
    val isPlaced = session.placedOnPoiId != null
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
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = session.text,
            onValueChange = { session.text = it },
            label = { Text(stringResource(R.string.field_text)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = stringResource(R.string.color_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            TEXT_LABEL_COLORS.forEach { (hex, nameRes) ->
                ColorChip(
                    hex = hex,
                    name = stringResource(nameRes),
                    selected = hex == session.color,
                    onClick = { session.selectColor(webView, hex) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.size_label), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TEXT_LABEL_SIZES.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = index == session.sizeIndex,
                    onClick = { session.selectSize(webView, index) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TEXT_LABEL_SIZES.size),
                ) {
                    Text(stringResource(option.labelRes))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.rotation_label), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TEXT_LABEL_ROTATIONS.forEachIndexed { index, degrees ->
                SegmentedButton(
                    selected = degrees == session.rotation,
                    onClick = { session.selectRotation(webView, degrees) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TEXT_LABEL_ROTATIONS.size),
                ) {
                    Text("$degrees°")
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { session.place(webView) },
                enabled = session.anchorId.isNotBlank(),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text(stringResource(R.string.label_place))
            }
            OutlinedButton(
                onClick = { session.updateText(webView) },
                enabled = isPlaced && session.text.isNotBlank(),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text(stringResource(R.string.action_update_text))
            }
            OutlinedButton(onClick = { session.remove(webView) }, enabled = isPlaced) {
                Text(stringResource(R.string.label_remove))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val error = session.error
        if (error != null) {
            Text(text = error.resolve(), color = MaterialTheme.colorScheme.error)
        } else {
            Text(
                text = session.status?.resolve()
                    ?: session.placedOnPoiId?.let { stringResource(R.string.label_placed_on, it) }
                    ?: stringResource(R.string.label_none),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
