# Compute Navigation

## Description

Computes a route between two POIs via `venue.computeNavigation(request)`, draws it on the map via `venue.createNavigationTrace(navigation)` + `view.setCurrentNavigationTrace(trace)`, and removes it via `view.removeCurrentNavigationTrace()`.

## SDK usage

```js
// Shared by compute-navigation and its variants (accessible-mode, navigation-exclude-modalities,
// custom-navigation-trace): `request` carries origin, destination, isAccessible and, optionally,
// excludedAttributes.
function drawNavigation(request) {
  if (!venue || !view) return;
  try {
    clearNavigationTrace();
    const navigation = venue.computeNavigation({
      type: 'fastest',
      firstNodeAsIntersection: false,
      mergeFloorChangeInstructions: false,
      ...request,
    });
    currentNavigationTrace = venue.createNavigationTrace(navigation);
    view.setCurrentNavigationTrace(currentNavigationTrace);
    bridge?.onNavigationComputed();
  } catch (error) {
    bridge?.onNavigationError(String(error?.message ?? error));
  }
}

function clearNavigationTrace() {
  if (!view || !currentNavigationTrace) return;
  currentNavigationTrace = null;
  view.removeCurrentNavigationTrace(); // also unregisters the trace: no venue.removeNavigationTrace()
}

window.MapBridge = {
  computeNavigation(origin, destination, isAccessible) {
    drawNavigation({ origin, destination, isAccessible });
  },
  clearNavigation() {
    clearNavigationTrace();
  },
  // ...
};
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
- **Remove a displayed trace with `view.removeCurrentNavigationTrace()` only.** It already removes the trace from the venue: calling `venue.removeNavigationTrace(trace)` on it afterwards throws `Cannot remove Navigation as it is unregistered.` and leaves the view unable to show the next trace (in this demo, a second "Itinerary" press then did nothing). Calling `removeCurrentNavigationTrace()` with no trace displayed throws too (`Cannot remove Navigation as there is no Navigation currently running.`), hence the `currentNavigationTrace` guard. `view.setCurrentNavigationTrace(newTrace)` also replaces the displayed trace on its own; this demo still clears the previous one first so a failed computation leaves no stale route on the map. All verified live against SDK 1.1.0.
- `isAccessible` is a boolean argument — Kotlin's `Boolean.toString()` already produces a valid JS literal (`true`/`false`), so no `JSONObject.quote()` is needed for it, unlike the string arguments.
- `type: 'fastest'`, `firstNodeAsIntersection: false`, `mergeFloorChangeInstructions: false` are just the values used in this demo — `NavigationRequestType` supports other routing strategies.

## Learn more

- `Navigation.instructions` (an array of `NavigationInstruction`) already contains everything needed for a turn-by-turn instruction list (direction, distance, duration, floor) — not used by this demo, which only draws the trace on the map.
- `venue.computeNavigationMultiDestination` supports routes with intermediate stops.
