package com.visioglobe.visioonemeet.ui

import android.content.res.Resources
import android.webkit.WebView
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.visioglobe.visioonemeet.R
import com.visioglobe.visioonemeet.model.DemoMap
import com.visioglobe.visioonemeet.model.Feature
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject

// Stand-in for a real occupancy sensor feed: cycles a POI's surface through
// these colors on a timer. See docs/features/occupancy-simulated.md.
private val OCCUPANCY_COLORS = listOf("#2ECC71", "#F1C40F", "#E74C3C")
private const val OCCUPANCY_INTERVAL_MS = 2500L

// `simulated-position`'s interpolation loop: how often a new point is injected, and how much of
// the origin-destination segment (as a 0..1 fraction) is covered per tick — ~7.5s to walk one way
// at these values. See docs/features/simulated-position.md.
private const val SIMULATED_POSITION_INTERVAL_MS = 150L
private const val SIMULATED_POSITION_STEP = 0.02
private const val SIMULATED_POSITION_MIN_RADIUS_METERS = 1f
private const val SIMULATED_POSITION_MAX_RADIUS_METERS = 20f
private const val SIMULATED_POSITION_DEFAULT_RADIUS_METERS = 5f

/**
 * Calls `window.MapBridge.updateOccupancy` in the WebView. `color: null` resets the surface to
 * its normal appearance rather than a hardcoded default.
 *
 * Arguments are JSON-encoded via [org.json] before being interpolated into the generated script
 * — never raw string concatenation, to avoid JS injection. Every bridge call in this file follows
 * that rule.
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
 * Recenters the camera on the whole venue by calling `view.goToGlobal()` in the WebView. See
 * docs/features/reset-view.md.
 */
private fun WebView.goToGlobal() {
    evaluateJavascript("window.MapBridge.goToGlobal()", null)
}

/**
 * Centers/zooms the camera on the POI matching `placeId` by calling `window.MapBridge.goToPlace`
 * in the WebView (`view.goToPOI` under the hood). See docs/features/goto-poi.md.
 */
private fun WebView.goToPlace(placeId: String) {
    // JSONObject.quote() JSON-encodes the single string argument (quoting + escaping) —
    // JSONArray().put(placeId) would wrap it in `[...]`, which is wrong here since
    // MapBridge.goToPlace takes a bare placeId string, not an array (unlike updateOccupancy).
    val script = "window.MapBridge.goToPlace(${JSONObject.quote(placeId)})"
    evaluateJavascript(script, null)
}

/**
 * Reverts the highlight applied by [goToPlace], without moving the camera back. See
 * docs/features/goto-poi.md.
 */
private fun WebView.clearPlace() {
    evaluateJavascript("window.MapBridge.clearPlace()", null)
}

/**
 * Moves the camera to the floor matching [floorId] within building [buildingId] by calling
 * `window.MapBridge.goToFloor` in the WebView (`view.goToFloor` under the hood). See
 * docs/features/floor-selector.md.
 */
private fun WebView.goToFloor(buildingId: String, floorId: String) {
    val script = "window.MapBridge.goToFloor(${JSONObject.quote(buildingId)}, ${JSONObject.quote(floorId)})"
    evaluateJavascript(script, null)
}

/**
 * Computes and draws a route between [origin] and [destination] (both Place IDs) by calling
 * `window.MapBridge.computeNavigation` in the WebView (`venue.computeNavigation` +
 * `venue.createNavigationTrace` + `view.setCurrentNavigationTrace` under the hood). See
 * docs/features/compute-navigation.md.
 */
private fun WebView.computeNavigation(origin: String, destination: String, isAccessible: Boolean) {
    val script = "window.MapBridge.computeNavigation(" +
        "${JSONObject.quote(origin)}, ${JSONObject.quote(destination)}, $isAccessible)"
    evaluateJavascript(script, null)
}

/** Removes the route drawn by [computeNavigation], if any. See docs/features/compute-navigation.md. */
private fun WebView.clearNavigation() {
    evaluateJavascript("window.MapBridge.clearNavigation()", null)
}

/**
 * Computes and draws a route between [origin] and [destination], optionally routing around
 * elevators, by calling `window.MapBridge.computeNavigationExcludingModalities` in the WebView
 * (`venue.computeNavigation()`'s `excludedAttributes` option under the hood — `['lift']` when
 * [excludeElevator] is true, `[]` otherwise). Same request shape, error surfacing
 * (`onNavigationError`/`onNavigationComputed`) as [computeNavigation]; both share one JS
 * implementation. See docs/features/navigation-exclude-modalities.md.
 */
private fun WebView.computeNavigationExcludingModalities(
    origin: String,
    destination: String,
    isAccessible: Boolean,
    excludeElevator: Boolean,
) {
    val script = "window.MapBridge.computeNavigationExcludingModalities(" +
        "${JSONObject.quote(origin)}, ${JSONObject.quote(destination)}, $isAccessible, $excludeElevator)"
    evaluateJavascript(script, null)
}

/**
 * A `NavigationTraceUpdateOptions` payload (colors only — `displayMode`/`thickness` can't be
 * changed after a trace is created), one of [NAVIGATION_TRACE_PRESETS], with the localized name
 * shown on its chip. See docs/features/custom-navigation-trace.md.
 */
private data class NavigationTraceColors(
    @StringRes val nameRes: Int,
    val progressColor: String,
    val progressOutlineColor: String,
    val progressFutureColor: String,
    val previewColor: String,
    val previewOutlineColor: String,
)

/**
 * `custom-navigation-trace`'s color catalogue, each a full [NavigationTraceColors] payload applied
 * via [WebView.updateNavigationTraceStyle] (`venue.updateNavigationTrace()` under the hood).
 * `visioglobeBlue` restates the SDK's own documented `Line` defaults (`progressColor` `#0094F0`,
 * the inactive/preview segments `#C5C5C5`) as an explicit preset rather than a fake "reset" — there
 * is no SDK call to revert to default once changed. See docs/features/custom-navigation-trace.md.
 */
private val NAVIGATION_TRACE_PRESETS = linkedMapOf(
    "visioglobeBlue" to NavigationTraceColors(
        nameRes = R.string.color_visioglobe_blue,
        progressColor = "#0094F0",
        progressOutlineColor = "#FFFFFF",
        progressFutureColor = "#C5C5C5",
        previewColor = "#C5C5C5",
        previewOutlineColor = "#FFFFFF",
    ),
    "brandRed" to NavigationTraceColors(
        nameRes = R.string.color_red,
        progressColor = "#E53935",
        progressOutlineColor = "#FFFFFF",
        progressFutureColor = "#F8C9C7",
        previewColor = "#F8C9C7",
        previewOutlineColor = "#FFFFFF",
    ),
    "brandGreen" to NavigationTraceColors(
        nameRes = R.string.color_green,
        progressColor = "#2E7D32",
        progressOutlineColor = "#FFFFFF",
        progressFutureColor = "#C8E6C9",
        previewColor = "#C8E6C9",
        previewOutlineColor = "#FFFFFF",
    ),
    "brandPurple" to NavigationTraceColors(
        nameRes = R.string.color_purple,
        progressColor = "#8E24AA",
        progressOutlineColor = "#FFFFFF",
        progressFutureColor = "#E1BEE7",
        previewColor = "#E1BEE7",
        previewOutlineColor = "#FFFFFF",
    ),
)

/**
 * Restyles the trace currently drawn by [computeNavigation] by calling
 * `window.MapBridge.updateNavigationTraceStyle` in the WebView (`venue.updateNavigationTrace()`
 * under the hood). A no-op if no trace is currently displayed. See
 * docs/features/custom-navigation-trace.md.
 */
private fun WebView.updateNavigationTraceStyle(colors: NavigationTraceColors) {
    val options = JSONObject().apply {
        put("progressColor", colors.progressColor)
        put("progressOutlineColor", colors.progressOutlineColor)
        put("progressFutureColor", colors.progressFutureColor)
        put("previewColor", colors.previewColor)
        put("previewOutlineColor", colors.previewOutlineColor)
    }
    evaluateJavascript("window.MapBridge.updateNavigationTraceStyle($options)", null)
}

/**
 * Shows/hides one of the SDK's own default UI overlays by calling
 * `window.MapBridge.setUIPartVisible` in the WebView (`view.setUIPartVisible` under the hood).
 * [uiPart] must be one of the SDK's exact, case-sensitive values (`floorSelector`, `navigation`,
 * `poiDetails`, `search`, `userTracking`) — see [UI_PART_TOGGLES] below. See
 * docs/features/ui-part-visibility.md.
 */
internal fun WebView.setUiPartVisible(uiPart: String, isVisible: Boolean) {
    val script = "window.MapBridge.setUIPartVisible(${JSONObject.quote(uiPart)}, $isVisible)"
    evaluateJavascript(script, null)
}

/**
 * The 5 UI parts the SDK's `View.setUIPartVisible` accepts, paired with a human-readable label.
 * These string values are exact and case-sensitive — they are not free-form, the SDK defines no
 * others. See docs/features/ui-part-visibility.md.
 */
private val UI_PART_TOGGLES = listOf(
    "floorSelector" to R.string.ui_part_floor_selector_label,
    "navigation" to R.string.ui_part_navigation_label,
    "poiDetails" to R.string.ui_part_poi_details_label,
    "search" to R.string.ui_part_search_label,
    "userTracking" to R.string.ui_part_user_tracking_label,
)

/**
 * Switches the SDK's building-exploration mode by calling `window.MapBridge.setExploreMode` in the
 * WebView (`view.currentExploreMode = mode` under the hood). [mode] must be one of the SDK's exact,
 * case-sensitive `ExploreMode` values (`global`, `building`, `floor`) — see [EXPLORE_MODE_OPTIONS]
 * below. See docs/features/explore-mode.md.
 */
private fun WebView.setExploreMode(mode: String) {
    val script = "window.MapBridge.setExploreMode(${JSONObject.quote(mode)})"
    evaluateJavascript(script, null)
}

/**
 * The 3 explore modes the SDK's `View.currentExploreMode` accepts, paired with a human-readable
 * label. These string values are exact and case-sensitive — they are not free-form, the SDK
 * defines no others. See docs/features/explore-mode.md.
 */
private val EXPLORE_MODE_OPTIONS = listOf(
    "global" to R.string.explore_mode_option_global,
    "building" to R.string.explore_mode_option_building,
    "floor" to R.string.explore_mode_option_floor,
)

/**
 * Resolves the WGS84 position of [originId]/[destinationId] in one round trip by calling
 * `window.MapBridge.resolvePositions` in the WebView. The result arrives asynchronously via
 * `AndroidBridge.onPositionsResolved`, echoing [requestId] back so the caller can match the
 * response to this particular call. See docs/features/simulated-position.md.
 */
private fun WebView.resolvePositions(requestId: Int, originId: String, destinationId: String) {
    val script = "window.MapBridge.resolvePositions(" +
        "$requestId, ${JSONObject.quote(originId)}, ${JSONObject.quote(destinationId)})"
    evaluateJavascript(script, null)
}

/**
 * Injects/updates the simulated tracked position and its accuracy circle by calling
 * `window.MapBridge.injectTrackedPosition` in the WebView (`view.injectTrackedPosition` under the
 * hood, after setting `view.allowTracking = true` on the first call only — re-setting it re-locks
 * the camera). [precisionCircleRadiusMeters] is the accuracy
 * circle's radius, in meters. See docs/features/simulated-position.md.
 */
private fun WebView.injectTrackedPosition(latitude: Double, longitude: Double, precisionCircleRadiusMeters: Double) {
    val script = "window.MapBridge.injectTrackedPosition($latitude, $longitude, $precisionCircleRadiusMeters)"
    evaluateJavascript(script, null)
}

/**
 * Removes the marker/circle injected by [injectTrackedPosition] by calling
 * `window.MapBridge.stopTrackedPosition` in the WebView (`view.allowTracking = false` under the
 * hood — there is no dedicated stop method on the SDK). See docs/features/simulated-position.md.
 */
private fun WebView.stopTrackedPosition() {
    evaluateJavascript("window.MapBridge.stopTrackedPosition()", null)
}

/**
 * Binds/unbinds the camera's focus to the tracked position by calling
 * `window.MapBridge.setCameraLockOnPosition` in the WebView (`view.lockCameraPositionOnTracking`
 * under the hood). See docs/features/camera-lock-on-position.md.
 */
private fun WebView.setCameraLockOnPosition(locked: Boolean) {
    evaluateJavascript("window.MapBridge.setCameraLockOnPosition($locked)", null)
}

/**
 * Recolors the tracked position's precision circle (never the arrow icon at its center) by calling
 * `window.MapBridge.setPositionTrackerStyle` in the WebView
 * (`view.updatePositionTrackerGraphicOptions({ color, opacity })` under the hood). Applied at the
 * next `injectTrackedPosition`; the JS side also re-applies it on every tracking (re)start, since
 * the SDK resets it whenever tracking stops. [onApplied] runs only if the SDK accepted the style
 * (the JS method returns `false` when it throws on an invalid color). See
 * docs/features/accuracy-circle-style.md.
 */
private fun WebView.setPositionTrackerStyle(color: String, opacity: Float, onApplied: () -> Unit) {
    // Rounded to one decimal so 0.1-step slider values reach the SDK as e.g. 0.3, not 0.30000001.
    val roundedOpacity = (opacity * 10).roundToInt() / 10.0
    evaluateJavascript(
        "window.MapBridge.setPositionTrackerStyle(${JSONObject.quote(color)}, $roundedOpacity)",
    ) { result -> if (result == "true") onApplied() }
}

/**
 * Makes the POI matching [placeId]'s surfaces interactive (or reverts them) by calling
 * `window.MapBridge.setSurfaceInteractive` in the WebView (`venue.updateSurface`'s `isInteractive`
 * flag under the hood). See docs/features/clickable-surface.md.
 */
private fun WebView.setSurfaceInteractive(placeId: String, interactive: Boolean) {
    val script = "window.MapBridge.setSurfaceInteractive(${JSONObject.quote(placeId)}, $interactive)"
    evaluateJavascript(script, null)
}

/**
 * Reloads CustomData from the server and reads the given POI's CustomData in one round trip by
 * calling `window.MapBridge.loadCustomData` in the WebView (`venue.refreshCustomData()` then
 * `venue.getPOICustomData(poi)` under the hood). The result arrives asynchronously via
 * `AndroidBridge.onCustomDataLoaded`, echoing [requestId] back, same convention as
 * [WebView.resolvePositions]. See docs/features/custom-data.md.
 */
private fun WebView.loadCustomData(requestId: Int, placeId: String) {
    val script = "window.MapBridge.loadCustomData($requestId, ${JSONObject.quote(placeId)})"
    evaluateJavascript(script, null)
}

