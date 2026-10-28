# Simulated Position

## Description

Animates a simulated tracked position (a dot + accuracy circle) back and forth between two POIs via `view.injectTrackedPosition(positionTrackerOptions)` — the same API a real indoor-positioning integration (BLE/Wi-Fi/UWB) would call, just fed here by a local linear interpolation instead of real sensor data.

## SDK usage

```js
// web/src/shared/poi.js — POIs carry no direct lat/lng field. One helper gives a POI's display
// position: its first label's position, else its first image's or marker's, else the centroid
// of its first surface's outline. `excluded` skips our own objects.
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
```
```js
// web/src/features/simulated-position.js
import { findPoi, poiDisplayPosition } from '../shared/poi.js';

export const mapBridge = {
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
  injectTrackedPosition(latitude, longitude, precisionCircleRadius) {
    if (!view) return;
    const wasTracking = view.allowTracking;
    if (!wasTracking) view.allowTracking = true;
    // accuracy-circle-style: re-applies the chosen tracker style (see accuracy-circle-style.md).
    if (!wasTracking) reapplyPositionTrackerStyle();
    view.injectTrackedPosition({ position: { latitude, longitude }, precisionCircleRadius });
  },
  stopTrackedPosition() {
    if (!view) return;
    view.allowTracking = false;
  },
};
```

Native call sites:

```kotlin
// app/src/main/java/com/visioglobe/visioonemeet/features/SimulatedPosition.kt
private fun WebView.resolvePositions(requestId: Int, originId: String, destinationId: String) =
    callBridge("resolvePositions", requestId, originId, destinationId)

private fun WebView.injectTrackedPosition(latitude: Double, longitude: Double, precisionCircleRadiusMeters: Double) =
    callBridge("injectTrackedPosition", latitude, longitude, precisionCircleRadiusMeters)

private fun WebView.stopTrackedPosition() = callBridge("stopTrackedPosition")
```

`resolvePositions`' result comes back through the native callback, echoing `requestId`:

```kotlin
// app/src/main/java/com/visioglobe/visioonemeet/bridge/NativeBridge.kt — exposed as window.AndroidBridge
@JavascriptInterface
fun onPositionsResolved(requestId: Int, originJson: String, destinationJson: String) =
    emit(BridgeEvent.PositionsResolved(requestId, originJson, destinationJson))
```
```kotlin
// app/src/main/java/com/visioglobe/visioonemeet/features/SimulatedPosition.kt
OnBridgeEvent<BridgeEvent.PositionsResolved>(map) { session.onPositionsResolved(it) }
```

## Things to know

- `injectTrackedPosition` requires `view.allowTracking = true` beforehand — calling it while `allowTracking` is still `false` throws on the SDK side.
- Set `allowTracking = true` only once, when tracking starts — not before every `injectTrackedPosition`. Setting it to `true` always turns the camera lock (`lockCameraPositionOnTracking`) back on, so doing it on every position update re-locks the camera each time, undoing a lock the user turned off or a pan. See `camera-lock-on-position.md`.
- There's no dedicated "stop" method: setting `view.allowTracking = false` is the documented way to make the tracked point and its accuracy circle disappear. There's no `view.removeTrackedPosition()` equivalent.
- The SDK has no position feed of its own: the tracked point only moves while your code keeps calling `injectTrackedPosition`. Own that loop in something that lives as long as the map view (not in a transient control panel), and make sure exactly one runs at a time — two concurrent loops make the point jump between two paths, and since the next injection after a stop re-enables `allowTracking`, stopping one of them doesn't make tracking stop.
- A POI has no direct latitude/longitude field. This repo reads it with one helper used by every feature, `poiDisplayPosition` (above, in `web/src/shared/poi.js`), always in the same order: the POI's first label (`poi.labels[0].position`), else its first image's or marker's (`poi.images[0]` / `poi.markers[0]`), else the centroid of its first surface's outline (`poi.surfaces[0].positions`, averaged). All of these are `Position`s (`{ latitude, longitude, altitude? }`), the shape `injectTrackedPosition` expects, so no conversion is needed. A POI with none of them, or an unknown id, resolves to `null`. Using one order everywhere matters: a POI with both a label and a marker would otherwise give different points to different features.
- `resolvePositions` is asynchronous: it returns nothing directly, and the result comes back later via `onPositionsResolved`. Echo a request id so the caller can match a response to the request that produced it, in case a second request could overlap with the first before it resolves.
- `callBridge` JSON-encodes each argument: the numeric ones (`latitude`, `longitude`, `precisionCircleRadiusMeters`) are written as is — `Double.toString()` is already a valid JS number literal — while the POI ids are quoted with `JSONObject.quote`.

## Learn more

- `view.updatePositionTrackerGraphicOptions({ color, opacity })` customizes the tracked point/circle's appearance.
- `injectTrackedPosition` accepts a second `AnimationOptions` argument, omitted here — without it, each new point is an instant jump rather than an animated transition.
- `view.lockCameraOrientationOnTracking` / `view.lockCameraPositionOnTracking` (booleans, only effective while `allowTracking = true`, and turned on by `allowTracking = true` itself) make the camera follow the tracked point — see `camera-lock-on-position.md`.
