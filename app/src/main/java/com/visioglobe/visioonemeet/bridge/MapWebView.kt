package com.visioglobe.visioonemeet.bridge

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import java.net.URLEncoder

private const val ASSET_LOADER_DOMAIN = "appassets.androidx.local"

/**
 * The VisioOne SDK's own default `LoadOptions.baseURL` — the pre-filled value of `custom-base-url`,
 * and the `?baseUrl=` every other screen implicitly sends, so nothing changes for them. See
 * docs/features/custom-base-url.md.
 */
const val DEFAULT_SDK_BASE_URL = "https://mapserver.visioglobe.com/"

/**
 * The SDK's own default `ViewCreateOptions.cameraProjection` (`@defaultValue` in its typings): every
 * screen but `camera-projection-toggle` keeps it, and omits the `?cameraProjection=` query param
 * entirely. See docs/features/camera-projection-toggle.md.
 */
const val DEFAULT_CAMERA_PROJECTION = "perspective"

/**
 * What the page loads, passed to it as URL query params (read by `web/src/main.js` and the
 * feature modules): changing any of them means loading a fresh page.
 *
 * - [mapHash] -> `?vg_maphash=`, the SDK's own `LoadOptions.vg_maphash` page-URL name.
 * - [baseUrl] -> `&baseUrl=` (`custom-base-url`).
 * - [enableShadows] -> `&enableShadows=true` (`custom-theme`: a theme only changes the scene
 *   lights, rendered only with shadows on; shadows are costly, so every other screen keeps the
 *   SDK default).
 * - [cameraProjection] -> `&cameraProjection=` when not the default (`camera-projection-toggle`).
 * - [isProtected] -> `?protected=true` and nothing else (`auth-token` code sample): the page then
 *   pulls the protected map's options through `AndroidBridge.getProtectedMapOptions` (a URL
 *   `vg_maphash` would override the load options).
 */
data class MapPageOptions(
    val mapHash: String,
    val baseUrl: String = DEFAULT_SDK_BASE_URL,
    val enableShadows: Boolean = false,
    val cameraProjection: String = DEFAULT_CAMERA_PROJECTION,
    val isProtected: Boolean = false,
) {
    fun pageUrl(): String {
        val query = if (isProtected) {
            "?protected=true"
        } else {
            "?vg_maphash=$mapHash&baseUrl=${URLEncoder.encode(baseUrl, "UTF-8")}" +
                (if (enableShadows) "&enableShadows=true" else "") +
                if (cameraProjection != DEFAULT_CAMERA_PROJECTION) "&cameraProjection=$cameraProjection" else ""
        }
        return "https://$ASSET_LOADER_DOMAIN/assets/www/index.html$query"
    }
}

/**
 * A WebView showing the bundled VisioOne page (built from `web/` with Vite into
 * `app/src/main/assets/www/`), with [bridge] exposed to it as `window.AndroidBridge`.
 *
 * The page is served through [WebViewAssetLoader] on the synthetic origin
 * `https://appassets.androidx.local/assets/www/...`, not `file://`: ES module imports fail under
 * `file://` in a WebView (CORS).
 */
fun createMapWebView(context: Context, options: MapPageOptions, bridge: NativeBridge): WebView {
    val assetLoader = WebViewAssetLoader.Builder()
        .setDomain(ASSET_LOADER_DOMAIN)
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        addJavascriptInterface(bridge, "AndroidBridge")
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)
        }
        loadUrl(options.pageUrl())
    }
}
