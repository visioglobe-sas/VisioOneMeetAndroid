package com.visioglobe.visioonemeet.ui

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.visioglobe.visioonemeet.R
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
 * Shows/hides one of the SDK's own default UI overlays by calling
 * `window.MapBridge.setUIPartVisible` in the WebView (`view.setUIPartVisible` under the hood).
 * [uiPart] must be one of the SDK's exact, case-sensitive values (`floorSelector`, `navigation`,
 * `poiDetails`, `search`, `userTracking`) — see [UI_PART_TOGGLES] below. See
 * docs/features/ui-part-visibility.md.
 */
private fun WebView.setUiPartVisible(uiPart: String, isVisible: Boolean) {
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
 * hood, after setting `view.allowTracking = true`). [precisionCircleRadiusMeters] is the accuracy
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

@Composable
fun ResetViewOverlay(webView: WebView?) {
    Button(
        onClick = { webView?.goToGlobal() },
        modifier = Modifier.padding(16.dp),
    ) {
        Text(stringResource(R.string.feature_reset_view_title))
    }
}

@Composable
fun OccupancySimulationOverlay(webView: WebView?) {
    var placeId by remember { mutableStateOf("") }
    var simulatingOccupancy by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = placeId,
            onValueChange = { placeId = it },
            label = { Text("Place ID") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = { simulatingOccupancy = !simulatingOccupancy },
            enabled = simulatingOccupancy || placeId.isNotBlank(),
        ) {
            Text(if (simulatingOccupancy) "Stop occupancy simulation" else "Simulate occupancy")
        }
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

/**
 * FAB-triggered control for `goto-poi`: a Place ID field plus "Go" (centers the camera on that
 * POI via [WebView.goToPlace]) and "Clear" (undoes the highlight via [WebView.clearPlace], camera
 * stays put) — same UX as the React Native sibling's `GoToPoiOverlay`. See docs/features/goto-poi.md.
 */
@Composable
fun GoToPoiOverlay(webView: WebView?) {
    var placeId by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = placeId,
            onValueChange = { placeId = it },
            label = { Text("Place ID") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = { webView?.goToPlace(placeId.trim()) },
            enabled = placeId.isNotBlank(),
        ) {
            Text("Go")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = { webView?.clearPlace() }) {
            Text("Clear")
        }
    }
}

/**
 * FAB-triggered control for `floor-selector`: one button per floor of the current building,
 * highest first, calling [WebView.goToFloor] on tap. The current floor (per
 * [FloorSelectorState.currentFloorId]) is rendered as a filled [Button] (and disabled, since
 * tapping it again would be a no-op); every other floor is an [OutlinedButton]. Demonstrates
 * driving `view.goToFloor` from native code — the SDK's own default floor-selector widget
 * (visible on the map with no app code at all) already offers this to the end user, see
 * docs/features/floor-selector.md, "Points d'attention".
 */
@Composable
fun FloorSelectorOverlay(webView: WebView?, floorSelector: FloorSelectorState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        if (floorSelector.floors.isEmpty()) {
            Text("Floor data is not available yet.")
            return@Column
        }
        val buildingId = floorSelector.buildingId
        floorSelector.floors
            .sortedByDescending { it.levelIndex }
            .forEach { floor ->
                val isCurrent = floor.id == floorSelector.currentFloorId
                val buttonModifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                if (isCurrent) {
                    Button(onClick = {}, enabled = false, modifier = buttonModifier) {
                        Text(floor.name)
                    }
                } else {
                    OutlinedButton(
                        onClick = { if (buildingId != null) webView?.goToFloor(buildingId, floor.id) },
                        modifier = buttonModifier,
                    ) {
                        Text(floor.name)
                    }
                }
            }
    }
}

/**
 * FAB-triggered control for `compute-navigation`: "From"/"To" Place ID fields plus an "Itinerary"
 * button (computes and draws the route via [WebView.computeNavigation]) and a "Clear" button
 * (removes it via [WebView.clearNavigation]) — same two-field UX as the React Native sibling's
 * `ComputeNavigationOverlay`, `isAccessible` fixed to `false` as on that sibling (no UI toggle for
 * it yet). A failed computation (bad/unreachable Place ID) surfaces [navigationError] below the
 * fields instead of leaving the map silently unchanged. See docs/features/compute-navigation.md.
 */