/**
 * Requests the venue's full category list in one round trip by calling
 * `window.MapBridge.getCategories` in the WebView (`venue.categories` under the hood). The result
 * arrives asynchronously via `AndroidBridge.onCategoriesLoaded`, echoing [requestId] back, same
 * convention as [WebView.loadCustomData]/[WebView.resolvePositions]. See
 * docs/features/category-highlight.md.
 */
private fun WebView.getCategories(requestId: Int) {
    val script = "window.MapBridge.getCategories($requestId)"
    evaluateJavascript(script, null)
}

/**
 * Highlights every POI belonging to [categoryId] by calling `window.MapBridge.highlightCategory`
 * in the WebView (`venue.pois.filter(...)` + `venue.updateSurface` under the hood — there is no
 * dedicated "highlight by category" SDK method). Fire-and-forget, no response expected, same
 * convention as [WebView.setSurfaceInteractive]. See docs/features/category-highlight.md.
 */
private fun WebView.highlightCategory(categoryId: String) {
    val script = "window.MapBridge.highlightCategory(${JSONObject.quote(categoryId)})"
    evaluateJavascript(script, null)
}

/**
 * Reverts the highlight applied by [highlightCategory], if any, by calling
 * `window.MapBridge.clearCategoryHighlight` in the WebView. See docs/features/category-highlight.md.
 */
private fun WebView.clearCategoryHighlight() {
    evaluateJavascript("window.MapBridge.clearCategoryHighlight()", null)
}

/**
 * Creates a POI at runtime and attaches a Label to it (copying its position from [anchorId]'s own
 * first label/marker/image) by calling `window.MapBridge.createDynamicPoi` in the WebView
 * (`venue.createPOI` + `venue.createLabel` under the hood). The result — success, or one of the
 * "anchor not found"/"anchor has no position"/"duplicate id" normal-state outcomes — arrives
 * asynchronously via `AndroidBridge.onDynamicPoiCreated`, echoing [requestId] back, same convention
 * as [WebView.loadCustomData]/[WebView.getCategories]. See docs/features/dynamic-poi-crud.md.
 */
private fun WebView.createDynamicPoi(requestId: Int, newId: String, anchorId: String, labelText: String) {
    val script = "window.MapBridge.createDynamicPoi(" +
        "$requestId, ${JSONObject.quote(newId)}, ${JSONObject.quote(anchorId)}, ${JSONObject.quote(labelText)})"
    evaluateJavascript(script, null)
}

/**
 * Updates the tracked dynamic POI's label text by calling `window.MapBridge.updateDynamicPoiLabel`
 * in the WebView (`venue.updateLabel` under the hood) — the actual "modify" story for a dynamic
 * POI, since `venue.updatePOI` itself can only change categories, never anything visual.
 * Fire-and-forget, no response expected, same convention as [WebView.highlightCategory]. See
 * docs/features/dynamic-poi-crud.md.
 */
private fun WebView.updateDynamicPoiLabel(text: String) {
    val script = "window.MapBridge.updateDynamicPoiLabel(${JSONObject.quote(text)})"
    evaluateJavascript(script, null)
}

/**
 * Removes the tracked dynamic POI by calling `window.MapBridge.removeDynamicPoi` in the WebView
 * (`venue.removePOI` under the hood, which cascades to remove the attached label from the view
 * too — no separate label removal needed). Fire-and-forget, no response expected. See
 * docs/features/dynamic-poi-crud.md.
 */
private fun WebView.removeDynamicPoi() {
    evaluateJavascript("window.MapBridge.removeDynamicPoi()", null)
}

/**
 * Requests the venue's current locale in one round trip by calling
 * `window.MapBridge.getCurrentLocale` in the WebView (`venue.currentLocale` under the hood). The
 * result arrives asynchronously via `AndroidBridge.onLocaleResolved`, echoing [requestId] back,
 * same convention as [WebView.getCategories]/[WebView.loadCustomData] — and shared with
 * [WebView.setLocale] below, since both report the same `{status, locale}` shape. See
 * docs/features/runtime-locale.md.
 */
private fun WebView.getCurrentLocale(requestId: Int) {
    val script = "window.MapBridge.getCurrentLocale($requestId)"
    evaluateJavascript(script, null)
}

/**
 * Switches the map's displayed language to [locale] by calling `window.MapBridge.setLocale` in
 * the WebView (`venue.setCurrentLocale(locale): Promise<void>` under the hood — the SDK re-renders
 * labels/UI itself, no manual re-fetch needed). The result arrives asynchronously via
 * `AndroidBridge.onLocaleResolved`, echoing [requestId] back, same convention as
 * [WebView.getCurrentLocale]. See docs/features/runtime-locale.md.
 */
private fun WebView.setLocale(requestId: Int, locale: String) {
    val script = "window.MapBridge.setLocale($requestId, ${JSONObject.quote(locale)})"
    evaluateJavascript(script, null)
}

/**
 * Adds the `'es'` locale at runtime by calling `window.MapBridge.addSpanishLocale` in the WebView
 * (`venue.translator.addLocale('es', resources)` under the hood, immediately followed by
 * `venue.translator.translate(key, 'es')` for each key in that fixed dictionary). The result
 * arrives asynchronously via `AndroidBridge.onAddLocaleResolved`, echoing [requestId] back, same
 * convention as [WebView.getCurrentLocale]/[WebView.setLocale]. See docs/features/add-locale.md.
 */
private fun WebView.addSpanishLocale(requestId: Int) {
    val script = "window.MapBridge.addSpanishLocale($requestId)"
    evaluateJavascript(script, null)
}

/**
 * Resolves [placeId] to a POI and caches its surfaces as the zone geofencing checks against by
 * calling `window.MapBridge.resolveZone` in the WebView (`venue.pois.find` + `poi.surfaces` under
 * the hood — no dedicated SDK geofence concept). The result — 'found', 'not-found', or
 * 'no-surface' — arrives asynchronously via `AndroidBridge.onZoneResolved`, echoing [requestId]
 * back, same convention as [WebView.resolvePositions]. See docs/features/geofencing.md.
 */
private fun WebView.resolveZone(requestId: Int, placeId: String) {
    val script = "window.MapBridge.resolveZone($requestId, ${JSONObject.quote(placeId)})"
    evaluateJavascript(script, null)
}

/**
 * Reverts the alert color applied while the tracked position was inside the zone, if any, and
 * forgets it, by calling `window.MapBridge.clearZone` in the WebView. See
 * docs/features/geofencing.md.
 */
private fun WebView.clearZone() {
    evaluateJavascript("window.MapBridge.clearZone()", null)
}

/**
 * Tests the given position against the zone resolved by [resolveZone] by calling
 * `window.MapBridge.checkGeofence` in the WebView (a hand-rolled point-in-polygon check against
 * `Surface.positions` — there is no SDK method for this). Meant to be called on every tick of the
 * tracked-position simulation loop, alongside [injectTrackedPosition]. The current inside/outside
 * state is reported back via `AndroidBridge.onGeofenceStateChanged`. See
 * docs/features/geofencing.md.
 */
private fun WebView.checkGeofence(latitude: Double, longitude: Double) {
    val script = "window.MapBridge.checkGeofence($latitude, $longitude)"
    evaluateJavascript(script, null)
}

/**
 * Result of a [WebView.loadCustomData] call: [requestId] echoes the value passed to that call, so
 * a stale response can be told apart from the current one, same convention as
 * [ResolvedPositionsPair]. [customData] is `null` when `placeId` didn't resolve to a POI at all
 * ("POI not found"), and an empty map when the POI resolves but has no CustomData (or the server
 * has none published yet) — `venue.getPOICustomData()` itself never returns null/undefined, only
 * `{}`, so that distinction is made on the JS side, not by the SDK. See
 * docs/features/custom-data.md.
 */
data class CustomDataResult(val requestId: Int, val customData: Map<String, String>?)

/**
 * Parses the JSON argument of `AndroidBridge.onCustomDataLoaded`, e.g. `{"price":"12€"}`, `{}` (no
 * CustomData for that POI), or the literal string `"null"` (`JSON.stringify(null)` on the JS side,
 * meaning the POI id didn't resolve at all).
 */
internal fun parseCustomDataPayload(json: String): Map<String, String>? {
    if (json == "null") return null
    val root = JSONObject(json)
    return root.keys().asSequence().associateWith { key -> root.getString(key) }
}

/** A WGS84 position resolved for a POI, as reported by `AndroidBridge.onPositionsResolved`. */
data class ResolvedPosition(val latitude: Double, val longitude: Double)

/**
 * Result of a [WebView.resolvePositions] call: [requestId] echoes the value passed to that call
 * so a stale response (e.g. a Start press superseded by another one) can be told apart from the
 * current one; [origin]/[destination] are `null` when the corresponding POI id didn't resolve to
 * a position ("POI not found" case). See docs/features/simulated-position.md.
 */
data class ResolvedPositionsPair(
    val requestId: Int,
    val origin: ResolvedPosition?,
    val destination: ResolvedPosition?,
)

/**
 * Parses one of the two JSON arguments of `AndroidBridge.onPositionsResolved`, e.g.
 * `{"latitude":48.858,"longitude":2.294}` or the literal string `"null"` (`JSON.stringify(null)`
 * on the JS side) when the POI id didn't resolve to a position.
 */
internal fun parseResolvedPosition(json: String): ResolvedPosition? {
    if (json == "null") return null
    val root = JSONObject(json)
    return ResolvedPosition(latitude = root.getDouble("latitude"), longitude = root.getDouble("longitude"))
}

/**
 * Result of a [WebView.resolveZone] call: [requestId] echoes the value passed to that call, so a
 * stale response can be told apart from the current one, same convention as [ResolvedPositionsPair].
 * [status] is one of `"found"`, `"not-found"` (no POI matches the given id), or `"no-surface"` (the
 * POI has no `surfaces` to test a position against). See docs/features/geofencing.md.
 */
data class ZoneResolution(val requestId: Int, val status: String)

/** A single venue category, carried by the `AndroidBridge.onCategoriesLoaded` payload. [id] is the
 * SDK's raw `Category.id` (a numeric string on the shared demo map, e.g. "1".."11") — used for
 * filtering/highlighting. [label] is the human-readable name resolved via
 * `venue.translator.translateCategory()` on the JS side — display only. See
 * docs/features/category-highlight.md. */
data class CategoryInfo(val id: String, val label: String)

/**
 * Result of a [WebView.getCategories] call: [requestId] echoes the value passed to that call, so a
 * stale response can be told apart from the current one, same convention as [CustomDataResult].
 */
data class CategoriesResult(val requestId: Int, val categories: List<CategoryInfo>)

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
 * Result of a [WebView.getCurrentLocale] or [WebView.setLocale] call, carried by
 * `AndroidBridge.onLocaleResolved`. [requestId] echoes the value passed to that call, same
 * convention as [CategoriesResult]/[CustomDataResult]. [locale] is the venue's resulting current
 * locale (e.g. `"en"`, `"fr"`) on success, `null` on failure, with [errorMessage] then carrying its
 * text. See docs/features/runtime-locale.md.
 */
data class LocaleResult(val requestId: Int, val locale: String?, val errorMessage: String?)

/**
 * Parses the JSON object argument of `AndroidBridge.onLocaleResolved`, e.g.
 * `{"status":"ok","locale":"en"}` or `{"status":"error","message":"..."}`.
 */
internal fun parseLocaleResultPayload(requestId: Int, json: String): LocaleResult {
    val root = JSONObject(json)
    return if (root.getString("status") == "ok") {
        LocaleResult(requestId = requestId, locale = root.getString("locale"), errorMessage = null)
    } else {
        LocaleResult(
            requestId = requestId,
            locale = null,
            errorMessage = root.optString("message").ifBlank { null },
        )
    }
}

/**
 * Result of a [WebView.addSpanishLocale] call, carried by `AndroidBridge.onAddLocaleResolved`.
 * [requestId] echoes the value passed to that call, same convention as [LocaleResult].
 * [translations] maps each of `add-locale`'s fixed demo keys to the Spanish text
 * `venue.translator.translate(key, 'es')` returned right after `addLocale('es', ...)` — non-null
 * on success, `null` on failure with [errorMessage] then carrying its text. See
 * docs/features/add-locale.md.
 */
data class AddLocaleResult(val requestId: Int, val translations: Map<String, String>?, val errorMessage: String?)

/**
 * Parses the JSON object argument of `AndroidBridge.onAddLocaleResolved`, e.g.
 * `{"status":"ok","translations":{"search-for-anything":"Busca lo que quieras","welcome-message":"¡Bienvenido al mapa!"}}`
 * or `{"status":"error","message":"..."}`.
 */
internal fun parseAddLocaleResultPayload(requestId: Int, json: String): AddLocaleResult {
    val root = JSONObject(json)
    return if (root.getString("status") == "ok") {
        val translationsObj = root.getJSONObject("translations")
        val translations = translationsObj.keys().asSequence().associateWith { key -> translationsObj.getString(key) }
        AddLocaleResult(requestId = requestId, translations = translations, errorMessage = null)
    } else {
        AddLocaleResult(
            requestId = requestId,
            translations = null,
            errorMessage = root.optString("message").ifBlank { null },
        )
    }
}

/**
 * Result of a [WebView.createDynamicPoi] call, carried by `AndroidBridge.onDynamicPoiCreated`.
 * [requestId] echoes the value passed to that call, same convention as [CustomDataResult]/
 * [CategoriesResult]. [status] is one of: `"created"` ([id]/[text] then non-null — the tracked
 * POI's id and its label's text), `"anchor-not-found"` (the anchor Place ID doesn't resolve to a
 * POI), `"anchor-has-no-position"` (the anchor POI has no label/marker/image to copy a position
 * from), `"duplicate-id"` (`venue.createPOI` threw `POIAlreadyExistsError`), or `"error"` (any
 * other failure, [message] carrying its text). See docs/features/dynamic-poi-crud.md.
 */
data class DynamicPoiCreationResult(
    val requestId: Int,
    val status: String,
    val id: String? = null,
    val text: String? = null,
    val message: String? = null,
)

/**
 * Parses the JSON object argument of `AndroidBridge.onDynamicPoiCreated`, e.g.
 * `{"status":"created","id":"demo-poi-1","text":"New POI"}` or `{"status":"anchor-not-found"}`.
 */
internal fun parseDynamicPoiCreationPayload(requestId: Int, json: String): DynamicPoiCreationResult {
    val root = JSONObject(json)
    return DynamicPoiCreationResult(
        requestId = requestId,
        status = root.getString("status"),
        id = if (root.has("id")) root.getString("id") else null,
        text = if (root.has("text")) root.getString("text") else null,
        message = if (root.has("message")) root.getString("message") else null,
    )
}

/** A single floor of the exposed building, carried by the `AndroidBridge.onFloorsReady` payload. See docs/features/floor-selector.md. */
data class FloorInfo(val id: String, val name: String, val levelIndex: Int)

