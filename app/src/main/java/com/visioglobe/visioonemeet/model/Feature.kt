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
    ;

    companion object {
        fun fromSlug(slug: String?): Feature? = entries.find { it.slug == slug }
    }
}
