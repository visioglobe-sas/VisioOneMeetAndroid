package com.visioglobe.visioonemeet.bridge

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * JS -> native: the object registered with `WebView.addJavascriptInterface` as
 * `window.AndroidBridge` (see [createMapWebView]). The page calls these methods by name (see
 * `notify` in `web/src/bridge.js`), so their names are the protocol and must not change.
 *
 * They run on a WebView background thread: each one posts to the main thread, then either
 * reports the map's load state ([onMapReady], [onMapError], [onAuthorizationError]) or emits a
 * [BridgeEvent] that the feature screens collect. Arguments are strings, numbers and booleans
 * only: structured payloads arrive as JSON strings, parsed by the feature that uses them.
 *
 * If you enable code shrinking, keep the `@JavascriptInterface` methods (see README).
 */
class NativeBridge(
    private val protectedMapOptionsJson: String?,
    private val onLoaded: () -> Unit,
    private val onLoadFailed: (message: String, isAuthorizationError: Boolean) -> Unit,
    private val onEvent: (BridgeEvent) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun emit(event: BridgeEvent) {
        mainHandler.post { onEvent(event) }
    }

    @JavascriptInterface
    fun onMapReady() {
        mainHandler.post { onLoaded() }
    }

    @JavascriptInterface
    fun onMapError(message: String) {
        mainHandler.post { onLoadFailed(message, false) }
    }

    /** auth-token: the protected map rejected the token. See docs/features/auth-token.md. */
    @JavascriptInterface
    fun onAuthorizationError(message: String) {
        mainHandler.post { onLoadFailed(message, true) }
    }

    /** auth-token: the protected map's load options, pulled by the page on start. */
    @JavascriptInterface
    fun getProtectedMapOptions(): String = protectedMapOptionsJson ?: "{}"

    @JavascriptInterface
    fun onPoiClick(payload: String) = emit(BridgeEvent.PoiClick(payload))

    @JavascriptInterface
    fun onFloorsReady(payload: String) = emit(BridgeEvent.FloorsReady(payload))

    @JavascriptInterface
    fun onFloorChanged(floorId: String?) = emit(BridgeEvent.FloorChanged(floorId))

    @JavascriptInterface
    fun onNavigationComputed() = emit(BridgeEvent.NavigationComputed)

    @JavascriptInterface
    fun onNavigationError(message: String) = emit(BridgeEvent.NavigationError(message))

    @JavascriptInterface
    fun onPositionsResolved(requestId: Int, originJson: String, destinationJson: String) =
        emit(BridgeEvent.PositionsResolved(requestId, originJson, destinationJson))

    @JavascriptInterface
    fun onCustomDataLoaded(requestId: Int, customDataJson: String) =
        emit(BridgeEvent.CustomDataLoaded(requestId, customDataJson))

    @JavascriptInterface
    fun onCategoriesLoaded(requestId: Int, categoriesJson: String) =
        emit(BridgeEvent.CategoriesLoaded(requestId, categoriesJson))

    @JavascriptInterface
    fun onDynamicPoiCreated(requestId: Int, resultJson: String) =
        emit(BridgeEvent.DynamicPoiCreated(requestId, resultJson))

    @JavascriptInterface
    fun onLocaleResolved(requestId: Int, resultJson: String) = emit(BridgeEvent.LocaleResolved(requestId, resultJson))

    @JavascriptInterface
    fun onExploreModeChanged(mode: String) = emit(BridgeEvent.ExploreModeChanged(mode))

    @JavascriptInterface
    fun onAddLocaleResolved(requestId: Int, resultJson: String) =
        emit(BridgeEvent.AddLocaleResolved(requestId, resultJson))

    @JavascriptInterface
    fun onZoneResolved(requestId: Int, status: String) = emit(BridgeEvent.ZoneResolved(requestId, status))

    @JavascriptInterface
    fun onGeofenceStateChanged(isInside: Boolean) = emit(BridgeEvent.GeofenceStateChanged(isInside))

    @JavascriptInterface
    fun onIconImagePlaced(requestId: Int, resultJson: String) = emit(BridgeEvent.IconImagePlaced(requestId, resultJson))

    @JavascriptInterface
    fun onTextLabelPlaced(requestId: Int, resultJson: String) = emit(BridgeEvent.TextLabelPlaced(requestId, resultJson))

    @JavascriptInterface
    fun onHtmlMarkerStateChanged(stateJson: String) = emit(BridgeEvent.HtmlMarkerStateChanged(stateJson))

    @JavascriptInterface
    fun onThemeStateChanged(stateJson: String) = emit(BridgeEvent.ThemeStateChanged(stateJson))

    @JavascriptInterface
    fun onCameraLockChanged(locked: Boolean) = emit(BridgeEvent.CameraLockChanged(locked))

    @JavascriptInterface
    fun onCameraProjectionReported(projection: String) = emit(BridgeEvent.CameraProjectionReported(projection))

    @JavascriptInterface
    fun onCarouselStateChanged(stateJson: String) = emit(BridgeEvent.CarouselStateChanged(stateJson))
}
