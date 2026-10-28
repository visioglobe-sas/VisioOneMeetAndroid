// goto-poi: centers the camera on a POI and highlights its surfaces. See
// docs/features/goto-poi.md.
import { venue, view } from '../context.js';
import { findPoi } from '../shared/poi.js';

// The POI last centered on via `goToPlace`, so `clearPlace` knows which
// surfaces to un-highlight.
let selectedPoi = null;

// Centers the camera on `poi` and highlights its surfaces (also used by
// html-overlay-marker to bring the badge's floor into view).
export function goToPoi(poi) {
  selectedPoi = poi;
  view.goToPOI(poi, {
    orientation: { pitch: 20 },
    padding: { top: 100, right: 100, bottom: 100, left: 100 },
  });
  poi.surfaces.forEach((surface) => {
    venue.updateSurface(surface, { selectionColor: view.surfaceSelectionColor });
  });
}

export function clearSelectedPoi() {
  if (!venue || !selectedPoi) return;
  selectedPoi.surfaces.forEach((surface) => {
    venue.updateSurface(surface, { selectionColor: 'default' });
  });
  selectedPoi = null;
}

export const mapBridge = {
  // Centers/zooms the camera on the POI matching `placeId`, and highlights its
  // surfaces so the result is visible even once the animation settles. A
  // missing placeId (typo, wrong venue) is a silent no-op — mirrors the other
  // MapBridge commands (see updateOccupancy), no error channel back to
  // native for this today.
  goToPlace(placeId) {
    if (!venue || !view) return;
    const poi = findPoi(placeId);
    if (!poi) return;
    goToPoi(poi);
  },
  // Reverts the highlight applied by goToPlace. Does not move the camera back
  // — "Clear" only undoes the visual marker (see docs/features/goto-poi.md,
  // "Things to know").
  clearPlace() {
    clearSelectedPoi();
  },
};
