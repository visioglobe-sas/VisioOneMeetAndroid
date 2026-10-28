package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.visioglobe.visioonemeet.ui.FeatureMapState
import com.visioglobe.visioonemeet.ui.OnBridgeEvent
import com.visioglobe.visioonemeet.ui.components.ErrorText
import com.visioglobe.visioonemeet.ui.components.FromToFields
import com.visioglobe.visioonemeet.ui.components.UiText
import com.visioglobe.visioonemeet.ui.components.resolve
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONObject

// simulated-position — JS side: web/src/features/simulated-position.js.
// See docs/features/simulated-position.md. The tracked-position simulation below (session, loop,
// controls) is also used by camera-lock-on-position, geofencing and accuracy-circle-style.

// The interpolation loop: how often a new point is injected, and how much of the
// origin-destination segment (as a 0..1 fraction) is covered per tick — ~7.5s to walk one way at
// these values.
private const val SIMULATED_POSITION_INTERVAL_MS = 150L
private const val SIMULATED_POSITION_STEP = 0.02
private const val SIMULATED_POSITION_MIN_RADIUS_METERS = 1f
private const val SIMULATED_POSITION_MAX_RADIUS_METERS = 20f
private const val SIMULATED_POSITION_DEFAULT_RADIUS_METERS = 5f

/**
 * Resolves the WGS84 position of [originId]/[destinationId] in one round trip. The result arrives
 * asynchronously via `AndroidBridge.onPositionsResolved`, echoing [requestId] back so the caller
 * can match the response to this particular call.
 */
private fun WebView.resolvePositions(requestId: Int, originId: String, destinationId: String) =
    callBridge("resolvePositions", requestId, originId, destinationId)

/**
 * Injects/updates the simulated tracked position and its accuracy circle
 * (`view.injectTrackedPosition`, after setting `view.allowTracking = true` on the first call only
 * — re-setting it re-locks the camera). [precisionCircleRadiusMeters] is the accuracy circle's
 * radius, in meters.
 */
private fun WebView.injectTrackedPosition(latitude: Double, longitude: Double, precisionCircleRadiusMeters: Double) =
    callBridge("injectTrackedPosition", latitude, longitude, precisionCircleRadiusMeters)

/**
 * Removes the marker/circle injected by [injectTrackedPosition] (`view.allowTracking = false` —
 * there is no dedicated stop method on the SDK).
 */
private fun WebView.stopTrackedPosition() = callBridge("stopTrackedPosition")

/** A WGS84 position resolved for a POI, as reported by `AndroidBridge.onPositionsResolved`. */
data class ResolvedPosition(val latitude: Double, val longitude: Double)

/**
 * Parses one of the two JSON arguments of `AndroidBridge.onPositionsResolved`, e.g.
 * `{"latitude":48.858,"longitude":2.294}` or the literal string `"null"` (`JSON.stringify(null)`
 * on the JS side) when the POI id didn't resolve to a position.
 */
internal fun parseResolvedPosition(json: String): ResolvedPosition? {
    if (json == "null") return null
    val root = JSONObject(json)
    return ResolvedPosition(latitude = root.getDouble("latitude"), longitude = root.getDouble("longitude"))
}

/**
 * Screen-level state of a tracked-position simulation (`simulated-position`,
 * `camera-lock-on-position`, `geofencing`, `accuracy-circle-style`): the Origin/Destination POI
 * IDs, the accuracy radius, the running flag and the resolved segment being walked.
 *
 * It is created once per feature screen and never inside the FAB's bottom sheet: the sheet's
 * content is disposed on every close, and the simulation must keep running while the sheet is
 * closed so the visitor can pan/zoom the map around the moving position. The loop itself lives in
 * [TrackedPositionSimulationHost], also composed at screen level, so there is exactly one loop per
 * screen whatever the number of sheet open/close cycles; [TrackedPositionSimulationControls] only
 * reads/writes this object. Leaving the screen disposes both (and the WebView), which stops the
 * simulation.
 *
 * [start] resolves both POI ids in one round trip via [resolvePositions]; the response is fed back
 * through [onPositionsResolved] by [TrackedPositionSimulationHost], which sees every bridge
 * response even while the sheet is closed. A `null` slot ("POI not found") surfaces [error]
 * instead of starting anything.
 */
@Stable
class TrackedPositionSession(
    initialOriginId: String = "",
    initialDestinationId: String = "",
    initialRadiusMeters: Float = SIMULATED_POSITION_DEFAULT_RADIUS_METERS,
) {
    var originId by mutableStateOf(initialOriginId)
    var destinationId by mutableStateOf(initialDestinationId)
    var radiusMeters by mutableFloatStateOf(initialRadiusMeters)
    var isRunning by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set
    internal var activePositions by mutableStateOf<Pair<ResolvedPosition, ResolvedPosition>?>(null)
        private set

    // `requestId` guards against a stale response from a superseded request overwriting the outcome
    // of a more recent one (both are near-instant in practice, but this costs nothing).
    private var pendingRequestId: Int? = null

    fun start(webView: WebView?) {
        if (isRunning) return
        error = null
        val requestId = nextBridgeRequestId()
        pendingRequestId = requestId
        webView?.resolvePositions(requestId, originId.trim(), destinationId.trim())
    }

    fun stop() {
        isRunning = false
        activePositions = null
        pendingRequestId = null
    }

    internal fun onPositionsResolved(event: BridgeEvent.PositionsResolved) {
        if (event.requestId != pendingRequestId) return
        pendingRequestId = null
        val origin = parseResolvedPosition(event.originJson)
        val destination = parseResolvedPosition(event.destinationJson)
        if (origin == null || destination == null) {
            error = UiText(R.string.poi_not_found)
            isRunning = false
        } else {
            error = null
            activePositions = origin to destination
            isRunning = true
        }
    }
}

