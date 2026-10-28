package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.compose.runtime.Composable
import com.visioglobe.visioonemeet.R
import com.visioglobe.visioonemeet.bridge.callBridge
import com.visioglobe.visioonemeet.model.DemoMap
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.components.ItineraryControls
import com.visioglobe.visioonemeet.ui.components.ItineraryOptionSwitch
import com.visioglobe.visioonemeet.ui.components.rememberItinerarySession
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState

// navigation-exclude-modalities — JS side: web/src/features/navigation-exclude-modalities.js.
// See docs/features/navigation-exclude-modalities.md.

/**
 * Computes and draws a route between [origin] and [destination], optionally routing around
 * elevators (`venue.computeNavigation()`'s `excludedAttributes` option — `['lift']` when
 * [excludeElevator] is true, `[]` otherwise). Same request shape and error reporting
 * (`onNavigationError`/`onNavigationComputed`) as compute-navigation's `computeNavigation`; both
 * share one JS implementation.
 */
private fun WebView.computeNavigationExcludingModalities(
    origin: String,
    destination: String,
    isAccessible: Boolean,
    excludeElevator: Boolean,
) = callBridge("computeNavigationExcludingModalities", origin, destination, isAccessible, excludeElevator)

/**
 * The itinerary form plus an "Avoid elevator" switch, read when "Itinerary" is pressed (not
 * applied live: changing it means recomputing, which is what "Itinerary" does), passed to
 * [computeNavigationExcludingModalities].
 */
@Composable
fun NavigationExcludeModalitiesScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val session = rememberItinerarySession(map, DemoMap.EXCLUDE_MODALITIES_FROM, DemoMap.EXCLUDE_MODALITIES_TO)
    FeatureMapScreen(map, Feature.NavigationExcludeModalities, onBack) {
        ItineraryControls(
            session = session,
            onItinerary = { origin, destination ->
                map.webView?.computeNavigationExcludingModalities(origin, destination, false, session.optionEnabled)
            },
            onClear = { map.webView?.clearNavigation() },
            extraContent = { ItineraryOptionSwitch(session, R.string.avoid_elevator_label) },
        )
    }
}
