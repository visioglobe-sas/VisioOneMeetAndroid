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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureMapScreen(
    mapHash: String,
    titleRes: Int?,
    modifier: Modifier = Modifier,
    reactsToPoiClicks: Boolean = false,
    onBack: () -> Unit,
    sheetContent: @Composable (webView: WebView?, lastPoiClick: List<PoiClickInfo>, floorSelector: FloorSelectorState) -> Unit,
) {
    var loadState by remember { mutableStateOf<MapLoadState>(MapLoadState.Loading) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showControls by remember { mutableStateOf(false) }
    var lastPoiClick by remember { mutableStateOf<List<PoiClickInfo>>(emptyList()) }
    var floorSelector by remember { mutableStateOf(FloorSelectorState()) }
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
                                onReady = { mainHandler.post { loadState = MapLoadState.Ready } },
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
            sheetContent(webView, lastPoiClick, floorSelector)
        }
    }
}

private class MapBridge(
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit,
    private val notifyPoiClick: (String) -> Unit,
    private val notifyFloorsReady: (String) -> Unit,
    private val notifyFloorChanged: (String?) -> Unit,
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
}
