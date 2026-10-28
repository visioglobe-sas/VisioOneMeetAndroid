# HTML Overlay Marker

## Description

Pins a regular HTML element — here a small "Open now · 12:00–14:00" badge — on the map at a geographic position, via `venue.createMarker({ poi, position, element })`. Unlike a `Label` or an `Image`, which are drawn inside the 3D scene, a marker's element is overlaid on top of the map: it always faces the screen, is never hidden by walls or other 3D geometry, and — the point of this demo — it is **interactive**: a tap on it reaches your app. It is shown/hidden with `venue.updateMarker(marker, { isVisible })` and taken down with `venue.removeMarker(marker)`.

In a WebView-based Android app the element lives in the WebView's DOM: the bridge JS creates it, and forwards taps on it to Kotlin through the `@JavascriptInterface` bridge.

The marker is owned by an existing "anchor" POI and placed next to that POI's own position — copied with the shared `poiDisplayPosition` helper (first label, else first image or marker, else the centroid of its first surface — see `simulated-position.md`), the same approach `dynamic-poi-crud.md`, `3d-icon-marker.md` and `custom-text-label.md` use, then offset by ~4 m.

## SDK usage

```js
// web/src/features/html-overlay-marker.js
import { notify } from '../bridge.js';
import { venue } from '../context.js';
import { findPoi, offsetFromAnchor, poiDisplayPosition } from '../shared/poi.js';
import { clearSelectedPoi, goToPoi } from './goto-poi.js';

// poiDisplayPosition (web/src/shared/poi.js): POIs carry no direct lat/lng field, so it returns
// the POI's first label's position, else its first image's or marker's, else the centroid of its
// first surface's outline; `excluded` skips our own objects. offsetFromAnchor adds ~4 m.

let htmlMarker = null;
let htmlMarkerTapCount = 0;

// No transform/translate/position/top/left here: the SDK owns them (see "Things to know").
const HTML_MARKER_STYLE =
  'background:#0B6BCB;color:#FFFFFF;font:600 14px sans-serif;padding:6px 12px;border-radius:14px;' +
  'white-space:nowrap;box-shadow:0 2px 6px rgba(0,0,0,.3);cursor:pointer';

// Reports the marker's whole state to native after every command and every tap.
function reportHtmlMarkerState(status, extra) {
  notify(
    'onHtmlMarkerStateChanged',
    JSON.stringify({
      status,
      placedOnPoiId: htmlMarker?.poi.id ?? null,
      isVisible: htmlMarker?.isVisible ?? true,
      tapCount: htmlMarkerTapCount,
      ...extra,
    }),
  );
}

export const mapBridge = {
  placeHtmlMarker(anchorId, text) {
    if (!venue) return;
    // Validate everything first: a failed place leaves the current marker untouched.
    const trimmed = String(text ?? '').trim();
    if (!trimmed) {
      reportHtmlMarkerState('text-required');
      return;
    }
    const anchor = findPoi(anchorId);
    if (!anchor) {
      reportHtmlMarkerState('anchor-not-found');
      return;
    }
    const anchorPosition = poiDisplayPosition(anchor, [htmlMarker]);
    if (!anchorPosition) {
      reportHtmlMarkerState('anchor-has-no-position');
      return;
    }
    removeHtmlMarkerSilently(); // only one marker at a time (venue.removeMarker)
    const position = offsetFromAnchor(anchorPosition);
    const element = document.createElement('div');
    element.style.cssText = HTML_MARKER_STYLE;
    element.textContent = trimmed;
    try {
      const marker = venue.createMarker({ poi: anchor, position, element });
      element.style.pointerEvents = 'auto'; // the SDK set it to 'none' (see "Things to know")
      element.addEventListener('click', (event) => {
        event.stopPropagation(); // don't let the tap reach the map underneath
        if (htmlMarker !== marker) return;
        htmlMarkerTapCount += 1;
        reportHtmlMarkerState('tapped');
      });
      htmlMarker = marker;
      htmlMarkerTapCount = 0;
      reportHtmlMarkerState('placed');
      goToPoi(anchor); // view.goToPOI(): show the anchor's floor
    } catch (error) {
      reportHtmlMarkerState('error', { message: String(error?.message ?? error) });
    }
  },
  // No SDK call: marker.element is a live DOM node.
  updateHtmlMarkerText(text) {
    if (!venue || !htmlMarker) return;
    const trimmed = String(text ?? '').trim();
    if (!trimmed) {
      reportHtmlMarkerState('text-required');
      return;
    }
    htmlMarker.element.textContent = trimmed;
    reportHtmlMarkerState('text-updated');
  },
  setHtmlMarkerVisible(isVisible) {
    if (!venue || !htmlMarker) return;
    venue.updateMarker(htmlMarker, { isVisible });
    reportHtmlMarkerState(isVisible ? 'shown' : 'hidden');
  },
  removeHtmlMarker() {
    if (!venue || !htmlMarker) return;
    removeHtmlMarkerSilently(); // venue.removeMarker(htmlMarker) — also detaches the element from the DOM
    clearSelectedPoi(); // revert goToPoi's highlight
    reportHtmlMarkerState('removed');
  },
};
```

