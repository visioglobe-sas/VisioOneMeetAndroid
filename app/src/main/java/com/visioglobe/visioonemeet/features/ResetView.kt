package com.visioglobe.visioonemeet.features

import android.webkit.WebView
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.visioglobe.visioonemeet.R
import com.visioglobe.visioonemeet.bridge.callBridge
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState

// reset-view — JS side: web/src/features/reset-view.js. See docs/features/reset-view.md.

/** Recenters the camera on the whole venue (`view.goToGlobal()`). */
private fun WebView.goToGlobal() = callBridge("goToGlobal")

@Composable
fun ResetViewScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    FeatureMapScreen(map, Feature.ResetView, onBack) {
        Button(
            onClick = { map.webView?.goToGlobal() },
            modifier = Modifier.padding(16.dp),
        ) {
            Text(stringResource(R.string.feature_reset_view_title))
        }
    }
}
