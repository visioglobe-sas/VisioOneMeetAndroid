# Go to POI

## Description

Centers and zooms the camera on a POI (looked up by id) via `view.goToPOI(poi, animationOptions)`, and highlights its surfaces via `venue.updateSurface(surface, { selectionColor: view.surfaceSelectionColor })` so the result stays visible once the camera animation ends. Clearing removes only the highlight, via `venue.updateSurface(surface, { selectionColor: 'default' })` — it does not move the camera back.

## SDK usage

```js
goToPlace(placeId) {
  if (!venue || !view) return;
  const poi = venue.pois.find((p) => p.id === placeId);
  if (!poi) return;

  selectedPoi = poi;
  view.goToPOI(poi, {
    orientation: { pitch: 20 },
    padding: { top: 100, right: 100, bottom: 100, left: 100 },
  });
  poi.surfaces.forEach((surface) => {
    venue.updateSurface(surface, { selectionColor: view.surfaceSelectionColor });
  });
},
clearPlace() {
  if (!venue || !selectedPoi) return;
  selectedPoi.surfaces.forEach((surface) => {
    venue.updateSurface(surface, { selectionColor: 'default' });
  });
  selectedPoi = null;
},
```
```kotlin
private fun WebView.goToPlace(placeId: String) {
    val script = "window.MapBridge.goToPlace(${JSONObject.quote(placeId)})"
    evaluateJavascript(script, null)
}

private fun WebView.clearPlace() {
    evaluateJavascript("window.MapBridge.clearPlace()", null)
}
```

## Things to know

- `goToPOI` takes a resolved `POI` object, not an id — the JS side must look it up via `venue.pois.find(...)` before calling it.
- `goToPOI` accepts a second `AnimationOptions` argument (`duration`, `easing`, `padding`, `orientation`); this demo only sets `padding` and `orientation.pitch`.
- An unknown id resolves to `undefined` from `.find(...)` and the call becomes a silent no-op — no error is surfaced.
- `selectionColor: 'default'` (not `undefined`/omitted) is the documented `SurfaceUpdateOptions.selectionColor: Color | 'default'` value that restores the view's global hover color (`view.surfaceSelectionColor`) rather than an arbitrary color.
- Encoding a single scalar string argument uses `JSONObject.quote(placeId)`, not `JSONArray` — wrapping it in a `JSONArray` would produce `goToPlace(["id"])`, which breaks the JS signature since it expects a bare string, not an array.
