# POI Click

## Description

Reacts to a tap on a POI by listening for the SDK's `poiclick` event on the `view`, and resolves the POI's translated name via `venue.translator.translatePOI(poi, venue.currentLocale).name`. Unlike commands that go native→JS, this is JS→native: the map notifies the app of a user interaction, reusing the same bridge (`window.AndroidBridge`) used for `onMapReady`/`onMapError`.

## SDK usage

```js
function onPoiClick(event) {
  if (!venue) return;
  const locale = venue.currentLocale;
  const pois = event.pois.map((poi) => ({
    id: poi.id,
    name: venue.translator.translatePOI(poi, locale).name,
  }));
  bridge?.onPoiClick(JSON.stringify(pois));
}

view.addEventListener('poiclick', onPoiClick);
```
```kotlin
@JavascriptInterface
fun onPoiClick(payload: String) = notifyPoiClick(payload)
```

The native side only ever receives a JSON string, never a raw `POI` object — `POI` instances carry circular references and methods, which aren't serializable across a `@JavascriptInterface` boundary. The JS side always projects to a minimal `{ id, name }` shape first.

## Things to know

- `event.pois` is an array, not a single POI — the SDK's contract allows several POIs stacked under the same tap point, even though a single POI is by far the common case. Model any payload/type around a list from the start.
- Name resolution happens on the JS side: `venue.translator.translatePOI(poi, venue.currentLocale)` returns the name in the SDK's current locale, which is independent from whatever locale the native app uses for its own UI strings.
- `translatePOI(...).name` can come back empty if no translation is defined for that POI/locale — plan a fallback (e.g. falling back to `poi.id`) rather than assuming a non-empty string.
- Kotlin gotcha, not an SDK one: don't name a `@JavascriptInterface` method's constructor parameter the same as the method itself (e.g. both called `onPoiClick`) — the Kotlin compiler reports a recursive type-checking error rather than a clear shadowing error, which makes it a confusing one to debug.

## Learn more

- `view.addEventListener` also supports other SDK events (e.g. `selectedpoischange`, `currentfloorchanged`) worth checking if you need to relay more than just clicks to native.
- A natural next step is to trigger a native action from the click, e.g. centering the camera on the tapped POI via `view.goToPOI` (see `goto-poi.md`).