/**
 * Snapshot of the floor-selector state, seeded once by `AndroidBridge.onFloorsReady` and kept in
 * sync afterwards by `AndroidBridge.onFloorChanged`. [floors] belongs to a single building —
 * today always the venue's first one, see `web/src/main.js`'s `main()` — and [currentFloorId]
 * reflects the SDK's actual current floor, whether it changed via this overlay's buttons or the
 * SDK's own default floor-selector widget. See docs/features/floor-selector.md.
 */
data class FloorSelectorState(
    val buildingId: String? = null,
    val floors: List<FloorInfo> = emptyList(),
    val currentFloorId: String? = null,
)

/**
 * Parses the JSON object emitted once by `window.MapBridge`'s floors-ready payload in
 * `web/src/main.js`, e.g.
 * `{"buildingId":"b1","currentFloorId":"f0","floors":[{"id":"f0","name":"Ground floor","levelIndex":0}]}`.
 * `name` falls back to an empty string when the venue has no translation for it, same convention
 * as [parsePoiClickPayload].
 */
internal fun parseFloorsReadyPayload(json: String): FloorSelectorState {
    val root = JSONObject(json)
    val floorsArray = root.getJSONArray("floors")
    val floors = List(floorsArray.length()) { index ->
        val entry = floorsArray.getJSONObject(index)
        FloorInfo(
            id = entry.getString("id"),
            name = entry.optString("name", ""),
            levelIndex = entry.getInt("levelIndex"),
        )
    }
    return FloorSelectorState(
        // `opt(...) as? String` (rather than `optString`) treats a missing key and an explicit
        // JSON `null` (e.g. `currentFloorId` when the SDK has no current floor yet) the same way:
        // JSONObject.NULL is not a String instance, so the cast yields null in both cases.
        buildingId = root.opt("buildingId") as? String,
        floors = floors,
        currentFloorId = root.opt("currentFloorId") as? String,
    )
}

// --- Shared building blocks -------------------------------------------------------------------

private val bridgeRequestIds = AtomicInteger()

/**
 * Next id to echo through a request/response bridge call (`resolvePositions`, `loadCustomData`,
 * ...). One counter for the whole app: a response can never be mistaken for — or be equal to — the
 * response of an earlier request, even one sent from a previous opening of the panel.
 */
internal fun nextBridgeRequestId(): Int = bridgeRequestIds.getAndIncrement()

/** A localized message held outside composition: a string resource and its format arguments. */
data class UiText(@StringRes val res: Int, val args: List<Any> = emptyList())

@Composable
private fun UiText.resolve(): String =
    if (args.isEmpty()) stringResource(res) else stringResource(res, *args.toTypedArray())

/** Parses a `#RRGGBB` color, the format of every color preset in this file. */
internal fun parseHexColor(hex: String): Color = Color(hex.toColorInt())

/**
 * One color choice chip. The swatch gets an outline in the panel's content color, so dark (Black)
 * and light (White/Yellow) swatches stay visible on either a dark or a light panel.
 */
@Composable
private fun ColorChip(hex: String, name: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(name) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(hex))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
            )
        },
        modifier = Modifier.padding(end = 8.dp),
    )
}

/** An error line under a control, or nothing when [text] is null. */
@Composable
private fun ErrorText(text: String?) {
    if (text == null) return
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = text, color = MaterialTheme.colorScheme.error)
}

/**
 * The From/To place-id fields shared by every itinerary screen ([ItineraryControls]) and every
 * tracked-position screen ([TrackedPositionSimulationControls]).
 */
@Composable
private fun FromToFields(
    from: String,
    onFromChange: (String) -> Unit,
    to: String,
    onToChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = from,
            onValueChange = onFromChange,
            label = { Text(stringResource(R.string.field_from_place_id)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            value = to,
            onValueChange = onToChange,
            label = { Text(stringResource(R.string.field_to_place_id)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * One full-width button per option, the [current] one rendered as a filled, disabled [Button]
 * (tapping it again would be a no-op), every other one as an [OutlinedButton] calling [onSelect].
 * Used by `floor-selector`, `runtime-locale` and `explore-mode`.
 */
@Composable
private fun CurrentOptionButtons(
    options: List<Pair<String, String>>,
    current: String?,
    onSelect: (String) -> Unit,
) {
    options.forEach { (value, label) ->
        val buttonModifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
        if (value == current) {
            Button(onClick = {}, enabled = false, modifier = buttonModifier) { Text(label) }
        } else {
            OutlinedButton(onClick = { onSelect(value) }, modifier = buttonModifier) { Text(label) }
        }
    }
}

/** Screen-level text field value, for screens whose only state is one place id. */
@Stable
class PlaceIdSession(initialPlaceId: String) {
    var placeId by mutableStateOf(initialPlaceId)
}

// --- Feature controls -----------------------------------------------------------------------

@Composable
fun ResetViewOverlay(webView: WebView?) {
    Button(
        onClick = { webView?.goToGlobal() },
        modifier = Modifier.padding(16.dp),
    ) {
        Text(stringResource(R.string.feature_reset_view_title))
    }
}

/**
 * Screen-level state of `occupancy-simulated`: the place id and whether the simulation runs. The
 * loop itself is [OccupancySimulationHost], composed at screen level, so the simulation keeps
 * running with the panel closed (the visitor closes it to watch the map). See
 * docs/features/occupancy-simulated.md.
 */
@Stable
class OccupancySession(initialPlaceId: String) {
    var placeId by mutableStateOf(initialPlaceId)
    var isRunning by mutableStateOf(false)
}

/**
 * The occupancy loop: while [OccupancySession.isRunning], cycles the place's surface through
 * [OCCUPANCY_COLORS]. Stopping it — or leaving the screen, which cancels this effect — resets the
 * surface in the `finally` block rather than leaving it on the last simulated color.
 */
@Composable
fun OccupancySimulationHost(session: OccupancySession, webView: WebView?) {
    LaunchedEffect(session.isRunning, webView) {
        val view = webView ?: return@LaunchedEffect
        if (!session.isRunning) return@LaunchedEffect
        val targetPlaceId = session.placeId.trim()
        var colorIndex = 0
        view.updateOccupancy(targetPlaceId, OCCUPANCY_COLORS[colorIndex])
        try {
            while (isActive) {
                delay(OCCUPANCY_INTERVAL_MS)
                colorIndex = (colorIndex + 1) % OCCUPANCY_COLORS.size
                view.updateOccupancy(targetPlaceId, OCCUPANCY_COLORS[colorIndex])
            }
        } finally {
            view.updateOccupancy(targetPlaceId, null)
        }
    }
}

/** FAB-triggered control for `occupancy-simulated`, bound to the screen-level [OccupancySession]. */
@Composable
fun OccupancySimulationOverlay(session: OccupancySession) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = session.placeId,
            onValueChange = { session.placeId = it },
            label = { Text(stringResource(R.string.field_place_id)) },
            singleLine = true,
            enabled = !session.isRunning,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = { session.isRunning = !session.isRunning },
            enabled = session.isRunning || session.placeId.isNotBlank(),
        ) {
            Text(
                stringResource(
                    if (session.isRunning) R.string.occupancy_stop else R.string.occupancy_start,
                ),
            )
        }
    }
}

/**
 * FAB-triggered control for `goto-poi`: a Place ID field plus "Go" (centers the camera on that
 * POI via [WebView.goToPlace]) and "Clear" (undoes the highlight via [WebView.clearPlace], camera
 * stays put). See docs/features/goto-poi.md.
 */
@Composable
fun GoToPoiOverlay(session: PlaceIdSession, webView: WebView?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = session.placeId,
            onValueChange = { session.placeId = it },
            label = { Text(stringResource(R.string.field_place_id)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = { webView?.goToPlace(session.placeId.trim()) },
            enabled = session.placeId.isNotBlank(),
        ) {
            Text(stringResource(R.string.action_go))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = { webView?.clearPlace() }) {
            Text(stringResource(R.string.action_clear))
        }
    }
}

/**
 * FAB-triggered control for `floor-selector`: one button per floor of the current building,
 * highest first, calling [WebView.goToFloor] on tap, the current floor (per
 * [FloorSelectorState.currentFloorId]) highlighted. Demonstrates driving `view.goToFloor` from
 * native code — the SDK's own default floor-selector widget (visible on the map with no app code at
 * all) already offers this to the end user, see docs/features/floor-selector.md.
 */
@Composable
fun FloorSelectorOverlay(webView: WebView?, floorSelector: FloorSelectorState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        if (floorSelector.floors.isEmpty()) {
            Text(stringResource(R.string.floor_selector_unavailable))
            return@Column
        }
        val buildingId = floorSelector.buildingId
        CurrentOptionButtons(
            options = floorSelector.floors.sortedByDescending { it.levelIndex }.map { it.id to it.name },
            current = floorSelector.currentFloorId,
            onSelect = { floorId -> if (buildingId != null) webView?.goToFloor(buildingId, floorId) },
        )
    }
}

/**
 * Screen-level state of an itinerary screen (`compute-navigation`, `custom-navigation-trace`,
 * `navigation-exclude-modalities`, `accessible-mode`): the From/To fields, the screen's switch
 * ("Avoid elevator" / "Accessible route") and the selected trace color, so closing and reopening
 * the panel keeps them — and the next "Itinerary" press uses what the panel shows.
 */
@Stable
class ItinerarySession(initialOrigin: String, initialDestination: String) {
    var origin by mutableStateOf(initialOrigin)
    var destination by mutableStateOf(initialDestination)

    /** `navigation-exclude-modalities`' "Avoid elevator" / `accessible-mode`'s "Accessible route". */
    var optionEnabled by mutableStateOf(false)

    /** `custom-navigation-trace`'s selected [NAVIGATION_TRACE_PRESETS] key. */
    var tracePresetKey by mutableStateOf(NAVIGATION_TRACE_PRESETS.keys.first())
}

/**
 * The From/To fields, "Itinerary" and "Clear" buttons and the [navigationError] line shared by the
 * four itinerary screens; [onItinerary] makes the screen's own bridge call with the trimmed ids,
 * [extraContent] renders the screen's own extra control below.
 */
@Composable
private fun ItineraryControls(
    session: ItinerarySession,
    webView: WebView?,
    navigationError: String?,
    onItinerary: (webView: WebView, origin: String, destination: String) -> Unit,
    extraContent: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        FromToFields(
            from = session.origin,
            onFromChange = { session.origin = it },
            to = session.destination,
            onToChange = { session.destination = it },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    webView?.let { onItinerary(it, session.origin.trim(), session.destination.trim()) }
                },
                enabled = session.origin.isNotBlank() && session.destination.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_itinerary))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { webView?.clearNavigation() }) {
                Text(stringResource(R.string.action_clear))
            }
        }
        ErrorText(navigationError)
        extraContent()
    }
}

/** A labeled switch row bound to [ItinerarySession.optionEnabled]. */
@Composable
private fun ItineraryOptionSwitch(session: ItinerarySession, @StringRes labelRes: Int) {
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(labelRes), modifier = Modifier.weight(1f))
        Switch(checked = session.optionEnabled, onCheckedChange = { session.optionEnabled = it })
    }
}

/**
 * FAB-triggered control for `compute-navigation`: [ItineraryControls] calling
 * [WebView.computeNavigation] with `isAccessible = false`. A failed computation (bad/unreachable
 * Place ID) surfaces [navigationError] below the fields. See docs/features/compute-navigation.md.
 */
@Composable
fun ComputeNavigationOverlay(session: ItinerarySession, webView: WebView?, navigationError: String?) {
    ItineraryControls(session, webView, navigationError, onItinerary = { view, origin, destination ->
        view.computeNavigation(origin, destination, false)
    })
}

/**
 * FAB-triggered control for `custom-navigation-trace`: [ItineraryControls] plus one color chip per
 * [NAVIGATION_TRACE_PRESETS] entry, applying [WebView.updateNavigationTraceStyle]
 * (`venue.updateNavigationTrace()` under the hood) to whatever trace is displayed (a no-op if none
 * is). "Itinerary" also applies the selected preset right after drawing the trace: the SDK only
 * builds a trace in its own default look, so this second call is always needed for anything but
 * the default. See docs/features/custom-navigation-trace.md.
 */
@Composable
fun CustomNavigationTraceOverlay(session: ItinerarySession, webView: WebView?, navigationError: String?) {
    ItineraryControls(
        session = session,
        webView = webView,
        navigationError = navigationError,
        onItinerary = { view, origin, destination ->
            view.computeNavigation(origin, destination, false)
            view.updateNavigationTraceStyle(NAVIGATION_TRACE_PRESETS.getValue(session.tracePresetKey))
        },
        extraContent = {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.trace_color_label),
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                NAVIGATION_TRACE_PRESETS.forEach { (presetKey, colors) ->
                    ColorChip(
                        hex = colors.progressColor,
                        name = stringResource(colors.nameRes),
                        selected = presetKey == session.tracePresetKey,
                        onClick = {
                            session.tracePresetKey = presetKey
                            webView?.updateNavigationTraceStyle(colors)
                        },
                    )
                }
            }
        },
    )
}

/**
 * FAB-triggered control for `navigation-exclude-modalities`: [ItineraryControls] plus an "Avoid
 * elevator" switch, read when "Itinerary" is pressed (not applied live: changing it means
 * recomputing, which is what "Itinerary" does), passed to
 * [WebView.computeNavigationExcludingModalities]. See docs/features/navigation-exclude-modalities.md.
 */
@Composable
fun NavigationExcludeModalitiesOverlay(session: ItinerarySession, webView: WebView?, navigationError: String?) {
    ItineraryControls(
        session = session,
        webView = webView,
        navigationError = navigationError,
        onItinerary = { view, origin, destination ->
            view.computeNavigationExcludingModalities(origin, destination, false, session.optionEnabled)
        },
        extraContent = { ItineraryOptionSwitch(session, R.string.avoid_elevator_label) },
    )
}

/**
 * FAB-triggered control for `accessible-mode`: [ItineraryControls] plus an "Accessible route"
 * switch, passed as [WebView.computeNavigation]'s `isAccessible` when "Itinerary" is pressed. With
 * it on, the SDK only uses segments the venue marks as accessible, so the computed route avoids
 * stairs. See docs/features/accessible-mode.md.
 */
@Composable
fun AccessibleModeOverlay(session: ItinerarySession, webView: WebView?, navigationError: String?) {
    ItineraryControls(
        session = session,
        webView = webView,
        navigationError = navigationError,
        onItinerary = { view, origin, destination ->
            view.computeNavigation(origin, destination, session.optionEnabled)
        },
        extraContent = { ItineraryOptionSwitch(session, R.string.accessible_route_label) },
    )
}

