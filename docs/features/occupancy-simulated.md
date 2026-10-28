# Occupancy Overlay (Simulated Data)

## Description

Colors a POI's surface(s) to reflect an occupancy status (free / busy soon / occupied) by calling `venue.updateSurface(surface, { color })` on each of the POI's surfaces. There's no real sensor behind it — a local timer cycles the color instead of a real IoT feed — but the SDK call itself is exactly what a real integration would use.

## SDK usage

```kotlin
// app/src/main/java/com/visioglobe/visioonemeet/features/OccupancySimulated.kt
private fun WebView.updateOccupancy(planId: String, color: String?) {
    val entry = JSONObject().apply {
        put("planId", planId)
        put("color", color ?: JSONObject.NULL)
    }
    callBridge("updateOccupancy", JSONArray().put(entry))
}
```
```js
// web/src/features/occupancy-simulated.js
export const mapBridge = {
  updateOccupancy(occupancy) {
    if (!venue) return;
    occupancy.forEach((entry) => {
      const poi = findPoi(entry.planId); // venue.pois.find((p) => p.id === entry.planId)
      if (!poi) return;
      poi.surfaces.forEach((surface) => {
        venue.updateSurface(surface, { color: entry.color });
      });
    });
  },
};
```

## Things to know

- An unknown `planId` fails silently — `findPoi(...)` (`venue.pois.find(...)`) returns `undefined` and the call becomes a no-op, with no error surfaced back to native.
- `org.json` (`JSONObject`/`JSONArray`), built into Android, is enough to encode arguments safely: `callBridge` (`bridge/CallBridge.kt`) JSON-encodes each argument before interpolating it into the JS call — a `JSONArray` as its JSON text, so the page receives a real array — never string-concatenate raw values.
- Encode a null color as `JSONObject.NULL`, not Kotlin `null` — passing a raw Kotlin `null` to `JSONObject.put` behaves differently (or throws, depending on the overload) than the `JSONObject.NULL` sentinel, which serializes correctly to a JSON `null`.
- `callBridge` must be called from the main thread (it runs `WebView.evaluateJavascript`).
- This demonstrates the update *mechanism*, not a real IoT integration — swap the local loop for a subscription to a real data source (websocket, polling) without touching the bridge or the `venue.updateSurface` call itself.
