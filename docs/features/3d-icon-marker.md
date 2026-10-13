# 3D Icon Marker

## Description

Places a custom image (here, a small PNG icon inlined as a data URL) at a precise spot on the 3D map, e.g. a product photo above a shelf, or a business pictogram above a shop. Built on `venue.createImage()`, with `venue.updateImage()` to switch its orientation live and `venue.removeImage()` to take it down.

The image is owned by an existing "anchor" POI and placed at that POI's own position — copied from its first label/marker/image, the same approach `dynamic-poi-crud.md` uses (there is no "tap the map to place a pin" UI here).

## SDK usage

```js
// web/src/main.js

// POIs carry no direct lat/lng field: read it off an attached visual element.
function anchorPositionOf(poi) {
  return poi.labels[0]?.position ?? poi.markers[0]?.position ?? poi.images[0]?.position;
}

const ICON_DATA_URL = 'data:image/png;base64,iVBORw0KGgo...';

// Images created at runtime, keyed by an id chosen by the native side.
const iconImages = new Map();

placeIconImage(requestId, imageId, anchorId, orientationType) {
  if (!venue) return;
  const report = (status, extra) => {
    bridge?.onIconImagePlaced(requestId, JSON.stringify({ status, ...extra }));
  };
  const anchor = venue.pois.find((p) => p.id === anchorId);
  if (!anchor) {
    report('anchor-not-found');
    return;
  }
  const position = anchorPositionOf(anchor);
  if (!position) {
    report('anchor-has-no-position');
    return;
  }
  window.MapBridge.removeIconImage(imageId); // only one image per id
  try {
    const image = venue.createImage({
      poi: anchor,
      position,
      width: 6,
      height: 6,
      url: ICON_DATA_URL,
      orientationType, // 'fixed' | 'flat' | 'facing'
    });
    iconImages.set(imageId, image);
    report('placed', { poiId: anchor.id });
  } catch (error) {
    report('error', { message: String(error?.message ?? error) });
  }
},

updateIconImageOrientation(imageId, orientationType) {
  const image = iconImages.get(imageId);
  if (!venue || !image) return;
  venue.updateImage(image, { orientationType });
},

removeIconImage(imageId) {
  const image = iconImages.get(imageId);
  if (!venue || !image) return;
  venue.removeImage(image);
  iconImages.delete(imageId);
},
```

Native call sites:

```kotlin
// FeatureOverlays.kt
private fun WebView.placeIconImage(requestId: Int, anchorId: String, orientationType: String) {
    val script = "window.MapBridge.placeIconImage(" +
        "$requestId, ${JSONObject.quote(ICON_MARKER_IMAGE_ID)}, ${JSONObject.quote(anchorId)}, " +
        "${JSONObject.quote(orientationType)})"
    evaluateJavascript(script, null)
}

private fun WebView.updateIconImageOrientation(orientationType: String) {
    val script = "window.MapBridge.updateIconImageOrientation(" +
        "${JSONObject.quote(ICON_MARKER_IMAGE_ID)}, ${JSONObject.quote(orientationType)})"
    evaluateJavascript(script, null)
}

private fun WebView.removeIconImage() {
    evaluateJavascript("window.MapBridge.removeIconImage(${JSONObject.quote(ICON_MARKER_IMAGE_ID)})", null)
}
```

`placeIconImage`'s result comes back asynchronously, echoing `requestId`, same convention as `dynamic-poi-crud.md`'s `createDynamicPoi`:

```kotlin
// FeatureMapScreen.kt
@JavascriptInterface
fun onIconImagePlaced(requestId: Int, resultJson: String) = notifyIconImagePlaced(requestId, resultJson)
```

On a `"placed"` result, the app then moves the camera to the anchor POI with goto-poi's own `view.goToPOI()` call (see "Things to know").

The SDK calls exercised:

- `venue.createImage(options: { poi: POI; position: Position; width: number; height?: number; rotation?: number; url: string; orientationType?: OrientationType }): Image` — `position` is WGS84 (`{ latitude, longitude, altitude? }`), `width`/`height` are in meters.
- `venue.updateImage(image: Image, options: { orientationType?, width?, height?, position?, rotation?, url?, isVisible? }): void`.
- `venue.removeImage(image: Image): void`.
- `OrientationType = 'fixed' | 'flat' | 'facing'` — `fixed`: flat on the ground with a fixed heading; `flat`: flat on the ground, turning to face the camera; `facing`: always facing the camera (billboard).

## Things to know

- **`'flat'`/`'facing'` require SDK ≥ 1.0.9.** In `@visioglobe/visioone` 1.0.5 an image created or updated at runtime ignored those two modes and rendered lying flat with a fixed heading (an SDK bug, fixed in 1.0.9). `'fixed'` works on both versions. A flat image can z-fight with whatever sits at the same altitude (e.g. the anchor's own label).
- **The owner POI keeps its own images.** `createImage` appends to the owner POI's `images` array: an anchor that already shows a pictogram from the published map ends up with two images. `removeImage` only removes yours; `removePOI` on the owner would remove every visual element attached to it, yours included.
- **One `Image` per `createImage` call.** Calling `createImage` again adds another image, it doesn't replace the previous one — keep the returned `Image` and `removeImage` it first if you want only one at a time.
- **Data URLs work.** A base64 PNG data URL renders with no extra configuration or network request.
- **The anchor's floor must be visible.** An image is attached to its owner POI's floor and only renders while that floor is displayed — not in the default whole-venue view. Move the camera there (e.g. `view.goToPOI(anchor)`) after placing it.
- **Sizes are in meters.** `width`/`height` are world sizes, so the icon grows or shrinks on screen as the camera zooms. Passing only `width` to `updateImage` updates both width and height.
- **POIs carry no direct lat/lng field.** Read the position off the anchor's `labels[0]`/`markers[0]`/`images[0]`; treat an anchor with none of them as an explicit "no position" state rather than passing `undefined` into `createImage`. Read it *before* removing/creating images on that same POI, since your own image becomes one of `anchor.images` once placed.

## Learn more

- `dynamic-poi-crud.md`: the same "copy an anchor POI's position" approach, used there to attach a `Label` to a freshly created POI.
- `goto-poi.md`: `view.goToPOI()`, used here to bring the anchor's floor into view.
