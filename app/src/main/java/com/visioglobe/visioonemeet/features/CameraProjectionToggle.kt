package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.visioglobe.visioonemeet.R
import com.visioglobe.visioonemeet.bridge.BridgeEvent
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.OnBridgeEvent
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState

// camera-projection-toggle — JS side: web/src/features/camera-projection-toggle.js.
// See docs/features/camera-projection-toggle.md.

/** The SDK's two `cameraProjection` values, paired with their localized label. */
private val CAMERA_PROJECTION_OPTIONS = listOf(
    "perspective" to R.string.camera_projection_option_perspective,
    "orthographic" to R.string.camera_projection_option_orthographic,
)

/**
 * `cameraProjection` is a `createView`-time option only (`view.cameraProjection` is read-only, no
 * runtime switch), so switching it recreates the WebView against a new `?cameraProjection=` query
 * param (`reloadWithCameraProjection`), i.e. a fresh page, `loadVenue` and `createView`. The page
 * reports the projection the new view was actually created with via
 * `AndroidBridge.onCameraProjectionReported`; it is reset to `null` while reloading.
 */
@Composable
fun CameraProjectionToggleScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    var reportedProjection by remember { mutableStateOf<String?>(null) }
    OnBridgeEvent<BridgeEvent.CameraProjectionReported>(map) { reportedProjection = it.projection }
    FeatureMapScreen(map, Feature.CameraProjectionToggle, onBack) {
        CameraProjectionToggleSheet(
            currentProjection = reportedProjection,
            isMapReady = map.isMapReady,
            onSelectProjection = { newProjection ->
                if (newProjection != map.cameraProjection) {
                    reportedProjection = null
                    map.reloadWithCameraProjection(newProjection)
                }
            },
        )
    }
}

/**
 * A two-option segmented control whose
 * selection is [currentProjection] — the value `view.cameraProjection` reported once the view was
 * created, not what was requested — and a one-line hint. Selecting the other option calls
 * [onSelectProjection], which recreates the whole map (fresh `loadVenue` + `createView` with the
 * new `cameraProjection`), since the SDK only takes the projection at view creation. Disabled while
 * that reload is in flight ([isMapReady] false, [currentProjection] null).
 */
@Composable
private fun CameraProjectionToggleSheet(
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
