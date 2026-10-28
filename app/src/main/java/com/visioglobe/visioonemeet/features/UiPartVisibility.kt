package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.visioglobe.visioonemeet.R
import com.visioglobe.visioonemeet.bridge.callBridge
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState

// ui-part-visibility — JS side: web/src/features/ui-part-visibility.js.
// See docs/features/ui-part-visibility.md. Also used by native-ui-replacement.

/**
 * Shows/hides one of the SDK's own default UI overlays (`view.setUIPartVisible`). [uiPart] must be
 * one of the SDK's exact, case-sensitive values — see [UI_PART_TOGGLES].
 */
internal fun WebView.setUiPartVisible(uiPart: String, isVisible: Boolean) =
    callBridge("setUIPartVisible", uiPart, isVisible)

/**
 * The 5 UI parts the SDK's `View.setUIPartVisible` accepts, paired with a human-readable label.
 * These string values are exact and case-sensitive — they are not free-form, the SDK defines no
 * others.
 */
private val UI_PART_TOGGLES = listOf(
    "floorSelector" to R.string.ui_part_floor_selector_label,
    "navigation" to R.string.ui_part_navigation_label,
    "poiDetails" to R.string.ui_part_poi_details_label,
    "search" to R.string.ui_part_search_label,
    "userTracking" to R.string.ui_part_user_tracking_label,
)

/**
 * Screen-level state: the visibility last set for each of the 5 parts, all visible at first,
 * matching the SDK's own default. Held outside the panel so its switches still match the map after
 * a close/reopen.
 */
@Composable
fun UiPartVisibilityScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val visibility = remember {
        mutableStateMapOf(*UI_PART_TOGGLES.map { (uiPart, _) -> uiPart to true }.toTypedArray())
    }
    FeatureMapScreen(map, Feature.UiPartVisibility, onBack) {
        UiPartVisibilitySheet(visibility, map.webView)
    }
}

/**
 * One switch per [UI_PART_TOGGLES] entry, each calling [setUiPartVisible] on toggle so the effect
 * is visible immediately on the map behind the panel.
 */
@Composable
private fun UiPartVisibilitySheet(visibility: SnapshotStateMap<String, Boolean>, webView: WebView?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        UI_PART_TOGGLES.forEach { (uiPart, labelRes) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(labelRes), modifier = Modifier.weight(1f))
                Switch(
                    checked = visibility[uiPart] == true,
                    onCheckedChange = { isVisible ->
                        visibility[uiPart] = isVisible
                        webView?.setUiPartVisible(uiPart, isVisible)
                    },
                )
            }
        }
    }
}
