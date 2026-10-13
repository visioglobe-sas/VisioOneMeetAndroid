# Custom Text Label

## Description

Places your own free text on the map — opening hours, a price, a zone name… — independently of the labels published with the map. Built on `venue.createLabel()`, restyled live with `venue.updateLabel()` (text, color, size) and taken down with `venue.removeLabel()`.

The label is owned by an existing "anchor" POI and placed next to that POI's own position — copied from its first label/marker/image, the same approach `dynamic-poi-crud.md` and `3d-icon-marker.md` use, then offset by ~4 m so it doesn't overlap the POI's own label.

## SDK usage

```js
// web/src/main.js

// POIs carry no direct lat/lng field: read it off an attached visual element,
// skipping `excludedLabel` (our own label, possibly still attached to the anchor).
function anchorPositionOf(poi, excludedLabel = null) {
  return (
    poi.labels.find((label) => label !== excludedLabel)?.position ??
    poi.markers[0]?.position ??
    poi.images[0]?.position
  );
}

let customTextLabel = null;
const CUSTOM_TEXT_LABEL_OFFSET = 0.00004; // degrees, ~4 m

// options = { text, color, width, height, rotation }
placeTextLabel(requestId, anchorId, options) {
  if (!venue) return;
  const report = (status, extra) => {
    bridge?.onTextLabelPlaced(requestId, JSON.stringify({ status, ...extra }));
  };
  // Validate everything first: a failed place leaves the current label untouched.
  const text = String(options.text ?? '').trim();
  if (!text) {
    report('text-required');
    return;
  }
  const anchor = venue.pois.find((p) => p.id === anchorId);
  if (!anchor) {
    report('anchor-not-found');
    return;
  }
  const anchorPosition = anchorPositionOf(anchor, customTextLabel);
  if (!anchorPosition) {
    report('anchor-has-no-position');
    return;
  }
  window.MapBridge.removeTextLabel(); // only one label at a time
  const position = {
    ...anchorPosition,
    latitude: anchorPosition.latitude + CUSTOM_TEXT_LABEL_OFFSET,
    longitude: anchorPosition.longitude + CUSTOM_TEXT_LABEL_OFFSET,
  };
  try {
    customTextLabel = venue.createLabel({
      poi: anchor,
      position,
      width: options.width,
      height: options.height, // always pass it, see "Things to know"
      text,
      color: options.color,
      rotation: options.rotation,
    });
    report('placed', { poiId: anchor.id });
  } catch (error) {
    report('error', { message: String(error?.message ?? error) });
  }
},

// options: any of { text }, { color }, { width, height } (width and height always together)
updateTextLabel(options) {
  if (!venue || !customTextLabel) return;
  if (options.text !== undefined && !String(options.text).trim()) return;
  venue.updateLabel(customTextLabel, options);
},

// rotation is create-only: remove, then recreate with the same values.
setTextLabelRotation(rotation) {
  if (!venue || !customTextLabel) return;
  const { poi, position, width, height, text, color } = customTextLabel;
  window.MapBridge.removeTextLabel();
  customTextLabel = venue.createLabel({ poi, position, width, height, text, color, rotation });
},

removeTextLabel() {
  if (!venue || !customTextLabel) return;
  venue.removeLabel(customTextLabel);
  customTextLabel = null;
},
```

Native call sites (arguments are JSON-encoded before being interpolated into the script):

```kotlin
// FeatureOverlays.kt
private fun WebView.placeTextLabel(
    requestId: Int, anchorId: String, text: String, color: String, size: TextLabelSize, rotation: Int,
) {
    val options = JSONObject()
        .put("text", text)
        .put("color", color)
        .put("width", size.width)
        .put("height", size.height)
        .put("rotation", rotation)
    evaluateJavascript("window.MapBridge.placeTextLabel($requestId, ${JSONObject.quote(anchorId)}, $options)", null)
}

private fun WebView.updateTextLabel(options: JSONObject) {
    evaluateJavascript("window.MapBridge.updateTextLabel($options)", null)
}

private fun WebView.setTextLabelRotation(rotation: Int) {
    evaluateJavascript("window.MapBridge.setTextLabelRotation($rotation)", null)
}

private fun WebView.removeTextLabel() {
    evaluateJavascript("window.MapBridge.removeTextLabel()", null)
}

// e.g. a color change, then a size change, while a label is placed:
webView.updateTextLabel(JSONObject().put("color", "#43A047"))
webView.updateTextLabel(JSONObject().put("width", 22).put("height", 4))
```

