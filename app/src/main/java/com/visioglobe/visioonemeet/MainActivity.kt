package com.visioglobe.visioonemeet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.AddLocaleOverlay
import com.visioglobe.visioonemeet.ui.CameraLockOnPositionOverlay
import com.visioglobe.visioonemeet.ui.CategoryHighlightOverlay
import com.visioglobe.visioonemeet.ui.ClickableSurfaceOverlay
import com.visioglobe.visioonemeet.ui.ComputeNavigationOverlay
import com.visioglobe.visioonemeet.ui.CustomDataOverlay
import com.visioglobe.visioonemeet.ui.DynamicPoiCrudOverlay
import com.visioglobe.visioonemeet.ui.ExploreModeOverlay
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.FeatureMenuScreen
import com.visioglobe.visioonemeet.ui.FloorSelectorOverlay
import com.visioglobe.visioonemeet.ui.GoToPoiOverlay
import com.visioglobe.visioonemeet.ui.NativeUiReplacementOverlay
import com.visioglobe.visioonemeet.ui.OccupancySimulationOverlay
import com.visioglobe.visioonemeet.ui.PoiClickOverlay
import com.visioglobe.visioonemeet.ui.ResetViewOverlay
import com.visioglobe.visioonemeet.ui.RuntimeLocaleOverlay
import com.visioglobe.visioonemeet.ui.SimulatedPositionOverlay
import com.visioglobe.visioonemeet.ui.UiPartVisibilityOverlay
import com.visioglobe.visioonemeet.ui.setUiPartVisible
import com.visioglobe.visioonemeet.ui.theme.VisioOneMeetTheme

/** Hash of the VisioOne map to display, as found on the my.visioglobe.com portal. */
private const val DEFAULT_MAP_HASH = "kbae8e6c066cca4b02c2afac2bc963a643d87437a"

/**
 * Dedicated map hash used only by [Feature.CustomData], which needs a venue with real published
 * CustomData to demonstrate anything beyond the empty state — [DEFAULT_MAP_HASH] has none. See
 * docs/features/custom-data.md.
 */
private const val CUSTOM_DATA_MAP_HASH = "kd9426d8cb3f1c532f22b5bcbd325c280bd351feb"

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
                        FeatureMapScreen(
                            mapHash = if (feature == Feature.CustomData) CUSTOM_DATA_MAP_HASH else DEFAULT_MAP_HASH,
                            titleRes = feature?.titleRes,
                            modifier = Modifier.fillMaxSize(),
                            reactsToPoiClicks = feature == Feature.PoiClick,
                            onMapReady = { webView ->
                                // Hides the SDK's own default floor-selector widget as soon as the
                                // map loads, matching NativeUiReplacementOverlay's switch, which
                                // starts off — see docs/features/native-ui-replacement.md.
                                if (feature == Feature.NativeUiReplacement) {
                                    webView.setUiPartVisible("floorSelector", false)
                                }
                            },
                            onBack = { navController.popBackStack() },
                            sheetContent = { webView, bridgeState ->
                                when (feature) {
                                    Feature.ResetView -> ResetViewOverlay(webView)
                                    Feature.OccupancySimulated -> OccupancySimulationOverlay(webView)
                                    Feature.PoiClick -> PoiClickOverlay(bridgeState.lastPoiClick)
                                    Feature.GoToPoi -> GoToPoiOverlay(webView)
                                    Feature.FloorSelector -> FloorSelectorOverlay(webView, bridgeState.floorSelector)
                                    Feature.ComputeNavigation ->
                                        ComputeNavigationOverlay(webView, bridgeState.navigationError)
                                    Feature.UiPartVisibility -> UiPartVisibilityOverlay(webView)
                                    Feature.NativeUiReplacement ->
                                        NativeUiReplacementOverlay(webView, bridgeState.floorSelector)
                                    Feature.SimulatedPosition ->
                                        SimulatedPositionOverlay(webView, bridgeState.positionsResolved)
                                    Feature.CameraLockOnPosition ->
                                        CameraLockOnPositionOverlay(webView, bridgeState.positionsResolved)
                                    Feature.ClickableSurface -> ClickableSurfaceOverlay(webView)
                                    Feature.CustomData -> CustomDataOverlay(webView, bridgeState.customDataLoaded)
                                    Feature.CategoryHighlight ->
                                        CategoryHighlightOverlay(webView, bridgeState.categoriesLoaded)
                                    Feature.DynamicPoiCrud ->
                                        DynamicPoiCrudOverlay(webView, bridgeState.dynamicPoiCreated)
                                    Feature.RuntimeLocale -> RuntimeLocaleOverlay(webView, bridgeState.localeResolved)
                                    Feature.ExploreMode ->
                                        ExploreModeOverlay(webView, bridgeState.currentExploreMode)
                                    Feature.AddLocale ->
                                        AddLocaleOverlay(webView, bridgeState.addLocaleResolved)
                                    null -> Unit
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
