package com.visioglobe.visioonemeet.sample

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.visioglobe.visioonemeet.bridge.callBridge
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/*
 * auth-token — CODE SAMPLE: loading a map protected by an authorization token.
 *
 * Every value below is a PLACEHOLDER: this demo has no protected map, and no screen of the app
 * calls this code. Replace the map hash and the token source with your own, then show
 * [ProtectedMapScreen] wherever your app displays its map. See docs/features/auth-token.md.
 */

/** Hash of YOUR protected map (a protected map has its own hash, distinct from public ones). */
const val PROTECTED_MAP_HASH = "YOUR_PROTECTED_MAP_HASH"

/** The protected map server: the only one that enforces the token. */
const val PROTECTED_MAP_BASE_URL = "https://mapserver-auth.visioglobe.com/"

/**
 * The JWT sent as `Authorization: Bearer <token>`. It must be issued by YOUR OWN backend
 * (Visioglobe provides the signing secret and the claims to use). Never embed the signing secret
 * in the app: the app only ever receives ready-made, short-lived tokens.
 */
const val PROTECTED_MAP_TOKEN = "YOUR_JWT_FROM_YOUR_BACKEND"

/** Refresh well before the token expires (the protected server accepts at most 1 hour). */
private val TOKEN_REFRESH_INTERVAL: Duration = 45.minutes

/** The `LoadOptions` the page passes to `visioOne.loadVenue` for a protected map. */
data class ProtectedMapOptions(
    val mapHash: String,
    val baseUrl: String,
    val authorizationToken: String,
) {
    /** JSON handed to the page through the JS bridge (`AndroidBridge.getProtectedMapOptions`). */
    fun toJson(): String = JSONObject()
        .put("vg_maphash", mapHash)
        .put("baseURL", baseUrl)
        .put("authorizationToken", authorizationToken)
        .toString()
}

/**
 * Placeholder: replace with a call to YOUR backend, which returns a fresh JWT for the signed-in
 * user. Never sign tokens in the app.
 */
suspend fun fetchTokenFromYourBackend(): String = PROTECTED_MAP_TOKEN

/**
 * Token refresh entry point: hands a fresh token to the loaded venue
 * (`venue.setAuthorizationToken`), used for every subsequent map request — no reload needed.
 */
fun WebView.refreshAuthorizationToken(freshToken: String) = callBridge("setAuthorizationToken", freshToken)

/**
 * A map screen loading the protected map: the page loads it with
 * `loadVenue({ vg_maphash, baseURL, authorizationToken })`, and the token is refreshed every
 * [TOKEN_REFRESH_INTERVAL] while the map is shown. An `UnauthorizedError` (token invalid, expired
 * or missing) is shown apart from other load errors.
 */
@Composable
fun ProtectedMapScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    options: ProtectedMapOptions = ProtectedMapOptions(
        mapHash = PROTECTED_MAP_HASH,
        baseUrl = PROTECTED_MAP_BASE_URL,
        authorizationToken = PROTECTED_MAP_TOKEN,
    ),
) {
    val map = rememberFeatureMapState()
    val webView = map.webView
    LaunchedEffect(webView, map.isMapReady) {
        if (webView == null || !map.isMapReady) return@LaunchedEffect
        while (true) {
            delay(TOKEN_REFRESH_INTERVAL)
            webView.refreshAuthorizationToken(fetchTokenFromYourBackend())
        }
    }
    FeatureMapScreen(
        map = map,
        feature = Feature.AuthToken,
        onBack = onBack,
        modifier = modifier,
        mapHash = options.mapHash,
        protectedMap = options,
        showControlsFab = false,
    ) {}
}
