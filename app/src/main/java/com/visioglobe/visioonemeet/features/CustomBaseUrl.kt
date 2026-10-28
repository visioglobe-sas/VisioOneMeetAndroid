package com.visioglobe.visioonemeet.features

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
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
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.rememberFeatureMapState

// custom-base-url — JS side: web/src/features/custom-base-url.js (the `?baseUrl=` query param,
// passed to loadVenue in web/src/main.js). See docs/features/custom-base-url.md.

/**
 * A "Base URL" field pre-filled with the map's current base URL (at first the SDK's own real
 * default, `DEFAULT_SDK_BASE_URL`) and a "Reload" button. Unlike every other screen, this doesn't
 * call anything on the WebView: `LoadOptions.baseURL` only takes effect when `loadVenue` runs, so
 * there is nothing to call on an already-loaded venue. Instead `reloadWithBaseUrl` makes
 * `FeatureMapScreen` fully recreate the WebView against the new `?baseUrl=` value. Both outcomes
 * surface through the screen's own loading/error UI, not a message rendered here: the default URL
 * reloading successfully proves the parameter is genuinely wired through (it's the same value the
 * SDK would use anyway), and a garbage URL reloading into the screen's load-error message — with
 * the SDK's own `VenueNotFoundError` message — is a valid demo outcome too.
 */
@Composable
fun CustomBaseUrlScreen(onBack: () -> Unit) {
    val map = rememberFeatureMapState()
    FeatureMapScreen(map, Feature.CustomBaseUrl, onBack) {
        var baseUrl by remember { mutableStateOf(map.baseUrl) }

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
                onClick = { map.reloadWithBaseUrl(baseUrl.trim()) },
                enabled = baseUrl.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_reload))
            }
        }
    }
}
