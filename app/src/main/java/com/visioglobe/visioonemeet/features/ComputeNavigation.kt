package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.compose.runtime.Composable
import com.visioglobe.visioonemeet.bridge.callBridge
import com.visioglobe.visioonemeet.model.DemoMap
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.components.ItineraryControls
import com.visioglobe.visioonemeet.ui.components.rememberItinerarySession
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState

// compute-navigation — JS side: web/src/features/compute-navigation.js.
// See docs/features/compute-navigation.md. The form is ui/components/ItineraryForm.kt; these
// calls are also used by custom-navigation-trace, navigation-exclude-modalities and
// accessible-mode.

/**
 * Computes and draws a route between [origin] and [destination] (both Place IDs)
 * (`venue.computeNavigation` + `venue.createNavigationTrace` + `view.setCurrentNavigationTrace`).
 * The outcome comes back through `onNavigationComputed` / `onNavigationError`, collected by
 * `rememberItinerarySession`.
 */
internal fun WebView.computeNavigation(origin: String, destination: String, isAccessible: Boolean) =
    callBridge("computeNavigation", origin, destination, isAccessible)

/** Removes the route drawn by [computeNavigation], if any. */
internal fun WebView.clearNavigation() = callBridge("clearNavigation")

/**
 * The itinerary form calling [computeNavigation] with `isAccessible = false`. A failed computation
 * (bad/unreachable Place ID) surfaces its error below the fields.
 */
@Composable
fun ComputeNavigationScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val session = rememberItinerarySession(map, DemoMap.ITINERARY_FROM, DemoMap.ITINERARY_TO)
    FeatureMapScreen(map, Feature.ComputeNavigation, onBack) {
        ItineraryControls(
            session = session,
            onItinerary = { origin, destination -> map.webView?.computeNavigation(origin, destination, false) },
            onClear = { map.webView?.clearNavigation() },
        )
    }
}
