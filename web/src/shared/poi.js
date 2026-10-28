import { venue } from '../context.js';

// The POI with this id, or undefined (unknown id, wrong venue). Callers check `venue` first.
export function findPoi(poiId) {
  return venue.pois.find((p) => p.id === poiId);
}

// POIs carry no direct lat/lng field: a position has to be read off one of
// the POI's attached visual elements instead. This is the single helper every
// feature uses to get a POI's display position (simulated-position's walk,
// geofencing, and the anchors of dynamic-poi-crud / 3d-icon-marker /
// custom-text-label / html-overlay-marker), always in the same order:
//   1. its first label's `position`,
//   2. else its first image's, then its first marker's `position`,
//   3. else the centroid of its first surface's `positions` (the outline's
//      vertices, averaged — accurate enough for a room-sized polygon).
// Returns undefined when the POI has none of them. `excluded` (optional) lists
// labels/images/markers to skip: a caller whose own object is attached to the
// anchor POI (custom-text-label's label, html-overlay-marker's marker) never
// copies its own position instead of the map's. See
// docs/features/simulated-position.md.
export function poiDisplayPosition(poi, excluded = []) {
  const first = (items) => items?.find((item) => !excluded.includes(item))?.position;
  const position = first(poi.labels) ?? first(poi.images) ?? first(poi.markers);
  if (position) return position;
  const vertices = poi.surfaces?.[0]?.positions;
  if (!vertices?.length) return undefined;
  return {
    latitude: vertices.reduce((sum, p) => sum + p.latitude, 0) / vertices.length,
    longitude: vertices.reduce((sum, p) => sum + p.longitude, 0) / vertices.length,
  };
}

// Offset applied to the anchor POI's copied position (~4 m at the demo
// venue's latitude), so the custom text label (custom-text-label) and the
// HTML badge (html-overlay-marker) don't sit on top of the map's own label
// for that POI. See docs/features/custom-text-label.md.
const ANCHOR_OFFSET = 0.00004;

export function offsetFromAnchor(anchorPosition) {
  return {
    ...anchorPosition,
    latitude: anchorPosition.latitude + ANCHOR_OFFSET,
    longitude: anchorPosition.longitude + ANCHOR_OFFSET,
  };
}
