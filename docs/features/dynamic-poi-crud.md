# Dynamic POI CRUD

## Description

Creates, updates and removes a POI at runtime — without republishing the map in VisioMapEditor. Demonstrates that map content can be driven dynamically by the app (e.g. kept in sync with a client's own CMS or business database) rather than always requiring a new map build/publish for every content change.

A bare `venue.createPOI()` has no visual representation of its own, so this demo also attaches a `Label` to the new POI (via `venue.createLabel()`) to give it something to actually see on the map, and positions that label by copying the WGS84 position of an existing "anchor" POI (there is no "tap the map to place a pin" UI here).

## SDK usage

```js
// web/src/main.js

// The POI/Label pair created at runtime, tracked so updateDynamicPoiLabel/
// removeDynamicPoi know what to act on. Only one dynamic POI at a time.
let dynamicPoi = null;
let dynamicLabel = null;

createDynamicPoi(requestId, newId, anchorId, labelText) {
  if (!venue) return;
  const report = (status, extra) => {
    bridge?.onDynamicPoiCreated(requestId, JSON.stringify({ status, ...extra }));
  };
  const anchor = venue.pois.find((p) => p.id === anchorId);
  if (!anchor) {
    report('anchor-not-found');
    return;
  }
  const anchorPosition = anchor.labels[0]?.position ?? anchor.markers[0]?.position ?? anchor.images[0]?.position;
  if (!anchorPosition) {
    report('anchor-has-no-position');
    return;
  }
  try {
    const poi = venue.createPOI({ id: newId });
    const label = venue.createLabel({ poi, position: anchorPosition, width: 2, text: labelText });
    dynamicPoi = poi;
    dynamicLabel = label;
    report('created', { id: poi.id, text: label.text });
  } catch (error) {
    if (error?.constructor?.name === 'POIAlreadyExistsError') {
      report('duplicate-id');
    } else {
      report('error', { message: String(error?.message ?? error) });
    }
  }
},

// The real "modify" story for a dynamic POI: venue.updatePOI itself can only
// touch categories, never anything visual.
updateDynamicPoiLabel(text) {
  if (!venue || !dynamicLabel) return;
  venue.updateLabel(dynamicLabel, { text });
},

// venue.removePOI cascades: it removes the attached label from the view too.
removeDynamicPoi() {
  if (!venue || !dynamicPoi) return;
  venue.removePOI(dynamicPoi);
  dynamicPoi = null;
  dynamicLabel = null;
},
```

Native call sites:

```kotlin
// FeatureOverlays.kt
private fun WebView.createDynamicPoi(requestId: Int, newId: String, anchorId: String, labelText: String) {
    val script = "window.MapBridge.createDynamicPoi(" +
        "$requestId, ${JSONObject.quote(newId)}, ${JSONObject.quote(anchorId)}, ${JSONObject.quote(labelText)})"
    evaluateJavascript(script, null)
}

private fun WebView.updateDynamicPoiLabel(text: String) {
    val script = "window.MapBridge.updateDynamicPoiLabel(${JSONObject.quote(text)})"
    evaluateJavascript(script, null)
}

private fun WebView.removeDynamicPoi() {
    evaluateJavascript("window.MapBridge.removeDynamicPoi()", null)
}
```

`createDynamicPoi`'s result comes back asynchronously on the native side, echoing `requestId`, same convention as `custom-data.md`'s `loadCustomData`/`category-highlight.md`'s `getCategories`:

```kotlin
// FeatureMapScreen.kt
@JavascriptInterface
fun onDynamicPoiCreated(requestId: Int, resultJson: String) =
    notifyDynamicPoiCreated(requestId, resultJson)
```

The SDK calls exercised:

- `venue.createPOI(options: { id: string; floor?: Floor; categories?: Category[] }): POI` — throws `POIAlreadyExistsError` if `id` is already used in the venue.
- `venue.createLabel(options: { poi: POI; position: Position; width: number; height?: number; text: string; color?: Color; rotation?: number }): Label` — `position` is WGS84 (`{ latitude, longitude, altitude? }`).
- `venue.updateLabel(label: Label, options: { position?, width?, height?, text?, color?, isVisible? }): void`.
- `venue.updatePOI(poi: POI, options: { categories: Category[] }): void` — not used by this demo's UI, since it has nothing visual to offer here, but listed since it's the other half of a POI's "update" story (see "Things to know").
- `venue.removePOI(poi: POI): void`.

## Things to know

- **A bare POI has no visual footprint.** `venue.createPOI()` returns a `POI` whose `images`/`labels`/`lines`/`surfaces`/`markers` are all empty arrays — it is purely a logical id/floor/categories container. Nothing appears on the map until at least one visual element (a `Label`, `Image`, `Line`, `Marker`, or `Surface`) is created and attached to it, as this demo does with `createLabel`.
- **`updatePOI` cannot touch anything visual.** Its `POIUpdateOptions` has exactly one field, `categories: Category[]` — passing `[]` clears every category the POI belongs to. There is no way to move a POI, rename it, or change its visual content through `updatePOI`; that's why this demo edits the attached *Label*'s text via `updateLabel` instead to demonstrate "modifying" a dynamic POI.
- **`removePOI` cascades to its visual elements.** Removing a POI automatically removes any Label/Image/Line/Marker/Surface attached to it from the view — there is no need to separately call `removeLabel` (or the equivalent for other element types) first.
- **`createPOI` throws on a duplicate id, it doesn't silently overwrite.** `POIAlreadyExistsError` is thrown synchronously when `id` is already used anywhere in the venue. It's a genuinely exceptional, not-attached-to-the-DOM error class — it isn't part of this package's public exports, so catching code can't use `instanceof POIAlreadyExistsError`; this demo instead checks `error.constructor.name === 'POIAlreadyExistsError'`, since the class's name survives bundling unminified. Either way, this is a normal, expected outcome to handle (e.g. a "pick another ID" message), not a crash.
- **POIs carry no direct lat/lng field.** Same quirk documented in `simulated-position.md`/`goto-poi.md`: a position has to be read off one of the POI's attached visual elements (`.labels[0]?.position`, `.markers[0]?.position`, `.images[0]?.position`) rather than the POI itself. This demo relies on exactly that to seed the new POI's label position from an existing "anchor" POI, and treats an anchor with none of the three as a normal "nothing to copy" state rather than crashing.

## Learn more

- `custom-data.md` and `category-highlight.md` document the same `requestId`-echoed native↔JS round-trip pattern used here for `createDynamicPoi`.
- `simulated-position.md` documents the same "no direct lat/lng field, read it off a label/marker/image" POI quirk, used here to resolve the anchor's position.
