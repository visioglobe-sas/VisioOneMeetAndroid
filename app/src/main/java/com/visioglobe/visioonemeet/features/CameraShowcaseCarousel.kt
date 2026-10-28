package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.visioglobe.visioonemeet.bridge.BridgeEvent
import com.visioglobe.visioonemeet.bridge.callBridge
import com.visioglobe.visioonemeet.model.DemoMap
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.OnBridgeEvent
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState
import org.json.JSONObject

// camera-showcase-carousel — JS side: web/src/features/camera-showcase-carousel.js (the loop
// itself). See docs/features/camera-showcase-carousel.md.

/**
 * Snapshot of the `camera-showcase-carousel` tour, pushed by the web page via
 * `AndroidBridge.onCarouselStateChanged` on every change — Start/Stop, but also a touch on the map
 * (pause) and the idle timer (resume), which happen with the sheet closed. [state] is `running`,
 * `paused` or `stopped`; [stopIndex] (0-based) / [poiId] is the stop in flight while running, or
 * the stop the tour resumes from while paused.
 */
private data class CarouselState(
    val state: String = "stopped",
    val stopIndex: Int = 0,
    val stopCount: Int = 0,
    val poiId: String = "",
    val resumeAfterMs: Long = 10_000,
)

private fun parseCarouselStatePayload(json: String): CarouselState {
    val obj = JSONObject(json)
    return CarouselState(
        state = obj.optString("state", "stopped"),
        stopIndex = obj.optInt("stopIndex", 0),
        stopCount = obj.optInt("stopCount", 0),
        poiId = obj.optString("poiId", ""),
        resumeAfterMs = obj.optLong("resumeAfterMs", 10_000),
    )
}

/**
 * Starts (or resumes right away) the tour over [DemoMap.CAROUSEL_STOP_IDS] — the loop itself runs
 * in the web page.
 */
private fun WebView.carouselStart() = callBridge("carouselStart", DemoMap.CAROUSEL_STOP_IDS)

/** Stops scheduling hops; the hop in flight, if any, ends on its own. */
private fun WebView.carouselStop() = callBridge("carouselStop")

/**
 * The tour loop runs entirely in the web page, so there is exactly one, it keeps running with the
 * panel closed, and it ends with the WebView when the screen is left. Its state is kept here, at
 * screen level, so the panel shows the right status when reopened after a touch paused the tour or
 * the idle timer resumed it with the panel closed.
 */
@Composable
fun CameraShowcaseCarouselScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    var carouselState by remember { mutableStateOf(CarouselState()) }
    OnBridgeEvent<BridgeEvent.CarouselStateChanged>(map) { carouselState = parseCarouselStatePayload(it.stateJson) }
    FeatureMapScreen(map, Feature.CameraShowcaseCarousel, onBack) {
        CameraShowcaseCarouselSheet(map.webView, carouselState)
    }
}

/** A Start/Stop button, a status line and a one-line hint. It holds no state of its own. */
@Composable
private fun CameraShowcaseCarouselSheet(webView: WebView?, carouselState: CarouselState) {
    val isStopped = carouselState.state == "stopped"
    Column(modifier = Modifier.padding(16.dp)) {
        if (isStopped) {
            Button(
                onClick = { webView?.carouselStart() },
                enabled = webView != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.carousel_start))
            }
        } else {
            OutlinedButton(
                onClick = { webView?.carouselStop() },
                enabled = webView != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.carousel_stop))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = when (carouselState.state) {
                "running" -> stringResource(
                    R.string.carousel_status_running,
                    carouselState.stopIndex + 1,
                    carouselState.stopCount,
                    carouselState.poiId,
                )
                "paused" -> stringResource(
                    R.string.carousel_status_paused,
                    (carouselState.resumeAfterMs / 1000).toInt(),
                )
                else -> stringResource(R.string.carousel_status_stopped)
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.carousel_hint, (carouselState.resumeAfterMs / 1000).toInt()),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
