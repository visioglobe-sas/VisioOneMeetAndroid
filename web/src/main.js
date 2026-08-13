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
};

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
    bridge?.onMapReady();
  } catch (error) {
    bridge?.onMapError(String(error?.message ?? error));
  }
}

main();
