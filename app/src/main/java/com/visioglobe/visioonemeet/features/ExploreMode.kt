package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.visioglobe.visioonemeet.ui.OnBridgeEvent
import com.visioglobe.visioonemeet.ui.components.CurrentOptionButtons
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState

// explore-mode — JS side: web/src/features/explore-mode.js. See docs/features/explore-mode.md.

/**
 * Switches the SDK's building-exploration mode (`view.currentExploreMode = mode`). [mode] must be
 * one of the SDK's exact, case-sensitive `ExploreMode` values — see [EXPLORE_MODE_OPTIONS].
 */
private fun WebView.setExploreMode(mode: String) = callBridge("setExploreMode", mode)

/**
 * The 3 explore modes the SDK's `View.currentExploreMode` accepts, paired with a human-readable
 * label. These string values are exact and case-sensitive — they are not free-form, the SDK
 * defines no others.
 */
private val EXPLORE_MODE_OPTIONS = listOf(
    "global" to R.string.explore_mode_option_global,
    "building" to R.string.explore_mode_option_building,
    "floor" to R.string.explore_mode_option_floor,
)

/**
 * One button per [EXPLORE_MODE_OPTIONS] entry, the active one highlighted. [setExploreMode] has no
 * direct response: the resulting mode is reported back, like any other explore-mode change
 * (including ones triggered by map interaction, not this call), via
 * `AndroidBridge.onExploreModeChanged`, also pushed once unprompted right after the map loads so
 * the control has a correct initial highlight. The highlight is never set optimistically from the
 * tap, so it stays correct when the mode changes from map interaction (a click while in
 * "building" mode, which the SDK switches to "floor" on its own). "Building" — the exploded
 * "carousel" view of the building's floors — needs no building to be open: the SDK falls back to
 * the venue's first one.
 */
@Composable
fun ExploreModeScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    var currentExploreMode by remember { mutableStateOf<String?>(null) }
    OnBridgeEvent<BridgeEvent.ExploreModeChanged>(map) { currentExploreMode = it.mode }
    FeatureMapScreen(map, Feature.ExploreMode, onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            CurrentOptionButtons(
                options = EXPLORE_MODE_OPTIONS.map { (mode, labelRes) -> mode to stringResource(labelRes) },
                current = currentExploreMode,
                onSelect = { mode -> map.webView?.setExploreMode(mode) },
            )
        }
    }
}
