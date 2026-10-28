package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.visioglobe.visioonemeet.R
import com.visioglobe.visioonemeet.bridge.callBridge
import com.visioglobe.visioonemeet.model.DemoMap
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.components.ColorChip
import com.visioglobe.visioonemeet.ui.components.ItineraryControls
import com.visioglobe.visioonemeet.ui.components.rememberItinerarySession
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState
import org.json.JSONObject

// custom-navigation-trace — JS side: web/src/features/custom-navigation-trace.js.
// See docs/features/custom-navigation-trace.md. The route itself comes from compute-navigation
// (ComputeNavigation.kt).

/**
 * A `NavigationTraceUpdateOptions` payload (colors only — `displayMode`/`thickness` can't be
 * changed after a trace is created), one of [NAVIGATION_TRACE_PRESETS], with the localized name
 * shown on its chip.
 */
private data class NavigationTraceColors(
    @StringRes val nameRes: Int,
    val progressColor: String,
    val progressOutlineColor: String,
    val progressFutureColor: String,
    val previewColor: String,
    val previewOutlineColor: String,
)

/**
 * The color catalogue, each a full [NavigationTraceColors] payload applied via
 * [updateNavigationTraceStyle]. `visioglobeBlue` restates the SDK's own documented `Line` defaults
 * (`progressColor` `#0094F0`, the inactive/preview segments `#C5C5C5`) as an explicit preset rather
 * than a fake "reset" — there is no SDK call to revert to default once changed.
 */
private val NAVIGATION_TRACE_PRESETS = linkedMapOf(
    "visioglobeBlue" to NavigationTraceColors(
        nameRes = R.string.color_visioglobe_blue,
        progressColor = "#0094F0",
        progressOutlineColor = "#FFFFFF",
        progressFutureColor = "#C5C5C5",
        previewColor = "#C5C5C5",
        previewOutlineColor = "#FFFFFF",
    ),
    "brandRed" to NavigationTraceColors(
        nameRes = R.string.color_red,
        progressColor = "#E53935",
        progressOutlineColor = "#FFFFFF",
        progressFutureColor = "#F8C9C7",
        previewColor = "#F8C9C7",
        previewOutlineColor = "#FFFFFF",
    ),
    "brandGreen" to NavigationTraceColors(
        nameRes = R.string.color_green,
        progressColor = "#2E7D32",
        progressOutlineColor = "#FFFFFF",
        progressFutureColor = "#C8E6C9",
        previewColor = "#C8E6C9",
        previewOutlineColor = "#FFFFFF",
    ),
    "brandPurple" to NavigationTraceColors(
        nameRes = R.string.color_purple,
        progressColor = "#8E24AA",
        progressOutlineColor = "#FFFFFF",
        progressFutureColor = "#E1BEE7",
        previewColor = "#E1BEE7",
        previewOutlineColor = "#FFFFFF",
    ),
)

/**
 * Restyles the trace currently drawn by `computeNavigation` (`venue.updateNavigationTrace()`). A
 * no-op if no trace is currently displayed.
 */
private fun WebView.updateNavigationTraceStyle(colors: NavigationTraceColors) {
    val options = JSONObject().apply {
        put("progressColor", colors.progressColor)
        put("progressOutlineColor", colors.progressOutlineColor)
        put("progressFutureColor", colors.progressFutureColor)
        put("previewColor", colors.previewColor)
        put("previewOutlineColor", colors.previewOutlineColor)
    }
    callBridge("updateNavigationTraceStyle", options)
}

/**
 * The itinerary form plus one color chip per [NAVIGATION_TRACE_PRESETS] entry, applying
 * [updateNavigationTraceStyle] to whatever trace is displayed (a no-op if none is). "Itinerary"
 * also applies the selected preset right after drawing the trace: the SDK only builds a trace in
 * its own default look, so this second call is always needed for anything but the default. The
 * selected preset is screen-level state, like the form.
 */
@Composable
fun CustomNavigationTraceScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val session = rememberItinerarySession(map, DemoMap.ITINERARY_FROM, DemoMap.ITINERARY_TO)
    var tracePresetKey by remember { mutableStateOf(NAVIGATION_TRACE_PRESETS.keys.first()) }
    FeatureMapScreen(map, Feature.CustomNavigationTrace, onBack) {
        ItineraryControls(
            session = session,
            onItinerary = { origin, destination ->
                map.webView?.let { view ->
                    view.computeNavigation(origin, destination, false)
                    view.updateNavigationTraceStyle(NAVIGATION_TRACE_PRESETS.getValue(tracePresetKey))
                }
            },
            onClear = { map.webView?.clearNavigation() },
            extraContent = {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.trace_color_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(modifier = Modifier.fillMaxWidth()) {
                    NAVIGATION_TRACE_PRESETS.forEach { (presetKey, colors) ->
                        ColorChip(
                            hex = colors.progressColor,
                            name = stringResource(colors.nameRes),
                            selected = presetKey == tracePresetKey,
                            onClick = {
                                tracePresetKey = presetKey
                                map.webView?.updateNavigationTraceStyle(colors)
                            },
                        )
                    }
                }
            },
        )
    }
}
