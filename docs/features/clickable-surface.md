# Clickable Surface

## Description

Makes a POI's surface(s) interactive via `venue.updateSurface(surface, options)`'s `isInteractive` flag — once set, the SDK itself swaps the surface's color on hover/tap using `hoverColor`/`selectionColor`, with no click listener needed on the app side for the coloring itself. This is the base building block behind any "availability" use case (a free/occupied room, a parking spot).

## SDK usage

```js
setSurfaceInteractive(placeId, interactive) {
  if (!venue) return;
  const poi = venue.pois.find((p) => p.id === placeId);
  if (!poi) return;
  poi.surfaces.forEach((surface) => {
    venue.updateSurface(
      surface,
      interactive
        ? { isInteractive: true, color: '#2ECC71', hoverColor: '#F1C40F', selectionColor: '#E74C3C' }
        : { isInteractive: false, color: 'initial' },
    );
  });
},
```

Native call site:

```kotlin
private fun WebView.setSurfaceInteractive(placeId: String, interactive: Boolean) {
    val script = "window.MapBridge.setSurfaceInteractive(${JSONObject.quote(placeId)}, $interactive)"
    evaluateJavascript(script, null)
}
```

## Things to know

- **The SDK owns the click/hover visual feedback** — once `isInteractive: true` is set, tapping the surface on the map swaps its color to `selectionColor` on its own; there is no `poiclick`-style event to listen for or any color-swapping code to write for this part. (`poiclick` still fires independently if the app wants to react to the tap for other reasons — see `poi-click.md`.)
- **`color: 'initial'` restores the map bundle's original color** — useful when disabling interactivity, so the surface doesn't stay stuck on whatever custom `color` was set while interactive.
- **A `Surface` belongs to a `POI`, not the other way around** — `poi.surfaces` is an array (a POI can have more than one surface); this call applies the same options to all of them.
- **`hoverColor`/`selectionColor` only take effect while `isInteractive` is `true`** — setting them alone, without `isInteractive: true`, has no visible effect.

## Learn more

- `View.surfaceHoverColor` / `View.surfaceSelectionColor` are venue-wide defaults used when an interactive surface doesn't specify its own `hoverColor`/`selectionColor`.
- `goto-poi.md` also sets a surface's `selectionColor`, but programmatically (via a native button, not a tap on the map) and without `isInteractive` — a one-off highlight rather than a persistent clickable state.
