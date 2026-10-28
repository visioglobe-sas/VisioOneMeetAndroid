// html-overlay-marker: pins a tappable HTML badge on the map, owned by an
// anchor POI, and reports its state (including taps) to native. See
// docs/features/html-overlay-marker.md.
import { notify } from '../bridge.js';
import { venue } from '../context.js';
import { findPoi, offsetFromAnchor, poiDisplayPosition } from '../shared/poi.js';
import { clearSelectedPoi, goToPoi } from './goto-poi.js';

// The HTML Marker placed by placeHtmlMarker, tracked so the
// update/visibility/remove commands know what to act on, plus how many
// times its badge was tapped since it was placed. Only one at a time — a
// demo-side simplification, not an SDK limitation.
let htmlMarker = null;
let htmlMarkerTapCount = 0;

// Inline style of the badge element. Deliberately no transform/translate/
// position/top/left: the SDK owns those on a marker's element (it centers it
// on the marker's position and moves it every frame).
const HTML_MARKER_STYLE =
  'background:#0B6BCB;color:#FFFFFF;font:600 14px sans-serif;padding:6px 12px;border-radius:14px;' +
  'white-space:nowrap;box-shadow:0 2px 6px rgba(0,0,0,.3);cursor:pointer';

// Reports the badge's whole state to native after every html-overlay-marker
// command and every tap — the JS side is the single source of truth, so the
// native panel can be closed (to tap the badge) and reopened without losing
// track of it. `status` names what just happened.
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

// The SDK throws "Provided marker is not registered" on a second removal, so
// the marker is forgotten as soon as it is removed. No state report: the
// caller reports once itself.
function removeHtmlMarkerSilently() {
  if (!venue || !htmlMarker) return;
  venue.removeMarker(htmlMarker);
  htmlMarker = null;
  htmlMarkerTapCount = 0;
}

export const mapBridge = {
  // Pins an HTML element (a `div` badge) on the map via venue.createMarker,
  // owned by the anchor POI, at that POI's own position (copied via
  // poiDisplayPosition, skipping our own marker, which sits in poi.markers once
  // placed) offset by offsetFromAnchor. Text, anchor and anchor
  // position are validated BEFORE touching the badge already placed: a failed
  // place leaves it untouched. The SDK forces `pointer-events: none` on the
  // element at creation, so it's set back to `auto` right after, making the
  // badge tappable; its click is stopped there (so it never reaches the map
  // and opens the POI underneath) and forwarded to native. On success the
  // camera moves to the anchor via goto-poi's goToPoi, since a marker only
  // shows while its POI's floor is displayed.
  placeHtmlMarker(anchorId, text) {
    if (!venue) return;
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
    removeHtmlMarkerSilently();
    const position = offsetFromAnchor(anchorPosition);
    const element = document.createElement('div');
    element.style.cssText = HTML_MARKER_STYLE;
    element.textContent = trimmed;
    try {
      const marker = venue.createMarker({ poi: anchor, position, element });
      element.style.pointerEvents = 'auto';
      element.addEventListener('click', (event) => {
        event.stopPropagation();
        if (htmlMarker !== marker) return;
        htmlMarkerTapCount += 1;
        reportHtmlMarkerState('tapped');
      });
      htmlMarker = marker;
      htmlMarkerTapCount = 0;
      reportHtmlMarkerState('placed');
      goToPoi(anchor);
    } catch (error) {
      reportHtmlMarkerState('error', { message: String(error?.message ?? error) });
    }
  },
  // Changes the badge's text. No SDK call: marker.element is a live DOM node,
  // so a plain DOM edit is all it takes. A no-op if nothing is placed.
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
  // Shows/hides the badge via venue.updateMarker's isVisible (the element
  // stays in the DOM, display:none). A no-op if nothing is placed.
  setHtmlMarkerVisible(isVisible) {
    if (!venue || !htmlMarker) return;
    venue.updateMarker(htmlMarker, { isVisible });
    reportHtmlMarkerState(isVisible ? 'shown' : 'hidden');
  },
  // Removes the badge via venue.removeMarker (which also detaches the element
  // from the DOM), forgets it — the SDK throws "Provided marker is not
  // registered" on a second removal — and reverts goToPoi's highlight. A
  // no-op if nothing is placed.
  removeHtmlMarker() {
    if (!venue || !htmlMarker) return;
    removeHtmlMarkerSilently();
    clearSelectedPoi();
    reportHtmlMarkerState('removed');
  },
};
