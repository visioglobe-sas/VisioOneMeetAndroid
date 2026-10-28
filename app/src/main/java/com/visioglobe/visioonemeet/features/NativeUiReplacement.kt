package com.visioglobe.visioonemeet.features

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.visioglobe.visioonemeet.R
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState

// native-ui-replacement — no JS of its own: it reuses ui-part-visibility's setUiPartVisible and
// floor-selector's floor list (UiPartVisibility.kt, FloorSelector.kt).
// See docs/features/native-ui-replacement.md.

/**
 * Hides the SDK's own default floor-selector widget as soon as the map loads, rather than only
 * once the panel's switch is touched. The switch ("Show SDK's own floor selector", screen-level
 * state, starting off to match) calls the same `setUiPartVisible`, so the SDK's widget stays
 * hidden unless the visitor flips it on to compare both driving the same floor.
 *
 * Below the switch, floor-selector's panel is reused as-is, unaware of this switch's state: the
 * app's own native floor list stays fully functional regardless of it, demonstrating that it is a
 * complete replacement for the SDK's widget.
 */
@Composable
fun NativeUiReplacementScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val floorSelector = rememberFloorSelectorState(map)
    var showSdkFloorSelector by remember { mutableStateOf(false) }
    LaunchedEffect(map.isMapReady) {
        if (map.isMapReady) map.webView?.setUiPartVisible("floorSelector", false)
    }
    FeatureMapScreen(map, Feature.NativeUiReplacement, onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.native_ui_replacement_show_sdk_selector_label),
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = showSdkFloorSelector,
                    onCheckedChange = { isVisible ->
                        showSdkFloorSelector = isVisible
                        map.webView?.setUiPartVisible("floorSelector", isVisible)
                    },
                )
            }
        }
        FloorSelectorSheet(map.webView, floorSelector)
    }
}
