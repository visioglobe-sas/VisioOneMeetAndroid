# Custom Data

## Description

Reads free-form business key/value data (price, opening hours, product reference, etc.) attached to a POI in VisioMapEditor, via `venue.getPOICustomData(poi)`. This cache is not populated automatically when the venue loads — `venue.refreshCustomData()` must be awaited at least once before any lookup can return real data.

## SDK usage

```js
// web/src/main.js
async loadCustomData(requestId, placeId) {
  if (!venue) return;
  try {
    await venue.refreshCustomData();
  } catch (error) {
    // See "Things to know" below — a venue with no CustomData published yet
    // rejects here instead of resolving empty; treated as "no data".
    console.warn('refreshCustomData failed, treating as no data available', error);
  }
  const poi = venue.pois.find((p) => p.id === placeId);
  const customData = poi ? venue.getPOICustomData(poi) : null;
  bridge?.onCustomDataLoaded(requestId, JSON.stringify(customData));
},
```

```kotlin
// FeatureOverlays.kt
private fun WebView.loadCustomData(requestId: Int, placeId: String) {
    val script = "window.MapBridge.loadCustomData($requestId, ${JSONObject.quote(placeId)})"
    evaluateJavascript(script, null)
}
```

The result comes back asynchronously on the native side via a `@JavascriptInterface` callback:

```kotlin
// FeatureMapScreen.kt
@JavascriptInterface
fun onCustomDataLoaded(requestId: Int, customDataJson: String) =
    notifyCustomDataLoaded(requestId, customDataJson)
```

`venue.refreshCustomData(): Promise<void>` and `venue.getPOICustomData(poi: POI): CustomData` are the two SDK calls exercised, where `CustomData` is `{ readonly [key: string]: string }`.

## Things to know

- **`refreshCustomData()` can reject, not just resolve empty.** Confirmed live against a venue with no CustomData published on the server yet: the request 404s and the returned promise rejects, it does not silently resolve to an empty cache. Since that is still a normal "no data yet" state for an integrator (not a real error), catch the rejection and proceed anyway — `getPOICustomData()` already degrades gracefully to `{}` against an empty/unrefreshed cache, so the subsequent POI lookup still produces the correct result. Skipping the try/catch leaves the awaiting call — and anything waiting on its result, like a bridge round trip back to native — hanging on an unhandled rejection.
- **Refresh before read.** The CustomData cache starts empty (`{}`) when a venue loads and is never populated automatically — `getPOICustomData()` silently returns `{}` for every POI until `refreshCustomData()` has been awaited at least once (successfully or not, per the point above). There is no lazy-load-on-first-read behavior; forgetting the refresh looks exactly like "this POI has no custom data."
- **Always `{}`, never null/undefined.** `getPOICustomData(poi)` never returns `null` or `undefined`, even for a POI that has no CustomData published at all — it returns an empty object. The only way to distinguish "resolved POI with no CustomData" from "id that isn't a POI at all" is to resolve the POI yourself first (e.g. `venue.pois.find(...)`) before calling `getPOICustomData`, as done above.
- **Synchronous read, asynchronous refresh.** `getPOICustomData` itself is synchronous and cheap to call repeatedly; only `refreshCustomData()` does network I/O and must be awaited. Calling `getPOICustomData` before any refresh (or before a slow refresh resolves) is valid — it just returns `{}`.
- **Values are always strings.** `CustomData` is a flat string-to-string map — no nested objects, numbers, or booleans. A price or a boolean flag arrives as its string representation (e.g. `"12.50"`), formatting/parsing is the integrator's responsibility.
- **Refreshing reloads for the whole venue, not per-POI.** `refreshCustomData()` has no parameters — it re-fetches CustomData for every POI in the venue in one call, not just the one being looked up.
