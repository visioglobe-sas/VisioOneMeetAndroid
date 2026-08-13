import { createVisioOne } from '@visioglobe/visioone';

// The Android app appends ?hash=<mapHash> when it loads this page (see
// VisioOneWebView.kt), so the same bundle can display any map without a rebuild.
const DEFAULT_HASH = 'kbae8e6c066cca4b02c2afac2bc963a643d87437a';
const hash = new URLSearchParams(window.location.search).get('hash') || DEFAULT_HASH;

const container = document.querySelector('#content');

// Optional bridge injected by MainActivity via WebView.addJavascriptInterface,
// used to reflect the map's loading state in the native Compose UI.
const bridge = window.AndroidBridge;

let venue = null;
let view = null;

// The POI last centered on via `goToPlace`, so `clearPlace` knows which
// surfaces to un-highlight. See docs/features/goto-poi.md.
let selectedPoi = null;

// Native -> JS bridge: one method per command, called from Kotlin via
// WebView.evaluateJavascript(). Kotlin JSON-encodes arguments before
// interpolating them into the generated script call, so what arrives here
// is already a real JS value (array/object), never a string to re-parse.
window.MapBridge = {
  goToGlobal() {
    if (view) view.goToGlobal();
  },
  updateOccupancy(occupancy) {
    if (!venue) return;
    occupancy.forEach((entry) => {
      const poi = venue.pois.find((p) => p.id === entry.planId);
      if (!poi) return;
      poi.surfaces.forEach((surface) => {
        venue.updateSurface(surface, { color: entry.color });
      });
    });
  },
  // Centers/zooms the camera on the POI matching `placeId`, and highlights its
  // surfaces so the result is visible even once the animation settles. A
  // missing placeId (typo, wrong venue) is a silent no-op — mirrors the other
  // MapBridge commands (see updateOccupancy above), no error channel back to
  // native for this today.
  goToPlace(placeId) {
    if (!venue || !view) return;
    const poi = venue.pois.find((p) => p.id === placeId);
    if (!poi) return;

    selectedPoi = poi;
    view.goToPOI(poi, {
      orientation: { pitch: 20 },
      padding: { top: 100, right: 100, bottom: 100, left: 100 },
    });
    poi.surfaces.forEach((surface) => {
      venue.updateSurface(surface, { selectionColor: view.surfaceSelectionColor });
    });
  },
  // Reverts the highlight applied by goToPlace. Does not move the camera back
  // — "Clear" only undoes the visual marker, matching the RN sibling's
  // clearPlace (see docs/features/goto-poi.md, "Points d'attention").
  clearPlace() {
    if (!venue || !selectedPoi) return;
    selectedPoi.surfaces.forEach((surface) => {
      venue.updateSurface(surface, { selectionColor: 'default' });
    });
    selectedPoi = null;
  },
  // Moves the camera to the floor matching buildingId/floorId by calling
  // view.goToFloor(floor) — both ids are resolved from venue.venueLayout so a
  // stale/unknown id (e.g. building removed between two loads) is a silent
  // no-op, same convention as the other MapBridge commands. See
  // docs/features/floor-selector.md.
  goToFloor(buildingId, floorId) {
    if (!venue || !view) return;
    const building = venue.venueLayout.buildings.find((b) => b.id === buildingId);
    if (!building) return;
    const floor = building.floors.find((f) => f.id === floorId);
    if (!floor) return;
    view.goToFloor(floor);
  },
};

// Builds the payload sent to native for the floor-selector UI: the
// building's id (needed by goToFloor above) plus one entry per floor with a
// human-readable name — resolved via the venue's Translator for the venue's
// currentLocale, same convention as onPoiClick — and its levelIndex, so the
// native side can order the list top-down without SDK-specific knowledge.
// currentFloorId seeds the initial highlight and is kept up to date afterwards
// by onCurrentFloorChanged below.
function buildingFloorsPayload(building) {
  const locale = venue.currentLocale;
  return {
    buildingId: building.id,
    currentFloorId: view.currentFloor?.id ?? null,
    floors: building.floors.map((floor) => ({
      id: floor.id,
      name: venue.translator.translateFloor(floor, locale).name,
      levelIndex: floor.levelIndex,
    })),
  };
}

// Forwards the SDK's 'currentfloorchanged' event to native as a bare floor id
// (or null, e.g. camera moved back outside), so the floor-selector list can
// keep its highlighted entry in sync even when the floor changes another way
// — the SDK's own default floor-selector widget, or a goToPOI on a POI of a
// different floor. Floor ids are unique across the whole venue (see the
// SDK's Floor.d.ts), so the native side can match this against its own
// building-scoped floor list with no ambiguity. See
// docs/features/floor-selector.md, "Points d'attention".
function onCurrentFloorChanged(event) {
  bridge?.onFloorChanged(event.newFloor?.id ?? null);
}

// Web -> Native bridge: forwards the SDK's 'poiclick' event to the native side,
// so a screen can react to the user tapping a POI on the map (see
// docs/features/poi-click.md). The payload is a JSON-encoded array (usually a
// single entry) of { id, name }, name being resolved via the venue's
// Translator for the venue's currentLocale so the native side never has to
// deal with raw/untranslated POI data.
function onPoiClick(event) {
  if (!venue) return;
  const locale = venue.currentLocale;
  const pois = event.pois.map((poi) => ({
    id: poi.id,
    name: venue.translator.translatePOI(poi, locale).name,
  }));
  bridge?.onPoiClick(JSON.stringify(pois));
}

async function main() {
  try {
    const visioOne = createVisioOne();
    venue = await visioOne.loadVenue({ hash });
    view = await visioOne.createView(container, venue);
    view.addEventListener('poiclick', onPoiClick);
    view.addEventListener('currentfloorchanged', onCurrentFloorChanged);
    // Only the first building's floors are exposed today — see
    // docs/features/floor-selector.md, "Points d'attention" for why a
    // building switcher is out of scope for this demo.
    const building = venue.venueLayout.buildings[0];
    if (building) {
      bridge?.onFloorsReady(JSON.stringify(buildingFloorsPayload(building)));
    }
    bridge?.onMapReady();
  } catch (error) {
    bridge?.onMapError(String(error?.message ?? error));
  }
}

main();