@Composable
fun ComputeNavigationOverlay(webView: WebView?, navigationError: String?) {
    var origin by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = origin,
                onValueChange = { origin = it },
                label = { Text("From (place ID)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = destination,
                onValueChange = { destination = it },
                label = { Text("To (place ID)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { webView?.computeNavigation(origin.trim(), destination.trim(), false) },
                enabled = origin.isNotBlank() && destination.isNotBlank(),
            ) {
                Text("Itinerary")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { webView?.clearNavigation() }) {
                Text("Clear")
            }
        }
        if (navigationError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = navigationError, color = MaterialTheme.colorScheme.error)
        }
    }
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
            Text("Tap a POI on the map to see its details.")
            return@Column
        }
        pois.forEachIndexed { index, poi ->
            if (index > 0) Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = poi.name.ifBlank { poi.id },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = "ID: ${poi.id}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * FAB-triggered control for `ui-part-visibility`: one switch per [UI_PART_TOGGLES] entry, each
 * calling [WebView.setUiPartVisible] on toggle so the effect is visible immediately on the map
 * behind the sheet. All 5 default to visible/on, matching the SDK's own default — nothing is
 * hidden until the user flips a switch. Local state only: this overlay does not read back
 * `view.isUIPartVisible`, so re-opening the sheet after leaving and returning to this screen still
 * shows all switches on regardless of the map's actual state (the WebView, and the underlying
 * view/venue, are recreated on every screen visit, see `FeatureMapScreen`'s kdoc — so in practice
 * the two stay in sync anyway). See docs/features/ui-part-visibility.md.
 */
@Composable
fun UiPartVisibilityOverlay(webView: WebView?) {
    val visibility = remember {
        mutableStateMapOf(*UI_PART_TOGGLES.map { (uiPart, _) -> uiPart to true }.toTypedArray())
    }

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
                    checked = visibility[uiPart] == true,
                    onCheckedChange = { isVisible ->
                        visibility[uiPart] = isVisible
                        webView?.setUiPartVisible(uiPart, isVisible)
                    },
                )
            }
        }
    }
}

/**
 * Origin/Destination POI ID fields, a radius `Slider` (1-20m,
 * [SIMULATED_POSITION_DEFAULT_RADIUS_METERS] by default) and a Start/Stop toggle button — same
 * toggle pattern as [OccupancySimulationOverlay] — shared by [SimulatedPositionOverlay] and
 * [CameraLockOnPositionOverlay] (the latter needs a moving position to demonstrate a camera lock
 * on it). Pressing Start resolves both POI ids in one round trip via [WebView.resolvePositions]; a
 * `null` slot in the response ("POI not found") surfaces [errorMessage] below the fields instead
 * of starting anything, same error-surfacing convention as [ComputeNavigationOverlay]. Once both
 * positions resolve, a [LaunchedEffect] repeatedly calls [WebView.injectTrackedPosition] with a
 * point linearly interpolated between origin and destination, ping-ponging back and forth —
 * reading [radiusMeters] fresh on every tick, so moving the slider while running changes the
 * radius on the *next* tick with no restart needed. Stop (or leaving the screen, which cancels
 * this effect and tears down the WebView) calls [WebView.stopTrackedPosition] via the effect's
 * `finally` block, same cleanup-on-cancellation idiom as [OccupancySimulationOverlay].
 * [onRunningChanged] reports every start/stop transition so a caller can react (e.g. resetting a
 * dependent switch); [extraContent] renders below the Start/Stop button and its error message,
 * nothing for [SimulatedPositionOverlay], the camera-lock switch for [CameraLockOnPositionOverlay].
 * See docs/features/simulated-position.md and docs/features/camera-lock-on-position.md.
 */
