package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.OnBridgeEvent
import com.visioglobe.visioonemeet.ui.components.ColorChip
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState
import org.json.JSONObject

// custom-theme — JS side: web/src/features/custom-theme.js. See docs/features/custom-theme.md.

/** The SDK's own defaults for `custom-theme` (the theme a view starts with, per the View typings' `@defaultValue`s). */
private const val THEME_DEFAULT_ACCENT_COLOR = "#0094F0"
private const val THEME_DEFAULT_TEXT_ACCENT_COLOR = "#FFFFFF"

/** Accent colors offered for `view.accentColor` (hex — the SDK only accepts hex / `rgb(a)` / `hsl`). */
private val THEME_ACCENT_COLORS = listOf(
    THEME_DEFAULT_ACCENT_COLOR to R.string.color_visioglobe_blue,
    "#E53935" to R.string.color_red,
    "#43A047" to R.string.color_green,
    "#8E24AA" to R.string.color_purple,
)

/** Text colors offered for `view.textAccentColor`. */
private val THEME_TEXT_ACCENT_COLORS = listOf(
    THEME_DEFAULT_TEXT_ACCENT_COLOR to R.string.color_white,
    "#FFEB3B" to R.string.color_yellow,
    "#212121" to R.string.color_black,
)

/**
 * Asks the page for the current `custom-theme` state (`view.themes` + the current theme and
 * accent colors); the answer comes back via `AndroidBridge.onThemeStateChanged`.
 */
private fun WebView.getThemeState() = callBridge("getThemeState")

/** Applies a scene theme (`view.setTheme`). */
private fun WebView.setTheme(themeName: String) = callBridge("setTheme", themeName)

/** Recolors the SDK's UI buttons (`view.accentColor = color`). */
private fun WebView.setAccentColor(color: String) = callBridge("setAccentColor", color)

/** Recolors the SDK's UI button text (`view.textAccentColor = color`). */
private fun WebView.setTextAccentColor(color: String) = callBridge("setTextAccentColor", color)

/** Back to the 'day' theme and the default accent colors. */
private fun WebView.resetTheme() = callBridge("resetTheme")

/**
 * The whole `custom-theme` state, as reported by `AndroidBridge.onThemeStateChanged` after every
 * command. [themes] is `view.themes` as-is; [currentTheme] is tracked JS-side (the SDK has no
 * getter for it); [accentColor]/[textAccentColor] are read back from the view (the SDK returns
 * whatever string was set, its own defaults in lowercase hex). [status] names what just happened —
 * `"loaded"`, `"theme-applied"`, `"accent-applied"`, `"text-accent-applied"`, `"reset"` or
 * `"error"` ([message] carrying the SDK's error text).
 */
private data class ThemeState(
    val status: String,
    val themes: List<String>,
    val currentTheme: String,
    val accentColor: String,
    val textAccentColor: String,
    val message: String? = null,
)

/** Parses the JSON object argument of `AndroidBridge.onThemeStateChanged`. */
private fun parseThemeStatePayload(json: String): ThemeState {
    val root = JSONObject(json)
    val themes = root.getJSONArray("themes")
    return ThemeState(
        status = root.getString("status"),
        themes = List(themes.length()) { themes.getString(it) },
        currentTheme = root.getString("currentTheme"),
        accentColor = root.getString("accentColor"),
        textAccentColor = root.getString("textAccentColor"),
        message = if (root.has("message")) root.getString("message") else null,
    )
}

/**
 * The view is created with `enableShadows: true`: a theme only changes the scene lights, which are
 * only rendered with shadows on. The theme state is the page's, pushed after every command via
 * `AndroidBridge.onThemeStateChanged` and kept here, at screen level: the visitor closes the panel
 * to tap a place and see its card's accent-colored buttons.
 */
@Composable
fun CustomThemeScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    var themeState by remember { mutableStateOf<ThemeState?>(null) }
    OnBridgeEvent<BridgeEvent.ThemeStateChanged>(map) { themeState = parseThemeStatePayload(it.stateJson) }
    FeatureMapScreen(map, Feature.CustomTheme, onBack, enableShadows = true) {
        CustomThemeSheet(map.webView, themeState)
    }
}

/**
 * A segmented control built at runtime from
 * `view.themes` (raw SDK ids), accent color and accent text color chips, a Reset button, a hint
 * and a status line. Every change applies immediately. Everything is rendered from [themeState] —
 * the JS side is the source of truth, and this sheet is disposed whenever it's closed, which it
 * has to be to tap a place and see its card's accent-colored buttons. The state is requested once
 * when the sheet opens.
 */
@Composable
private fun CustomThemeSheet(webView: WebView?, themeState: ThemeState?) {
    LaunchedEffect(webView) { webView?.getThemeState() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(text = stringResource(R.string.theme_label), style = MaterialTheme.typography.labelLarge)
        val themes = themeState?.themes.orEmpty()
        if (themes.isEmpty()) {
            Text(text = stringResource(R.string.themes_loading), style = MaterialTheme.typography.bodyMedium)
        } else {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                themes.forEachIndexed { index, themeName ->
                    SegmentedButton(
                        selected = themeName == themeState?.currentTheme,
                        onClick = { webView?.setTheme(themeName) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = themes.size),
                    ) {
                        Text(themeName, maxLines = 1)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = stringResource(R.string.accent_color_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            THEME_ACCENT_COLORS.forEach { (hex, nameRes) ->
                ColorChip(
                    hex = hex,
                    name = stringResource(nameRes),
                    selected = hex.equals(themeState?.accentColor ?: THEME_DEFAULT_ACCENT_COLOR, ignoreCase = true),
                    onClick = { webView?.setAccentColor(hex) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.accent_text_color_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            THEME_TEXT_ACCENT_COLORS.forEach { (hex, nameRes) ->
                ColorChip(
                    hex = hex,
                    name = stringResource(nameRes),
                    selected = hex.equals(
                        themeState?.textAccentColor ?: THEME_DEFAULT_TEXT_ACCENT_COLOR,
                        ignoreCase = true,
                    ),
                    onClick = { webView?.setTextAccentColor(hex) },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = { webView?.resetTheme() }) {
            Text(stringResource(R.string.action_reset))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.custom_theme_hint), style = MaterialTheme.typography.bodyMedium)
        if (themeState?.status == "error") {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = themeState.message ?: stringResource(R.string.theme_change_failed),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