/** A single POI carried by the `AndroidBridge.onPoiClick` payload. See docs/features/poi-click.md. */
data class PoiClickInfo(val id: String, val name: String)

/**
 * Parses the JSON array emitted by `window.MapBridge`'s `poiclick` forwarder in `web/src/main.js`,
 * e.g. `[{"id":"poi-42","name":"Meeting room A"}]` — one entry per POI under the tap (almost always
 * a single one). `name` falls back to an empty string when the venue has no translation for it,
 * never throwing, so a missing translation never crashes the bridge callback.
 */
internal fun parsePoiClickPayload(json: String): List<PoiClickInfo> {
    val array = JSONArray(json)
    return List(array.length()) { index ->
        val entry = array.getJSONObject(index)
        PoiClickInfo(id = entry.getString("id"), name = entry.optString("name", ""))
    }
}

/**
 * Displays the POI(s) tapped on the map, driven entirely by the `poiclick` SDK event relayed
 * through the native<->JS bridge — there is no manual control here, unlike the other overlays.
 * [FeatureMapScreen] auto-opens the modal bottom sheet hosting this content as soon as a click
 * arrives; the FAB remains available too, so re-opening the sheet re-shows the last tapped POI.
 */
@Composable
fun PoiClickOverlay(pois: List<PoiClickInfo>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        if (pois.isEmpty()) {
            Text(stringResource(R.string.poi_click_empty))
            return@Column
        }
        pois.forEachIndexed { index, poi ->
            if (index > 0) Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = poi.name.ifBlank { poi.id },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = stringResource(R.string.poi_click_id, poi.id), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Screen-level state of `ui-part-visibility`: the visibility last set for each of the 5
 * [UI_PART_TOGGLES] parts, all visible at first, matching the SDK's own default. Held outside the
 * panel so its switches still match the map after a close/reopen.
 */
@Stable
class UiPartVisibilitySession {
    val visibility = mutableStateMapOf(*UI_PART_TOGGLES.map { (uiPart, _) -> uiPart to true }.toTypedArray())
}

/**
 * FAB-triggered control for `ui-part-visibility`: one switch per [UI_PART_TOGGLES] entry, each
 * calling [WebView.setUiPartVisible] on toggle so the effect is visible immediately on the map
 * behind the panel. See docs/features/ui-part-visibility.md.
 */
@Composable
fun UiPartVisibilityOverlay(session: UiPartVisibilitySession, webView: WebView?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        UI_PART_TOGGLES.forEach { (uiPart, labelRes) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(labelRes), modifier = Modifier.weight(1f))
                Switch(
                    checked = session.visibility[uiPart] == true,
                    onCheckedChange = { isVisible ->
                        session.visibility[uiPart] = isVisible
                        webView?.setUiPartVisible(uiPart, isVisible)
                    },
                )
            }
        }
    }
}

/**
 * Screen-level state of `native-ui-replacement`: whether the SDK's own floor selector is shown.
 * Starts off, matching the hide-on-load call made for this screen (see `MainActivity.kt`).
 */
@Stable
class NativeUiReplacementSession {
    var showSdkFloorSelector by mutableStateOf(false)
}

/**
 * FAB-triggered control for `native-ui-replacement`: a single switch, "Show SDK's own floor
 * selector", calling the same [WebView.setUiPartVisible] as [UiPartVisibilityOverlay], so the SDK's
 * own widget stays hidden unless the visitor flips it on to compare both driving the same floor.
 *
 * Below the switch, [FloorSelectorOverlay] is reused as-is, unaware of this switch's state: the
 * app's own native floor list stays fully functional regardless of it, demonstrating that it is a
 * complete replacement for the SDK's widget. See docs/features/native-ui-replacement.md.
 */
@Composable
fun NativeUiReplacementOverlay(
    session: NativeUiReplacementSession,
    webView: WebView?,
    floorSelector: FloorSelectorState,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.native_ui_replacement_show_sdk_selector_label),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = session.showSdkFloorSelector,
                onCheckedChange = { isVisible ->
                    session.showSdkFloorSelector = isVisible
                    webView?.setUiPartVisible("floorSelector", isVisible)
                },
            )
        }
    }
    FloorSelectorOverlay(webView, floorSelector)
}

/**
 * Screen-level state of a tracked-position simulation (`simulated-position`,
 * `camera-lock-on-position`, `geofencing`, `accuracy-circle-style`): the Origin/Destination POI
 * IDs, the accuracy radius, the running flag and the resolved segment being walked.
 *
 * It is created once per feature screen (in `MainActivity`, alongside the screen's other hoisted
 * state) and never inside the FAB's bottom sheet: the sheet's content is disposed on every close,
 * and the simulation must keep running while the sheet is closed so the visitor can pan/zoom the
 * map around the moving position. The loop itself lives in [TrackedPositionSimulationHost], also
 * composed at screen level, so there is exactly one loop per screen whatever the number of sheet
 * open/close cycles; [TrackedPositionSimulationControls] only reads/writes this object. Leaving the
 * screen disposes both (and the WebView), which stops the simulation.
 *
 * [start] resolves both POI ids in one round trip via [WebView.resolvePositions]; the response is
 * fed back through [onPositionsResolved] (by [TrackedPositionSimulationHost], which sees every
 * bridge response even while the sheet is closed). A `null` slot ("POI not found") surfaces
 * [error] instead of starting anything.
 * See docs/features/simulated-position.md.
 */
@Stable
class TrackedPositionSession(
    initialOriginId: String = "",
    initialDestinationId: String = "",
    initialRadiusMeters: Float = SIMULATED_POSITION_DEFAULT_RADIUS_METERS,
) {
    var originId by mutableStateOf(initialOriginId)
    var destinationId by mutableStateOf(initialDestinationId)
    var radiusMeters by mutableFloatStateOf(initialRadiusMeters)
    var isRunning by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set
    internal var activePositions by mutableStateOf<Pair<ResolvedPosition, ResolvedPosition>?>(null)
        private set

    // `requestId` guards against a stale response from a superseded request overwriting the outcome
    // of a more recent one (both are near-instant in practice, but this costs nothing).
    private var pendingRequestId: Int? = null

    fun start(webView: WebView?) {
        if (isRunning) return
        error = null
        val requestId = nextBridgeRequestId()
        pendingRequestId = requestId
        webView?.resolvePositions(requestId, originId.trim(), destinationId.trim())
    }

    fun stop() {
        isRunning = false
        activePositions = null
        pendingRequestId = null
    }

    internal fun onPositionsResolved(result: ResolvedPositionsPair) {
        if (result.requestId != pendingRequestId) return
        pendingRequestId = null
        val origin = result.origin
        val destination = result.destination
        if (origin == null || destination == null) {
            error = UiText(R.string.poi_not_found)
            isRunning = false
        } else {
            error = null
            activePositions = origin to destination
            isRunning = true
        }
    }
}

/** [TrackedPositionSession] pre-filled for `accuracy-circle-style` (the demo walk, 6 m radius). */
fun accuracyCircleStyleSession() = TrackedPositionSession(
    initialOriginId = DemoMap.TRACKING_FROM,
    initialDestinationId = DemoMap.TRACKING_TO,
    initialRadiusMeters = ACCURACY_CIRCLE_STYLE_DEFAULT_RADIUS_METERS,
)

/**
 * Drives a [TrackedPositionSession]; composed at screen level (via `FeatureMapScreen`'s
 * `screenContent`), never inside the bottom sheet. Feeds [positionsResolved] to the session, then,
 * while it runs, repeatedly calls [WebView.injectTrackedPosition] with a point linearly
 * interpolated between origin and destination, ping-ponging back and forth — reading
 * [TrackedPositionSession.radiusMeters] fresh on every tick, so moving the slider while running
 * changes the radius on the *next* tick with no restart needed. Stop (or leaving the screen, which
 * cancels this effect and tears down the WebView) calls [WebView.stopTrackedPosition] via the
 * effect's `finally` block, same cleanup-on-cancellation idiom as [OccupancySimulationHost].
 * [onRunningChanged] reports every start/stop transition (e.g. geofencing's zone resolve/clear);
 * [onTick] runs with every injected point.
 */
@Composable
fun TrackedPositionSimulationHost(
    session: TrackedPositionSession,
    webView: WebView?,
    positionsResolved: ResolvedPositionsPair?,
    onRunningChanged: (Boolean) -> Unit = {},
    onTick: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
) {
    val currentOnRunningChanged by rememberUpdatedState(onRunningChanged)
    val currentOnTick by rememberUpdatedState(onTick)

    LaunchedEffect(positionsResolved) {
        positionsResolved?.let(session::onPositionsResolved)
    }

    LaunchedEffect(session.isRunning) { currentOnRunningChanged(session.isRunning) }

    LaunchedEffect(session.isRunning, session.activePositions, webView) {
        val (origin, destination) = session.activePositions ?: return@LaunchedEffect
        val view = webView ?: return@LaunchedEffect
        if (!session.isRunning) return@LaunchedEffect

        var fraction = 0.0
        var direction = 1.0
        try {
            while (isActive) {
                val latitude = origin.latitude + (destination.latitude - origin.latitude) * fraction
                val longitude = origin.longitude + (destination.longitude - origin.longitude) * fraction
                view.injectTrackedPosition(latitude, longitude, session.radiusMeters.toDouble())
                currentOnTick(latitude, longitude)
                delay(SIMULATED_POSITION_INTERVAL_MS)
                fraction += direction * SIMULATED_POSITION_STEP
                when {
                    fraction >= 1.0 -> {
                        fraction = 1.0
                        direction = -1.0
                    }
                    fraction <= 0.0 -> {
                        fraction = 0.0
                        direction = 1.0
                    }
                }
            }
        } finally {
            view.stopTrackedPosition()
        }
    }
}

/**
 * Origin/Destination POI ID fields, a radius `Slider` (1-20m,
 * [SIMULATED_POSITION_DEFAULT_RADIUS_METERS] by default) and a Start/Stop toggle button — same
 * toggle pattern as [OccupancySimulationOverlay] — bound to a screen-level
 * [TrackedPositionSession] (see its kdoc for why it isn't held here), so reopening the sheet shows
 * the current state. Shared by the four tracked-position screens; [extraContent] renders below the
 * Start/Stop button and its error message (nothing for [SimulatedPositionOverlay], the camera-lock
 * switch for [CameraLockOnPositionOverlay], etc.). [startEnabled] gates Start on screen-specific
 * input (geofencing's zone id).
 * See docs/features/simulated-position.md and docs/features/camera-lock-on-position.md.
 */
@Composable
private fun TrackedPositionSimulationControls(
    session: TrackedPositionSession,
    webView: WebView?,
    startEnabled: Boolean = true,
    extraContent: @Composable () -> Unit = {},
) {
    val isRunning = session.isRunning
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        FromToFields(
            from = session.originId,
            onFromChange = { session.originId = it },
            to = session.destinationId,
            onToChange = { session.destinationId = it },
            enabled = !isRunning,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.tracking_radius_label, session.radiusMeters.roundToInt()))
        Slider(
            value = session.radiusMeters,
            onValueChange = { session.radiusMeters = it },
            valueRange = SIMULATED_POSITION_MIN_RADIUS_METERS..SIMULATED_POSITION_MAX_RADIUS_METERS,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { if (isRunning) session.stop() else session.start(webView) },
            enabled = isRunning ||
                (session.originId.isNotBlank() && session.destinationId.isNotBlank() && startEnabled),
        ) {
            Text(stringResource(if (isRunning) R.string.tracking_stop else R.string.tracking_start))
        }
        ErrorText(session.error?.resolve())
        extraContent()
    }
}

/**
 * FAB-triggered control for `simulated-position`: [TrackedPositionSimulationControls] with no
 * extra content. See docs/features/simulated-position.md.
 */
@Composable
fun SimulatedPositionOverlay(session: TrackedPositionSession, webView: WebView?) {
    TrackedPositionSimulationControls(session, webView)
}

/**
 * FAB-triggered control for `camera-lock-on-position`: [TrackedPositionSimulationControls] (a
 * moving position is needed to demonstrate a camera lock on it) plus a "Recenter on me" `Switch`
 * that flips `view.lockCameraPositionOnTracking` via [WebView.setCameraLockOnPosition] — the
 * GPS-app-style behavior this feature demonstrates. The switch is disabled while no simulation is
 * running (nothing to lock onto). It never holds its own value: [isCameraLocked] mirrors the SDK's
 * flag, pushed back on every `trackingon`/`trackingoff`/`trackingchanged` event (see
 * [FeatureBridgeState.isCameraLocked]) — so it shows on right after Start (the SDK locks by itself
 * when tracking starts), off after Stop, and off when the visitor pans the map while locked (the
 * simulation keeps running with the sheet closed, so the map can be panned meanwhile).
 * See docs/features/camera-lock-on-position.md.
 */
@Composable
fun CameraLockOnPositionOverlay(
    session: TrackedPositionSession,
    webView: WebView?,
    isCameraLocked: Boolean,
) {
    TrackedPositionSimulationControls(
        session = session,
        webView = webView,
        extraContent = {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(R.string.camera_lock_switch_label), modifier = Modifier.weight(1f))
                Switch(
                    checked = isCameraLocked,
                    enabled = session.isRunning,
                    onCheckedChange = { locked -> webView?.setCameraLockOnPosition(locked) },
                )
            }
        },
    )
}

/**
 * The `accuracy-circle-style` selection, hoisted by the caller (it outlives the bottom sheet,
 * which is disposed on every close — the SDK has no getter to read it back from). Defaults match
 * the SDK's own (`#0094f0`, 0.3). See docs/features/accuracy-circle-style.md.
 */
data class PositionTrackerStyle(
    val color: String = POSITION_TRACKER_DEFAULT_COLOR,
    val opacity: Float = POSITION_TRACKER_DEFAULT_OPACITY,
)

/** The SDK's default position tracker color/opacity (reset to these whenever tracking stops). */
private const val POSITION_TRACKER_DEFAULT_COLOR = "#0094f0"
private const val POSITION_TRACKER_DEFAULT_OPACITY = 0.3f

/**
 * Opacity slider bounds. 0.1 rather than 0: the SDK validates opacity with a falsy check, so `0`
 * would skip validation entirely — see docs/features/accuracy-circle-style.md.
 */
private const val POSITION_TRACKER_MIN_OPACITY = 0.1f
private const val POSITION_TRACKER_MAX_OPACITY = 1f

private const val ACCURACY_CIRCLE_STYLE_DEFAULT_RADIUS_METERS = 6f

/** Color presets offered for the tracked position (hex, label resource). */
private val POSITION_TRACKER_COLOR_PRESETS = listOf(
    POSITION_TRACKER_DEFAULT_COLOR to R.string.accuracy_circle_style_color_default,
    "#E53935" to R.string.color_red,
    "#43A047" to R.string.color_green,
    "#8E24AA" to R.string.color_purple,
)

