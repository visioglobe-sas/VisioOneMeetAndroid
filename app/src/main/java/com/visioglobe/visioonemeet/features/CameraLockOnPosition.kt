package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Switch
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
import com.visioglobe.visioonemeet.bridge.BridgeEvent
import com.visioglobe.visioonemeet.bridge.callBridge
import com.visioglobe.visioonemeet.model.DemoMap
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.OnBridgeEvent
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState

// camera-lock-on-position — JS side: web/src/features/camera-lock-on-position.js.
// See docs/features/camera-lock-on-position.md. The moving position comes from simulated-position
// (SimulatedPosition.kt).

/** Binds/unbinds the camera's focus to the tracked position (`view.lockCameraPositionOnTracking`). */
private fun WebView.setCameraLockOnPosition(locked: Boolean) = callBridge("setCameraLockOnPosition", locked)

/**
 * The tracked-position simulation (a moving position is needed to demonstrate a camera lock on it)
 * plus a "Recenter on me" `Switch` that flips `view.lockCameraPositionOnTracking` via
 * [setCameraLockOnPosition] — the GPS-app-style behavior this feature demonstrates. The switch is
 * disabled while no simulation is running (nothing to lock onto). It never holds its own value:
 * `isCameraLocked` mirrors the SDK's flag, pushed back by the page on every
 * `trackingon`/`trackingoff`/`trackingchanged` event via `AndroidBridge.onCameraLockChanged` and
 * kept at screen level, so it shows on right after Start (the SDK locks by itself when tracking
 * starts), off after Stop, and off when the visitor pans the map while locked (the simulation keeps
 * running with the sheet closed, so the map can be panned meanwhile).
 */
@Composable
fun CameraLockOnPositionScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val session = remember {
        TrackedPositionSession(initialOriginId = DemoMap.TRACKING_FROM, initialDestinationId = DemoMap.TRACKING_TO)
    }
    var isCameraLocked by remember { mutableStateOf(false) }
    OnBridgeEvent<BridgeEvent.CameraLockChanged>(map) { isCameraLocked = it.locked }
    TrackedPositionSimulationHost(session, map)
    FeatureMapScreen(map, Feature.CameraLockOnPosition, onBack) {
        TrackedPositionSimulationControls(
            session = session,
            webView = map.webView,
            extraContent = {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = stringResource(R.string.camera_lock_switch_label), modifier = Modifier.weight(1f))
                    Switch(
                        checked = isCameraLocked,
                        enabled = session.isRunning,
                        onCheckedChange = { locked -> map.webView?.setCameraLockOnPosition(locked) },
                    )
                }
            },
        )
    }
}
