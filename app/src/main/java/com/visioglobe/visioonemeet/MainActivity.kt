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
import com.visioglobe.visioonemeet.ui.ComputeNavigationOverlay
import com.visioglobe.visioonemeet.ui.FeatureMapScreen
import com.visioglobe.visioonemeet.ui.FeatureMenuScreen
import com.visioglobe.visioonemeet.ui.FloorSelectorOverlay
import com.visioglobe.visioonemeet.ui.GoToPoiOverlay
import com.visioglobe.visioonemeet.ui.OccupancySimulationOverlay
import com.visioglobe.visioonemeet.ui.PoiClickOverlay
import com.visioglobe.visioonemeet.ui.ResetViewOverlay
import com.visioglobe.visioonemeet.ui.theme.VisioOneMeetTheme

/** Hash of the VisioOne map to display, as found on the my.visioglobe.com portal. */
private const val DEFAULT_MAP_HASH = "kbae8e6c066cca4b02c2afac2bc963a643d87437a"

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
                            mapHash = DEFAULT_MAP_HASH,
                            titleRes = feature?.titleRes,
                            modifier = Modifier.fillMaxSize(),
                            reactsToPoiClicks = feature == Feature.PoiClick,
                            onBack = { navController.popBackStack() },
                            sheetContent = { webView, lastPoiClick, floorSelector, navigationError ->
                                when (feature) {
                                    Feature.ResetView -> ResetViewOverlay(webView)
                                    Feature.OccupancySimulated -> OccupancySimulationOverlay(webView)
                                    Feature.PoiClick -> PoiClickOverlay(lastPoiClick)
                                    Feature.GoToPoi -> GoToPoiOverlay(webView)
                                    Feature.FloorSelector -> FloorSelectorOverlay(webView, floorSelector)
                                    Feature.ComputeNavigation -> ComputeNavigationOverlay(webView, navigationError)
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
