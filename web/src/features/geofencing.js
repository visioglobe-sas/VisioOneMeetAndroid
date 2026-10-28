// geofencing: tests the tracked position (from simulated-position) against a
// zone defined by a POI's surfaces, and recolors the zone while inside it.
// See docs/features/geofencing.md.
import { notify } from '../bridge.js';
import { venue } from '../context.js';
import { isPositionInsidePolygon } from '../shared/geometry.js';
import { findPoi } from '../shared/poi.js';

// The zone POI last resolved by `resolveZone`, cached so `checkGeofence` has
// something to test the tracked position against without re-resolving the
// POI on every tick, and so `clearZone` knows which surfaces to revert. Only
// one zone is tracked at a time — a demo-side simplification, not an SDK
// limitation.
let geofenceZone = null;

// Whether the last position checked by `checkGeofence` was inside
// `geofenceZone`, so a repeated tick at the same state doesn't re-issue the
// same `venue.updateSurface` call.
let isInsideGeofenceZone = false;

// Any clearly visible "alert" color works here — this isn't an SDK-mandated
// value, just a demo choice.
const GEOFENCE_ALERT_COLOR = '#E74C3C';

export const mapBridge = {
  // Resolves placeId to a POI and caches its surfaces as the zone geofencing
  // checks against, reporting one of 'found'/'not-found'/'no-surface' back to
  // native via AndroidBridge.onZoneResolved, requestId echoed back, same
  // convention as resolvePositions/getCurrentLocale. A POI with no surfaces
  // (e.g. a point/marker-only outdoor POI) has no polygon to test a position
  // against, so it's reported as its own distinct outcome rather than
  // silently behaving like "always outside".
  resolveZone(requestId, placeId) {
    if (!venue) return;
    const poi = findPoi(placeId);
    if (!poi) {
      geofenceZone = null;
      notify('onZoneResolved', requestId, 'not-found');
      return;
    }
    if (!poi.surfaces.length) {
      geofenceZone = null;
      notify('onZoneResolved', requestId, 'no-surface');
      return;
    }
    geofenceZone = { surfaces: poi.surfaces };
    isInsideGeofenceZone = false;
    notify('onZoneResolved', requestId, 'found');
  },
  // Reverts the alert color applied by checkGeofence below (if the tracked
  // position was ever inside the zone) and forgets the resolved zone. Called
  // when the simulation is stopped.
  clearZone() {
    if (!venue || !geofenceZone) return;
    geofenceZone.surfaces.forEach((surface) => {
      venue.updateSurface(surface, { color: 'initial' });
    });
    geofenceZone = null;
    isInsideGeofenceZone = false;
  },
  // Tests the tracked position injected by injectTrackedPosition against the
  // zone resolved by resolveZone above (isPositionInsidePolygon — there is no
  // SDK method for this) and, on a state transition, recolors the zone's
  // surface(s) as a visual alert via venue.updateSurface — the same
  // "recolor a surface" primitive already used by
  // clickable-surface/category-highlight, not a dedicated alert/marker SDK
  // concept. Reports the current state back to native on every call via
  // AndroidBridge.onGeofenceStateChanged, not just on a transition, so a late
  // subscriber (e.g. the sheet reopening) still gets a correct value. A no-op
  // if no zone is resolved (e.g. Start pressed with an unresolved/invalid
  // Zone POI ID).
  checkGeofence(latitude, longitude) {
    if (!venue || !geofenceZone) return;
    const isInside = geofenceZone.surfaces.some((surface) =>
      isPositionInsidePolygon({ latitude, longitude }, surface.positions),
    );
    if (isInside !== isInsideGeofenceZone) {
      isInsideGeofenceZone = isInside;
      geofenceZone.surfaces.forEach((surface) => {
        venue.updateSurface(surface, { color: isInside ? GEOFENCE_ALERT_COLOR : 'initial' });
      });
    }
    notify('onGeofenceStateChanged', isInside);
  },
};
