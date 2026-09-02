# Geofencing

## Description

Triggers a visual alert when a simulated tracked position enters a zone defined by an existing POI's surface. A "zone" here is just a `Surface` — the same object already used by `clickable-surface`/`category-highlight` — resolved from a POI id, not a dedicated geofence concept in the SDK. Builds directly on the tracked-position simulation from `simulated-position`.

## SDK usage

Resolving a zone POI's polygon:

```js
resolveZone(requestId, placeId) {
  if (!venue) return;
  const poi = venue.pois.find((p) => p.id === placeId);
  if (!poi) {
    geofenceZone = null;
    bridge?.onZoneResolved(requestId, 'not-found');
    return;
  }
  if (!poi.surfaces.length) {
    geofenceZone = null;
    bridge?.onZoneResolved(requestId, 'no-surface');
    return;
  }
  geofenceZone = { surfaces: poi.surfaces };
  isInsideGeofenceZone = false;
  bridge?.onZoneResolved(requestId, 'found');
},
```

Testing a position on every tick of the tracked-position simulation loop (see `simulated-position.md`), and recoloring the zone's surface(s) on a state transition:

```js
function isPositionInsidePolygon(position, polygonPositions) {
  const { latitude: y, longitude: x } = position;
  let inside = false;
  for (let i = 0, j = polygonPositions.length - 1; i < polygonPositions.length; j = i++) {
    const xi = polygonPositions[i].longitude;
    const yi = polygonPositions[i].latitude;
    const xj = polygonPositions[j].longitude;
    const yj = polygonPositions[j].latitude;
    const intersects = yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi;
    if (intersects) inside = !inside;
  }
  return inside;
}

checkGeofence(latitude, longitude) {
  if (!venue || !geofenceZone) return;
  const isInside = geofenceZone.surfaces.some((surface) =>
    isPositionInsidePolygon({ latitude, longitude }, surface.positions),
  );
  if (isInside !== isInsideGeofenceZone) {
    isInsideGeofenceZone = isInside;
    geofenceZone.surfaces.forEach((surface) => {
      venue.updateSurface(surface, { color: isInside ? GEOFENCE_ALERT_COLOR : 'initial' });
    });
  }
  bridge?.onGeofenceStateChanged(isInside);
},
```

Native call site:

```kotlin
private fun WebView.resolveZone(requestId: Int, placeId: String) {
    val script = "window.MapBridge.resolveZone($requestId, ${JSONObject.quote(placeId)})"
    evaluateJavascript(script, null)
}

private fun WebView.checkGeofence(latitude: Double, longitude: Double) {
    val script = "window.MapBridge.checkGeofence($latitude, $longitude)"
    evaluateJavascript(script, null)
}
```

`checkGeofence` is called every tick right after `injectTrackedPosition`, from the same interpolation loop `simulated-position` already runs.

## Things to know

- **The SDK has no geofencing or point-in-polygon primitive.** This demo implements containment itself against the public `Surface.positions` (a `readonly Position[]`, WGS84 lat/lng/altitude vertices) using standard ray-casting — there is no `venue`/`view` method to call for this.
- **`Surface.positions` and `injectTrackedPosition`'s position argument share the same WGS84 coordinate space** — no conversion is needed between the zone's geometry and the tracked position.
- **There is no position-changed event** (`view.latestTrackedPosition` is a plain readonly property, not an event source) — this demo piggybacks the containment check onto the existing position-simulation tick loop rather than polling separately.
- **A POI can have more than one surface** (`poi.surfaces` is an array) — the zone is considered entered if the position falls inside *any* of them, all colored together as one zone.
- **`venue.updateSurface(surface, { color })` is the same "recolor a surface" primitive already used by `clickable-surface`/`category-highlight`** — there is no dedicated alert/marker API; `'initial'` restores the map bundle's original color, same convention as those two.

## Learn more

- `simulated-position.md` — the tracked-position simulation loop this feature attaches to.
- `clickable-surface.md` / `category-highlight.md` — other features built on the same `venue.updateSurface(surface, { color })` recoloring primitive.
