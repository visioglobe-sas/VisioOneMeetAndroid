package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.visioglobe.visioonemeet.ui.components.ErrorText
import com.visioglobe.visioonemeet.ui.components.UiText
import com.visioglobe.visioonemeet.ui.components.resolve
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState
import org.json.JSONObject

// add-locale — JS side: web/src/features/add-locale.js. See docs/features/add-locale.md.
// Switching to the added locale uses runtime-locale's setLocale (RuntimeLocale.kt).

/**
 * Adds the `'es'` locale at runtime (`venue.translator.addLocale('es', resources)`, immediately
 * followed by `venue.translator.translate(key, 'es')` for each key in that fixed dictionary). The
 * result arrives asynchronously via `AndroidBridge.onAddLocaleResolved`, echoing [requestId] back.
 */
private fun WebView.addSpanishLocale(requestId: Int) = callBridge("addSpanishLocale", requestId)

/**
 * Parses the JSON object argument of `AndroidBridge.onAddLocaleResolved`, e.g.
 * `{"status":"ok","translations":{"search-for-anything":"Busca lo que quieras","welcome-message":"¡Bienvenido al mapa!"}}`
 * or `{"status":"error","message":"..."}`. Returns the translations (each demo key mapped to the
 * Spanish text `translate(key, 'es')` returned right after `addLocale`) on success, `null` and the
 * error message (if any) on failure.
 */
private fun parseAddLocaleResultPayload(json: String): Pair<Map<String, String>?, String?> {
    val root = JSONObject(json)
    return if (root.getString("status") == "ok") {
        val translationsObj = root.getJSONObject("translations")
        translationsObj.keys().asSequence().associateWith { key -> translationsObj.getString(key) } to null
    } else {
        null to root.optString("message").ifBlank { null }
    }
}

/**
 * The fixed keys demonstrated by `add-locale`, with the label shown above each one's value:
 * `'search-for-anything'` is one of the SDK's own predefined UI keys (see `Translator.addLocale`'s
 * doc), showing that `addLocale` can override the SDK's own built-in strings; `'welcome-message'` is
 * a custom, app-defined key, showing the same store is a general-purpose one. The Spanish values
 * live in `web/src/features/add-locale.js`'s `ADD_LOCALE_RESOURCES`.
 */
private val ADD_LOCALE_DEMO_KEYS = listOf(
    "search-for-anything" to R.string.add_locale_sdk_key_label,
    "welcome-message" to R.string.add_locale_custom_key_label,
)

/**
 * Screen-level state of `add-locale`: the Spanish translations read back after `addLocale` (null
 * until added), whether the venue's locale was switched to Spanish, and the last error. Responses
 * to both calls (`onAddLocaleResolved`, and `onLocaleResolved` for the switch) are fed in at
 * screen level.
 */
@Stable
private class AddLocaleSession {
    var translations by mutableStateOf<Map<String, String>?>(null)
        private set
    var isSpanishDisplayed by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set
    private var pendingAddRequestId: Int? = null
    private var pendingSwitchRequestId: Int? = null

    fun addSpanish(webView: WebView?) {
        error = null
        val requestId = nextBridgeRequestId()
        pendingAddRequestId = requestId
        webView?.addSpanishLocale(requestId)
    }

    fun switchToSpanish(webView: WebView?) {
        error = null
        val requestId = nextBridgeRequestId()
        pendingSwitchRequestId = requestId
        webView?.setLocale(requestId, "es")
    }

    fun onAddLocaleResolved(event: BridgeEvent.AddLocaleResolved) {
        if (event.requestId != pendingAddRequestId) return
        pendingAddRequestId = null
        val (newTranslations, errorMessage) = parseAddLocaleResultPayload(event.resultJson)
        if (newTranslations != null) {
            translations = newTranslations
            error = null
        } else {
            error = errorMessage?.let { UiText(R.string.raw_message, listOf(it)) }
                ?: UiText(R.string.add_locale_failed)
        }
    }

    fun onLocaleResolved(event: BridgeEvent.LocaleResolved) {
        if (event.requestId != pendingSwitchRequestId) return
        pendingSwitchRequestId = null
        val response = parseLocaleResultPayload(event.resultJson)
        if (response.locale != null) {
            isSpanishDisplayed = response.locale == "es"
            error = null
        } else {
            error = response.errorUiText()
        }
    }
}

@Composable
fun AddLocaleScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val session = remember { AddLocaleSession() }
    OnBridgeEvent<BridgeEvent.AddLocaleResolved>(map) { session.onAddLocaleResolved(it) }
    OnBridgeEvent<BridgeEvent.LocaleResolved>(map) { session.onLocaleResolved(it) }
    FeatureMapScreen(map, Feature.AddLocale, onBack) {
        AddLocaleSheet(session, map.webView)
    }
}

/**
 * One row per [ADD_LOCALE_DEMO_KEYS] entry showing "(not added yet)" until "Add Spanish locale" is
 * pressed, then the real Spanish text — this before/after contrast is the proof that
 * `venue.translator.addLocale('es', ...)` worked, since neither key changes any POI/label name on
 * the map (see docs/features/add-locale.md, "Things to know"). The button calls
 * [addSpanishLocale], which adds the locale and reads every key back via `translate` in one round
 * trip. "Switch to Spanish" then calls [setLocale] (`runtime-locale`'s call), enabled once the
 * locale is added; its result (or error) is shown.
 */
@Composable
private fun AddLocaleSheet(session: AddLocaleSession, webView: WebView?) {
    val translations = session.translations
    val isAdded = translations != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        ADD_LOCALE_DEMO_KEYS.forEach { (key, labelRes) ->
            Text(text = stringResource(labelRes, key), style = MaterialTheme.typography.bodySmall)
            Text(
                text = translations?.get(key) ?: stringResource(R.string.add_locale_not_added),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Button(onClick = { session.addSpanish(webView) }) {
            Text(stringResource(if (isAdded) R.string.add_locale_readd else R.string.add_locale_add))
        }
        ErrorText(session.error?.resolve())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { session.switchToSpanish(webView) },
            enabled = isAdded && !session.isSpanishDisplayed,
        ) {
            Text(
                stringResource(
                    if (session.isSpanishDisplayed) R.string.add_locale_switched else R.string.add_locale_switch,
                ),
            )
        }
    }
}