/**
 * Drives a [TrackedPositionSession]; composed at screen level, never inside the bottom sheet.
 * Feeds `onPositionsResolved` responses to the session, then, while it runs, repeatedly calls
 * [injectTrackedPosition] with a point linearly interpolated between origin and destination,
 * ping-ponging back and forth — reading [TrackedPositionSession.radiusMeters] fresh on every tick,
 * so moving the slider while running changes the radius on the *next* tick with no restart needed.
 * Stop (or leaving the screen, which cancels this effect and tears down the WebView) calls
 * [stopTrackedPosition] via the effect's `finally` block. [onRunningChanged] reports every
 * start/stop transition (e.g. geofencing's zone resolve/clear); [onTick] runs with every injected
 * point.
 */
@Composable
internal fun TrackedPositionSimulationHost(
    session: TrackedPositionSession,
    map: FeatureMapState,
    onRunningChanged: (Boolean) -> Unit = {},
    onTick: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
) {
    val currentOnRunningChanged by rememberUpdatedState(onRunningChanged)
    val currentOnTick by rememberUpdatedState(onTick)
    val webView = map.webView

    OnBridgeEvent<BridgeEvent.PositionsResolved>(map) { session.onPositionsResolved(it) }

    LaunchedEffect(session.isRunning) { currentOnRunningChanged(session.isRunning) }

    LaunchedEffect(session.isRunning, session.activePositions, webView) {
        val (origin, destination) = session.activePositions ?: return@LaunchedEffect
        val view = webView ?: return@LaunchedEffect
        if (!session.isRunning) return@LaunchedEffect

        var fraction = 0.0
        var direction = 1.0
        try {
            while (isActive) {
                val latitude = origin.latitude + (destination.latitude - origin.latitude) * fraction
                val longitude = origin.longitude + (destination.longitude - origin.longitude) * fraction
                view.injectTrackedPosition(latitude, longitude, session.radiusMeters.toDouble())
                currentOnTick(latitude, longitude)
                delay(SIMULATED_POSITION_INTERVAL_MS)
                fraction += direction * SIMULATED_POSITION_STEP
                when {
                    fraction >= 1.0 -> {
                        fraction = 1.0
                        direction = -1.0
                    }
                    fraction <= 0.0 -> {
                        fraction = 0.0
                        direction = 1.0
                    }
                }
            }
        } finally {
            view.stopTrackedPosition()
        }
    }
}

/**
 * Origin/Destination POI ID fields, a radius `Slider` (1-20m,
 * [SIMULATED_POSITION_DEFAULT_RADIUS_METERS] by default) and a Start/Stop toggle button, bound to
 * a screen-level [TrackedPositionSession] (see its kdoc for why it isn't held here), so reopening
 * the sheet shows the current state. Shared by the four tracked-position screens; [extraContent]
 * renders below the Start/Stop button and its error message (nothing for `simulated-position`,
 * the camera-lock switch for `camera-lock-on-position`, etc.). [startEnabled] gates Start on
 * screen-specific input (geofencing's zone id).
 */
@Composable
internal fun TrackedPositionSimulationControls(
    session: TrackedPositionSession,
    webView: WebView?,
    startEnabled: Boolean = true,
    extraContent: @Composable () -> Unit = {},
) {
    val isRunning = session.isRunning
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        FromToFields(
            from = session.originId,
            onFromChange = { session.originId = it },
            to = session.destinationId,
            onToChange = { session.destinationId = it },
            enabled = !isRunning,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.tracking_radius_label, session.radiusMeters.roundToInt()))
        Slider(
            value = session.radiusMeters,
            onValueChange = { session.radiusMeters = it },
            valueRange = SIMULATED_POSITION_MIN_RADIUS_METERS..SIMULATED_POSITION_MAX_RADIUS_METERS,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { if (isRunning) session.stop() else session.start(webView) },
            enabled = isRunning ||
                (session.originId.isNotBlank() && session.destinationId.isNotBlank() && startEnabled),
        ) {
            Text(stringResource(if (isRunning) R.string.tracking_stop else R.string.tracking_start))
        }
        ErrorText(session.error?.resolve())
        extraContent()
    }
}

/** The tracked-position simulation with no extra control. */
@Composable
fun SimulatedPositionScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val session = remember {
        TrackedPositionSession(initialOriginId = DemoMap.TRACKING_FROM, initialDestinationId = DemoMap.TRACKING_TO)
    }
    TrackedPositionSimulationHost(session, map)
    FeatureMapScreen(map, Feature.SimulatedPosition, onBack) {
        TrackedPositionSimulationControls(session, map.webView)
    }
}
