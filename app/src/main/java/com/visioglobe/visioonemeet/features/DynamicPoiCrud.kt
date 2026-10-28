package com.visioglobe.visioonemeet.features

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
import com.visioglobe.visioonemeet.ui.components.ErrorText
import com.visioglobe.visioonemeet.ui.components.UiText
import com.visioglobe.visioonemeet.ui.components.resolve
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState
import org.json.JSONObject

// dynamic-poi-crud — JS side: web/src/features/dynamic-poi-crud.js.
// See docs/features/dynamic-poi-crud.md.

/**
 * Creates a POI at runtime and attaches a Label to it (copying its position from [anchorId]'s own
 * position) (`venue.createPOI` + `venue.createLabel`). The result — success, or one of the
 * "anchor not found"/"anchor has no position"/"duplicate id" normal-state outcomes — arrives
 * asynchronously via `AndroidBridge.onDynamicPoiCreated`, echoing [requestId] back.
 */
private fun WebView.createDynamicPoi(requestId: Int, newId: String, anchorId: String, labelText: String) =
    callBridge("createDynamicPoi", requestId, newId, anchorId, labelText)

/**
 * Updates the tracked dynamic POI's label text (`venue.updateLabel`) — the actual "modify" story
 * for a dynamic POI, since `venue.updatePOI` itself can only change categories, never anything
 * visual. Fire-and-forget.
 */
private fun WebView.updateDynamicPoiLabel(text: String) = callBridge("updateDynamicPoiLabel", text)

/**
 * Removes the tracked dynamic POI (`venue.removePOI`, which cascades to remove the attached label
 * from the view too — no separate label removal needed). Fire-and-forget.
 */
private fun WebView.removeDynamicPoi() = callBridge("removeDynamicPoi")

/**
 * The JSON object argument of `AndroidBridge.onDynamicPoiCreated`, e.g.
 * `{"status":"created","id":"demo-poi-1","text":"New POI"}` or `{"status":"anchor-not-found"}`.
 * [status] is one of: `"created"` ([id]/[text] then non-null — the tracked POI's id and its label's
 * text), `"anchor-not-found"`, `"anchor-has-no-position"` (the anchor POI has no
 * label/marker/image/surface to copy a position from), `"duplicate-id"` (`venue.createPOI` threw
 * `POIAlreadyExistsError`), or `"error"` (any other failure, [message] carrying its text).
 */
private data class DynamicPoiCreationResult(
    val status: String,
    val id: String? = null,
    val text: String? = null,
    val message: String? = null,
)

private fun parseDynamicPoiCreationPayload(json: String): DynamicPoiCreationResult {
    val root = JSONObject(json)
    return DynamicPoiCreationResult(
        status = root.getString("status"),
        id = if (root.has("id")) root.getString("id") else null,
        text = if (root.has("text")) root.getString("text") else null,
        message = if (root.has("message")) root.getString("message") else null,
    )
}

/**
 * Screen-level state of `dynamic-poi-crud`: the three fields and the POI currently on the map
 * ([trackedPoiId]/[trackedLabelText]), so after a close/reopen Update text and Remove still act on
 * it and Create stays disabled (a second Create would otherwise replace the tracked POI on the JS
 * side and leave the first one on the map for good). Only one dynamic POI is tracked at a time — a
 * demo-side simplification, not an SDK limitation.
 */
@Stable
private class DynamicPoiCrudSession(initialLabelText: String) {
    var newId by mutableStateOf(DemoMap.DYNAMIC_POI_NEW_ID)
    var anchorId by mutableStateOf(DemoMap.ANCHOR_POI_ID)
    var labelText by mutableStateOf(initialLabelText)
    var trackedPoiId by mutableStateOf<String?>(null)
        private set
    var trackedLabelText by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set
    private var pendingRequestId: Int? = null

    fun create(webView: WebView?) {
        error = null
        val requestId = nextBridgeRequestId()
        pendingRequestId = requestId
        webView?.createDynamicPoi(requestId, newId.trim(), anchorId.trim(), labelText.trim())
    }

    fun updateText(webView: WebView?) {
        webView?.updateDynamicPoiLabel(labelText.trim())
        trackedLabelText = labelText.trim()
    }

    fun remove(webView: WebView?) {
        webView?.removeDynamicPoi()
        trackedPoiId = null
        trackedLabelText = null
        error = null
    }

    fun onCreated(event: BridgeEvent.DynamicPoiCreated) {
        if (event.requestId != pendingRequestId) return
        pendingRequestId = null
        val response = parseDynamicPoiCreationPayload(event.resultJson)
        error = when (response.status) {
            "created" -> null
            "anchor-not-found" -> UiText(R.string.dynamic_poi_anchor_not_found)
            "anchor-has-no-position" -> UiText(R.string.dynamic_poi_anchor_no_position)
            "duplicate-id" -> UiText(R.string.dynamic_poi_duplicate_id)
            else -> response.message?.let { UiText(R.string.raw_message, listOf(it)) }
                ?: UiText(R.string.dynamic_poi_create_failed)
        }
        if (response.status == "created") {
            trackedPoiId = response.id
            trackedLabelText = response.text
        }
    }
}

@Composable
fun DynamicPoiCrudScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val defaultLabel = stringResource(R.string.dynamic_poi_default_label)
    val session = remember { DynamicPoiCrudSession(defaultLabel) }
    OnBridgeEvent<BridgeEvent.DynamicPoiCreated>(map) { session.onCreated(it) }
    FeatureMapScreen(map, Feature.DynamicPoiCrud, onBack) {
        DynamicPoiCrudSheet(session, map.webView)
    }
}

/**
 * "New POI ID", "Anchor POI ID" and "Label text" fields plus Create/Update text/Remove buttons.
 * The two ID fields are disabled while a POI is tracked. "Label text" stays editable: it seeds the
 * label created by Create, and its current value is what Update text sends via
 * [updateDynamicPoiLabel]. Create calls [createDynamicPoi]; any outcome other than `"created"`
 * (including `POIAlreadyExistsError` on a duplicate id) surfaces as a normal state. Remove calls
 * [removeDynamicPoi]; the SDK cascades the removal to the attached label.
 */
@Composable
private fun DynamicPoiCrudSheet(session: DynamicPoiCrudSession, webView: WebView?) {
    val hasTrackedPoi = session.trackedPoiId != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            text = if (hasTrackedPoi) {
                stringResource(R.string.dynamic_poi_created, session.trackedPoiId.orEmpty(), session.trackedLabelText.orEmpty())
            } else {
                stringResource(R.string.dynamic_poi_none)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = session.newId,
                onValueChange = { session.newId = it },
                label = { Text(stringResource(R.string.field_new_poi_id)) },
                singleLine = true,
                enabled = !hasTrackedPoi,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = session.anchorId,
                onValueChange = { session.anchorId = it },
                label = { Text(stringResource(R.string.field_anchor_poi_id)) },
                singleLine = true,
                enabled = !hasTrackedPoi,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = session.labelText,
            onValueChange = { session.labelText = it },
            label = { Text(stringResource(R.string.field_label_text)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { session.create(webView) },
                enabled = !hasTrackedPoi && session.newId.isNotBlank() && session.anchorId.isNotBlank() &&
                    session.labelText.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_create))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { session.updateText(webView) },
                enabled = hasTrackedPoi && session.labelText.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_update_text))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { session.remove(webView) }, enabled = hasTrackedPoi) {
                Text(stringResource(R.string.action_remove))
            }
        }
        ErrorText(session.error?.resolve())
    }
}
