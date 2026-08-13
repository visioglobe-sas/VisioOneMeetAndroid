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
};

async function main() {
  try {
    const visioOne = createVisioOne();
    venue = await visioOne.loadVenue({ hash });
    view = await visioOne.createView(container, venue);
    bridge?.onMapReady();
  } catch (error) {
    bridge?.onMapError(String(error?.message ?? error));
  }
}

main();
