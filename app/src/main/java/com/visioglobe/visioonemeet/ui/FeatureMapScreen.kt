package com.visioglobe.visioonemeet.ui

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.visioglobe.visioonemeet.R
import com.visioglobe.visioonemeet.sample.ProtectedMapOptions
import java.net.URLEncoder

private const val ASSET_LOADER_DOMAIN = "appassets.androidx.local"

/**
 * The VisioOne SDK's own default `LoadOptions.baseURL` (confirmed by reading the SDK source) —
 * used here as the pre-filled value for [CustomBaseUrlOverlay], and as the query param value every
 * other screen implicitly sends, so nothing changes for them. See docs/features/custom-base-url.md.
 */
const val DEFAULT_SDK_BASE_URL = "https://mapserver.visioglobe.com/"

/**
 * The SDK's own default `ViewCreateOptions.cameraProjection` (`@defaultValue` in its typings): every
 * screen but `camera-projection-toggle` keeps it, and omits the `?cameraProjection=` query param
 * entirely. See docs/features/camera-projection-toggle.md.
 */
const val DEFAULT_CAMERA_PROJECTION = "perspective"

private sealed interface MapLoadState {
    data object Loading : MapLoadState
    data object Ready : MapLoadState
    data class Error(val message: String) : MapLoadState
}

/**
 * Every piece of state the JS bridge can report back asynchronously, bundled into one value handed
 * to [FeatureMapScreen]'s [sheetContent] — see that parameter's kdoc for what feeds each field.
 *
 * This is a single data class rather than one [sheetContent] parameter per field (which is how it
 * used to be) because the Compose compiler's parameter-packing for `@Composable` function *types*
 * breaks down past 9 parameters — a 10-parameter `sheetContent` lambda fails IR lowering with
 * `Function with 11 params had 1 changed params but expected 2` (a real compiler bug, not a
 * project-side mistake; confirmed by reverting to 9 params and rebuilding successfully). Bundling
 * keeps [sheetContent] at a fixed 2 parameters no matter how many features are added later.
 */
data class FeatureBridgeState(
    val lastPoiClick: List<PoiClickInfo> = emptyList(),
    val floorSelector: FloorSelectorState = FloorSelectorState(),
    val navigationError: String? = null,
    val positionsResolved: ResolvedPositionsPair? = null,
    val customDataLoaded: CustomDataResult? = null,
    val categoriesLoaded: CategoriesResult? = null,
    val dynamicPoiCreated: DynamicPoiCreationResult? = null,
    val localeResolved: LocaleResult? = null,
    val currentExploreMode: String? = null,
    val addLocaleResolved: AddLocaleResult? = null,
    val zoneResolved: ZoneResolution? = null,
    val isInsideGeofenceZone: Boolean = false,
    val currentBaseUrl: String = DEFAULT_SDK_BASE_URL,
    val iconImagePlaced: IconImagePlacementResult? = null,
    val textLabelPlaced: TextLabelPlacementResult? = null,
    val htmlMarkerState: HtmlMarkerState? = null,
    val themeState: ThemeState? = null,
    val isCameraLocked: Boolean = false,
    val onReloadWithBaseUrl: (String) -> Unit = {},
    val isMapReady: Boolean = false,
    val cameraProjection: String? = null,
    val onReloadWithCameraProjection: (String) -> Unit = {},
    val carouselState: CarouselState = CarouselState(),
)