@Composable
private fun TrackedPositionSimulationControls(
    webView: WebView?,
    positionsResolved: ResolvedPositionsPair?,
    onRunningChanged: (Boolean) -> Unit = {},
    extraContent: @Composable () -> Unit = {},
) {
    var originId by remember { mutableStateOf("") }
    var destinationId by remember { mutableStateOf("") }
    var radiusMeters by remember { mutableFloatStateOf(SIMULATED_POSITION_DEFAULT_RADIUS_METERS) }
    var isRunning by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var activePositions by remember { mutableStateOf<Pair<ResolvedPosition, ResolvedPosition>?>(null) }
    var pendingRequestId by remember { mutableStateOf<Int?>(null) }
    var nextRequestId by remember { mutableStateOf(0) }

    // Reacts to the response of the resolvePositions() call fired by the Start button below.
    // `requestId` guards against a stale response from a superseded request overwriting the
    // outcome of a more recent one (both are near-instant in practice, but this costs nothing).
    LaunchedEffect(positionsResolved) {
        val result = positionsResolved ?: return@LaunchedEffect
        if (result.requestId != pendingRequestId) return@LaunchedEffect
        pendingRequestId = null
        val origin = result.origin
        val destination = result.destination
        if (origin == null || destination == null) {
            errorMessage = "POI not found"
            isRunning = false
        } else {
            errorMessage = null
            activePositions = origin to destination
            isRunning = true
        }
    }

    LaunchedEffect(isRunning) { onRunningChanged(isRunning) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = originId,
                onValueChange = { originId = it },
                label = { Text("Origin POI ID") },
                singleLine = true,
                enabled = !isRunning,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = destinationId,
                onValueChange = { destinationId = it },
                label = { Text("Destination POI ID") },
                singleLine = true,
                enabled = !isRunning,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Accuracy radius: ${radiusMeters.roundToInt()} m")
        Slider(
            value = radiusMeters,
            onValueChange = { radiusMeters = it },
            valueRange = SIMULATED_POSITION_MIN_RADIUS_METERS..SIMULATED_POSITION_MAX_RADIUS_METERS,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (isRunning) {
                    isRunning = false
                    activePositions = null
                    pendingRequestId = null
                } else {
                    errorMessage = null
                    val requestId = nextRequestId++
                    pendingRequestId = requestId
                    webView?.resolvePositions(requestId, originId.trim(), destinationId.trim())
                }
            },
            enabled = isRunning || (originId.isNotBlank() && destinationId.isNotBlank()),
        ) {
            Text(if (isRunning) "Stop simulated position" else "Start simulated position")
        }
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
        }
        extraContent()
    }

    LaunchedEffect(isRunning, activePositions, webView) {
        val (origin, destination) = activePositions ?: return@LaunchedEffect
        val view = webView ?: return@LaunchedEffect
        if (!isRunning) return@LaunchedEffect

        var fraction = 0.0
        var direction = 1.0
        try {
            while (isActive) {
                val latitude = origin.latitude + (destination.latitude - origin.latitude) * fraction
                val longitude = origin.longitude + (destination.longitude - origin.longitude) * fraction
                view.injectTrackedPosition(latitude, longitude, radiusMeters.toDouble())
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
 * FAB-triggered control for `simulated-position`: [TrackedPositionSimulationControls] with no
 * extra content. See docs/features/simulated-position.md.
 */
@Composable
fun SimulatedPositionOverlay(webView: WebView?, positionsResolved: ResolvedPositionsPair?) {
    TrackedPositionSimulationControls(webView, positionsResolved)
}

/**
 * FAB-triggered control for `camera-lock-on-position`: [TrackedPositionSimulationControls] (a
 * moving position is needed to demonstrate a camera lock on it) plus a "Recenter on me" `Switch`
 * that flips `view.lockCameraPositionOnTracking` via [WebView.setCameraLockOnPosition] — the
 * GPS-app-style behavior this feature demonstrates. The switch is disabled while no simulation is
 * running (nothing to lock onto) and is reset to off whenever the simulation stops, so restarting
 * always begins unlocked — a fresh, deliberate opt-in each time, same pattern as radiusMeters not
 * needing a restart to apply. See docs/features/camera-lock-on-position.md.
 */
@Composable
fun CameraLockOnPositionOverlay(webView: WebView?, positionsResolved: ResolvedPositionsPair?) {
    var isRunning by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }

    TrackedPositionSimulationControls(
        webView = webView,
        positionsResolved = positionsResolved,
        onRunningChanged = { running ->
            isRunning = running
            if (!running && isLocked) {
                isLocked = false
                webView?.setCameraLockOnPosition(false)
            }
        },
        extraContent = {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Recenter camera on position", modifier = Modifier.weight(1f))
                Switch(
                    checked = isLocked,
                    enabled = isRunning,
                    onCheckedChange = { locked ->
                        isLocked = locked
                        webView?.setCameraLockOnPosition(locked)
                    },
                )
            }
        },
    )
}

/**
 * FAB-triggered control for `clickable-surface`: a Place ID field plus "Enable"/"Disable" buttons
 * calling [WebView.setSurfaceInteractive] — same two-button pattern as [GoToPoiOverlay]'s
 * "Go"/"Clear". Once enabled, the SDK itself handles the hover/selection color swap when the
 * surface is tapped on the map; this overlay has no click listener of its own for that. See
 * docs/features/clickable-surface.md.
 */
@Composable
fun ClickableSurfaceOverlay(webView: WebView?) {
    var placeId by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = placeId,
            onValueChange = { placeId = it },
            label = { Text("Place ID") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = { webView?.setSurfaceInteractive(placeId.trim(), true) },
            enabled = placeId.isNotBlank(),
        ) {
            Text("Enable")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = { webView?.setSurfaceInteractive(placeId.trim(), false) },
            enabled = placeId.isNotBlank(),
        ) {
            Text("Disable")
        }
    }
}

