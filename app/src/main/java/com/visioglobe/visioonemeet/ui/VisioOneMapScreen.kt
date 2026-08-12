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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject

private const val ASSET_LOADER_DOMAIN = "appassets.androidx.local"

// Stand-in for a real occupancy sensor feed: cycles a POI's surface through
// these colors on a timer. See docs/features/occupancy-simulated.md.
private val OCCUPANCY_COLORS = listOf("#2ECC71", "#F1C40F", "#E74C3C")
private const val OCCUPANCY_INTERVAL_MS = 2500L

private sealed interface MapLoadState {
    data object Loading : MapLoadState
    data object Ready : MapLoadState
    data class Error(val message: String) : MapLoadState
}

/**
 * Calls `window.MapBridge.updateOccupancy` in the WebView. `color: null` resets the surface to
 * its normal appearance rather than a hardcoded default.
 *
 * Arguments are JSON-encoded via [org.json] before being interpolated into the generated script
 * — never raw string concatenation, to avoid JS injection (same rule as the other platforms'
 * native<->JS bridges, see docs/COMMUNICATION_GUIDE.md equivalents).
 */
private fun WebView.updateOccupancy(planId: String, color: String?) {
    val entry = JSONObject().apply {
        put("planId", planId)
        put("color", color ?: JSONObject.NULL)
    }
    val script = "window.MapBridge.updateOccupancy(${JSONArray().put(entry)})"
    evaluateJavascript(script, null)
}

/**
 * Hosts the VisioOne JS SDK inside a WebView. The SDK is bundled with Vite (see /web) and
 * served from the app's assets through [WebViewAssetLoader], which exposes it on a synthetic
 * https:// origin instead of file:// so ES module imports resolve without CORS issues.
 */
@Composable
fun VisioOneMapScreen(mapHash: String, modifier: Modifier = Modifier) {
    var loadState by remember { mutableStateOf<MapLoadState>(MapLoadState.Loading) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var placeId by remember { mutableStateOf("") }
    var simulatingOccupancy by remember { mutableStateOf(false) }

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

            is MapLoadState.Ready ->
                OccupancySimulationPanel(
                    placeId = placeId,
                    onPlaceIdChange = { placeId = it },
                    simulating = simulatingOccupancy,
                    onToggle = { simulatingOccupancy = !simulatingOccupancy },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
        }

        LaunchedEffect(simulatingOccupancy, placeId, webView) {
            val targetPlaceId = placeId.trim()
            val view = webView
            if (!simulatingOccupancy || targetPlaceId.isEmpty() || view == null) return@LaunchedEffect

            var colorIndex = 0
            view.updateOccupancy(targetPlaceId, OCCUPANCY_COLORS[colorIndex])
            try {
                while (isActive) {
                    delay(OCCUPANCY_INTERVAL_MS)
                    colorIndex = (colorIndex + 1) % OCCUPANCY_COLORS.size
                    view.updateOccupancy(targetPlaceId, OCCUPANCY_COLORS[colorIndex])
                }
            } finally {
                // Reset the surface rather than leaving it stuck on the last simulated color.
                view.updateOccupancy(targetPlaceId, null)
            }
        }
    }
}

@Composable
private fun OccupancySimulationPanel(
    placeId: String,
    onPlaceIdChange: (String) -> Unit,
    simulating: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) {
        OutlinedTextField(
            value = placeId,
            onValueChange = onPlaceIdChange,
            label = { Text("Place ID") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onToggle) {
            Text(if (simulating) "Stop occupancy simulation" else "Simulate occupancy")
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