/**
 * FAB-triggered control for `accuracy-circle-style`: [TrackedPositionSimulationControls] bound to a
 * session pre-filled by [accuracyCircleStyleSession] (two POIs of the demo map and a 6 m radius — a
 * moving tracked position is needed to see its style; only the circle is recolored, not the center
 * arrow) plus color preset chips and an opacity `Slider` (0.1-1.0, step 0.1). Every change calls
 * [WebView.setPositionTrackerStyle] immediately, running or not: the SDK applies it at the next
 * inject (every tick while running), and the JS side re-applies it on each Start since the SDK
 * resets it on every stop. [style] is hoisted by the caller so it survives the sheet being closed
 * and reopened, and only changes once the SDK has accepted the new style. See
 * docs/features/accuracy-circle-style.md.
 */
@Composable
fun AccuracyCircleStyleOverlay(
    session: TrackedPositionSession,
    webView: WebView?,
    style: PositionTrackerStyle,
    onStyleChange: (PositionTrackerStyle) -> Unit,
) {
    val applyStyle = { newStyle: PositionTrackerStyle ->
        webView?.setPositionTrackerStyle(newStyle.color, newStyle.opacity) { onStyleChange(newStyle) }
    }

    TrackedPositionSimulationControls(
        session = session,
        webView = webView,
        extraContent = {
            // Kept compact on purpose: the camera follows the tracked position (centered in the
            // map), so the sheet must stay short enough for the precision circle to remain visible
            // above it while tweaking the style.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.color_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 8.dp),
                )
                FlowRow(modifier = Modifier.weight(1f)) {
                    POSITION_TRACKER_COLOR_PRESETS.forEach { (hex, labelRes) ->
                        ColorChip(
                            hex = hex,
                            name = stringResource(labelRes),
                            selected = hex.equals(style.color, ignoreCase = true),
                            onClick = { applyStyle(style.copy(color = hex)) },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.accuracy_circle_style_opacity_label,
                        "%.1f".format(java.util.Locale.ROOT, style.opacity),
                    ),
                    modifier = Modifier.padding(end = 8.dp),
                )
                Slider(
                    value = style.opacity,
                    onValueChange = { value ->
                        val rounded = (value * 10).roundToInt() / 10f
                        if (rounded != style.opacity) applyStyle(style.copy(opacity = rounded))
                    },
                    valueRange = POSITION_TRACKER_MIN_OPACITY..POSITION_TRACKER_MAX_OPACITY,
                    // 0.1..1.0 in 0.1 steps = 8 intermediate stops.
                    steps = 8,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    )
}

/**
 * FAB-triggered control for `clickable-surface`: a Place ID field plus "Enable"/"Disable" buttons
 * calling [WebView.setSurfaceInteractive]. Once enabled, the SDK itself handles the
 * hover/selection color swap when the surface is tapped on the map; this control has no click
 * listener of its own for that. See docs/features/clickable-surface.md.
 */
@Composable
fun ClickableSurfaceOverlay(session: PlaceIdSession, webView: WebView?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = session.placeId,
            onValueChange = { session.placeId = it },
            label = { Text(stringResource(R.string.field_place_id)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = { webView?.setSurfaceInteractive(session.placeId.trim(), true) },
            enabled = session.placeId.isNotBlank(),
        ) {
            Text(stringResource(R.string.action_enable))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = { webView?.setSurfaceInteractive(session.placeId.trim(), false) },
            enabled = session.placeId.isNotBlank(),
        ) {
            Text(stringResource(R.string.action_disable))
        }
    }
}

/**
 * Screen-level state of `custom-data`: the place id and the last loaded result. [onLoaded] is fed
 * every `AndroidBridge.onCustomDataLoaded` response at screen level, so a response arriving while
 * the panel is closed is not lost. See docs/features/custom-data.md.
 */
@Stable
class CustomDataSession(initialPlaceId: String) {
    var placeId by mutableStateOf(initialPlaceId)

    /** `null` until a load completes; then [CustomDataResult.customData] (`null` = POI not found). */
    var result by mutableStateOf<CustomDataResult?>(null)
        private set
    private var pendingRequestId: Int? = null

    fun load(webView: WebView?, targetPlaceId: String) {
        result = null
        val requestId = nextBridgeRequestId()
        pendingRequestId = requestId
        webView?.loadCustomData(requestId, targetPlaceId)
    }

    internal fun onLoaded(response: CustomDataResult) {
        if (response.requestId != pendingRequestId) return
        pendingRequestId = null
        result = response
    }
}

/**
 * FAB-triggered control for `custom-data`: a Place ID field plus a single "Load" button that calls
 * [WebView.loadCustomData] — combining `venue.refreshCustomData()` and `venue.getPOICustomData()`
 * into one round trip. Above the field, one chip per [DemoMap.CUSTOM_DATA_SAMPLE_PLACE_IDS] entry
 * fills it and loads it right away. All three outcomes are normal states, not errors: every
 * key/value pair, "No custom data for this POI" (empty map) or "POI not found" (`null`). See
 * docs/features/custom-data.md.
 */
@Composable
fun CustomDataOverlay(session: CustomDataSession, webView: WebView?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.custom_data_samples_label), style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            DemoMap.CUSTOM_DATA_SAMPLE_PLACE_IDS.forEach { sampleId ->
                OutlinedButton(
                    onClick = {
                        session.placeId = sampleId
                        session.load(webView, sampleId)
                    },
                    modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                ) {
                    Text(sampleId)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = session.placeId,
                onValueChange = { session.placeId = it },
                label = { Text(stringResource(R.string.field_place_id)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { session.load(webView, session.placeId.trim()) },
                enabled = session.placeId.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_load))
            }
        }
        val result = session.result
        if (result != null) {
            Spacer(modifier = Modifier.height(12.dp))
            val data = result.customData
            when {
                data == null -> Text(stringResource(R.string.poi_not_found), color = MaterialTheme.colorScheme.error)
                data.isEmpty() -> Text(stringResource(R.string.custom_data_empty))
                else -> data.forEach { (key, value) ->
                    Text(text = "$key: $value", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * Screen-level state of `category-highlight`: the venue's categories (requested the first time the
 * panel opens) and the highlighted one, so the panel still shows the selection the map shows after
 * a close/reopen. Only one category is highlighted at a time — [WebView.highlightCategory] reverts
 * the previous one on the JS side. See docs/features/category-highlight.md.
 */
@Stable
class CategoryHighlightSession {
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

    internal fun onCategoriesLoaded(response: CategoriesResult) {
        if (response.requestId != pendingRequestId) return
        pendingRequestId = null
        categories = response.categories
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

/**
 * FAB-triggered control for `category-highlight`: one [FilterChip] per `venue.categories` entry
 * (label = [CategoryInfo.label], the name resolved via `translateCategory` on the JS side;
 * [CategoryInfo.id] is what filtering/highlighting uses). Tapping a chip calls
 * [WebView.highlightCategory]; tapping the selected chip again, or "Clear", calls
 * [WebView.clearCategoryHighlight]. See docs/features/category-highlight.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryHighlightOverlay(session: CategoryHighlightSession, webView: WebView?) {
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

/**
 * Screen-level state of `dynamic-poi-crud`: the three fields and the POI currently on the map
 * ([trackedPoiId]/[trackedLabelText]), so after a close/reopen Update text and Remove still act on
 * it and Create stays disabled (a second Create would otherwise replace the tracked POI on the JS
 * side and leave the first one on the map for good). Only one dynamic POI is tracked at a time — a
 * demo-side simplification, not an SDK limitation. See docs/features/dynamic-poi-crud.md.
 */
@Stable
class DynamicPoiCrudSession(initialLabelText: String) {
    var newId by mutableStateOf(DemoMap.DYNAMIC_POI_NEW_ID)
    var anchorId by mutableStateOf(DemoMap.ANCHOR_POI_ID)
    var labelText by mutableStateOf(initialLabelText)
    var trackedPoiId by mutableStateOf<String?>(null)
        private set
    var trackedLabelText by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set
    private var pendingRequestId: Int? = null

    fun create(webView: WebView?) {
        error = null
        val requestId = nextBridgeRequestId()
        pendingRequestId = requestId
        webView?.createDynamicPoi(requestId, newId.trim(), anchorId.trim(), labelText.trim())
    }

    fun updateText(webView: WebView?) {
        webView?.updateDynamicPoiLabel(labelText.trim())
        trackedLabelText = labelText.trim()
    }

    fun remove(webView: WebView?) {
        webView?.removeDynamicPoi()
        trackedPoiId = null
        trackedLabelText = null
        error = null
    }

    internal fun onCreated(response: DynamicPoiCreationResult) {
        if (response.requestId != pendingRequestId) return
        pendingRequestId = null
        error = when (response.status) {
            "created" -> null
            "anchor-not-found" -> UiText(R.string.dynamic_poi_anchor_not_found)
            "anchor-has-no-position" -> UiText(R.string.dynamic_poi_anchor_no_position)
            "duplicate-id" -> UiText(R.string.dynamic_poi_duplicate_id)
            else -> response.message?.let { UiText(R.string.raw_message, listOf(it)) }
                ?: UiText(R.string.dynamic_poi_create_failed)
        }
        if (response.status == "created") {
            trackedPoiId = response.id
            trackedLabelText = response.text
        }
    }
}

/**
 * FAB-triggered control for `dynamic-poi-crud`: "New POI ID", "Anchor POI ID" and "Label text"
 * fields plus Create/Update text/Remove buttons, bound to the screen-level [DynamicPoiCrudSession].
 * The two ID fields are disabled while a POI is tracked. "Label text" stays editable: it seeds the
 * label created by Create, and its current value is what Update text sends via
 * [WebView.updateDynamicPoiLabel]. Create calls [WebView.createDynamicPoi]; any outcome other than
 * `"created"` (including `POIAlreadyExistsError` on a duplicate id) surfaces as a normal state.
 * Remove calls [WebView.removeDynamicPoi]; the SDK cascades the removal to the attached label. See
 * docs/features/dynamic-poi-crud.md.
 */
@Composable
fun DynamicPoiCrudOverlay(session: DynamicPoiCrudSession, webView: WebView?) {
    val hasTrackedPoi = session.trackedPoiId != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            text = if (hasTrackedPoi) {
                stringResource(R.string.dynamic_poi_created, session.trackedPoiId.orEmpty(), session.trackedLabelText.orEmpty())
            } else {
                stringResource(R.string.dynamic_poi_none)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = session.newId,
                onValueChange = { session.newId = it },
                label = { Text(stringResource(R.string.field_new_poi_id)) },
                singleLine = true,
                enabled = !hasTrackedPoi,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = session.anchorId,
                onValueChange = { session.anchorId = it },
                label = { Text(stringResource(R.string.field_anchor_poi_id)) },
                singleLine = true,
                enabled = !hasTrackedPoi,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = session.labelText,
            onValueChange = { session.labelText = it },
            label = { Text(stringResource(R.string.field_label_text)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { session.create(webView) },
                enabled = !hasTrackedPoi && session.newId.isNotBlank() && session.anchorId.isNotBlank() &&
                    session.labelText.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_create))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { session.updateText(webView) },
                enabled = hasTrackedPoi && session.labelText.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_update_text))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { session.remove(webView) }, enabled = hasTrackedPoi) {
                Text(stringResource(R.string.action_remove))
            }
        }
        ErrorText(session.error?.resolve())
    }
}

/**
 * The two locale options offered by `runtime-locale`, paired with a human-readable label. The
 * shared demo map's `venue.translator.allLocales` is actually `['default', 'en', 'fr']`, but
 * `'default'` is a byte-identical duplicate of `'fr'` on this venue — offering it as a third,
 * seemingly distinct choice would be misleading, so only these two SDK-valid locale codes are
 * listed here rather than populated from `allLocales` at runtime. See
 * docs/features/runtime-locale.md.
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
class RuntimeLocaleSession {
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

    internal fun onLocaleResolved(response: LocaleResult) {
        if (response.requestId != pendingRequestId) return
        pendingRequestId = null
        if (response.locale != null) {
            currentLocale = response.locale
            error = null
        } else {
            error = response.errorUiText()
        }
    }
}

private fun LocaleResult.errorUiText(): UiText =
    errorMessage?.let { UiText(R.string.raw_message, listOf(it)) } ?: UiText(R.string.locale_change_failed)

/**
 * FAB-triggered control for `runtime-locale`: one button per [RUNTIME_LOCALE_OPTIONS] entry, the
 * venue's current locale highlighted (queried via [WebView.getCurrentLocale] when the panel opens).
 * Tapping the other option calls [WebView.setLocale], which switches `venue.currentLocale` and
 * re-renders every POI/label name and UI item already on screen — no separate refresh call needed.
 * A failed switch surfaces an error below the buttons. See docs/features/runtime-locale.md.
 */
@Composable
fun RuntimeLocaleOverlay(session: RuntimeLocaleSession, webView: WebView?) {
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

/**
 * FAB-triggered control for `explore-mode`: one button per [EXPLORE_MODE_OPTIONS] entry, the
 * active one (per [currentExploreMode]) highlighted. Tapping another option calls
 * [WebView.setExploreMode]; the highlight is never set optimistically from the tap — it only moves
 * in response to `AndroidBridge.onExploreModeChanged`, so it stays correct when the mode changes
 * from map interaction (a click while in "building" mode, which the SDK switches to "floor" on its
 * own). "Building" — the exploded "carousel" view of the building's floors — needs no building to
 * be open: the SDK falls back to the venue's first one. See docs/features/explore-mode.md.
 */
@Composable
fun ExploreModeOverlay(webView: WebView?, currentExploreMode: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        CurrentOptionButtons(
            options = EXPLORE_MODE_OPTIONS.map { (mode, labelRes) -> mode to stringResource(labelRes) },
            current = currentExploreMode,
            onSelect = { mode -> webView?.setExploreMode(mode) },
        )
    }
}

/**
 * The fixed keys demonstrated by `add-locale`, with the label shown above each one's value:
 * `'search-for-anything'` is one of the SDK's own predefined UI keys (see `Translator.addLocale`'s
 * doc), showing that `addLocale` can override the SDK's own built-in strings; `'welcome-message'` is
 * a custom, app-defined key, showing the same store is a general-purpose one. The Spanish values
 * live in `web/src/main.js`'s `ADD_LOCALE_RESOURCES`. See docs/features/add-locale.md.
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
class AddLocaleSession {
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

    internal fun onAddLocaleResolved(response: AddLocaleResult) {
        if (response.requestId != pendingAddRequestId) return
        pendingAddRequestId = null
        if (response.translations != null) {
            translations = response.translations
            error = null
        } else {
            error = response.errorMessage?.let { UiText(R.string.raw_message, listOf(it)) }
                ?: UiText(R.string.add_locale_failed)
        }
    }

    internal fun onLocaleResolved(response: LocaleResult) {
        if (response.requestId != pendingSwitchRequestId) return
        pendingSwitchRequestId = null
        if (response.locale != null) {
            isSpanishDisplayed = response.locale == "es"
            error = null
        } else {
            error = response.errorUiText()
        }
    }
}

/**
 * FAB-triggered control for `add-locale`: one row per [ADD_LOCALE_DEMO_KEYS] entry showing "(not
 * added yet)" until "Add Spanish locale" is pressed, then the real Spanish text — this
 * before/after contrast is the proof that `venue.translator.addLocale('es', ...)` worked, since
 * neither key changes any POI/label name on the map (see docs/features/add-locale.md, "Things to
 * know"). The button calls [WebView.addSpanishLocale], which adds the locale and reads every key
 * back via `translate` in one round trip. "Switch to Spanish" then calls [WebView.setLocale]
 * (`runtime-locale`'s call), enabled once the locale is added; its result (or error) is shown.
 */
@Composable
fun AddLocaleOverlay(session: AddLocaleSession, webView: WebView?) {
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

/**
 * Screen-level state for `geofencing`: the shared [tracking] simulation plus the "Zone POI ID"
 * field and its resolution outcome — held outside the bottom sheet for the same reason as
 * [TrackedPositionSession]. See docs/features/geofencing.md.
 */
@Stable
class GeofencingSession(val tracking: TrackedPositionSession = TrackedPositionSession()) {
    var zoneId by mutableStateOf(DemoMap.GEOFENCE_ZONE_ID)
    var zoneError by mutableStateOf<UiText?>(null)
        private set
    private var pendingZoneRequestId: Int? = null

    internal fun onRunningChanged(webView: WebView?, running: Boolean) {
        if (running) {
            zoneError = null
            val requestId = nextBridgeRequestId()
            pendingZoneRequestId = requestId
            webView?.resolveZone(requestId, zoneId.trim())
        } else {
            pendingZoneRequestId = null
            webView?.clearZone()
        }
    }

    // `requestId` guards against a stale response the same way positionsResolved does.
    internal fun onZoneResolved(result: ZoneResolution) {
        if (result.requestId != pendingZoneRequestId) return
        pendingZoneRequestId = null
        zoneError = when (result.status) {
            "not-found" -> UiText(R.string.zone_not_found)
            "no-surface" -> UiText(R.string.zone_no_surface)
            else -> null
        }
    }
}

/**
 * Screen-level driver for `geofencing`: [TrackedPositionSimulationHost] whose start resolves the
 * zone once via [WebView.resolveZone] and whose stop calls [WebView.clearZone] (reverting the zone's
 * color and forgetting it, so restarting always begins from a clean, unresolved zone — a "fresh
 * opt-in each time" idiom). Every tick also calls [WebView.checkGeofence] with the freshly
 * interpolated position (no separate polling), whose result is reported back live via
 * `AndroidBridge.onGeofenceStateChanged` — the JS side itself recolors the zone's surface(s) as a
 * visual alert on a state transition, so the alert keeps working while the sheet is closed.
 */
@Composable
fun GeofencingSimulationHost(
    session: GeofencingSession,
    webView: WebView?,
    positionsResolved: ResolvedPositionsPair?,
    zoneResolved: ZoneResolution?,
) {
    LaunchedEffect(zoneResolved) {
        zoneResolved?.let(session::onZoneResolved)
    }

    TrackedPositionSimulationHost(
        session = session.tracking,
        webView = webView,
        positionsResolved = positionsResolved,
        onRunningChanged = { running -> session.onRunningChanged(webView, running) },
        onTick = { latitude, longitude -> webView?.checkGeofence(latitude, longitude) },
    )
}

/**
 * FAB-triggered control for `geofencing`: [TrackedPositionSimulationControls] (a moving simulated
 * position is needed to demonstrate entering/leaving a zone) plus the "Zone POI ID" field — Start
 * is disabled until it is non-blank — and the live "Inside zone"/"Outside zone" state. Everything
 * it shows lives in the screen-level [GeofencingSession], driven by [GeofencingSimulationHost].
 * See docs/features/geofencing.md.
 */
@Composable
fun GeofencingOverlay(
    session: GeofencingSession,
    webView: WebView?,
    isInsideZone: Boolean,
) {
    val isRunning = session.tracking.isRunning
    TrackedPositionSimulationControls(
        session = session.tracking,
        webView = webView,
        startEnabled = session.zoneId.isNotBlank(),
        extraContent = {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = session.zoneId,
                onValueChange = { session.zoneId = it },
                label = { Text(stringResource(R.string.field_zone_poi_id)) },
                singleLine = true,
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
            )
            val zoneError = session.zoneError
            when {
                zoneError != null -> ErrorText(zoneError.resolve())
                isRunning -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(if (isInsideZone) R.string.zone_inside else R.string.zone_outside))
                }
            }
        },
    )
}

/**
 * FAB-triggered control for `custom-base-url`: a "Base URL" field pre-filled with
 * [currentBaseUrl] (the SDK's own real default, [DEFAULT_SDK_BASE_URL]) and a "Reload" button.
 * Unlike every other bridge call in this file, this doesn't call anything on [webView] directly —
 * `LoadOptions.baseURL` only takes effect when `loadVenue` runs, so there is nothing to call on an
 * already-loaded venue. Instead [onReload] (== [FeatureBridgeState.onReloadWithBaseUrl]) tells
 * [FeatureMapScreen] to fully recreate the WebView against the new value — see that function's
 * kdoc. The two outcomes both surface through [FeatureMapScreen]'s own existing loading/error UI,
 * not a message rendered here: the default URL reloading successfully proves the parameter is
 * genuinely wired through (it's the same value the SDK would use anyway), and a garbage URL
 * reloading into the screen's load-error message — with the SDK's own `VenueNotFoundError`
 * message — is a valid demo outcome too. See
 * docs/features/custom-base-url.md.
 */
@Composable
fun CustomBaseUrlOverlay(currentBaseUrl: String, onReload: (String) -> Unit) {
    var baseUrl by remember { mutableStateOf(currentBaseUrl) }

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text(stringResource(R.string.field_base_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onReload(baseUrl.trim()) },
            enabled = baseUrl.isNotBlank(),
        ) {
            Text(stringResource(R.string.action_reload))
        }
    }
}

