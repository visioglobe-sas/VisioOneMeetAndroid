package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState
import org.json.JSONArray

// category-highlight — JS side: web/src/features/category-highlight.js.
// See docs/features/category-highlight.md.

/**
 * Requests the venue's full category list in one round trip (`venue.categories`). The result
 * arrives asynchronously via `AndroidBridge.onCategoriesLoaded`, echoing [requestId] back.
 */
private fun WebView.getCategories(requestId: Int) = callBridge("getCategories", requestId)

/**
 * Highlights every POI belonging to [categoryId] (`venue.pois.filter(...)` + `venue.updateSurface`
 * — there is no dedicated "highlight by category" SDK method). Fire-and-forget.
 */
private fun WebView.highlightCategory(categoryId: String) = callBridge("highlightCategory", categoryId)

/** Reverts the highlight applied by [highlightCategory], if any. */
private fun WebView.clearCategoryHighlight() = callBridge("clearCategoryHighlight")

/**
 * A single venue category, carried by the `AndroidBridge.onCategoriesLoaded` payload. [id] is the
 * SDK's raw `Category.id` (a numeric string on the shared demo map, e.g. "1".."11") — used for
 * filtering/highlighting. [label] is the human-readable name resolved via
 * `venue.translator.translateCategory()` on the JS side — display only.
 */
data class CategoryInfo(val id: String, val label: String)

/**
 * Parses the JSON array argument of `AndroidBridge.onCategoriesLoaded`, e.g.
 * `[{"id":"5","label":"Food and Beverage"},{"id":"9","label":"Shops"}]`.
 */
internal fun parseCategoriesPayload(json: String): List<CategoryInfo> {
    val array = JSONArray(json)
    return List(array.length()) { index ->
        val obj = array.getJSONObject(index)
        CategoryInfo(id = obj.getString("id"), label = obj.getString("label"))
    }
}

/**
 * Screen-level state of `category-highlight`: the venue's categories (requested the first time the
 * panel opens) and the highlighted one, so the panel still shows the selection the map shows after
 * a close/reopen. Only one category is highlighted at a time — [highlightCategory] reverts the
 * previous one on the JS side.
 */
@Stable
private class CategoryHighlightSession {
    var categories by mutableStateOf<List<CategoryInfo>>(emptyList())
        private set
    var selectedCategoryId by mutableStateOf<String?>(null)
        private set
    private var pendingRequestId: Int? = null

    fun requestCategoriesIfNeeded(webView: WebView?) {
        val view = webView ?: return
        if (categories.isNotEmpty() || pendingRequestId != null) return
        val requestId = nextBridgeRequestId()
        pendingRequestId = requestId
        view.getCategories(requestId)
    }

    fun onCategoriesLoaded(event: BridgeEvent.CategoriesLoaded) {
        if (event.requestId != pendingRequestId) return
        pendingRequestId = null
        categories = parseCategoriesPayload(event.categoriesJson)
    }

    /** Highlights [categoryId], or clears the highlight if it is the selected one. */
    fun toggle(webView: WebView?, categoryId: String) {
        if (selectedCategoryId == categoryId) clear(webView) else {
            webView?.highlightCategory(categoryId)
            selectedCategoryId = categoryId
        }
    }

    fun clear(webView: WebView?) {
        webView?.clearCategoryHighlight()
        selectedCategoryId = null
    }
}

@Composable
fun CategoryHighlightScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    val session = remember { CategoryHighlightSession() }
    OnBridgeEvent<BridgeEvent.CategoriesLoaded>(map) { session.onCategoriesLoaded(it) }
    FeatureMapScreen(map, Feature.CategoryHighlight, onBack) {
        CategoryHighlightSheet(session, map.webView)
    }
}

/**
 * One [FilterChip] per `venue.categories` entry (label = [CategoryInfo.label]; [CategoryInfo.id]
 * is what filtering/highlighting uses). Tapping a chip calls [highlightCategory]; tapping the
 * selected chip again, or "Clear", calls [clearCategoryHighlight].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryHighlightSheet(session: CategoryHighlightSession, webView: WebView?) {
    LaunchedEffect(webView) { session.requestCategoriesIfNeeded(webView) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        if (session.categories.isEmpty()) {
            Text(stringResource(R.string.categories_loading))
            return@Column
        }
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            session.categories.forEach { category ->
                FilterChip(
                    selected = category.id == session.selectedCategoryId,
                    onClick = { session.toggle(webView, category.id) },
                    label = { Text(category.label) },
                    modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                )
            }
        }
        if (session.selectedCategoryId != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { session.clear(webView) }) {
                Text(stringResource(R.string.action_clear))
            }
        }
    }
}
