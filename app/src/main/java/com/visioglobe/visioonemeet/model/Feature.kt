package com.visioglobe.visioonemeet.model

import androidx.annotation.StringRes
import com.visioglobe.visioonemeet.R

/** Single source of truth for the feature menu and the `feature/{slug}` nav route lookup. */
enum class Feature(
    val slug: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
) {
    ResetView(
        slug = "reset-view",
        titleRes = R.string.feature_reset_view_title,
        descriptionRes = R.string.feature_reset_view_description,
    ),
    OccupancySimulated(
        slug = "occupancy-simulated",
        titleRes = R.string.feature_occupancy_simulated_title,
        descriptionRes = R.string.feature_occupancy_simulated_description,
    ),
    PoiClick(
        slug = "poi-click",
        titleRes = R.string.feature_poi_click_title,
        descriptionRes = R.string.feature_poi_click_description,
    ),
    GoToPoi(
        slug = "goto-poi",
        titleRes = R.string.feature_goto_poi_title,
        descriptionRes = R.string.feature_goto_poi_description,
    ),
    FloorSelector(
        slug = "floor-selector",
        titleRes = R.string.feature_floor_selector_title,
        descriptionRes = R.string.feature_floor_selector_description,
    ),
    ComputeNavigation(
        slug = "compute-navigation",
        titleRes = R.string.feature_compute_navigation_title,
        descriptionRes = R.string.feature_compute_navigation_description,
    ),
    UiPartVisibility(
        slug = "ui-part-visibility",
        titleRes = R.string.feature_ui_part_visibility_title,
        descriptionRes = R.string.feature_ui_part_visibility_description,
    ),
    SimulatedPosition(
        slug = "simulated-position",
        titleRes = R.string.feature_simulated_position_title,
        descriptionRes = R.string.feature_simulated_position_description,
    ),
    CameraLockOnPosition(
        slug = "camera-lock-on-position",
        titleRes = R.string.feature_camera_lock_on_position_title,
        descriptionRes = R.string.feature_camera_lock_on_position_description,
    ),
    ClickableSurface(
        slug = "clickable-surface",
        titleRes = R.string.feature_clickable_surface_title,
        descriptionRes = R.string.feature_clickable_surface_description,
    ),
    CustomData(
        slug = "custom-data",
        titleRes = R.string.feature_custom_data_title,
        descriptionRes = R.string.feature_custom_data_description,
    ),
    CategoryHighlight(
        slug = "category-highlight",
        titleRes = R.string.feature_category_highlight_title,
        descriptionRes = R.string.feature_category_highlight_description,
    ),
    DynamicPoiCrud(
        slug = "dynamic-poi-crud",
        titleRes = R.string.feature_dynamic_poi_crud_title,
        descriptionRes = R.string.feature_dynamic_poi_crud_description,
    ),
    ;

    companion object {
        fun fromSlug(slug: String?): Feature? = entries.find { it.slug == slug }
    }
}