/**
 * The single image id this demo places under on the JS side — only one icon at a time, a
 * demo-side simplification, not an SDK limitation.
 */
private const val ICON_MARKER_IMAGE_ID = "demo-icon"

/** The SDK's three `OrientationType` values, paired with a localized label. */
private val ICON_MARKER_ORIENTATIONS = listOf(
    "fixed" to R.string.orientation_fixed,
    "flat" to R.string.orientation_flat,
    "facing" to R.string.orientation_facing,
)

/**
 * Places the demo icon on [anchorId] by calling `window.MapBridge.placeIconImage` in the WebView
 * (`venue.createImage` under the hood, owned by the anchor POI itself, at its own position). The
 * result arrives asynchronously via `AndroidBridge.onIconImagePlaced`, echoing [requestId] back,
 * same convention as [WebView.createDynamicPoi]. See docs/features/3d-icon-marker.md.
 */
private fun WebView.placeIconImage(requestId: Int, anchorId: String, orientationType: String) {
    val script = "window.MapBridge.placeIconImage(" +
        "$requestId, ${JSONObject.quote(ICON_MARKER_IMAGE_ID)}, ${JSONObject.quote(anchorId)}, " +
        "${JSONObject.quote(orientationType)})"
    evaluateJavascript(script, null)
}

/**
 * Switches the placed icon's orientation live via `window.MapBridge.updateIconImageOrientation`
 * (`venue.updateImage` under the hood). Fire-and-forget. See docs/features/3d-icon-marker.md.
 */
private fun WebView.updateIconImageOrientation(orientationType: String) {
    val script = "window.MapBridge.updateIconImageOrientation(" +
        "${JSONObject.quote(ICON_MARKER_IMAGE_ID)}, ${JSONObject.quote(orientationType)})"
    evaluateJavascript(script, null)
}

/**
 * Removes the placed icon via `window.MapBridge.removeIconImage` (`venue.removeImage` under the
 * hood). Fire-and-forget. See docs/features/3d-icon-marker.md.
 */
private fun WebView.removeIconImage() {
    evaluateJavascript("window.MapBridge.removeIconImage(${JSONObject.quote(ICON_MARKER_IMAGE_ID)})", null)
}

/**
 * Result of a [WebView.placeIconImage] or [WebView.placeTextLabel] call, carried by
 * `AndroidBridge.onIconImagePlaced` / `AndroidBridge.onTextLabelPlaced`. [status] is one of
 * `"placed"` ([poiId] then non-null), `"text-required"` (text label only), `"anchor-not-found"`,
 * `"anchor-has-no-position"`, or `"error"` ([message] carrying its text).
 */
data class PlacementResult(
    val requestId: Int,
    val status: String,
    val poiId: String? = null,
    val message: String? = null,
)

/** Parses the JSON object argument of `onIconImagePlaced`/`onTextLabelPlaced`, e.g. `{"status":"placed","poiId":"…"}`. */
internal fun parsePlacementPayload(requestId: Int, json: String): PlacementResult {
    val root = JSONObject(json)
    return PlacementResult(
        requestId = requestId,
        status = root.getString("status"),
        poiId = if (root.has("poiId")) root.getString("poiId") else null,
        message = if (root.has("message")) root.getString("message") else null,
    )
}

/** The error to show for a failed [PlacementResult], or null when it was placed. */
private fun PlacementResult.errorUiText(@StringRes fallbackRes: Int): UiText? = when (status) {
    "placed" -> null
    "text-required" -> UiText(R.string.text_required)
    "anchor-not-found" -> UiText(R.string.poi_not_found)
    "anchor-has-no-position" -> UiText(R.string.poi_has_no_position)
    else -> message?.let { UiText(R.string.raw_message, listOf(it)) } ?: UiText(fallbackRes)
}

/**
 * Screen-level state of `3d-icon-marker`: the fields, and the POI the icon is placed on, so after a
 * close/reopen "Remove icon" and the live orientation chips still act on the icon on the map.
 * [onPlaced] is fed every `onIconImagePlaced` response at screen level; on success it moves the
 * camera to the anchor POI via [WebView.goToPlace] (goto-poi's call — from the default whole-venue
 * view the indoor floor, and so the icon, isn't shown).
 */
@Stable
class IconMarkerSession {
    var anchorId by mutableStateOf(DemoMap.ANCHOR_POI_ID)
    var orientation by mutableStateOf("facing")
    var placedOnPoiId by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set
    private var pendingRequestId: Int? = null

    fun place(webView: WebView?) {
        error = null
        val requestId = nextBridgeRequestId()
        pendingRequestId = requestId
        webView?.placeIconImage(requestId, anchorId.trim(), orientation)
    }

    fun selectOrientation(webView: WebView?, value: String) {
        orientation = value
        if (placedOnPoiId != null) webView?.updateIconImageOrientation(value)
    }

    fun remove(webView: WebView?) {
        webView?.removeIconImage()
        webView?.clearPlace()
        placedOnPoiId = null
        error = null
    }

    internal fun onPlaced(webView: WebView?, response: PlacementResult) {
        if (response.requestId != pendingRequestId) return
        pendingRequestId = null
        error = response.errorUiText(R.string.icon_place_failed)
        if (response.status == "placed") {
            placedOnPoiId = response.poiId
            response.poiId?.let { webView?.goToPlace(it) }
        }
    }
}

/**
 * FAB-triggered control for `3d-icon-marker`: an "Anchor POI ID" field, one chip per
 * [ICON_MARKER_ORIENTATIONS] entry (default "Facing camera"), Place icon/Remove icon buttons and a
 * status line, bound to the screen-level [IconMarkerSession]. Place calls [WebView.placeIconImage];
 * "POI not found"/"no position" outcomes surface as normal states. Changing the orientation while
 * an icon is placed calls [WebView.updateIconImageOrientation] live. See
 * docs/features/3d-icon-marker.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconMarker3dOverlay(session: IconMarkerSession, webView: WebView?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = session.anchorId,
            onValueChange = { session.anchorId = it },
            label = { Text(stringResource(R.string.field_anchor_poi_id)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = stringResource(R.string.orientation_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            ICON_MARKER_ORIENTATIONS.forEach { (value, labelRes) ->
                FilterChip(
                    selected = value == session.orientation,
                    onClick = { session.selectOrientation(webView, value) },
                    label = { Text(stringResource(labelRes)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { session.place(webView) }, enabled = session.anchorId.isNotBlank()) {
                Text(stringResource(R.string.icon_place))
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = { session.remove(webView) }, enabled = session.placedOnPoiId != null) {
                Text(stringResource(R.string.icon_remove))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val error = session.error
        if (error != null) {
            Text(text = error.resolve(), color = MaterialTheme.colorScheme.error)
        } else {
            Text(
                text = session.placedOnPoiId?.let { stringResource(R.string.icon_placed_on, it) }
                    ?: stringResource(R.string.icon_none),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Font colors offered for the label (hex — the SDK only accepts hex / `rgb()` / `hsl()`). */
private val TEXT_LABEL_COLORS = listOf(
    "#E53935" to R.string.color_red,
    "#43A047" to R.string.color_green,
    "#1E88E5" to R.string.color_blue,
    "#212121" to R.string.color_dark,
)

/** A label size preset: [width]×[height] in meters, always sent together to the SDK. */
private data class TextLabelSize(@StringRes val labelRes: Int, val width: Int, val height: Int)

private val TEXT_LABEL_SIZES = listOf(
    TextLabelSize(R.string.size_small, width = 10, height = 2),
    TextLabelSize(R.string.size_medium, width = 16, height = 3),
    TextLabelSize(R.string.size_large, width = 22, height = 4),
)

/** Rotations offered for the label, in degrees (create-only in the SDK, see the doc). */
private val TEXT_LABEL_ROTATIONS = listOf(0, 45, 90)

/**
 * Places the demo text label on [anchorId] by calling `window.MapBridge.placeTextLabel` in the
 * WebView (`venue.createLabel` under the hood, owned by the anchor POI, at its position offset by
 * ~4 m). The result arrives asynchronously via `AndroidBridge.onTextLabelPlaced`, echoing
 * [requestId] back, same convention as [WebView.placeIconImage]. See
 * docs/features/custom-text-label.md.
 */
private fun WebView.placeTextLabel(
    requestId: Int,
    anchorId: String,
    text: String,
    color: String,
    size: TextLabelSize,
    rotation: Int,
) {
    val options = JSONObject()
        .put("text", text)
        .put("color", color)
        .put("width", size.width)
        .put("height", size.height)
        .put("rotation", rotation)
    val script = "window.MapBridge.placeTextLabel($requestId, ${JSONObject.quote(anchorId)}, $options)"
    evaluateJavascript(script, null)
}

