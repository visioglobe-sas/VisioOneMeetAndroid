# Camera Lock on Position

## Description

Locks/unlocks the camera onto a tracked position via `view.lockCameraPositionOnTracking` (boolean) — the same property a real indoor-positioning integration would use for a "recenter on me" control. This only has a visible effect once a position is actually being tracked (see `simulated-position.md`).

The SDK changes this flag on its own (tracking start/stop, user pan), so the switch mirrors the SDK state through the `trackingon` / `trackingoff` / `trackingchanged` view events instead of keeping its own value.

## SDK usage

JS side — the setter, plus the events that report the resulting state back to native:

```js
setCameraLockOnPosition(locked) {
  if (!view) return;
  view.lockCameraPositionOnTracking = locked;
},

// None of these events carries the lock value: read it from the view.
function onTrackingStateChanged() {
  if (!view) return;
  bridge?.onCameraLockChanged(view.lockCameraPositionOnTracking);
}

// Subscribed once, right after createView():
view.addEventListener('trackingon', onTrackingStateChanged);
view.addEventListener('trackingoff', onTrackingStateChanged);
view.addEventListener('trackingchanged', onTrackingStateChanged);
```

Native side:

```kotlin
private fun WebView.setCameraLockOnPosition(locked: Boolean) {
    evaluateJavascript("window.MapBridge.setCameraLockOnPosition($locked)", null)
}

// Exposed to JS as window.AndroidBridge (addJavascriptInterface)
@JavascriptInterface
fun onCameraLockChanged(locked: Boolean) = notifyCameraLockChanged(locked)
```

The switch's `checked` value is the last `locked` received from `onCameraLockChanged`; toggling it only calls `setCameraLockOnPosition(newValue)` and lets the event update the UI.

## Things to know

- **`view.allowTracking = true` turns the lock on by itself**: it always forces `lockCameraPositionOnTracking` (and `lockCameraOrientationOnTracking`) to `true` and dispatches `trackingon`. So right after tracking starts, the camera follows the position without any call to the setter. `view.allowTracking = false` forces both locks to `false` and dispatches `trackingoff`.
- **Don't set `allowTracking = true` on every position update.** Since it re-locks the camera each time, doing it before every `injectTrackedPosition` (e.g. every 150 ms) undoes a lock the user just turned off, or a pan, almost immediately. Set it only on the `false` -> `true` transition (`if (!view.allowTracking) view.allowTracking = true;`).
- **A user pan turns the lock off**: manipulating the map while the camera is locked sets `lockCameraPositionOnTracking` to `false` and dispatches `trackingchanged` — the consumer-GPS-app "touching the map unlocks" behavior is built in.
- Setting `lockCameraPositionOnTracking` dispatches `trackingchanged`, but the setter is a no-op (no event) when the value is unchanged or when `allowTracking` is `false`. It doesn't throw in that case — unlike `injectTrackedPosition`, it simply has no effect until tracking is active.
- `trackingon` / `trackingoff` / `trackingchanged` carry no lock value: read `view.lockCameraPositionOnTracking` in the handler. Listening to all three is what keeps a UI in sync with the SDK.
- `locked` is a boolean argument — no `JSONObject.quote()` needed, same as the numeric arguments used by `injectTrackedPosition`.
- `view.lockCameraOrientationOnTracking` is a separate flag that locks the camera's *orientation* to device-orientation data injected via `injectDeviceOrientation` — a third state ("locked position + orientation") beyond free/locked-position. That needs an additional orientation data source and isn't covered here.