/**
 * Place IDs confirmed (via a direct query against the venue behind [CUSTOM_DATA_MAP_HASH] in
 * `MainActivity.kt`) to carry real, non-empty CustomData — offered as quick-select chips in
 * [CustomDataOverlay] so trying the feature doesn't require knowing a valid POI id upfront. See
 * docs/features/custom-data.md.
 */
private val CUSTOM_DATA_SAMPLE_PLACE_IDS = listOf("B1", "B3-UL00-ID0065", "B3-UL00-ID0064")

/**
 * FAB-triggered control for `custom-data`: a Place ID field plus a single "Load" button that calls
 * [WebView.loadCustomData] — combining `venue.refreshCustomData()` and `venue.getPOICustomData()`
 * into one round trip, since a demo has no reason to expose them as two separate steps. Above the
 * field, one chip per [CUSTOM_DATA_SAMPLE_PLACE_IDS] entry fills it and immediately triggers Load,
 * since free-typing a valid id is otherwise guesswork; the field still accepts free-text entry for
 * any other id. `requestId` guards against a stale response overwriting a more recent request's
 * outcome, same pattern as [TrackedPositionSimulationControls]. All three outcomes are rendered as
 * normal states, not errors: every key/value pair when [CustomDataResult.customData] is non-empty,
 * "No custom data for this POI" when it resolves to an empty map, and "POI not found" when it's
 * `null`. See docs/features/custom-data.md.
 */
@Composable
fun CustomDataOverlay(webView: WebView?, customDataLoaded: CustomDataResult?) {
    var placeId by remember { mutableStateOf("") }
    var pendingRequestId by remember { mutableStateOf<Int?>(null) }
    var nextRequestId by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<Map<String, String>?>(null) }
    var hasResult by remember { mutableStateOf(false) }

    LaunchedEffect(customDataLoaded) {
        val response = customDataLoaded ?: return@LaunchedEffect
        if (response.requestId != pendingRequestId) return@LaunchedEffect
        pendingRequestId = null
        result = response.customData
        hasResult = true
    }

    fun load(targetPlaceId: String) {
        hasResult = false
        result = null
        val requestId = nextRequestId++
        pendingRequestId = requestId
        webView?.loadCustomData(requestId, targetPlaceId)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text("Known POIs with real data:", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            CUSTOM_DATA_SAMPLE_PLACE_IDS.forEach { sampleId ->
                OutlinedButton(
                    onClick = {
                        placeId = sampleId
                        load(sampleId)
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
                value = placeId,
                onValueChange = { placeId = it },
                label = { Text("Place ID") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { load(placeId.trim()) },
                enabled = placeId.isNotBlank(),
            ) {
                Text("Load")
            }
        }
        if (hasResult) {
            Spacer(modifier = Modifier.height(12.dp))
            val data = result
            when {
                data == null -> Text("POI not found", color = MaterialTheme.colorScheme.error)
                data.isEmpty() -> Text("No custom data for this POI")
                else -> data.forEach { (key, value) ->
                    Text(text = "$key: $value", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