/**
 * Restyles the placed label live via `window.MapBridge.updateTextLabel` (`venue.updateLabel` under
 * the hood) with whatever subset of text/color/width+height [options] carries. Fire-and-forget.
 * See docs/features/custom-text-label.md.
 */
private fun WebView.updateTextLabel(options: JSONObject) {
    evaluateJavascript("window.MapBridge.updateTextLabel($options)", null)
}

/**
 * Changes the placed label's rotation via `window.MapBridge.setTextLabelRotation`, which removes
 * and recreates it (rotation can't go through `venue.updateLabel`). Fire-and-forget. See
 * docs/features/custom-text-label.md.
 */
private fun WebView.setTextLabelRotation(rotation: Int) {
    evaluateJavascript("window.MapBridge.setTextLabelRotation($rotation)", null)
}

/**
 * Removes the placed label via `window.MapBridge.removeTextLabel` (`venue.removeLabel` under the
 * hood). Fire-and-forget, and a no-op on the JS side if nothing is placed. See
 * docs/features/custom-text-label.md.
 */
private fun WebView.removeTextLabel() {
    evaluateJavascript("window.MapBridge.removeTextLabel()", null)
}

/**
 * Screen-level state of `custom-text-label`: the fields and style, and the POI the label is placed
 * on, so after a close/reopen Update text/Remove label and the live style controls still act on
 * the label on the map. [onPlaced] is fed every `onTextLabelPlaced` response at screen level; on
 * success it moves the camera to the anchor POI via [WebView.goToPlace] (a label only renders
 * while its POI's floor is displayed). The JS side validates text, anchor and position before
 * touching the label already placed, so a failed place leaves it on the map.
 */
@Stable
class TextLabelSession(initialText: String) {
    var anchorId by mutableStateOf(DemoMap.ANCHOR_POI_ID)
    var text by mutableStateOf(initialText)
    var color by mutableStateOf(TEXT_LABEL_COLORS.first().first)
        private set
    var sizeIndex by mutableIntStateOf(1)
        private set
    var rotation by mutableIntStateOf(TEXT_LABEL_ROTATIONS.first())
        private set
    var placedOnPoiId by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set
    var status by mutableStateOf<UiText?>(null)
        private set
    private var pendingRequestId: Int? = null

    fun place(webView: WebView?) {
        status = null
        error = null
        val requestId = nextBridgeRequestId()
        pendingRequestId = requestId
        webView?.placeTextLabel(requestId, anchorId.trim(), text.trim(), color, TEXT_LABEL_SIZES[sizeIndex], rotation)
    }

    fun updateText(webView: WebView?) {
        error = null
        status = UiText(R.string.text_updated)
        webView?.updateTextLabel(JSONObject().put("text", text.trim()))
    }

    fun selectColor(webView: WebView?, hex: String) {
        color = hex
        if (placedOnPoiId != null) webView?.updateTextLabel(JSONObject().put("color", hex))
    }

    fun selectSize(webView: WebView?, index: Int) {
        sizeIndex = index
        val size = TEXT_LABEL_SIZES[index]
        if (placedOnPoiId != null) {
            webView?.updateTextLabel(JSONObject().put("width", size.width).put("height", size.height))
        }
    }

    fun selectRotation(webView: WebView?, degrees: Int) {
        rotation = degrees
        if (placedOnPoiId != null) webView?.setTextLabelRotation(degrees)
    }

    fun remove(webView: WebView?) {
        webView?.removeTextLabel()
        webView?.clearPlace()
        placedOnPoiId = null
        error = null
        status = UiText(R.string.label_removed)
    }

    internal fun onPlaced(webView: WebView?, response: PlacementResult) {
        if (response.requestId != pendingRequestId) return
        pendingRequestId = null
        error = response.errorUiText(R.string.label_place_failed)
        if (response.status == "placed") {
            placedOnPoiId = response.poiId
            status = null
            response.poiId?.let { webView?.goToPlace(it) }
        }
    }
}

/**
 * FAB-triggered control for `custom-text-label`: "Anchor POI ID" and "Text" fields, one chip per
 * [TEXT_LABEL_COLORS] entry, segmented [TEXT_LABEL_SIZES]/[TEXT_LABEL_ROTATIONS] controls, Place
 * label/Update text/Remove label buttons and a status line, bound to the screen-level
 * [TextLabelSession]. Place calls [WebView.placeTextLabel]. While a label is placed, color/size
 * changes apply live via [WebView.updateTextLabel] (width and height always together) and a
 * rotation change recreates it via [WebView.setTextLabelRotation]. Text is validated on the JS
 * side only (`"text-required"`). See docs/features/custom-text-label.md.
 */
