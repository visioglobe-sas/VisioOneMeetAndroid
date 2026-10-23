package com.visioglobe.visioonemeet.model

// Values of Visioglobe's shared demo map — replace them with your own map's hash and POI ids.
// (The localized default texts — label, badge, dynamic POI label — live in res/values*/strings.xml.)
object DemoMap {
    /** Hash of the VisioOne map displayed by every screen, as found on the my.visioglobe.com portal. */
    const val MAP_HASH = "kbae8e6c066cca4b02c2afac2bc963a643d87437a"

    /**
     * Map used only by `custom-data`, which needs a venue with real published CustomData —
     * [MAP_HASH] has none. See docs/features/custom-data.md.
     */
    const val CUSTOM_DATA_MAP_HASH = "kd9426d8cb3f1c532f22b5bcbd325c280bd351feb"

    /** `goto-poi`: place the camera flies to. */
    const val GOTO_POI_ID = "B3-UL00-ID0076"

    /** `occupancy-simulated`: place whose surface is recolored. */
    const val OCCUPANCY_PLACE_ID = "B3-UL00-ID0065"

    /** `clickable-surface`: place whose surface is recolored. */
    const val CLICKABLE_SURFACE_PLACE_ID = "B3-UL00-ID0064"

    /** `compute-navigation` and `custom-navigation-trace`: itinerary From -> To. */
    const val ITINERARY_FROM = "B4-UL00-ID0009"
    const val ITINERARY_TO = "B3-UL00-ID0064"

    /** `navigation-exclude-modalities`: a route that uses an elevator unless it is excluded. */
    const val EXCLUDE_MODALITIES_FROM = "B1-LL01-ID0013"
    const val EXCLUDE_MODALITIES_TO = "B1-UL02-ID0012"

    /** `accessible-mode`: a route that uses stairs unless `isAccessible` is set. */
    const val ACCESSIBLE_FROM = "B4-UL00-ID0010"
    const val ACCESSIBLE_TO = "B4-UL01-ID0014"

    /**
     * `simulated-position`, `camera-lock-on-position`, `geofencing`, `accuracy-circle-style`:
     * Origin -> Destination of the simulated walk.
     */
    const val TRACKING_FROM = "B4-UL00-ID0009"
    const val TRACKING_TO = "B4-UL00-ID0010"

    /** `geofencing`: POI whose surface is the zone (the walk's destination, so it is entered). */
    const val GEOFENCE_ZONE_ID = "B4-UL00-ID0010"

    /** `custom-data` (on [CUSTOM_DATA_MAP_HASH]): prefilled place id. */
    const val CUSTOM_DATA_PLACE_ID = "B3-UL00-ID0065"

    /** `custom-data` (on [CUSTOM_DATA_MAP_HASH]): quick-select chips, all carrying real CustomData. */
    val CUSTOM_DATA_SAMPLE_PLACE_IDS = listOf("B1", "B3-UL00-ID0065", "B3-UL00-ID0064")

    /** `dynamic-poi-crud`: id of the POI the demo creates (any id not already used by the venue). */
    const val DYNAMIC_POI_NEW_ID = "demo-poi"

    /**
     * Anchor POI ("Restaurant", B4 ground floor) used by `dynamic-poi-crud`, `3d-icon-marker`,
     * `custom-text-label` and `html-overlay-marker` — it has a label to copy a position from.
     */
    const val ANCHOR_POI_ID = "B4-UL00-ID0009"

    /** `camera-showcase-carousel`: the tour's stops, across 3 buildings, passed to the page on Start. */
    val CAROUSEL_STOP_IDS = listOf("B4-UL00-ID0009", "B1-LL01-ID0152", "B3-UL00-ID0064", "B4-UL03-ID0015")
}
