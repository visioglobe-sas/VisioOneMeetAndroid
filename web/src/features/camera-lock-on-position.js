// camera-lock-on-position: binds/unbinds the camera to the tracked position
// (from simulated-position) and mirrors the SDK's lock state to native. See
// docs/features/camera-lock-on-position.md.
import { notify } from '../bridge.js';
import { view } from '../context.js';

// Forwards the SDK's camera-lock state to native as a bare boolean, so the
// camera-lock switch mirrors the SDK instead of keeping its own value. The
// lock changes on its own: allowTracking = true turns it on ('trackingon'),
// allowTracking = false turns it off ('trackingoff'), and a user pan while
// locked turns it off ('trackingchanged', also fired by setting
// lockCameraPositionOnTracking). None of these events carries the value, so
// it's read from the view.
function onTrackingStateChanged() {
  if (!view) return;
  notify('onCameraLockChanged', view.lockCameraPositionOnTracking);
}

export function onViewCreated() {
  view.addEventListener('trackingon', onTrackingStateChanged);
  view.addEventListener('trackingoff', onTrackingStateChanged);
  view.addEventListener('trackingchanged', onTrackingStateChanged);
}

export const mapBridge = {
  // Binds/unbinds the camera's focus to the tracked position, GPS-app
  // "recenter on me" style, via view.lockCameraPositionOnTracking. Has no
  // visible effect until a position has been injected (allowTracking must be
  // true — see simulated-position's injectTrackedPosition), same precondition
  // the SDK documents on the flag itself. The resulting state is reported
  // back via onCameraLockChanged (see onTrackingStateChanged above), not here.
  setCameraLockOnPosition(locked) {
    if (!view) return;
    view.lockCameraPositionOnTracking = locked;
  },
};
