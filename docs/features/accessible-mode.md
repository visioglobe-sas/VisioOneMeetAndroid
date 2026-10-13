# Accessible Mode

## Description

Computes a route that avoids the venue's own published accessible-route exclusions — typically stairs — via `venue.computeNavigation(request)`'s `isAccessible` option, on top of the same request/draw/remove flow as [Compute Navigation](compute-navigation.md) (`venue.createNavigationTrace()` + `view.setCurrentNavigationTrace()`). This is the opposite direction from [Exclude Navigation Modalities](navigation-exclude-modalities.md): `isAccessible: true` avoids stairs to force an elevator/ramp route, whereas that feature's `excludedAttributes: ['lift']` avoids the elevator to force a stairway detour.

## SDK usage

```js
const navigation = venue.computeNavigation({
  origin,
  destination,
  isAccessible: true,
  type: 'fastest',
  firstNodeAsIntersection: false,
  mergeFloorChangeInstructions: false,
});
```

Native call site — no new bridge method needed, `computeNavigation` already threads `isAccessible` end-to-end (also used, hardcoded `false`, by [Compute Navigation](compute-navigation.md) and [Custom Navigation Trace](custom-navigation-trace.md)):

```kotlin
private fun WebView.computeNavigation(origin: String, destination: String, isAccessible: Boolean) {
    val script = "window.MapBridge.computeNavigation(" +
        "${JSONObject.quote(origin)}, ${JSONObject.quote(destination)}, $isAccessible)"
    evaluateJavascript(script, null)
}
```

```js
// window.MapBridge, JS side
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
```

## Things to know

- **`isAccessible: true` excludes the venue's own published `accessibleRouteAttributes`/`accessibleRouteModalities` list from routing.** This list is venue-defined (configured when the venue is built in VisioMapEditor), not a fixed SDK constant — there is no public SDK getter to inspect it in advance, so which segments get excluded (stairs, in practice, on the venues tested) is only discoverable empirically by comparing routes with and without the option. Confirmed live against this repo's shared demo venue: `venue.computeNavigation({ origin: 'B4-UL00-ID0010', destination: 'B4-UL01-ID0014' })` (no `isAccessible`) returns a route using a segment tagged `attributes: ['stairway', 'B4-stairs2']`; the same call with `isAccessible: true` reroutes entirely through a different segment tagged `attributes: ['lift', 'B4-lift1']` — a genuinely different route, not a no-op.
- **If no accessible route exists, this fails exactly like an ordinary unreachable origin/destination pair.** `venue.computeNavigation` throws the same `InvalidNavigationRequestError` it would for a bad/unreachable id (see [Compute Navigation](compute-navigation.md), "Things to know") — there is no separate error type or message distinguishing "no route avoiding the venue's accessible-route exclusions" from any other unreachable-pair failure.
- `isAccessible` defaults to `false` when omitted (per the SDK's own `NavigationRequest.d.ts` JSDoc) — every other call site in this repo passes it explicitly as `false`.
- Every other `NavigationRequest` field (`origin`/`destination` accepting a `POI`, a `Position`, or a string id; `type`; `firstNodeAsIntersection`; `mergeFloorChangeInstructions`) behaves identically to [Compute Navigation](compute-navigation.md) — `isAccessible` is a plain boolean flag on the same request shape.

## Learn more

See [Compute Navigation](compute-navigation.md) for the base request/draw/remove flow this feature builds on, and [Exclude Navigation Modalities](navigation-exclude-modalities.md) for the opposite-direction feature (excluding a specific modality like the elevator, rather than routing around the venue's own accessible-route exclusions).
