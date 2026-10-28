package com.visioglobe.visioonemeet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.visioglobe.visioonemeet.features.FeatureScreen
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.ui.FeatureMenuScreen
import com.visioglobe.visioonemeet.ui.theme.VisioOneMeetTheme

/** The feature menu, and one screen per [Feature] (see `features/FeatureScreens.kt`). */
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
                            ?: return@composable
                        FeatureScreen(feature, onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
