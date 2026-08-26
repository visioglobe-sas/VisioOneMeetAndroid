# Category Highlight

## Description

Highlights every POI belonging to a chosen category (e.g. all restaurants, all shops) in one action, built entirely from primitives — there is no dedicated "highlight by category" SDK method. It combines `venue.categories`/`poi.categories` (to find which POIs match) with `venue.updateSurface()` (to recolor them), the same surface-recoloring call used by `occupancy-simulated.md` and `clickable-surface.md`.

## SDK usage

```js
// web/src/main.js

// Sends the venue's full category list back to native. Category is
// `{ readonly id: string }` — a raw internal identifier (a numeric string
// on this app's shared demo map, e.g. "1".."11"), not itself human-readable.
// The display name comes from venue.translator.translateCategory(), same
// Translator lookup already used for POI/floor names elsewhere in this file.
getCategories(requestId) {
  if (!venue) return;
  const locale = venue.currentLocale;
  const categories = venue.categories.map((category) => ({
    id: category.id,
    label: venue.translator.translateCategory(category, locale).name || category.id,
  }));
  bridge?.onCategoriesLoaded(requestId, JSON.stringify(categories));
},

// Highlights every POI belonging to categoryId. Reverts any previously
// highlighted category first, so at most one category is highlighted at a
// time.
highlightCategory(categoryId) {
  if (!venue) return;
  this.clearCategoryHighlight();
  venue.pois
    .filter((poi) => poi.categories.some((category) => category.id === categoryId))
    .forEach((poi) => {
      poi.surfaces.forEach((surface) => {
        venue.updateSurface(surface, { color: CATEGORY_HIGHLIGHT_COLOR });
      });
    });
  highlightedCategoryId = categoryId;
},

// Reverts the highlight applied by highlightCategory, if any.
clearCategoryHighlight() {
  if (!venue || !highlightedCategoryId) return;
  venue.pois
    .filter((poi) => poi.categories.some((category) => category.id === highlightedCategoryId))
    .forEach((poi) => {
      poi.surfaces.forEach((surface) => {
        venue.updateSurface(surface, { color: 'initial' });
      });
    });
  highlightedCategoryId = null;
},
```

Native call sites:

```kotlin
// FeatureOverlays.kt
private fun WebView.getCategories(requestId: Int) {
    val script = "window.MapBridge.getCategories($requestId)"
    evaluateJavascript(script, null)
}

private fun WebView.highlightCategory(categoryId: String) {
    val script = "window.MapBridge.highlightCategory(${JSONObject.quote(categoryId)})"
    evaluateJavascript(script, null)
}

private fun WebView.clearCategoryHighlight() {
    evaluateJavascript("window.MapBridge.clearCategoryHighlight()", null)
}
```

`getCategories`'s result comes back asynchronously on the native side, echoing `requestId`, same convention as `custom-data.md`'s `loadCustomData`:

```kotlin
// FeatureMapScreen.kt
@JavascriptInterface
fun onCategoriesLoaded(requestId: Int, categoriesJson: String) =
    notifyCategoriesLoaded(requestId, categoriesJson)
```

`venue.categories: Category[]`, `poi.categories: Category[]` and `poi.surfaces: Surface[]` are the properties read; `venue.updateSurface(surface: Surface, options: SurfaceUpdateOptions): void` is the only mutating call, same method used by `clickable-surface.md`/`occupancy-simulated.md`.

## Things to know

- **No dedicated SDK method for this.** "Highlight by category" is a demo-side composition of `venue.pois.filter(poi => poi.categories.some(...))` and `venue.updateSurface()` in a loop — not a single call. An integrator wanting a different visual treatment (e.g. an icon change instead of a color) would build it the same way, from the same primitives.
- **A POI can belong to more than one category.** `poi.categories` is an array, so a single POI (e.g. a food court kiosk tagged both "Food and Beverage" and "Services") can be matched — and highlighted — by more than one category selection.
- **Not every POI has surfaces.** `poi.surfaces` is an empty array for point/marker-only POIs (e.g. outdoor markers with `type: -1`), so `updateSurface` simply has nothing to iterate for them — they don't visually highlight even though they may belong to the selected category. This is expected, not a bug; it means "highlight by category" only ever affects POIs that have an actual surface geometry (rooms, shops, zones), not marker-only points of interest.
- **`color: 'initial'` is the correct reset sentinel, not `undefined`.** Per `SurfaceUpdateOptions`'s own doc comment, `'initial'` tells the SDK to restore the surface's bundle-defined color. Omitting the `color` key (or passing `undefined`) is not equivalent — it simply leaves the color update out of that particular `updateSurface` call, which does not revert a color set by an earlier call. Always pass `'initial'` explicitly to clear a highlight, same gotcha documented in `clickable-surface.md`.
- **Only one category highlighted at a time is a demo-side choice, not an SDK limitation.** The SDK has no concept of a "current" highlighted category — this repo's `highlightCategory` tracks the previously highlighted category id itself and reverts its POIs' surfaces before applying the new selection, so calling it repeatedly with different ids never leaves more than one category's POIs highlighted simultaneously. An integrator wanting several categories highlighted at once (e.g. different colors per category) can simply skip that revert step.
- **`Category.id` is a raw internal identifier, not a display name.** Despite `Category`'s own doc comment suggesting `id` is "its name translated in default language", live testing against this app's shared demo map (`DEFAULT_MAP_HASH`) shows `id` is actually a plain numeric string (`"1"`, `"2"`, … `"11"`). The real, resolved display name comes from `venue.translator.translateCategory(category, venue.currentLocale).name` — the same `Translator` API already used for POI/floor names elsewhere in this file (`getVenueLayout`/`onPoiClick`). `id` remains what filtering/highlighting must use; `label` is for display only.

## Learn more

- `clickable-surface.md` and `occupancy-simulated.md` both use `venue.updateSurface()` for a single POI rather than a whole category — same underlying call, narrower scope.
- `custom-data.md` documents the same `requestId`-echoed native↔JS round-trip pattern used here for `getCategories`.
