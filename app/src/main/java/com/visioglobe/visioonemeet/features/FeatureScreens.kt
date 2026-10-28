package com.visioglobe.visioonemeet.features

import androidx.compose.runtime.Composable
import com.visioglobe.visioonemeet.model.Feature

/**
 * The screen of each [Feature] (the menu registry), one `XScreen()` per `features/<Feature>.kt`.
 * Exhaustive, so adding a [Feature] entry without its screen does not compile.
 */
@Composable
fun FeatureScreen(feature: Feature, onBack: () -> Unit) {
    when (feature) {
        Feature.ResetView -> ResetViewScreen(onBack)
        Feature.OccupancySimulated -> OccupancySimulatedScreen(onBack)
        Feature.PoiClick -> PoiClickScreen(onBack)
        Feature.GoToPoi -> GoToPoiScreen(onBack)
        Feature.FloorSelector -> FloorSelectorScreen(onBack)
        Feature.ComputeNavigation -> ComputeNavigationScreen(onBack)
        Feature.UiPartVisibility -> UiPartVisibilityScreen(onBack)
        Feature.NativeUiReplacement -> NativeUiReplacementScreen(onBack)
        Feature.SimulatedPosition -> SimulatedPositionScreen(onBack)
        Feature.CameraLockOnPosition -> CameraLockOnPositionScreen(onBack)
        Feature.ClickableSurface -> ClickableSurfaceScreen(onBack)
        Feature.CustomData -> CustomDataScreen(onBack)
        Feature.CategoryHighlight -> CategoryHighlightScreen(onBack)
        Feature.DynamicPoiCrud -> DynamicPoiCrudScreen(onBack)
        Feature.RuntimeLocale -> RuntimeLocaleScreen(onBack)
        Feature.ExploreMode -> ExploreModeScreen(onBack)
        Feature.AddLocale -> AddLocaleScreen(onBack)
        Feature.Geofencing -> GeofencingScreen(onBack)
        Feature.CustomBaseUrl -> CustomBaseUrlScreen(onBack)
        Feature.CustomNavigationTrace -> CustomNavigationTraceScreen(onBack)
        Feature.NavigationExcludeModalities -> NavigationExcludeModalitiesScreen(onBack)
        Feature.AccessibleMode -> AccessibleModeScreen(onBack)
        Feature.IconMarker3d -> IconMarker3dScreen(onBack)
        Feature.CustomTextLabel -> CustomTextLabelScreen(onBack)
        Feature.HtmlOverlayMarker -> HtmlOverlayMarkerScreen(onBack)
        Feature.CustomTheme -> CustomThemeScreen(onBack)
        Feature.AccuracyCircleStyle -> AccuracyCircleStyleScreen(onBack)
        Feature.CameraProjectionToggle -> CameraProjectionToggleScreen(onBack)
        Feature.CameraShowcaseCarousel -> CameraShowcaseCarouselScreen(onBack)
        Feature.AuthToken -> AuthTokenScreen(onBack)
    }
}
