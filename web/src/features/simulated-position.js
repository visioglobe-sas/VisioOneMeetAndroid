// simulated-position: resolves two POIs to positions, then injects a tracked
// position the native side moves between them. Also used by
// camera-lock-on-position, geofencing and accuracy-circle-style. See
// docs/features/simulated-position.md.
import { notify } from '../bridge.js';
import { venue, view } from '../context.js';
import { findPoi, poiDisplayPosition } from '../shared/poi.js';
import { reapplyPositionTrackerStyle } from './accuracy-circle-style.js';

export const mapBridge = {
  // Resolves the WGS84 position of two POIs by id in one round trip, and
  // reports both back to native via AndroidBridge.onPositionsResolved.
  // POIs carry no direct lat/lng field — see poiDisplayPosition. A
  // missing id (or a POI with no position at all) resolves to `null` for
  // that slot rather than throwing, so native can tell "not
  // found" apart from a real position. `requestId` is echoed back as-is so
  // native can match this response to the Start press that triggered it.
  resolvePositions(requestId, originId, destinationId) {
    const resolve = (poiId) => {
      const poi = venue && findPoi(poiId);
      const position = poi && poiDisplayPosition(poi);
      return position ? { latitude: position.latitude, longitude: position.longitude } : null;
    };
    notify(
      'onPositionsResolved',
      requestId,
      JSON.stringify(resolve(originId)),
      JSON.stringify(resolve(destinationId)),
    );
  },
  // Injects/updates the simulated tracked position and its accuracy circle
  // (precisionCircleRadius, in meters) via view.injectTrackedPosition.
  // allowTracking must be true first — the SDK throws otherwise — but it's
  // only set on the false -> true transition: setting it to true always
  // forces lockCameraPositionOnTracking back to true, so re-setting it on
  // every ~150 ms tick would re-lock the camera each time (undoing a lock
  // turned off by the user or by a pan).
  injectTrackedPosition(latitude, longitude, precisionCircleRadius) {
    if (!view) return;
    const wasTracking = view.allowTracking;
    if (!wasTracking) view.allowTracking = true;
    // accuracy-circle-style: the SDK resets the tracker's color/opacity to
    // its defaults every time tracking stops, so the chosen style is
    // re-applied on each false -> true transition, before the first inject
    // (graphic options are only read at inject time).
    if (!wasTracking) reapplyPositionTrackerStyle();
    view.injectTrackedPosition({ position: { latitude, longitude }, precisionCircleRadius });
  },
  // Removes the marker/circle injected above. There is no dedicated stop
  // method on the SDK — setting allowTracking back to false is how it's
  // cleared.
  stopTrackedPosition() {
    if (!view) return;
    view.allowTracking = false;
  },
};
