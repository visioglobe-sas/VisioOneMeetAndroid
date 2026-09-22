# Exclude Navigation Modalities

## Description

Computes a route that avoids one or more segment "particularities" — elevators, stairs, escalators, etc. — via `venue.computeNavigation(request)`'s optional `excludedAttributes` option, on top of the same request/draw/remove flow as [Compute Navigation](compute-navigation.md) (`venue.createNavigationTrace()` + `view.setCurrentNavigationTrace()`).

## SDK usage

```js
const navigation = venue.computeNavigation({
  origin,
  destination,
  isAccessible,
  type: 'fastest',
  firstNodeAsIntersection: false,
  mergeFloorChangeInstructions: false,
  excludedAttributes: ['lift'],
});
```

Native call site and the JS side of the bridge:

```kotlin
private fun WebView.computeNavigationExcludingModalities(
    origin: String,
    destination: String,
    isAccessible: Boolean,
    excludeElevator: Boolean,
) {
    val script = "window.MapBridge.computeNavigationExcludingModalities(" +
        "${JSONObject.quote(origin)}, ${JSONObject.quote(destination)}, $isAccessible, $excludeElevator)"
    evaluateJavascript(script, null)
}
```

```js
// window.MapBridge, JS side
computeNavigationExcludingModalities(origin, destination, isAccessible, excludeElevator) {
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
      excludedAttributes: excludeElevator ? ['lift'] : [],
    });
    currentNavigationTrace = venue.createNavigationTrace(navigation);
    view.setCurrentNavigationTrace(currentNavigationTrace);
    bridge?.onNavigationComputed();
  } catch (error) {
    bridge?.onNavigationError(String(error?.message ?? error));
  }
},
```

## Things to know

- **The elevator segment attribute string is `'lift'`, not `'elevator'`.** The SDK's own JSDoc comment on `excludedAttributes` (`NavigationRequest.d.ts`) describes it in terms of "elevator", which is misleading — the actual `SegmentAttribute` value emitted for an elevator hop's instructions (and the value `excludedAttributes` must be given to exclude it) is `'lift'`. Confirmed live against the shared demo venue used by this repo: `venue.computeNavigation({ origin: 'B1-LL01-ID0013', destination: 'B1-UL02-ID0012' })` (no exclusion) returns a route with one elevator hop, whose instruction carries `attributes: ['lift', 'B1-lift-1']`; the same call with `excludedAttributes: ['lift']` reroutes through stairways instead — genuinely different, longer route, not a no-op.
- `excludedAttributes` is an array — passing more than one attribute string excludes every segment carrying any of them. An empty array (or omitting the option) behaves exactly like a plain `computeNavigation` call with no exclusion.
- **If no route survives the exclusion, this fails exactly like an ordinary unreachable origin/destination pair.** `venue.computeNavigation` throws the same `InvalidNavigationRequestError` it would for a bad/unreachable id (see [Compute Navigation](compute-navigation.md), "Things to know") — there is no separate error type or message distinguishing "no route without the excluded attribute(s)" from any other unreachable-pair failure.
- Every other `NavigationRequest` field (`origin`/`destination` accepting a `POI`, a `Position`, or a string id; `isAccessible`; `type`; `firstNodeAsIntersection`; `mergeFloorChangeInstructions`) behaves identically to [Compute Navigation](compute-navigation.md) — `excludedAttributes` is purely additive.

## Learn more

See [Compute Navigation](compute-navigation.md) for the base request/draw/remove flow this feature builds on, and [Custom Navigation Trace](custom-navigation-trace.md) for restyling the resulting route line.