/**
 * Hosts the VisioOne JS SDK inside a WebView. The SDK is bundled with Vite (see /web) and
 * served from the app's assets through [WebViewAssetLoader], which exposes it on a synthetic
 * https:// origin instead of file:// so ES module imports resolve without CORS issues.
 *
 * The feature control lives in [sheetContent], rendered inside a [ModalBottomSheet] opened via
 * the floating action button once the map is [MapLoadState.Ready], and handed the live [WebView]
 * so it can drive its own native<->JS bridge calls (see `FeatureOverlays.kt`). [onBack] pops the
 * nav back stack to the Home menu, in addition to the system back button.
 *
 * The web bundle always forwards the SDK's `poiclick` event to `AndroidBridge.onPoiClick`
 * (see `web/src/main.js`), but only a screen that opts in with [reactsToPoiClicks] acts on it:
 * it auto-opens the modal bottom sheet (same as tapping the FAB) and hands the parsed POIs to
 * [sheetContent] via [FeatureBridgeState.lastPoiClick]. Other screens simply ignore the event, so
 * tapping a POI on, say, the reset-view screen has no side effect. See docs/features/poi-click.md.
 *
 * The web bundle also always pushes the current building's floors once via
 * `AndroidBridge.onFloorsReady`, then keeps the active one in sync via
 * `AndroidBridge.onFloorChanged` (see `web/src/main.js`). Unlike POI clicks, this never
 * auto-opens the sheet — it is handed to [sheetContent] via [FeatureBridgeState.floorSelector] for
 * whichever screen wants to render it. See docs/features/floor-selector.md.
 *
 * `computeNavigation` failures (bad/unreachable Place IDs) are reported back via
 * `AndroidBridge.onNavigationError`, surfaced via [FeatureBridgeState.navigationError]; a
 * subsequent successful computation clears it via `AndroidBridge.onNavigationComputed`. See
 * docs/features/compute-navigation.md.
 *
 * `resolvePositions` (`simulated-position`'s POI-id-to-lat/lng lookup) reports its result back via
 * `AndroidBridge.onPositionsResolved`, surfaced via [FeatureBridgeState.positionsResolved]. See
 * docs/features/simulated-position.md.
 *
 * `loadCustomData` (`custom-data`'s refresh-then-read round trip) reports its result back via
 * `AndroidBridge.onCustomDataLoaded`, surfaced via [FeatureBridgeState.customDataLoaded]. See
 * docs/features/custom-data.md.
 *
 * `getCategories` (`category-highlight`'s venue-categories lookup) reports its result back via
 * `AndroidBridge.onCategoriesLoaded`, surfaced via [FeatureBridgeState.categoriesLoaded]. See
 * docs/features/category-highlight.md.
 *
 * `createDynamicPoi` (`dynamic-poi-crud`'s create-then-attach-a-label round trip) reports its
 * result back via `AndroidBridge.onDynamicPoiCreated`, surfaced via
 * [FeatureBridgeState.dynamicPoiCreated]. See docs/features/dynamic-poi-crud.md.
 *
 * `getCurrentLocale`/`setLocale` (`runtime-locale`'s current-locale lookup and locale switch)
 * both report back via the same `AndroidBridge.onLocaleResolved`, surfaced via
 * [FeatureBridgeState.localeResolved]. See docs/features/runtime-locale.md.
 *
 * `addSpanishLocale` (`add-locale`'s add-then-translate-back round trip) reports its result back via
 * `AndroidBridge.onAddLocaleResolved`, surfaced via [FeatureBridgeState.addLocaleResolved]. See
 * docs/features/add-locale.md.
 *
 * `resolveZone` (`geofencing`'s zone-POI-id-to-surfaces lookup) reports its result back via
 * `AndroidBridge.onZoneResolved`, surfaced via [FeatureBridgeState.zoneResolved]. `checkGeofence`
 * (the per-tick point-in-polygon test against that zone) reports the current inside/outside state,
 * on every call rather than only on a transition, via `AndroidBridge.onGeofenceStateChanged`,
 * surfaced via [FeatureBridgeState.isInsideGeofenceZone]. See docs/features/geofencing.md.
 *
 * `placeIconImage` (`3d-icon-marker`'s anchor-POI lookup + `venue.createImage` round trip) reports
 * its result back via `AndroidBridge.onIconImagePlaced`, surfaced via
 * [FeatureBridgeState.iconImagePlaced]. See docs/features/3d-icon-marker.md.
 *
 * `placeTextLabel` (`custom-text-label`'s anchor-POI lookup + `venue.createLabel` round trip)
 * reports its result back via `AndroidBridge.onTextLabelPlaced`, surfaced via
 * [FeatureBridgeState.textLabelPlaced]. See docs/features/custom-text-label.md.
 *
 * Every `html-overlay-marker` command (place/update text/show-hide/remove) and every tap on the
 * badge itself reports the badge's whole state back via `AndroidBridge.onHtmlMarkerStateChanged`,
 * surfaced via [FeatureBridgeState.htmlMarkerState]. It's kept here, outside the bottom sheet,
 * because the visitor has to close the sheet to tap the badge on the map: the sheet's own state
 * doesn't survive that, so the reopened sheet renders from this snapshot instead. See
 * docs/features/html-overlay-marker.md.
 *
 * Every `custom-theme` command (theme state lookup/set theme/set accent colors/reset) reports the
 * whole theme state back via `AndroidBridge.onThemeStateChanged`, surfaced via
 * [FeatureBridgeState.themeState] — kept here for the same reason as `htmlMarkerState`: the
 * visitor closes the sheet to tap a place and see its card's accent-colored buttons. See
 * docs/features/custom-theme.md.
 *
 * `setExploreMode` (`explore-mode`'s building-exploration mode switch) has no direct response —
 * the resulting mode is reported back, like any other explore-mode change (including ones
 * triggered by map interaction, not this call), via `AndroidBridge.onExploreModeChanged`, also
 * pushed once unprompted right after the map loads so the control has a correct initial highlight.
 * Surfaced via [FeatureBridgeState.currentExploreMode]. See docs/features/explore-mode.md.
 *
 * `view.lockCameraPositionOnTracking` (`camera-lock-on-position`) is likewise mirrored rather than
 * kept app-side: the SDK turns it on by itself when tracking starts, off when it stops or when the
 * user pans the map, so the web bundle forwards every `trackingon`/`trackingoff`/`trackingchanged`
 * event via `AndroidBridge.onCameraLockChanged`, surfaced via [FeatureBridgeState.isCameraLocked]
 * — kept here, outside the bottom sheet, so a change made while the sheet is closed isn't lost.
 * See docs/features/camera-lock-on-position.md.
 *
 * [FeatureBridgeState.currentBaseUrl]/[FeatureBridgeState.onReloadWithBaseUrl] back `custom-base-url`:
 * unlike every other bridge call, `LoadOptions.baseURL` cannot be changed on an already-loaded venue
 * — it only takes effect when `loadVenue` runs, so "Reload" here means fully recreating the WebView
 * against a new `?baseUrl=` query param, not calling a JS setter. [key] around the [AndroidView] call
 * does that: changing [currentBaseUrl] gives the underlying node a new identity, so Compose disposes
 * the old WebView and runs `factory` again from scratch. Every other screen just keeps using the
 * default value, so nothing changes for them. See docs/features/custom-base-url.md.
 *
 * [FeatureBridgeState.cameraProjection]/[FeatureBridgeState.onReloadWithCameraProjection] back
 * `camera-projection-toggle`, on the same principle as `custom-base-url`: `cameraProjection` is a
 * `createView`-time option only (`view.cameraProjection` is read-only, no runtime switch), so
 * switching it recreates the WebView — via the same [key] — against a new `?cameraProjection=`
 * query param, i.e. a fresh page, `loadVenue` and `createView`. The web bundle reports the
 * projection the new view was actually created with via `AndroidBridge.onCameraProjectionReported`
 * (`null` while reloading), and [FeatureBridgeState.isMapReady] lets the control disable itself
 * until then. See docs/features/camera-projection-toggle.md.
 *
 * `camera-showcase-carousel`'s tour loop runs entirely in the web page; every change of its state —
 * Start/Stop, but also a touch on the map (pause) or the idle timer (resume), both of which happen
 * with the sheet closed — is pushed via `AndroidBridge.onCarouselStateChanged`, surfaced via
 * [FeatureBridgeState.carouselState], so the reopened sheet shows the current status. Leaving the
 * screen destroys the WebView (below), which ends the loop and its timers with it. See
 * docs/features/camera-showcase-carousel.md.
 *
 * `auth-token` (code sample, not used by any screen of this demo): a non-null [protectedMap] loads
 * the page with `?protected=true` and no map hash in the URL (the SDK would let a URL
 * `vg_maphash` override the load options); the page then pulls [protectedMap] through
 * `AndroidBridge.getProtectedMapOptions` and loads it with its authorization token. An
 * `UnauthorizedError` comes back through `AndroidBridge.onAuthorizationError`, apart from other
 * load errors. [showControlsFab] hides the controls FAB for a screen with nothing to control. See
 * docs/features/auth-token.md.
 *
 * Every WebView recreated that way (and the one left when the screen is popped) is destroyed in
 * `onRelease`, so repeated reloads don't pile up live pages, WebGL contexts or bridge callbacks
 * from a page that is no longer on screen.
 *
 * [enableShadows] appends `&enableShadows=true` to the bridge page URL, which then passes
 * `{ enableShadows: true }` to `createView` — only `custom-theme` sets it, since a theme only
 * changes the scene lights, which are only rendered with shadows/lights enabled (and shadows are
 * costly, so every other screen keeps the SDK default). See docs/features/custom-theme.md.
 *
 * [onMapReady] fires once, right after [MapLoadState.Ready] is reached, with the live [WebView] —
 * for a screen that needs to drive the bridge before the visitor ever opens the FAB's bottom
 * sheet. `native-ui-replacement` is the only user of it today: it hides the SDK's own default
 * floor-selector widget as soon as the map loads, rather than only once the sheet's toggle is
 * touched, see docs/features/native-ui-replacement.md.
 *
 * [screenContent] is composed for the whole lifetime of the screen, outside the bottom sheet, with
 * the same arguments as [sheetContent]. It renders nothing visible: it hosts work that must outlive
 * the sheet, which is disposed on every close — today the tracked-position simulation loop of the
 * four tracking screens (see `TrackedPositionSimulationHost`), so the simulation keeps running
 * while the sheet is closed and the visitor pans the map, and stops when the screen is left.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureMapScreen(
    mapHash: String,
    titleRes: Int?,
    modifier: Modifier = Modifier,
    reactsToPoiClicks: Boolean = false,
    enableShadows: Boolean = false,
    protectedMap: ProtectedMapOptions? = null,
    showControlsFab: Boolean = true,
    onMapReady: (WebView) -> Unit = {},
    onBack: () -> Unit,
    screenContent: @Composable (webView: WebView?, bridgeState: FeatureBridgeState) -> Unit = { _, _ -> },
    sheetContent: @Composable (webView: WebView?, bridgeState: FeatureBridgeState) -> Unit,
) {
    var loadState by remember { mutableStateOf<MapLoadState>(MapLoadState.Loading) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showControls by remember { mutableStateOf(false) }
    var lastPoiClick by remember { mutableStateOf<List<PoiClickInfo>>(emptyList()) }
    var floorSelector by remember { mutableStateOf(FloorSelectorState()) }
    var navigationError by remember { mutableStateOf<String?>(null) }
    var positionsResolved by remember { mutableStateOf<ResolvedPositionsPair?>(null) }
    var customDataLoaded by remember { mutableStateOf<CustomDataResult?>(null) }
    var categoriesLoaded by remember { mutableStateOf<CategoriesResult?>(null) }
    var dynamicPoiCreated by remember { mutableStateOf<DynamicPoiCreationResult?>(null) }
    var localeResolved by remember { mutableStateOf<LocaleResult?>(null) }
    var currentExploreMode by remember { mutableStateOf<String?>(null) }
    var addLocaleResolved by remember { mutableStateOf<AddLocaleResult?>(null) }
    var zoneResolved by remember { mutableStateOf<ZoneResolution?>(null) }
    var isInsideGeofenceZone by remember { mutableStateOf(false) }
    var currentBaseUrl by remember { mutableStateOf(DEFAULT_SDK_BASE_URL) }
    var iconImagePlaced by remember { mutableStateOf<IconImagePlacementResult?>(null) }
    var textLabelPlaced by remember { mutableStateOf<TextLabelPlacementResult?>(null) }
    var htmlMarkerState by remember { mutableStateOf<HtmlMarkerState?>(null) }
    var themeState by remember { mutableStateOf<ThemeState?>(null) }
    var isCameraLocked by remember { mutableStateOf(false) }
    var requestedCameraProjection by remember { mutableStateOf(DEFAULT_CAMERA_PROJECTION) }
    var reportedCameraProjection by remember { mutableStateOf<String?>(null) }
    var carouselState by remember { mutableStateOf(CarouselState()) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { titleRes?.let { Text(stringResource(it)) } },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back_to_menu_content_description),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (showControlsFab && loadState is MapLoadState.Ready) {
                FloatingActionButton(onClick = { showControls = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tune),
                        contentDescription = stringResource(R.string.open_controls_content_description),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            key(mapHash, currentBaseUrl, requestedCameraProjection, protectedMap) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        val assetLoader = WebViewAssetLoader.Builder()
                            .setDomain(ASSET_LOADER_DOMAIN)
                            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                            .build()

                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false

                            addJavascriptInterface(
                                MapBridge(
                                    onReady = {
                                        mainHandler.post {
                                            loadState = MapLoadState.Ready
                                            webView?.let(onMapReady)
                                        }
                                    },
                                    onError = { message -> mainHandler.post { loadState = MapLoadState.Error(message) } },
                                    onAuthorizationError = { message ->
                                        Log.w(AUTH_TOKEN_LOG_TAG, "UnauthorizedError: $message")
                                        val text = context.getString(R.string.auth_token_unauthorized_error)
                                        mainHandler.post { loadState = MapLoadState.Error("$text\n($message)") }
                                    },
                                    protectedMapOptionsJson = protectedMap?.toJson(),
                                    notifyPoiClick = { payload ->
                                        if (reactsToPoiClicks) {
                                            mainHandler.post {
                                                lastPoiClick = parsePoiClickPayload(payload)
                                                showControls = true
                                            }
                                        }
                                    },
                                    notifyFloorsReady = { payload ->
                                        mainHandler.post { floorSelector = parseFloorsReadyPayload(payload) }
                                    },
                                    notifyFloorChanged = { floorId ->
                                        mainHandler.post { floorSelector = floorSelector.copy(currentFloorId = floorId) }
                                    },
                                    notifyNavigationComputed = {
                                        mainHandler.post { navigationError = null }
                                    },
                                    notifyNavigationError = { message ->
                                        mainHandler.post { navigationError = message }
                                    },
                                    notifyPositionsResolved = { requestId, originJson, destinationJson ->
                                        mainHandler.post {
                                            positionsResolved = ResolvedPositionsPair(
                                                requestId = requestId,
                                                origin = parseResolvedPosition(originJson),
                                                destination = parseResolvedPosition(destinationJson),
                                            )
                                        }
                                    },
                                    notifyCustomDataLoaded = { requestId, customDataJson ->
                                        mainHandler.post {
                                            customDataLoaded = CustomDataResult(
                                                requestId = requestId,
                                                customData = parseCustomDataPayload(customDataJson),
                                            )
                                        }
                                    },
                                    notifyCategoriesLoaded = { requestId, categoriesJson ->
                                        mainHandler.post {
                                            categoriesLoaded = CategoriesResult(
                                                requestId = requestId,
                                                categories = parseCategoriesPayload(categoriesJson),
                                            )
                                        }
                                    },
                                    notifyDynamicPoiCreated = { requestId, resultJson ->
                                        mainHandler.post {
                                            dynamicPoiCreated = parseDynamicPoiCreationPayload(requestId, resultJson)
                                        }
                                    },
                                    notifyLocaleResolved = { requestId, resultJson ->
                                        mainHandler.post {
                                            localeResolved = parseLocaleResultPayload(requestId, resultJson)
                                        }
                                    },
                                    notifyExploreModeChanged = { mode ->
                                        mainHandler.post { currentExploreMode = mode }
                                    },
                                    notifyAddLocaleResolved = { requestId, resultJson ->
                                        mainHandler.post {
                                            addLocaleResolved = parseAddLocaleResultPayload(requestId, resultJson)
                                        }
                                    },
                                    notifyZoneResolved = { requestId, status ->
                                        mainHandler.post {
                                            zoneResolved = ZoneResolution(requestId = requestId, status = status)
                                        }
                                    },
                                    notifyGeofenceStateChanged = { isInside ->
                                        mainHandler.post { isInsideGeofenceZone = isInside }
                                    },
                                    notifyIconImagePlaced = { requestId, resultJson ->
                                        mainHandler.post {
                                            iconImagePlaced = parseIconImagePlacementPayload(requestId, resultJson)
                                        }
                                    },
                                    notifyTextLabelPlaced = { requestId, resultJson ->
                                        mainHandler.post {
                                            textLabelPlaced = parseTextLabelPlacementPayload(requestId, resultJson)
                                        }
                                    },
                                    notifyHtmlMarkerStateChanged = { stateJson ->
                                        mainHandler.post { htmlMarkerState = parseHtmlMarkerStatePayload(stateJson) }
                                    },
                                    notifyThemeStateChanged = { stateJson ->
                                        mainHandler.post { themeState = parseThemeStatePayload(stateJson) }
                                    },
                                    notifyCameraLockChanged = { locked ->
                                        mainHandler.post { isCameraLocked = locked }
                                    },
                                    notifyCameraProjectionReported = { projection ->
                                        mainHandler.post { reportedCameraProjection = projection }
                                    },
                                    notifyCarouselStateChanged = { stateJson ->
                                        mainHandler.post { carouselState = parseCarouselStatePayload(stateJson) }
                                    },
                                ),
                                "AndroidBridge",
                            )

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
                            }

                            val encodedBaseUrl = URLEncoder.encode(currentBaseUrl, "UTF-8")
                            val query = if (protectedMap != null) {
                                "?protected=true"
                            } else {
                                "?vg_maphash=$mapHash&baseUrl=$encodedBaseUrl" +
                                    (if (enableShadows) "&enableShadows=true" else "") +
                                    if (requestedCameraProjection != DEFAULT_CAMERA_PROJECTION) {
                                        "&cameraProjection=$requestedCameraProjection"
                                    } else {
                                        ""
                                    }
                            }
                            loadUrl("https://$ASSET_LOADER_DOMAIN/assets/www/index.html$query")
                        }.also { webView = it }
                    },
                    onRelease = { it.destroy() },
                )
            }

            when (val state = loadState) {
                is MapLoadState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                is MapLoadState.Error -> Text(
                    text = "Impossible de charger la carte VisioOne :\n${state.message}",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                )

                is MapLoadState.Ready -> Unit
            }
        }
    }

    val bridgeState = FeatureBridgeState(
        lastPoiClick = lastPoiClick,
        floorSelector = floorSelector,
        navigationError = navigationError,
        positionsResolved = positionsResolved,
        customDataLoaded = customDataLoaded,
        categoriesLoaded = categoriesLoaded,
        dynamicPoiCreated = dynamicPoiCreated,
        localeResolved = localeResolved,
        currentExploreMode = currentExploreMode,
        addLocaleResolved = addLocaleResolved,
        zoneResolved = zoneResolved,
        isInsideGeofenceZone = isInsideGeofenceZone,
        currentBaseUrl = currentBaseUrl,
        iconImagePlaced = iconImagePlaced,
        textLabelPlaced = textLabelPlaced,
        htmlMarkerState = htmlMarkerState,
        themeState = themeState,
        isCameraLocked = isCameraLocked,
        onReloadWithBaseUrl = { newBaseUrl ->
            loadState = MapLoadState.Loading
            currentBaseUrl = newBaseUrl
        },
        isMapReady = loadState is MapLoadState.Ready,
        cameraProjection = reportedCameraProjection,
        onReloadWithCameraProjection = { newProjection ->
            if (newProjection != requestedCameraProjection) {
                loadState = MapLoadState.Loading
                reportedCameraProjection = null
                requestedCameraProjection = newProjection
            }
        },
        carouselState = carouselState,
    )

    screenContent(webView, bridgeState)

    if (showControls) {
        ModalBottomSheet(
            onDismissRequest = { showControls = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            sheetContent(webView, bridgeState)
        }
    }
}

private const val AUTH_TOKEN_LOG_TAG = "AuthToken"

private class MapBridge(
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit,
    private val onAuthorizationError: (String) -> Unit,
    private val protectedMapOptionsJson: String?,
    private val notifyPoiClick: (String) -> Unit,
    private val notifyFloorsReady: (String) -> Unit,
    private val notifyFloorChanged: (String?) -> Unit,
    private val notifyNavigationComputed: () -> Unit,
    private val notifyNavigationError: (String) -> Unit,
    private val notifyPositionsResolved: (Int, String, String) -> Unit,
    private val notifyCustomDataLoaded: (Int, String) -> Unit,
    private val notifyCategoriesLoaded: (Int, String) -> Unit,
    private val notifyDynamicPoiCreated: (Int, String) -> Unit,
    private val notifyLocaleResolved: (Int, String) -> Unit,
    private val notifyExploreModeChanged: (String) -> Unit,
    private val notifyAddLocaleResolved: (Int, String) -> Unit,
    private val notifyZoneResolved: (Int, String) -> Unit,
    private val notifyGeofenceStateChanged: (Boolean) -> Unit,
    private val notifyIconImagePlaced: (Int, String) -> Unit,
    private val notifyTextLabelPlaced: (Int, String) -> Unit,
    private val notifyHtmlMarkerStateChanged: (String) -> Unit,
    private val notifyThemeStateChanged: (String) -> Unit,
    private val notifyCameraLockChanged: (Boolean) -> Unit,
    private val notifyCameraProjectionReported: (String) -> Unit,
    private val notifyCarouselStateChanged: (String) -> Unit,
) {
    @JavascriptInterface
    fun onMapReady() = onReady()

    @JavascriptInterface
    fun onMapError(message: String) = onError(message)

    @JavascriptInterface
    fun onAuthorizationError(message: String) = onAuthorizationError.invoke(message)

    @JavascriptInterface
    fun getProtectedMapOptions(): String = protectedMapOptionsJson ?: "{}"

    @JavascriptInterface
    fun onPoiClick(payload: String) = notifyPoiClick(payload)

    @JavascriptInterface
    fun onFloorsReady(payload: String) = notifyFloorsReady(payload)

    @JavascriptInterface
    fun onFloorChanged(floorId: String?) = notifyFloorChanged(floorId)

    @JavascriptInterface
    fun onNavigationComputed() = notifyNavigationComputed()

    @JavascriptInterface
    fun onNavigationError(message: String) = notifyNavigationError(message)

    @JavascriptInterface
    fun onPositionsResolved(requestId: Int, originJson: String, destinationJson: String) =
        notifyPositionsResolved(requestId, originJson, destinationJson)

    @JavascriptInterface
    fun onCustomDataLoaded(requestId: Int, customDataJson: String) =
        notifyCustomDataLoaded(requestId, customDataJson)

    @JavascriptInterface
    fun onCategoriesLoaded(requestId: Int, categoriesJson: String) =
        notifyCategoriesLoaded(requestId, categoriesJson)

    @JavascriptInterface
    fun onDynamicPoiCreated(requestId: Int, resultJson: String) =
        notifyDynamicPoiCreated(requestId, resultJson)

    @JavascriptInterface
    fun onLocaleResolved(requestId: Int, resultJson: String) =
        notifyLocaleResolved(requestId, resultJson)

    @JavascriptInterface
    fun onExploreModeChanged(mode: String) = notifyExploreModeChanged(mode)

    @JavascriptInterface
    fun onAddLocaleResolved(requestId: Int, resultJson: String) = notifyAddLocaleResolved(requestId, resultJson)

    @JavascriptInterface
    fun onZoneResolved(requestId: Int, status: String) = notifyZoneResolved(requestId, status)

    @JavascriptInterface
    fun onGeofenceStateChanged(isInside: Boolean) = notifyGeofenceStateChanged(isInside)

    @JavascriptInterface
    fun onIconImagePlaced(requestId: Int, resultJson: String) = notifyIconImagePlaced(requestId, resultJson)

    @JavascriptInterface
    fun onTextLabelPlaced(requestId: Int, resultJson: String) = notifyTextLabelPlaced(requestId, resultJson)

    @JavascriptInterface
    fun onHtmlMarkerStateChanged(stateJson: String) = notifyHtmlMarkerStateChanged(stateJson)

    @JavascriptInterface
    fun onThemeStateChanged(stateJson: String) = notifyThemeStateChanged(stateJson)

    @JavascriptInterface
    fun onCameraLockChanged(locked: Boolean) = notifyCameraLockChanged(locked)

    @JavascriptInterface
    fun onCameraProjectionReported(projection: String) = notifyCameraProjectionReported(projection)

    @JavascriptInterface
    fun onCarouselStateChanged(stateJson: String) = notifyCarouselStateChanged(stateJson)
}
