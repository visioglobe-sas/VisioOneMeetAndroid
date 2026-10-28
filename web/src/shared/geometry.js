// The SDK has no built-in geofencing/point-in-polygon primitive — this demo
// implements containment itself against `Surface.positions` (the public,
// WGS84 vertex list of a zone POI's surface, same coordinate space as
// `injectTrackedPosition`, so no conversion is needed). Standard ray-casting,
// treating latitude/longitude as planar y/x, which is accurate enough at
// building scale. See docs/features/geofencing.md.
export function isPositionInsidePolygon(position, polygonPositions) {
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