@Composable
fun CustomTextLabelOverlay(session: TextLabelSession, webView: WebView?) {
    val isPlaced = session.placedOnPoiId != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = session.anchorId,
            onValueChange = { session.anchorId = it },
            label = { Text(stringResource(R.string.field_anchor_poi_id)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = session.text,
            onValueChange = { session.text = it },
            label = { Text(stringResource(R.string.field_text)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = stringResource(R.string.color_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            TEXT_LABEL_COLORS.forEach { (hex, nameRes) ->
                ColorChip(
                    hex = hex,
                    name = stringResource(nameRes),
                    selected = hex == session.color,
                    onClick = { session.selectColor(webView, hex) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.size_label), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TEXT_LABEL_SIZES.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = index == session.sizeIndex,
                    onClick = { session.selectSize(webView, index) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TEXT_LABEL_SIZES.size),
                ) {
                    Text(stringResource(option.labelRes))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.rotation_label), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TEXT_LABEL_ROTATIONS.forEachIndexed { index, degrees ->
                SegmentedButton(
                    selected = degrees == session.rotation,
                    onClick = { session.selectRotation(webView, degrees) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TEXT_LABEL_ROTATIONS.size),
                ) {
                    Text("$degrees°")
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { session.place(webView) },
                enabled = session.anchorId.isNotBlank(),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text(stringResource(R.string.label_place))
            }
            OutlinedButton(
                onClick = { session.updateText(webView) },
                enabled = isPlaced && session.text.isNotBlank(),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text(stringResource(R.string.action_update_text))
            }
            OutlinedButton(onClick = { session.remove(webView) }, enabled = isPlaced) {
                Text(stringResource(R.string.label_remove))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val error = session.error
        if (error != null) {
            Text(text = error.resolve(), color = MaterialTheme.colorScheme.error)
        } else {
            Text(
                text = session.status?.resolve()
                    ?: session.placedOnPoiId?.let { stringResource(R.string.label_placed_on, it) }
                    ?: stringResource(R.string.label_none),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Pins the demo HTML badge on [anchorId] by calling `window.MapBridge.placeHtmlMarker` in the
 * WebView (`venue.createMarker` under the hood, with a `div` element the bridge JS builds). The
 * outcome comes back via `AndroidBridge.onHtmlMarkerStateChanged`, like every other badge state
 * change. See docs/features/html-overlay-marker.md.
 */
private fun WebView.placeHtmlMarker(anchorId: String, text: String) {
    val script = "window.MapBridge.placeHtmlMarker(${JSONObject.quote(anchorId)}, ${JSONObject.quote(text)})"
    evaluateJavascript(script, null)
}

/**
 * Replaces the placed badge's text via `window.MapBridge.updateHtmlMarkerText` — a plain DOM edit
 * on the marker's element, no SDK call. See docs/features/html-overlay-marker.md.
 */
private fun WebView.updateHtmlMarkerText(text: String) {
    evaluateJavascript("window.MapBridge.updateHtmlMarkerText(${JSONObject.quote(text)})", null)
}

/**
 * Shows/hides the placed badge via `window.MapBridge.setHtmlMarkerVisible`
 * (`venue.updateMarker(marker, { isVisible })` under the hood). See
 * docs/features/html-overlay-marker.md.
 */
private fun WebView.setHtmlMarkerVisible(isVisible: Boolean) {
    evaluateJavascript("window.MapBridge.setHtmlMarkerVisible($isVisible)", null)
}

/**
 * Removes the placed badge via `window.MapBridge.removeHtmlMarker` (`venue.removeMarker` under the
 * hood). A no-op on the JS side if nothing is placed. See docs/features/html-overlay-marker.md.
 */
private fun WebView.removeHtmlMarker() {
    evaluateJavascript("window.MapBridge.removeHtmlMarker()", null)
}

/**
 * The badge's whole state, as reported by `AndroidBridge.onHtmlMarkerStateChanged` after every
 * `html-overlay-marker` command and every tap on the badge. [status] names what just happened:
 * `"placed"`, `"tapped"`, `"text-updated"`, `"hidden"`, `"shown"`, `"removed"`,
 * `"text-required"`, `"anchor-not-found"`, `"anchor-has-no-position"` or `"error"` ([message]
 * carrying its text). [placedOnPoiId] is null while no badge is placed; [tapCount] counts taps on
 * the badge since it was last placed.
 */
data class HtmlMarkerState(
    val status: String,
    val placedOnPoiId: String?,
    val isVisible: Boolean,
    val tapCount: Int,
    val message: String? = null,
)

/** Parses the JSON object argument of `AndroidBridge.onHtmlMarkerStateChanged`. */
internal fun parseHtmlMarkerStatePayload(json: String): HtmlMarkerState {
    val root = JSONObject(json)
    return HtmlMarkerState(
        status = root.getString("status"),
        placedOnPoiId = if (root.isNull("placedOnPoiId")) null else root.getString("placedOnPoiId"),
        isVisible = root.getBoolean("isVisible"),
        tapCount = root.getInt("tapCount"),
        message = if (root.has("message")) root.getString("message") else null,
    )
}

/**
 * Screen-level values of the `html-overlay-marker` fields, kept across a close/reopen. The badge's
 * own state lives in the web page and comes back as [HtmlMarkerState].
 */
@Stable
class HtmlMarkerFieldsSession(initialText: String) {
    var anchorId by mutableStateOf(DemoMap.ANCHOR_POI_ID)
    var text by mutableStateOf(initialText)
}

/**
 * FAB-triggered control for `html-overlay-marker`: "Anchor POI ID" and "Badge text" fields, Place
 * badge/Update text/Hide-Show badge/Remove badge buttons and a status line. Everything except the
 * two fields is rendered from [htmlMarkerState] — the JS side is the source of truth (validation
 * included), and the panel has to be closed to tap the badge on the map. The badge's taps reach
 * native as a `"tapped"` state with the running [HtmlMarkerState.tapCount].
 * See docs/features/html-overlay-marker.md.
 */
@Composable
fun HtmlOverlayMarkerOverlay(fields: HtmlMarkerFieldsSession, webView: WebView?, htmlMarkerState: HtmlMarkerState?) {
    val placedOnPoiId = htmlMarkerState?.placedOnPoiId
    val isVisible = htmlMarkerState?.isVisible ?: true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = fields.anchorId,
            onValueChange = { fields.anchorId = it },
            label = { Text(stringResource(R.string.field_anchor_poi_id)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = fields.text,
            onValueChange = { fields.text = it },
            label = { Text(stringResource(R.string.field_badge_text)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { webView?.placeHtmlMarker(fields.anchorId.trim(), fields.text) },
                enabled = fields.anchorId.isNotBlank(),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text(stringResource(R.string.badge_place))
            }
            OutlinedButton(
                onClick = { webView?.updateHtmlMarkerText(fields.text) },
                enabled = placedOnPoiId != null,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text(stringResource(R.string.action_update_text))
            }
            OutlinedButton(
                onClick = { webView?.setHtmlMarkerVisible(!isVisible) },
                enabled = placedOnPoiId != null,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text(stringResource(if (isVisible) R.string.badge_hide else R.string.badge_show))
            }
            OutlinedButton(
                onClick = { webView?.removeHtmlMarker() },
                enabled = placedOnPoiId != null,
            ) {
                Text(stringResource(R.string.badge_remove))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val errorMessage = when (htmlMarkerState?.status) {
            "text-required" -> stringResource(R.string.text_required)
            "anchor-not-found" -> stringResource(R.string.poi_not_found)
            "anchor-has-no-position" -> stringResource(R.string.poi_has_no_position)
            "error" -> htmlMarkerState.message ?: stringResource(R.string.badge_place_failed)
            else -> null
        }
        if (errorMessage != null) {
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        } else {
            val statusMessage = when (htmlMarkerState?.status) {
                "placed" -> stringResource(R.string.badge_placed_on, placedOnPoiId.orEmpty())
                "tapped" -> stringResource(R.string.badge_tapped, htmlMarkerState.tapCount)
                "text-updated" -> stringResource(R.string.text_updated)
                "hidden" -> stringResource(R.string.badge_hidden)
                "shown" -> stringResource(R.string.badge_shown)
                else -> stringResource(R.string.badge_none)
            }
            Text(text = statusMessage, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

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
 * Asks the bridge for the current `custom-theme` state (`view.themes` + the current theme and
 * accent colors) via `window.MapBridge.getThemeState`; the answer comes back via
 * `AndroidBridge.onThemeStateChanged`. See docs/features/custom-theme.md.
 */
private fun WebView.getThemeState() {
    evaluateJavascript("window.MapBridge.getThemeState()", null)
}

/** Applies a scene theme via `window.MapBridge.setTheme` (`view.setTheme` under the hood). */
private fun WebView.setTheme(themeName: String) {
    evaluateJavascript("window.MapBridge.setTheme(${JSONObject.quote(themeName)})", null)
}

/** Recolors the SDK's UI buttons via `window.MapBridge.setAccentColor` (`view.accentColor = color`). */
private fun WebView.setAccentColor(color: String) {
    evaluateJavascript("window.MapBridge.setAccentColor(${JSONObject.quote(color)})", null)
}

/** Recolors the SDK's UI button text via `window.MapBridge.setTextAccentColor` (`view.textAccentColor = color`). */
private fun WebView.setTextAccentColor(color: String) {
    evaluateJavascript("window.MapBridge.setTextAccentColor(${JSONObject.quote(color)})", null)
}

/** Back to the 'day' theme and the default accent colors via `window.MapBridge.resetTheme`. */
private fun WebView.resetTheme() {
    evaluateJavascript("window.MapBridge.resetTheme()", null)
}

/**
 * The whole `custom-theme` state, as reported by `AndroidBridge.onThemeStateChanged` after every
 * command. [themes] is `view.themes` as-is; [currentTheme] is tracked JS-side (the SDK has no
 * getter for it); [accentColor]/[textAccentColor] are read back from the view (the SDK returns
 * whatever string was set, its own defaults in lowercase hex). [status] names what just happened —
 * `"loaded"`, `"theme-applied"`, `"accent-applied"`, `"text-accent-applied"`, `"reset"` or
 * `"error"` ([message] carrying the SDK's error text).
 */
data class ThemeState(
    val status: String,
    val themes: List<String>,
    val currentTheme: String,
    val accentColor: String,
    val textAccentColor: String,
    val message: String? = null,
)

/** Parses the JSON object argument of `AndroidBridge.onThemeStateChanged`. */
internal fun parseThemeStatePayload(json: String): ThemeState {
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
 * FAB-triggered control for `custom-theme`: a segmented control built at runtime from
 * `view.themes` (raw SDK ids), accent color and accent text color chips, a Reset button, a hint
 * and a status line. Every change applies immediately. Everything is rendered from [themeState] —
 * the JS side is the source of truth, and this sheet is disposed whenever it's closed, which it
 * has to be to tap a place and see its card's accent-colored buttons. The state is requested once
 * when the sheet opens. See docs/features/custom-theme.md.
 */
@Composable
fun CustomThemeOverlay(webView: WebView?, themeState: ThemeState?) {
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

/** The SDK's two `cameraProjection` values, paired with their localized label. */
private val CAMERA_PROJECTION_OPTIONS = listOf(
    "perspective" to R.string.camera_projection_option_perspective,
    "orthographic" to R.string.camera_projection_option_orthographic,
)

/**
 * FAB-triggered control for `camera-projection-toggle`: a two-option segmented control whose
 * selection is [currentProjection] — the value `view.cameraProjection` reported once the view was
 * created, not what was requested — and a one-line hint. Selecting the other option calls
 * [onSelectProjection], which recreates the whole map (fresh `loadVenue` + `createView` with the
 * new `cameraProjection`), since the SDK only takes the projection at view creation. Disabled while
 * that reload is in flight ([isMapReady] false, [currentProjection] null). See
 * docs/features/camera-projection-toggle.md.
 */
@Composable
fun CameraProjectionToggleOverlay(
    currentProjection: String?,
    isMapReady: Boolean,
    onSelectProjection: (String) -> Unit,
) {
    val enabled = isMapReady && currentProjection != null
    Column(modifier = Modifier.padding(16.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            CAMERA_PROJECTION_OPTIONS.forEachIndexed { index, (projection, labelRes) ->
                SegmentedButton(
                    selected = projection == currentProjection,
                    onClick = { if (projection != currentProjection) onSelectProjection(projection) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = CAMERA_PROJECTION_OPTIONS.size),
                ) {
                    Text(stringResource(labelRes))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.camera_projection_hint),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// --- camera-showcase-carousel ---

/**
 * Snapshot of the `camera-showcase-carousel` tour, pushed by the web page via
 * `AndroidBridge.onCarouselStateChanged` on every change — Start/Stop, but also a touch on the map
 * (pause) and the idle timer (resume), which happen with the sheet closed. [state] is `running`,
 * `paused` or `stopped`; [stopIndex] (0-based) / [poiId] is the stop in flight while running, or
 * the stop the tour resumes from while paused. See docs/features/camera-showcase-carousel.md.
 */
data class CarouselState(
    val state: String = "stopped",
    val stopIndex: Int = 0,
    val stopCount: Int = 0,
    val poiId: String = "",
    val resumeAfterMs: Long = 10_000,
)

internal fun parseCarouselStatePayload(json: String): CarouselState {
    val obj = JSONObject(json)
    return CarouselState(
        state = obj.optString("state", "stopped"),
        stopIndex = obj.optInt("stopIndex", 0),
        stopCount = obj.optInt("stopCount", 0),
        poiId = obj.optString("poiId", ""),
        resumeAfterMs = obj.optLong("resumeAfterMs", 10_000),
    )
}

/**
 * Starts (or resumes right away) the tour over [DemoMap.CAROUSEL_STOP_IDS] — the loop itself runs
 * in the web page, see web/src/main.js.
 */
private fun WebView.carouselStart() {
    val stopIds = JSONArray(DemoMap.CAROUSEL_STOP_IDS)
    evaluateJavascript("window.MapBridge.carouselStart($stopIds)", null)
}

/** Stops scheduling hops; the hop in flight, if any, ends on its own. */
private fun WebView.carouselStop() {
    evaluateJavascript("window.MapBridge.carouselStop()", null)
}

/**
 * FAB-triggered control for `camera-showcase-carousel`: a Start/Stop button, a status line and a
 * one-line hint. It holds no state of its own: the tour loop lives in the web page, and the sheet
 * renders [carouselState] (screen-level, see `FeatureBridgeState`), so it shows the right status
 * when reopened after a touch paused the tour or the idle timer resumed it with the sheet closed.
 * See docs/features/camera-showcase-carousel.md.
 */
@Composable
fun CameraShowcaseCarouselOverlay(webView: WebView?, carouselState: CarouselState) {
    val isStopped = carouselState.state == "stopped"
    Column(modifier = Modifier.padding(16.dp)) {
        if (isStopped) {
            Button(
                onClick = { webView?.carouselStart() },
                enabled = webView != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.carousel_start))
            }
        } else {
            OutlinedButton(
                onClick = { webView?.carouselStop() },
                enabled = webView != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.carousel_stop))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = when (carouselState.state) {
                "running" -> stringResource(
                    R.string.carousel_status_running,
                    carouselState.stopIndex + 1,
                    carouselState.stopCount,
                    carouselState.poiId,
                )
                "paused" -> stringResource(
                    R.string.carousel_status_paused,
                    (carouselState.resumeAfterMs / 1000).toInt(),
                )
                else -> stringResource(R.string.carousel_status_stopped)
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.carousel_hint, (carouselState.resumeAfterMs / 1000).toInt()),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// --- Screen-level state ---------------------------------------------------------------------

/**
 * Every piece of per-feature state that must outlive the FAB's bottom sheet, which is disposed on
 * every close: one instance per feature screen (remembered in `MainActivity`), each session created
 * lazily on first use, so a screen only ever builds its own. The rule for every screen: after the
 * panel is closed and reopened, it shows the map's real current state; leaving the screen drops
 * all of it (and [FeatureScreenHost]'s loops) together with the WebView.
 */
@Stable
class FeatureSessions(private val feature: Feature?, private val resources: Resources) {
    val occupancy by lazy { OccupancySession(DemoMap.OCCUPANCY_PLACE_ID) }
    val goToPoi by lazy { PlaceIdSession(DemoMap.GOTO_POI_ID) }
    val clickableSurface by lazy { PlaceIdSession(DemoMap.CLICKABLE_SURFACE_PLACE_ID) }
    val itinerary by lazy {
        when (feature) {
            Feature.NavigationExcludeModalities ->
                ItinerarySession(DemoMap.EXCLUDE_MODALITIES_FROM, DemoMap.EXCLUDE_MODALITIES_TO)
            Feature.AccessibleMode -> ItinerarySession(DemoMap.ACCESSIBLE_FROM, DemoMap.ACCESSIBLE_TO)
            else -> ItinerarySession(DemoMap.ITINERARY_FROM, DemoMap.ITINERARY_TO)
        }
    }
    val uiPartVisibility by lazy { UiPartVisibilitySession() }
    val nativeUiReplacement by lazy { NativeUiReplacementSession() }
    val tracking by lazy {
        if (feature == Feature.AccuracyCircleStyle) {
            accuracyCircleStyleSession()
        } else {
            TrackedPositionSession(initialOriginId = DemoMap.TRACKING_FROM, initialDestinationId = DemoMap.TRACKING_TO)
        }
    }
    val geofencing by lazy { GeofencingSession(tracking) }

    /** `accuracy-circle-style`'s selection — the SDK has no getter to read it back from. */
    var positionTrackerStyle by mutableStateOf(PositionTrackerStyle())
    val customData by lazy { CustomDataSession(DemoMap.CUSTOM_DATA_PLACE_ID) }
    val categoryHighlight by lazy { CategoryHighlightSession() }
    val dynamicPoi by lazy { DynamicPoiCrudSession(resources.getString(R.string.dynamic_poi_default_label)) }
    val runtimeLocale by lazy { RuntimeLocaleSession() }
    val addLocale by lazy { AddLocaleSession() }
    val iconMarker by lazy { IconMarkerSession() }
    val textLabel by lazy { TextLabelSession(resources.getString(R.string.custom_text_label_default_text)) }
    val htmlMarkerFields by lazy { HtmlMarkerFieldsSession(resources.getString(R.string.html_marker_default_text)) }
}

/**
 * Composed for the whole lifetime of a feature screen, outside the bottom sheet (via
 * `FeatureMapScreen`'s `screenContent`); renders nothing. It runs the screen's loops (tracked
 * position, geofencing, occupancy — exactly one per screen, whatever the number of panel
 * open/close cycles) and feeds every bridge response to its session, so a response that arrives
 * while the panel is closed is not lost.
 */
@Composable
fun FeatureScreenHost(
    feature: Feature?,
    sessions: FeatureSessions,
    webView: WebView?,
    bridgeState: FeatureBridgeState,
) {
    when (feature) {
        Feature.SimulatedPosition,
        Feature.CameraLockOnPosition,
        Feature.AccuracyCircleStyle,
        -> TrackedPositionSimulationHost(sessions.tracking, webView, bridgeState.positionsResolved)
        Feature.Geofencing -> GeofencingSimulationHost(
            sessions.geofencing,
            webView,
            bridgeState.positionsResolved,
            bridgeState.zoneResolved,
        )
        Feature.OccupancySimulated -> OccupancySimulationHost(sessions.occupancy, webView)
        Feature.CustomData -> LaunchedEffect(bridgeState.customDataLoaded) {
            bridgeState.customDataLoaded?.let(sessions.customData::onLoaded)
        }
        Feature.CategoryHighlight -> LaunchedEffect(bridgeState.categoriesLoaded) {
            bridgeState.categoriesLoaded?.let(sessions.categoryHighlight::onCategoriesLoaded)
        }
        Feature.DynamicPoiCrud -> LaunchedEffect(bridgeState.dynamicPoiCreated) {
            bridgeState.dynamicPoiCreated?.let(sessions.dynamicPoi::onCreated)
        }
        Feature.RuntimeLocale -> LaunchedEffect(bridgeState.localeResolved) {
            bridgeState.localeResolved?.let(sessions.runtimeLocale::onLocaleResolved)
        }
        Feature.AddLocale -> {
            LaunchedEffect(bridgeState.addLocaleResolved) {
                bridgeState.addLocaleResolved?.let(sessions.addLocale::onAddLocaleResolved)
            }
            LaunchedEffect(bridgeState.localeResolved) {
                bridgeState.localeResolved?.let(sessions.addLocale::onLocaleResolved)
            }
        }
        Feature.IconMarker3d -> LaunchedEffect(bridgeState.iconImagePlaced) {
            bridgeState.iconImagePlaced?.let { sessions.iconMarker.onPlaced(webView, it) }
        }
        Feature.CustomTextLabel -> LaunchedEffect(bridgeState.textLabelPlaced) {
            bridgeState.textLabelPlaced?.let { sessions.textLabel.onPlaced(webView, it) }
        }
        else -> Unit
    }
}

/** The FAB's bottom-sheet content of each feature screen, bound to that screen's [sessions]. */
@Composable
fun FeatureSheetContent(
    feature: Feature?,
    sessions: FeatureSessions,
    webView: WebView?,
    bridgeState: FeatureBridgeState,
) {
    when (feature) {
        Feature.ResetView -> ResetViewOverlay(webView)
        Feature.OccupancySimulated -> OccupancySimulationOverlay(sessions.occupancy)
        Feature.PoiClick -> PoiClickOverlay(bridgeState.lastPoiClick)
        Feature.GoToPoi -> GoToPoiOverlay(sessions.goToPoi, webView)
        Feature.FloorSelector -> FloorSelectorOverlay(webView, bridgeState.floorSelector)
        Feature.ComputeNavigation ->
            ComputeNavigationOverlay(sessions.itinerary, webView, bridgeState.navigationError)
        Feature.UiPartVisibility -> UiPartVisibilityOverlay(sessions.uiPartVisibility, webView)
        Feature.NativeUiReplacement ->
            NativeUiReplacementOverlay(sessions.nativeUiReplacement, webView, bridgeState.floorSelector)
        Feature.SimulatedPosition -> SimulatedPositionOverlay(sessions.tracking, webView)
        Feature.CameraLockOnPosition ->
            CameraLockOnPositionOverlay(sessions.tracking, webView, bridgeState.isCameraLocked)
        Feature.ClickableSurface -> ClickableSurfaceOverlay(sessions.clickableSurface, webView)
        Feature.CustomData -> CustomDataOverlay(sessions.customData, webView)
        Feature.CategoryHighlight -> CategoryHighlightOverlay(sessions.categoryHighlight, webView)
        Feature.DynamicPoiCrud -> DynamicPoiCrudOverlay(sessions.dynamicPoi, webView)
        Feature.RuntimeLocale -> RuntimeLocaleOverlay(sessions.runtimeLocale, webView)
        Feature.ExploreMode -> ExploreModeOverlay(webView, bridgeState.currentExploreMode)
        Feature.AddLocale -> AddLocaleOverlay(sessions.addLocale, webView)
        Feature.Geofencing ->
            GeofencingOverlay(sessions.geofencing, webView, bridgeState.isInsideGeofenceZone)
        Feature.CustomBaseUrl -> CustomBaseUrlOverlay(bridgeState.currentBaseUrl, bridgeState.onReloadWithBaseUrl)
        Feature.CustomNavigationTrace ->
            CustomNavigationTraceOverlay(sessions.itinerary, webView, bridgeState.navigationError)
        Feature.NavigationExcludeModalities ->
            NavigationExcludeModalitiesOverlay(sessions.itinerary, webView, bridgeState.navigationError)
        Feature.AccessibleMode -> AccessibleModeOverlay(sessions.itinerary, webView, bridgeState.navigationError)
        Feature.IconMarker3d -> IconMarker3dOverlay(sessions.iconMarker, webView)
        Feature.CustomTextLabel -> CustomTextLabelOverlay(sessions.textLabel, webView)
        Feature.HtmlOverlayMarker ->
            HtmlOverlayMarkerOverlay(sessions.htmlMarkerFields, webView, bridgeState.htmlMarkerState)
        Feature.CustomTheme -> CustomThemeOverlay(webView, bridgeState.themeState)
        Feature.AccuracyCircleStyle -> AccuracyCircleStyleOverlay(
            sessions.tracking,
            webView,
            sessions.positionTrackerStyle,
            onStyleChange = { sessions.positionTrackerStyle = it },
        )
        Feature.CameraProjectionToggle -> CameraProjectionToggleOverlay(
            bridgeState.cameraProjection,
            bridgeState.isMapReady,
            bridgeState.onReloadWithCameraProjection,
        )
        Feature.CameraShowcaseCarousel -> CameraShowcaseCarouselOverlay(webView, bridgeState.carouselState)
        Feature.AuthToken, null -> Unit
    }
}