`placeTextLabel`'s result comes back asynchronously, echoing `requestId`, same convention as `3d-icon-marker.md`'s `placeIconImage`:

```kotlin
// FeatureMapScreen.kt
@JavascriptInterface
fun onTextLabelPlaced(requestId: Int, resultJson: String) = notifyTextLabelPlaced(requestId, resultJson)
```

On a `"placed"` result, the app then moves the camera to the anchor POI with goto-poi's own `view.goToPOI()` call (see "Things to know").

The SDK calls exercised:

- `venue.createLabel(options: { poi: POI; position: Position; width: number; height?: number; text: string; color?: Color; rotation?: number }): Label` — `position` is WGS84 (`{ latitude, longitude, altitude? }`), `width`/`height` in meters, `rotation` in degrees relative to the map's orientation.
- `venue.updateLabel(label: Label, options: { position?, width?, height?, text?, isVisible?, color? }): void` — note: no `rotation`.
- `venue.removeLabel(label: Label): void`.
- `Label` exposes its current `poi`, `position`, `width`, `height`, `text`, `color`, `rotation` (all read-only), which is what the rotation change reads back to recreate it.

## Things to know

- **Always pass `height`.** It is optional on `createLabel` and **defaults to `width`**: a 16 m wide label becomes a 16×16 m box and the text is fitted into it — wrapped and oversized. The text is fitted inside the `width`×`height` box (long text wraps to two lines).
- **Always update `width` and `height` together.** `updateLabel(label, { width })` alone also sets `height = width` (documented in `LabelUpdateOptions`), garbling the text the same way; `height` alone has no effect.
- **`rotation` is create-only.** It is part of `LabelCreateOptions` but absent from `LabelUpdateOptions`. Passing it to `updateLabel` anyway does rotate the rendering, but `label.rotation` keeps reporting the old value — undocumented, don't rely on it. To change the rotation, `removeLabel` and `createLabel` again with the same values.
- **Color format.** `color` must be hex, `rgb()` or `hsl()` (alpha is ignored); anything else throws `Invalid color format provided…`.
- **Empty text throws.** `createLabel` with an empty `text` throws `Cannot create Label, text is required` — validate before calling.
- **Removing twice throws.** `removeLabel` (or `updateLabel`) on a label that was already removed throws `Provided label is not registered`. Forget your reference right after removing it.
- **The anchor POI keeps its own label.** `createLabel` appends your label to `poi.labels`, next to the label published with the map — it doesn't replace or alter it. `removeLabel` only removes yours. When copying the anchor's position, skip your own label (it sits in `poi.labels` too), so you never copy — and re-offset — your own label's position.
- **One `Label` per `createLabel` call.** Calling it again adds another label; keep the returned `Label` and remove it first if you want only one.
- **The anchor's floor must be visible.** A label is attached to its owner POI's floor and only renders while that floor is displayed — not in the default whole-venue view. Move the camera there (e.g. `view.goToPOI(anchor)`) after placing it.
- **Offsetting a position.** A position is plain WGS84; `0.00004°` of latitude/longitude is roughly 4 m, enough to clear the POI's own label at building scale.

## Learn more

- `dynamic-poi-crud.md`: `createLabel`/`updateLabel` on a freshly created POI, and the same "copy an anchor POI's position" approach.
- `3d-icon-marker.md`: the image counterpart, `venue.createImage()`/`updateImage()`/`removeImage()`.
- `goto-poi.md`: `view.goToPOI()`, used here to bring the anchor's floor into view.
