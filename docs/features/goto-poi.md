# Go to POI

## Description

Centers and zooms the camera on a POI (looked up by id) via `view.goToPOI(poi, animationOptions)`, and highlights its surfaces via `venue.updateSurface(surface, { selectionColor: view.surfaceSelectionColor })` so the result stays visible once the camera animation ends. Clearing removes only the highlight, via `venue.updateSurface(surface, { selectionColor: 'default' })` — it does not move the camera back.

## SDK usage

```js
// web/src/features/goto-poi.js
export function goToPoi(poi) {
  selectedPoi = poi;
  view.goToPOI(poi, {
    orientation: { pitch: 20 },
    padding: { top: 100, right: 100, bottom: 100, left: 100 },
  });
  poi.surfaces.forEach((surface) => {
    venue.updateSurface(surface, { selectionColor: view.surfaceSelectionColor });
  });
}

export function clearSelectedPoi() {
  if (!venue || !selectedPoi) return;
  selectedPoi.surfaces.forEach((surface) => {
    venue.updateSurface(surface, { selectionColor: 'default' });
  });
  selectedPoi = null;
}

export const mapBridge = {
  goToPlace(placeId) {
    if (!venue || !view) return;
    const poi = findPoi(placeId); // venue.pois.find((p) => p.id === placeId)
    if (!poi) return;
    goToPoi(poi);
  },
  clearPlace() {
    clearSelectedPoi();
  },
};
```
```kotlin
// app/src/main/java/com/visioglobe/visioonemeet/features/GoToPoi.kt
internal fun WebView.goToPlace(placeId: String) = callBridge("goToPlace", placeId)

internal fun WebView.clearPlace() = callBridge("clearPlace")
```

## Things to know

- `goToPOI` takes a resolved `POI` object, not an id — the JS side must look it up first (`findPoi(id)` in `web/src/shared/poi.js`, i.e. `venue.pois.find((p) => p.id === id)`).
- `goToPOI` accepts a second `AnimationOptions` argument (`duration`, `easing`, `padding`, `orientation`); this demo only sets `padding` and `orientation.pitch`.
- An unknown id resolves to `undefined` from `.find(...)` and the call becomes a silent no-op — no error is surfaced.
- `selectionColor: 'default'` (not `undefined`/omitted) is the documented `SurfaceUpdateOptions.selectionColor: Color | 'default'` value that restores the view's global hover color (`view.surfaceSelectionColor`) rather than an arbitrary color.
- `callBridge` (`bridge/CallBridge.kt`) JSON-encodes each argument on its own: a `String` such as `placeId` goes through `JSONObject.quote`, producing `goToPlace("id")`. Don't wrap a single id in a list or `JSONArray` — that would produce `goToPlace(["id"])`, which breaks the JS signature since it expects a bare string, not an array.
