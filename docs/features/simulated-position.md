# Simulated Position

## Description

Animates a simulated tracked position (a dot + accuracy circle) back and forth between two POIs via `view.injectTrackedPosition(positionTrackerOptions)` — the same API a real indoor-positioning integration (BLE/Wi-Fi/UWB) would call, just fed here by a local linear interpolation instead of real sensor data.

## SDK usage

```js
resolvePositions(requestId, originId, destinationId) {
  const resolve = (poiId) => {
    const poi = venue?.pois.find((p) => p.id === poiId);
    const position = poi?.markers?.[0]?.position ?? poi?.labels?.[0]?.position ?? poi?.images?.[0]?.position;
    return position ? { latitude: position.latitude, longitude: position.longitude } : null;
  };
  bridge?.onPositionsResolved(
    requestId,
    JSON.stringify(resolve(originId)),
    JSON.stringify(resolve(destinationId)),
  );
},
injectTrackedPosition(latitude, longitude, precisionCircleRadius) {
  if (!view) return;
  view.allowTracking = true;
  view.injectTrackedPosition({ position: { latitude, longitude }, precisionCircleRadius });
},
stopTrackedPosition() {
  if (!view) return;
  view.allowTracking = false;
},
```

Native call site:

```kotlin
private fun WebView.injectTrackedPosition(latitude: Double, longitude: Double, precisionCircleRadiusMeters: Double) {
    val script = "window.MapBridge.injectTrackedPosition($latitude, $longitude, $precisionCircleRadiusMeters)"
    evaluateJavascript(script, null)
}

private fun WebView.stopTrackedPosition() {
    evaluateJavascript("window.MapBridge.stopTrackedPosition()", null)
}
```

## Things to know

- `injectTrackedPosition` requires `view.allowTracking = true` beforehand — calling it while `allowTracking` is still `false` throws on the SDK side.
- There's no dedicated "stop" method: setting `view.allowTracking = false` is the documented way to make the tracked point and its accuracy circle disappear. There's no `view.removeTrackedPosition()` equivalent.
- A POI has no direct latitude/longitude field. Position comes from the first available marker/label/image on it: `poi.markers?.[0]?.position ?? poi.labels?.[0]?.position ?? poi.images?.[0]?.position`. All three carry a `Position` (`{ latitude, longitude, altitude? }`) in the same shape `injectTrackedPosition` expects, so no conversion is needed. A POI with none of the three, or an unknown id, resolves to `null`.
- `resolvePositions` is asynchronous: it returns nothing directly, and the result comes back later via `onPositionsResolved`. Echo a request id so the caller can match a response to the request that produced it, in case a second request could overlap with the first before it resolves.
- Numeric arguments (`latitude`, `longitude`, `precisionCircleRadiusMeters`) don't need `JSONObject.quote()` — `Double.toString()` is already a valid JS number literal.

## Learn more

- `view.updatePositionTrackerGraphicOptions({ color, opacity })` customizes the tracked point/circle's appearance.
- `injectTrackedPosition` accepts a second `AnimationOptions` argument, omitted here — without it, each new point is an instant jump rather than an animated transition.
- `view.lockCameraOrientationOnTracking` / `view.lockCameraPositionOnTracking` (booleans, only effective while `allowTracking = true`) can make the camera follow the tracked point — see `camera-lock-on-position.md`.
