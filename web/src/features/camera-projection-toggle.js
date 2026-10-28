// camera-projection-toggle: creates the view with the projection the app asks
// for, and reports the projection the view really uses. See
// docs/features/camera-projection-toggle.md.
import { notify } from '../bridge.js';
import { view } from '../context.js';

// `?cameraProjection=orthographic` is appended by the Android app on this
// feature's screen only, and mapped here to createView's `cameraProjection`
// option. The projection is a creation-time option only —
// `view.cameraProjection` is read-only and there is no runtime switch — so the
// app switches projection by recreating the whole WebView with the other value
// (a fresh loadVenue + createView). Any other value (or none, every other
// screen) leaves the option out and keeps the SDK default, 'perspective'.
// This is deliberately not the SDK's own `?orthographic` page-URL flag, which
// would silently override the option.
const requestedCameraProjection = new URLSearchParams(window.location.search).get('cameraProjection');
const cameraProjection = requestedCameraProjection === 'orthographic' ? 'orthographic' : undefined;

// This feature's createView options (merged by main.js).
export function viewOptions() {
  return cameraProjection ? { cameraProjection } : {};
}

// Reports the projection the view was actually created with (read-only,
// resolved by the SDK as "page URL > createView option > default"), so the
// camera-projection control mirrors the SDK rather than what was requested.
export function onViewCreated() {
  notify('onCameraProjectionReported', view.cameraProjection);
}
