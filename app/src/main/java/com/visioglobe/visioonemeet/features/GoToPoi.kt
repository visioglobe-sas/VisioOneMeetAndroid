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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.visioglobe.visioonemeet.R
import com.visioglobe.visioonemeet.bridge.callBridge
import com.visioglobe.visioonemeet.model.DemoMap
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.components.PlaceIdSession
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState

// goto-poi — JS side: web/src/features/goto-poi.js. See docs/features/goto-poi.md.

/**
 * Centers/zooms the camera on the POI matching [placeId] (`view.goToPOI`) and highlights its
 * surfaces. Also used by `3d-icon-marker` and `custom-text-label` to bring their anchor POI's floor
 * into view.
 */
internal fun WebView.goToPlace(placeId: String) = callBridge("goToPlace", placeId)

/** Reverts the highlight applied by [goToPlace], without moving the camera back. */
internal fun WebView.clearPlace() = callBridge("clearPlace")

@Composable
fun GoToPoiScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val session = remember { PlaceIdSession(DemoMap.GOTO_POI_ID) }
    FeatureMapScreen(map, Feature.GoToPoi, onBack) {
        GoToPoiSheet(session, map.webView)
    }
}

/**
 * A Place ID field plus "Go" (centers the camera on that POI via [goToPlace]) and "Clear"
 * (undoes the highlight via [clearPlace], camera stays put).
 */
@Composable
private fun GoToPoiSheet(session: PlaceIdSession, webView: WebView?) {
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
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = { webView?.goToPlace(session.placeId.trim()) },
            enabled = session.placeId.isNotBlank(),
        ) {
            Text(stringResource(R.string.action_go))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = { webView?.clearPlace() }) {
            Text(stringResource(R.string.action_clear))
        }
    }
}
