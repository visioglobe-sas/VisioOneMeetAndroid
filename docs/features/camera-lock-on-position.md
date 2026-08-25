# Camera Lock on Position

## Description

Locks/unlocks the camera onto a tracked position via `view.lockCameraPositionOnTracking` (boolean) — the same property a real indoor-positioning integration would use for a "recenter on me" control. This only has a visible effect once a position is actually being tracked (see `simulated-position.md`).

## SDK usage

```js
setCameraLockOnPosition(locked) {
  if (!view) return;
  view.lockCameraPositionOnTracking = locked;
},
```
```kotlin
private fun WebView.setCameraLockOnPosition(locked: Boolean) {
    evaluateJavascript("window.MapBridge.setCameraLockOnPosition($locked)", null)
}
```

## Things to know

- Setting `lockCameraPositionOnTracking` while `allowTracking` is `false` doesn't throw — unlike `injectTrackedPosition`, the property simply has no visible effect until tracking is active.
- `locked` is a boolean argument — no `JSONObject.quote()` needed, same as the numeric arguments used by `injectTrackedPosition`.
- The SDK also exposes `view.lockCameraOrientationOnTracking`, a separate flag that locks the camera's *orientation* to device-orientation data injected via `injectDeviceOrientation` — a third state ("locked position + orientation") beyond free/locked-position. That needs an additional orientation data source and isn't covered here.
- The SDK doesn't document an event for the user manually panning/zooming while the camera is locked — check case by case if you need "touching the map implicitly unlocks tracking" behavior, as in consumer GPS apps.
