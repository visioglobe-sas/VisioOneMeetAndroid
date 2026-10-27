package com.visioglobe.visioonemeet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.visioglobe.visioonemeet.model.DemoMap
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.AuthTokenSampleScreen
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.FeatureMenuScreen
import com.visioglobe.visioonemeet.ui.FeatureScreenHost
import com.visioglobe.visioonemeet.ui.FeatureSessions
import com.visioglobe.visioonemeet.ui.FeatureSheetContent
import com.visioglobe.visioonemeet.ui.setUiPartVisible
import com.visioglobe.visioonemeet.ui.theme.VisioOneMeetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VisioOneMeetTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        FeatureMenuScreen(
                            onFeatureSelected = { feature -> navController.navigate("feature/${feature.slug}") },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    composable("feature/{slug}") { backStackEntry ->
                        val feature = Feature.fromSlug(backStackEntry.arguments?.getString("slug"))
                        // auth-token is a code sample only (no protected demo map): a text screen,
                        // no map. See docs/features/auth-token.md.
                        if (feature == Feature.AuthToken) {
                            AuthTokenSampleScreen(
                                onBack = { navController.popBackStack() },
                                modifier = Modifier.fillMaxSize(),
                            )
                            return@composable
                        }
                        // Per-feature state lives here, at screen level, never inside the FAB's bottom
                        // sheet (disposed on every close): see FeatureSessions.
                        val resources = LocalResources.current
                        val sessions = remember { FeatureSessions(feature, resources) }
                        FeatureMapScreen(
                            mapHash = if (feature == Feature.CustomData) DemoMap.CUSTOM_DATA_MAP_HASH else DemoMap.MAP_HASH,
                            titleRes = feature?.titleRes,
                            modifier = Modifier.fillMaxSize(),
                            reactsToPoiClicks = feature == Feature.PoiClick,
                            // Themes only change the scene lights, rendered only with shadows on —
                            // see docs/features/custom-theme.md.
                            enableShadows = feature == Feature.CustomTheme,
                            onMapReady = { webView ->
                                // Hides the SDK's own default floor-selector widget as soon as the
                                // map loads, matching NativeUiReplacementOverlay's switch, which
                                // starts off — see docs/features/native-ui-replacement.md.
                                if (feature == Feature.NativeUiReplacement) {
                                    webView.setUiPartVisible("floorSelector", false)
                                }
                            },
                            onBack = { navController.popBackStack() },
                            screenContent = { webView, bridgeState ->
                                FeatureScreenHost(feature, sessions, webView, bridgeState)
                            },
                            sheetContent = { webView, bridgeState ->
                                FeatureSheetContent(feature, sessions, webView, bridgeState)
                            },
                        )
                    }
                }
            }
        }
    }
}
