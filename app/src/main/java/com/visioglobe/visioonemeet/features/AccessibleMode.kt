package com.visioglobe.visioonemeet.features

import androidx.compose.runtime.Composable
import com.visioglobe.visioonemeet.R
import com.visioglobe.visioonemeet.model.DemoMap
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.components.ItineraryControls
import com.visioglobe.visioonemeet.ui.components.ItineraryOptionSwitch
import com.visioglobe.visioonemeet.ui.components.rememberItinerarySession
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState

// accessible-mode — no JS of its own: compute-navigation's computeNavigation with
// `isAccessible` (ComputeNavigation.kt, web/src/features/compute-navigation.js).
// See docs/features/accessible-mode.md.

/**
 * The itinerary form plus an "Accessible route" switch, passed as `computeNavigation`'s
 * `isAccessible` when "Itinerary" is pressed. With it on, the SDK only uses segments the venue
 * marks as accessible, so the computed route avoids stairs.
 */
@Composable
fun AccessibleModeScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val session = rememberItinerarySession(map, DemoMap.ACCESSIBLE_FROM, DemoMap.ACCESSIBLE_TO)
    FeatureMapScreen(map, Feature.AccessibleMode, onBack) {
        ItineraryControls(
            session = session,
            onItinerary = { origin, destination ->
                map.webView?.computeNavigation(origin, destination, session.optionEnabled)
            },
            onClear = { map.webView?.clearNavigation() },
            extraContent = { ItineraryOptionSwitch(session, R.string.accessible_route_label) },
        )
    }
}