Native call sites (`callBridge` JSON-encodes each argument: strings with `JSONObject.quote`, the boolean as is):

```kotlin
// app/src/main/java/com/visioglobe/visioonemeet/features/HtmlOverlayMarker.kt
private fun WebView.placeHtmlMarker(anchorId: String, text: String) = callBridge("placeHtmlMarker", anchorId, text)

private fun WebView.updateHtmlMarkerText(text: String) = callBridge("updateHtmlMarkerText", text)

private fun WebView.setHtmlMarkerVisible(isVisible: Boolean) = callBridge("setHtmlMarkerVisible", isVisible)

private fun WebView.removeHtmlMarker() = callBridge("removeHtmlMarker")
```

Every outcome — including a tap on the badge — comes back through one bridge method, carrying a JSON snapshot such as `{"status":"tapped","placedOnPoiId":"B4-UL00-ID0009","isVisible":true,"tapCount":3}`:

```kotlin
// app/src/main/java/com/visioglobe/visioonemeet/bridge/NativeBridge.kt — exposed as window.AndroidBridge
@JavascriptInterface
fun onHtmlMarkerStateChanged(stateJson: String) = emit(BridgeEvent.HtmlMarkerStateChanged(stateJson))
```

`@JavascriptInterface` methods run on a WebView background thread: `emit` posts the event to the main thread before any UI state is touched. The feature screen then collects it:

```kotlin
// app/src/main/java/com/visioglobe/visioonemeet/features/HtmlOverlayMarker.kt
OnBridgeEvent<BridgeEvent.HtmlMarkerStateChanged>(map) { htmlMarkerState = parseHtmlMarkerStatePayload(it.stateJson) }
```

The SDK calls exercised:

- `venue.createMarker(options: { poi: POI; position: Position; element?: HTMLElement; isForeignMarker?: boolean }): Marker` — `position` is WGS84 (`{ latitude, longitude, altitude? }`).
- `venue.updateMarker(marker: Marker, options: { position?: Position; isVisible?: boolean }): void`.
- `venue.removeMarker(marker: Marker): void`.
- `Marker` exposes `poi`, `position`, `element`, `isVisible` (read-only).

## Things to know

- **`element` is required for an HTML marker.** `createMarker` without it throws `Cannot createMarker, invalid element provided, options.element is missing`.
- **The SDK owns the element's layout.** It moves the element into its own overlay container and sets `position: absolute; top: 0; left: 0; translate: -50% -50%` (the element is **centered** on the position), a `transform: translate(x, y)` rewritten every frame, and a `z-index`. Don't set any of those on the element yourself — wrap your content in a child element if you need your own transforms.
- **Markers are not tappable by default.** The SDK sets `pointer-events: none` on the element at creation, so taps fall through to the map (and open the POI card underneath). Set `element.style.pointerEvents = 'auto'` right after `createMarker` — the SDK doesn't rewrite it afterwards (camera moves included).
- **Stop the click's propagation.** Call `event.stopPropagation()` in the element's `click` handler, so a tap on the marker is never also handled as a tap on the map. With both in place, a tap on the badge doesn't fire the view's `poiclick` event, while a tap right next to it still does.
- **Drawn above the 3D scene.** A marker is never occluded by walls or other geometry and stays readable at any camera pitch — unlike `createLabel`/`createImage`, which live in the 3D scene.
- **Only shown on its POI's floor.** While another floor is displayed, the SDK sets `display: none` on the element. Move the camera to the anchor (e.g. `view.goToPOI(anchor)`) after placing it.
- **`updateMarker` only takes `position` and `isVisible`.** `isVisible: false` sets `display: none`; the element stays in the DOM. To change the content, edit `marker.element` directly — it's a live DOM node.
- **`removeMarker` detaches the element from the DOM.** Removing twice, or updating after removal, throws `Provided marker is not registered`. Forget your reference right after removing it.
- **The marker belongs to the anchor POI.** It is appended to `poi.markers`. When copying the anchor's position, skip your own marker, so you never copy — and re-offset — your own marker's position.

## Learn more

- `MarkerCreateOptions.isForeignMarker`: instead of an HTML element, a marker can stand for a native view that your app positions itself, listening for the view's `foreignmarkerupdated` event fired on every camera move. Not demonstrated here.
- `custom-text-label.md` and `3d-icon-marker.md`: the in-scene counterparts, `venue.createLabel()` and `venue.createImage()`.
- `poi-click.md`: the view's `poiclick` event, which a tap on the marker deliberately doesn't trigger.
- `goto-poi.md`: `view.goToPOI()`, used here to bring the anchor's floor into view.
