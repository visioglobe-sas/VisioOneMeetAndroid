package com.visioglobe.visioonemeet.bridge

/**
 * JS -> native: one event per `window.AndroidBridge` callback the page calls with a feature result
 * or an SDK event (see [NativeBridge]). Each one carries the callback's raw arguments; the feature
 * that cares about it collects it at screen level (`OnBridgeEvent` in `ui/FeatureMapScreen.kt`)
 * and parses it itself, in its own `features/<Feature>.kt` file.
 *
 * Results of a request carry the `requestId` the request was sent with ([nextBridgeRequestId]),
 * typed `Int`: `@JavascriptInterface` converts the JS number to the parameter's declared type.
 * A response to an older request is told apart by its id, never by comparing values, so the same
 * result coming back twice is delivered twice.
 */
sealed interface BridgeEvent {
    // poi-click
    data class PoiClick(val poisJson: String) : BridgeEvent

    // floor-selector, native-ui-replacement
    data class FloorsReady(val floorsJson: String) : BridgeEvent
    data class FloorChanged(val floorId: String?) : BridgeEvent

    // compute-navigation and its variants
    data object NavigationComputed : BridgeEvent
    data class NavigationError(val message: String) : BridgeEvent

    // simulated-position and the other tracked-position screens
    data class PositionsResolved(val requestId: Int, val originJson: String, val destinationJson: String) :
        BridgeEvent

    // custom-data
    data class CustomDataLoaded(val requestId: Int, val customDataJson: String) : BridgeEvent

    // category-highlight
    data class CategoriesLoaded(val requestId: Int, val categoriesJson: String) : BridgeEvent

    // dynamic-poi-crud
    data class DynamicPoiCreated(val requestId: Int, val resultJson: String) : BridgeEvent

    // runtime-locale, add-locale
    data class LocaleResolved(val requestId: Int, val resultJson: String) : BridgeEvent

    // explore-mode
    data class ExploreModeChanged(val mode: String) : BridgeEvent

    // add-locale
    data class AddLocaleResolved(val requestId: Int, val resultJson: String) : BridgeEvent

    // geofencing
    data class ZoneResolved(val requestId: Int, val status: String) : BridgeEvent
    data class GeofenceStateChanged(val isInside: Boolean) : BridgeEvent

    // 3d-icon-marker
    data class IconImagePlaced(val requestId: Int, val resultJson: String) : BridgeEvent

    // custom-text-label
    data class TextLabelPlaced(val requestId: Int, val resultJson: String) : BridgeEvent

    // html-overlay-marker
    data class HtmlMarkerStateChanged(val stateJson: String) : BridgeEvent

    // custom-theme
    data class ThemeStateChanged(val stateJson: String) : BridgeEvent

    // camera-lock-on-position
    data class CameraLockChanged(val locked: Boolean) : BridgeEvent

    // camera-projection-toggle
    data class CameraProjectionReported(val projection: String) : BridgeEvent

    // camera-showcase-carousel
    data class CarouselStateChanged(val stateJson: String) : BridgeEvent
}
