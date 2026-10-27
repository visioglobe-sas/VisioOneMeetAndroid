# Accuracy Circle Style

## Description

Recolors the tracked position's precision (accuracy) circle via `view.updatePositionTrackerGraphicOptions({ color, opacity })`, while a simulated position is moving (see `simulated-position.md`). The circle's radius is not a style option: it is the `precisionCircleRadius` (meters) passed on every `view.injectTrackedPosition(...)` call.

## SDK usage

```js
// Style kept app-side: the SDK has no getter for it and resets it whenever tracking stops.
let positionTrackerStyle = null;

// Returns whether the SDK accepted the style (it throws on an invalid color).
setPositionTrackerStyle(color, opacity) {
  if (!view) return false;
  try {
    view.updatePositionTrackerGraphicOptions({ color, opacity });
    positionTrackerStyle = { color, opacity };
    return true;
  } catch (error) {
    console.error('updatePositionTrackerGraphicOptions failed', error);
    return false;
  }
},

injectTrackedPosition(latitude, longitude, precisionCircleRadius) {
  if (!view) return;
  const wasTracking = view.allowTracking;
  if (!wasTracking) view.allowTracking = true;
  // Re-apply the chosen style on every (re)start, before the first inject.
  if (!wasTracking && positionTrackerStyle) {
    view.updatePositionTrackerGraphicOptions(positionTrackerStyle);
  }
  view.injectTrackedPosition({ position: { latitude, longitude }, precisionCircleRadius });
},
```
```kotlin
// onApplied only runs if the SDK accepted the style: evaluateJavascript's callback receives the
// JS return value, JSON-encoded ("true"/"false").
private fun WebView.setPositionTrackerStyle(color: String, opacity: Float, onApplied: () -> Unit) {
    val roundedOpacity = (opacity * 10).roundToInt() / 10.0
    evaluateJavascript(
        "window.MapBridge.setPositionTrackerStyle(${JSONObject.quote(color)}, $roundedOpacity)",
    ) { result -> if (result == "true") onApplied() }
}

private fun WebView.injectTrackedPosition(latitude: Double, longitude: Double, precisionCircleRadiusMeters: Double) {
    val script = "window.MapBridge.injectTrackedPosition($latitude, $longitude, $precisionCircleRadiusMeters)"
    evaluateJavascript(script, null)
}
```

Signature: `updatePositionTrackerGraphicOptions(options: { color?: Color; opacity?: number }): void`.

## Things to know

- `color` and `opacity` recolor **only the precision circle** (drawn via the SDK's internal blue-dot primitive). The arrow icon at its center is a separate image that is never recolored, despite the SDK typings mentioning "dot and precision circle". With `precisionCircleRadius` at `0` (or omitted) there is no circle, so a style change has no visible effect.
- The call only **stores** the options: they are drawn at the **next** `injectTrackedPosition` call. With positions injected every ~150 ms, it looks live; with a real positioning source, expect the change to show up at the next position update. Calling it while not tracking doesn't throw — the options are used once tracking starts.
- The SDK **resets** color and opacity to its defaults (`#0094f0`, `0.3`) every time tracking stops (`view.allowTracking = false`). Re-apply your style after each restart — right after `allowTracking = true`, before or with the first `injectTrackedPosition`.
- There is no getter for the current graphic options: keep track of them on your side.
- The radius is `PositionTrackerOptions.precisionCircleRadius`, in meters, passed on each inject; omitted or `0` means no circle at all.
- An invalid `color` throws (accepted formats: hex, `rgba()`, `hsl()`). A non-number `opacity` throws `"Blue dot's opacity must be a number."`. The validation is a falsy check, so `opacity: 0` skips it entirely — use a small positive minimum (e.g. `0.1`) rather than `0` for "almost invisible".
- Setting `view.allowTracking = true` also forces `lockCameraPositionOnTracking` back to `true` (observed in the SDK 1.1.0 source), so a camera lock you turned off is turned back on at every restart.

## Learn more

- `simulated-position.md` — the `injectTrackedPosition` loop this feature builds on.
- `camera-lock-on-position.md` — locking the camera onto the same tracked position.
