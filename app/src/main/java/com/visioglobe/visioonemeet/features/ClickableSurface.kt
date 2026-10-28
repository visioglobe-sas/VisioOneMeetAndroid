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

// clickable-surface — JS side: web/src/features/clickable-surface.js.
// See docs/features/clickable-surface.md.

/** Makes [placeId]'s surfaces interactive, or reverts them (`venue.updateSurface`'s `isInteractive`). */
private fun WebView.setSurfaceInteractive(placeId: String, interactive: Boolean) =
    callBridge("setSurfaceInteractive", placeId, interactive)

/**
 * A Place ID field plus "Enable"/"Disable" buttons calling [setSurfaceInteractive]. Once enabled,
 * the SDK itself handles the hover/selection color swap when the surface is tapped on the map;
 * this screen has no click listener of its own for that.
 */
@Composable
fun ClickableSurfaceScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val session = remember { PlaceIdSession(DemoMap.CLICKABLE_SURFACE_PLACE_ID) }
    FeatureMapScreen(map, Feature.ClickableSurface, onBack) {
        val webView = map.webView
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
                onClick = { webView?.setSurfaceInteractive(session.placeId.trim(), true) },
                enabled = session.placeId.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_enable))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { webView?.setSurfaceInteractive(session.placeId.trim(), false) },
                enabled = session.placeId.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_disable))
            }
        }
    }
}
