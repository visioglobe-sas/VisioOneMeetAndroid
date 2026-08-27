package com.visioglobe.visioonemeet.ui

import android.os.Handler
import android.os.Looper
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

private const val ASSET_LOADER_DOMAIN = "appassets.androidx.local"

private sealed interface MapLoadState {
    data object Loading : MapLoadState
    data object Ready : MapLoadState
    data class Error(val message: String) : MapLoadState
}

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
 * [sheetContent] as its second argument. Other screens simply ignore the event, so tapping a POI
 * on, say, the reset-view screen has no side effect. See docs/features/poi-click.md.
 *
 * The web bundle also always pushes the current building's floors once via
 * `AndroidBridge.onFloorsReady`, then keeps the active one in sync via
 * `AndroidBridge.onFloorChanged` (see `web/src/main.js`). Unlike POI clicks, this never
 * auto-opens the sheet — it is handed to [sheetContent] as its third argument for whichever
 * screen wants to render it. See docs/features/floor-selector.md.
 *
 * `computeNavigation` failures (bad/unreachable Place IDs) are reported back via
 * `AndroidBridge.onNavigationError`, surfaced to [sheetContent] as its fourth argument; a
 * subsequent successful computation clears it via `AndroidBridge.onNavigationComputed`. See
 * docs/features/compute-navigation.md.
 *
 * `resolvePositions` (`simulated-position`'s POI-id-to-lat/lng lookup) reports its result back via
 * `AndroidBridge.onPositionsResolved`, surfaced to [sheetContent] as its fifth argument. See
 * docs/features/simulated-position.md.
 *
 * `loadCustomData` (`custom-data`'s refresh-then-read round trip) reports its result back via
 * `AndroidBridge.onCustomDataLoaded`, surfaced to [sheetContent] as its sixth argument. See
 * docs/features/custom-data.md.
 *
 * `getCategories` (`category-highlight`'s venue-categories lookup) reports its result back via
 * `AndroidBridge.onCategoriesLoaded`, surfaced to [sheetContent] as its seventh argument. See
 * docs/features/category-highlight.md.
 *
 * `createDynamicPoi` (`dynamic-poi-crud`'s create-then-attach-a-label round trip) reports its
 * result back via `AndroidBridge.onDynamicPoiCreated`, surfaced to [sheetContent] as its eighth
 * argument. See docs/features/dynamic-poi-crud.md.
 *
 * `getCurrentLocale`/`setLocale` (`runtime-locale`'s current-locale lookup and locale switch)
 * both report back via the same `AndroidBridge.onLocaleResolved`, surfaced to [sheetContent] as
 * its ninth argument. See docs/features/runtime-locale.md.
 *
 * [onMapReady] fires once, right after [MapLoadState.Ready] is reached, with the live [WebView] —
 * for a screen that needs to drive the bridge before the visitor ever opens the FAB's bottom
 * sheet. `native-ui-replacement` is the only user of it today: it hides the SDK's own default
 * floor-selector widget as soon as the map loads, rather than only once the sheet's toggle is
 * touched, see docs/features/native-ui-replacement.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureMapScreen(
    mapHash: String,
    titleRes: Int?,
    modifier: Modifier = Modifier,
    reactsToPoiClicks: Boolean = false,
    onMapReady: (WebView) -> Unit = {},
    onBack: () -> Unit,
    sheetContent: @Composable (
        webView: WebView?,
        lastPoiClick: List<PoiClickInfo>,
        floorSelector: FloorSelectorState,
        navigationError: String?,
        positionsResolved: ResolvedPositionsPair?,
        customDataLoaded: CustomDataResult?,
        categoriesLoaded: CategoriesResult?,
        dynamicPoiCreated: DynamicPoiCreationResult?,
        localeResolved: LocaleResult?,
    ) -> Unit,
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
            if (loadState is MapLoadState.Ready) {
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
                            ),
                            "AndroidBridge",
                        )

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
                        }

                        loadUrl("https://$ASSET_LOADER_DOMAIN/assets/www/index.html?hash=$mapHash")
                    }.also { webView = it }
                },
            )

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

    if (showControls) {
        ModalBottomSheet(
            onDismissRequest = { showControls = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            sheetContent(
                webView,
                lastPoiClick,
                floorSelector,
                navigationError,
                positionsResolved,
                customDataLoaded,
                categoriesLoaded,
                dynamicPoiCreated,
                localeResolved,
            )
        }
    }
}

private class MapBridge(
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit,
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
) {
    @JavascriptInterface
    fun onMapReady() = onReady()

    @JavascriptInterface
    fun onMapError(message: String) = onError(message)

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
}
