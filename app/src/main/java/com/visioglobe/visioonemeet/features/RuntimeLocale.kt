package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.visioglobe.visioonemeet.R
import com.visioglobe.visioonemeet.bridge.BridgeEvent
import com.visioglobe.visioonemeet.bridge.callBridge
import com.visioglobe.visioonemeet.bridge.nextBridgeRequestId
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.OnBridgeEvent
import com.visioglobe.visioonemeet.ui.components.CurrentOptionButtons
import com.visioglobe.visioonemeet.ui.components.ErrorText
import com.visioglobe.visioonemeet.ui.components.UiText
import com.visioglobe.visioonemeet.ui.components.resolve
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState
import org.json.JSONObject

// runtime-locale — JS side: web/src/features/runtime-locale.js. See docs/features/runtime-locale.md.
// setLocale and the locale result are also used by add-locale.

/**
 * Requests the venue's current locale in one round trip (`venue.currentLocale`). The result
 * arrives asynchronously via `AndroidBridge.onLocaleResolved`, echoing [requestId] back — shared
 * with [setLocale], since both report the same `{status, locale}` shape.
 */
private fun WebView.getCurrentLocale(requestId: Int) = callBridge("getCurrentLocale", requestId)

/**
 * Switches the map's displayed language to [locale] (`venue.setCurrentLocale(locale):
 * Promise<void>` — the SDK re-renders labels/UI itself, no manual re-fetch needed). The result
 * arrives asynchronously via `AndroidBridge.onLocaleResolved`, echoing [requestId] back.
 */
internal fun WebView.setLocale(requestId: Int, locale: String) = callBridge("setLocale", requestId, locale)

/**
 * Result of a [getCurrentLocale] or [setLocale] call. [locale] is the venue's resulting current
 * locale (e.g. `"en"`, `"fr"`) on success, `null` on failure, with [errorMessage] then carrying its
 * text.
 */
internal data class LocaleResult(val locale: String?, val errorMessage: String?)

/**
 * Parses the JSON object argument of `AndroidBridge.onLocaleResolved`, e.g.
 * `{"status":"ok","locale":"en"}` or `{"status":"error","message":"..."}`.
 */
internal fun parseLocaleResultPayload(json: String): LocaleResult {
    val root = JSONObject(json)
    return if (root.getString("status") == "ok") {
        LocaleResult(locale = root.getString("locale"), errorMessage = null)
    } else {
        LocaleResult(locale = null, errorMessage = root.optString("message").ifBlank { null })
    }
}

internal fun LocaleResult.errorUiText(): UiText =
    errorMessage?.let { UiText(R.string.raw_message, listOf(it)) } ?: UiText(R.string.locale_change_failed)

/**
 * The two locale options offered by `runtime-locale`, paired with a human-readable label. The
 * shared demo map's `venue.translator.allLocales` is actually `['default', 'en', 'fr']`, but
 * `'default'` is a byte-identical duplicate of `'fr'` on this venue — offering it as a third,
 * seemingly distinct choice would be misleading, so only these two SDK-valid locale codes are
 * listed here rather than populated from `allLocales` at runtime.
 */
private val RUNTIME_LOCALE_OPTIONS = listOf(
    "en" to R.string.runtime_locale_option_english,
    "fr" to R.string.runtime_locale_option_french,
)

/**
 * Screen-level state of `runtime-locale`: the venue's current locale as last reported, and the
 * error of the last switch. [refresh] asks the venue again (every time the panel opens — the SDK
 * has no locale-change event); responses are fed to [onLocaleResolved] at screen level.
 */
@Stable
private class RuntimeLocaleSession {
    var currentLocale by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set
    private var pendingRequestId: Int? = null

    fun refresh(webView: WebView?) {
        val view = webView ?: return
        val requestId = nextBridgeRequestId()
        pendingRequestId = requestId
        view.getCurrentLocale(requestId)
    }

    fun select(webView: WebView?, locale: String) {
        error = null
        val requestId = nextBridgeRequestId()
        pendingRequestId = requestId
        webView?.setLocale(requestId, locale)
    }

    fun onLocaleResolved(event: BridgeEvent.LocaleResolved) {
        if (event.requestId != pendingRequestId) return
        pendingRequestId = null
        val response = parseLocaleResultPayload(event.resultJson)
        if (response.locale != null) {
            currentLocale = response.locale
            error = null
        } else {
            error = response.errorUiText()
        }
    }
}

@Composable
fun RuntimeLocaleScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val session = remember { RuntimeLocaleSession() }
    OnBridgeEvent<BridgeEvent.LocaleResolved>(map) { session.onLocaleResolved(it) }
    FeatureMapScreen(map, Feature.RuntimeLocale, onBack) {
        RuntimeLocaleSheet(session, map.webView)
    }
}

/**
 * One button per [RUNTIME_LOCALE_OPTIONS] entry, the venue's current locale highlighted (queried
 * via [getCurrentLocale] when the panel opens). Tapping the other option calls [setLocale], which
 * switches `venue.currentLocale` and re-renders every POI/label name and UI item already on screen
 * — no separate refresh call needed. A failed switch surfaces an error below the buttons.
 */
@Composable
private fun RuntimeLocaleSheet(session: RuntimeLocaleSession, webView: WebView?) {
    LaunchedEffect(webView) { session.refresh(webView) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        CurrentOptionButtons(
            options = RUNTIME_LOCALE_OPTIONS.map { (locale, labelRes) -> locale to stringResource(labelRes) },
            current = session.currentLocale,
            onSelect = { locale -> session.select(webView, locale) },
        )
        ErrorText(session.error?.resolve())
    }
}
