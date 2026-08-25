# Compute Navigation

## Description

Computes a route between two POIs via `venue.computeNavigation(request)`, draws it on the map via `venue.createNavigationTrace(navigation)` + `view.setCurrentNavigationTrace(trace)`, and removes it via `view.removeCurrentNavigationTrace()` + `venue.removeNavigationTrace(trace)`.

## SDK usage

```js
computeNavigation(origin, destination, isAccessible) {
  if (!venue || !view) return;
  this.clearNavigation();
  try {
    const navigation = venue.computeNavigation({
      origin,
      destination,
      isAccessible,
      type: 'fastest',
      firstNodeAsIntersection: false,
      mergeFloorChangeInstructions: false,
    });
    currentNavigationTrace = venue.createNavigationTrace(navigation);
    view.setCurrentNavigationTrace(currentNavigationTrace);
    bridge?.onNavigationComputed();
  } catch (error) {
    bridge?.onNavigationError(String(error?.message ?? error));
  }
},
clearNavigation() {
  if (!venue || !view || !currentNavigationTrace) return;
  view.removeCurrentNavigationTrace();
  venue.removeNavigationTrace(currentNavigationTrace);
  currentNavigationTrace = null;
},
```

Native call site and the JS→native error channel:

```kotlin
private fun WebView.computeNavigation(origin: String, destination: String, isAccessible: Boolean) {
    val script = "window.MapBridge.computeNavigation(" +
        "${JSONObject.quote(origin)}, ${JSONObject.quote(destination)}, $isAccessible)"
    evaluateJavascript(script, null)
}
```
```kotlin
@JavascriptInterface
fun onNavigationComputed() = notifyNavigationComputed()

@JavascriptInterface
fun onNavigationError(message: String) = notifyNavigationError(message)
```

## Things to know

- `origin`/`destination` accept a `POI`, a `Position`, or directly a string id (`NavigationRequest.origin`/`.destination` are typed `POIOrIDOrPosition`) — no need to resolve POIs from ids first, unlike `view.goToPOI`, which requires an already-resolved `POI`.
- `venue.computeNavigation` can throw (the SDK documents `InvalidNavigationRequestError`) on an invalid id or an unreachable pair. This is the one call in this repo whose failure isn't a silent no-op: a free-text place-ID field invites typos far more than a picker would, so the failure is routed back to native (`onNavigationError`) instead of leaving the map visually unchanged with no explanation.
- Remove the previous trace before computing a new one. `view.setCurrentNavigationTrace` only replaces what's *displayed* (one active trace at a time) — the previous `NavigationTrace` object stays allocated on the SDK side until you explicitly call `venue.removeNavigationTrace` on it.
- `isAccessible` is a boolean argument — Kotlin's `Boolean.toString()` already produces a valid JS literal (`true`/`false`), so no `JSONObject.quote()` is needed for it, unlike the string arguments.
- `type: 'fastest'`, `firstNodeAsIntersection: false`, `mergeFloorChangeInstructions: false` are just the values used in this demo — `NavigationRequestType` supports other routing strategies.

## Learn more

- `Navigation.instructions` (an array of `NavigationInstruction`) already contains everything needed for a turn-by-turn instruction list (direction, distance, duration, floor) — not used by this demo, which only draws the trace on the map.
- `venue.computeNavigationMultiDestination` supports routes with intermediate stops.
