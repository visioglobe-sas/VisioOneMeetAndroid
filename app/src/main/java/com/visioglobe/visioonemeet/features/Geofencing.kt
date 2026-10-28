package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

// geofencing — JS side: web/src/features/geofencing.js. See docs/features/geofencing.md.
// The moving position comes from simulated-position (SimulatedPosition.kt).

/**
 * Resolves [placeId] to a POI and caches its surfaces as the zone geofencing checks against
 * (`venue.pois.find` + `poi.surfaces` — no dedicated SDK geofence concept). The result — 'found',
 * 'not-found', or 'no-surface' — arrives asynchronously via `AndroidBridge.onZoneResolved`,
 * echoing [requestId] back.
 */
private fun WebView.resolveZone(requestId: Int, placeId: String) = callBridge("resolveZone", requestId, placeId)

/** Reverts the alert color applied while the tracked position was inside the zone, and forgets it. */
private fun WebView.clearZone() = callBridge("clearZone")

/**
 * Tests the given position against the zone resolved by [resolveZone] (a hand-rolled
 * point-in-polygon check against `Surface.positions` — there is no SDK method for this). Called on
 * every tick of the tracked-position loop, right after `injectTrackedPosition`. The current
 * inside/outside state is reported back via `AndroidBridge.onGeofenceStateChanged`.
 */
private fun WebView.checkGeofence(latitude: Double, longitude: Double) =
    callBridge("checkGeofence", latitude, longitude)

/**
 * Screen-level state for `geofencing`, besides the shared tracked-position session: the
 * "Zone POI ID" field, its resolution outcome and the live inside/outside state — held outside
 * the bottom sheet for the same reason as [TrackedPositionSession].
 */
@Stable
private class GeofencingSession {
    var zoneId by mutableStateOf(DemoMap.GEOFENCE_ZONE_ID)
    var zoneError by mutableStateOf<UiText?>(null)
        private set
    var isInsideZone by mutableStateOf(false)
    private var pendingZoneRequestId: Int? = null
    private var isZoneRequested = false

    /**
     * Start resolves the zone once via [resolveZone]; Stop calls [clearZone] (reverting the zone's
     * color and forgetting it, so restarting always begins from a clean, unresolved zone). The
     * initial "not running" report, before any Start, clears nothing: the page may not even be
     * loaded yet.
     */
    fun onRunningChanged(webView: WebView?, running: Boolean) {
        if (running) {
            zoneError = null
            val requestId = nextBridgeRequestId()
            pendingZoneRequestId = requestId
            isZoneRequested = true
            webView?.resolveZone(requestId, zoneId.trim())
        } else if (isZoneRequested) {
            pendingZoneRequestId = null
            isZoneRequested = false
            webView?.clearZone()
        }
    }

    // `requestId` guards against a stale response the same way positionsResolved does.
    fun onZoneResolved(event: BridgeEvent.ZoneResolved) {
        if (event.requestId != pendingZoneRequestId) return
        pendingZoneRequestId = null
        zoneError = when (event.status) {
            "not-found" -> UiText(R.string.zone_not_found)
            "no-surface" -> UiText(R.string.zone_no_surface)
            else -> null
        }
    }
}

/**
 * Every tick of the tracked-position loop also calls [checkGeofence] with the freshly interpolated
 * position (no separate polling), whose result is reported back live via
 * `AndroidBridge.onGeofenceStateChanged` — the JS side itself recolors the zone's surface(s) as a
 * visual alert on a state transition, so the alert keeps working while the sheet is closed. The
 * panel adds the "Zone POI ID" field — Start is disabled until it is non-blank — and the live
 * "Inside zone"/"Outside zone" state.
 */
@Composable
fun GeofencingScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val tracking = remember {
        TrackedPositionSession(initialOriginId = DemoMap.TRACKING_FROM, initialDestinationId = DemoMap.TRACKING_TO)
    }
    val session = remember { GeofencingSession() }
    OnBridgeEvent<BridgeEvent.ZoneResolved>(map) { session.onZoneResolved(it) }
    OnBridgeEvent<BridgeEvent.GeofenceStateChanged>(map) { session.isInsideZone = it.isInside }
    TrackedPositionSimulationHost(
        session = tracking,
        map = map,
        onRunningChanged = { running -> session.onRunningChanged(map.webView, running) },
        onTick = { latitude, longitude -> map.webView?.checkGeofence(latitude, longitude) },
    )
    FeatureMapScreen(map, Feature.Geofencing, onBack) {
        val isRunning = tracking.isRunning
        TrackedPositionSimulationControls(
            session = tracking,
            webView = map.webView,
            startEnabled = session.zoneId.isNotBlank(),
            extraContent = {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = session.zoneId,
                    onValueChange = { session.zoneId = it },
                    label = { Text(stringResource(R.string.field_zone_poi_id)) },
                    singleLine = true,
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth(),
                )
                val zoneError = session.zoneError
                when {
                    zoneError != null -> ErrorText(zoneError.resolve())
                    isRunning -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(if (session.isInsideZone) R.string.zone_inside else R.string.zone_outside))
                    }
                }
            },
        )
    }
}
