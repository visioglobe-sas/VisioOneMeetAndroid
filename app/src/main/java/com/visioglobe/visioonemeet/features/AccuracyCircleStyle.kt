package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.visioglobe.visioonemeet.R
import com.visioglobe.visioonemeet.bridge.callBridge
import com.visioglobe.visioonemeet.model.DemoMap
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.components.ColorChip
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState
import kotlin.math.roundToInt

// accuracy-circle-style — JS side: web/src/features/accuracy-circle-style.js.
// See docs/features/accuracy-circle-style.md. The moving position comes from simulated-position
// (SimulatedPosition.kt).

/**
 * Recolors the tracked position's precision circle (never the arrow icon at its center)
 * (`view.updatePositionTrackerGraphicOptions({ color, opacity })`). Applied at the next
 * `injectTrackedPosition`; the JS side also re-applies it on every tracking (re)start, since the
 * SDK resets it whenever tracking stops. [onApplied] runs only if the SDK accepted the style (the
 * JS method returns `false` when it throws on an invalid color).
 */
private fun WebView.setPositionTrackerStyle(color: String, opacity: Float, onApplied: () -> Unit) {
    // Rounded to one decimal so 0.1-step slider values reach the SDK as e.g. 0.3, not 0.30000001.
    val roundedOpacity = (opacity * 10).roundToInt() / 10.0
    callBridge("setPositionTrackerStyle", color, roundedOpacity) { result -> if (result == "true") onApplied() }
}

/**
 * The `accuracy-circle-style` selection, hoisted at screen level (it outlives the bottom sheet,
 * which is disposed on every close — the SDK has no getter to read it back from). Defaults match
 * the SDK's own (`#0094f0`, 0.3).
 */
private data class PositionTrackerStyle(
    val color: String = POSITION_TRACKER_DEFAULT_COLOR,
    val opacity: Float = POSITION_TRACKER_DEFAULT_OPACITY,
)

/** The SDK's default position tracker color/opacity (reset to these whenever tracking stops). */
private const val POSITION_TRACKER_DEFAULT_COLOR = "#0094f0"
private const val POSITION_TRACKER_DEFAULT_OPACITY = 0.3f

/**
 * Opacity slider bounds. 0.1 rather than 0: the SDK validates opacity with a falsy check, so `0`
 * would skip validation entirely.
 */
private const val POSITION_TRACKER_MIN_OPACITY = 0.1f
private const val POSITION_TRACKER_MAX_OPACITY = 1f

private const val ACCURACY_CIRCLE_STYLE_DEFAULT_RADIUS_METERS = 6f

/** Color presets offered for the tracked position (hex, label resource). */
private val POSITION_TRACKER_COLOR_PRESETS = listOf(
    POSITION_TRACKER_DEFAULT_COLOR to R.string.accuracy_circle_style_color_default,
    "#E53935" to R.string.color_red,
    "#43A047" to R.string.color_green,
    "#8E24AA" to R.string.color_purple,
)

/**
 * The tracked-position simulation, pre-filled with two POIs of the demo map and a 6 m radius (a
 * moving tracked position is needed to see its style; only the circle is recolored, not the center
 * arrow), plus color preset chips and an opacity `Slider` (0.1-1.0, step 0.1). Every change calls
 * [setPositionTrackerStyle] immediately, running or not: the SDK applies it at the next inject
 * (every tick while running), and the JS side re-applies it on each Start since the SDK resets it
 * on every stop. The selection only changes once the SDK has accepted the new style.
 */
@Composable
fun AccuracyCircleStyleScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val session = remember {
        TrackedPositionSession(
            initialOriginId = DemoMap.TRACKING_FROM,
            initialDestinationId = DemoMap.TRACKING_TO,
            initialRadiusMeters = ACCURACY_CIRCLE_STYLE_DEFAULT_RADIUS_METERS,
        )
    }
    var style by remember { mutableStateOf(PositionTrackerStyle()) }
    TrackedPositionSimulationHost(session, map)
    FeatureMapScreen(map, Feature.AccuracyCircleStyle, onBack) {
        val applyStyle = { newStyle: PositionTrackerStyle ->
            map.webView?.setPositionTrackerStyle(newStyle.color, newStyle.opacity) { style = newStyle }
        }
        TrackedPositionSimulationControls(
            session = session,
            webView = map.webView,
            extraContent = {
                // Kept compact on purpose: the camera follows the tracked position (centered in the
                // map), so the sheet must stay short enough for the precision circle to remain
                // visible above it while tweaking the style.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.color_label),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    FlowRow(modifier = Modifier.weight(1f)) {
                        POSITION_TRACKER_COLOR_PRESETS.forEach { (hex, labelRes) ->
                            ColorChip(
                                hex = hex,
                                name = stringResource(labelRes),
                                selected = hex.equals(style.color, ignoreCase = true),
                                onClick = { applyStyle(style.copy(color = hex)) },
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            R.string.accuracy_circle_style_opacity_label,
                            "%.1f".format(java.util.Locale.ROOT, style.opacity),
                        ),
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Slider(
                        value = style.opacity,
                        onValueChange = { value ->
                            val rounded = (value * 10).roundToInt() / 10f
                            if (rounded != style.opacity) applyStyle(style.copy(opacity = rounded))
                        },
                        valueRange = POSITION_TRACKER_MIN_OPACITY..POSITION_TRACKER_MAX_OPACITY,
                        // 0.1..1.0 in 0.1 steps = 8 intermediate stops.
                        steps = 8,
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        )
    }
}
