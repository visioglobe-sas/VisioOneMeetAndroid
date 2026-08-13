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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader

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
 * [overlay] renders once the map is [MapLoadState.Ready], and is handed the live [WebView] so it
 * can drive its own native<->JS bridge calls (see `FeatureOverlays.kt`).
 */
@Composable
fun FeatureMapScreen(
    mapHash: String,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.(webView: WebView?) -> Unit,
) {
    var loadState by remember { mutableStateOf<MapLoadState>(MapLoadState.Loading) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
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

            is MapLoadState.Ready -> overlay(webView)
        }
    }
}

private class MapBridge(
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit,
) {
    @JavascriptInterface
    fun onMapReady() = onReady()

    @JavascriptInterface
    fun onMapError(message: String) = onError(message)
}
