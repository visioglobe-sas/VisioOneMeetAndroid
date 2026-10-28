// dynamic-poi-crud: creates, updates and removes a POI at runtime. See
// docs/features/dynamic-poi-crud.md.
import { notify } from '../bridge.js';
import { venue } from '../context.js';
import { findPoi, poiDisplayPosition } from '../shared/poi.js';

// The POI/Label pair created at runtime by createDynamicPoi, tracked so
// updateDynamicPoiLabel/removeDynamicPoi know what to act on. Only one
// dynamic POI is tracked at a time — a demo-side simplification, not an SDK
// limitation.
let dynamicPoi = null;
let dynamicLabel = null;

export const mapBridge = {
  // Creates a POI at runtime, without republishing the map in VisioMapEditor,
  // and gives it a visual footprint by attaching a Label — a bare POI created
  // via venue.createPOI has no images/labels/lines/surfaces/markers of its
  // own, so it would otherwise be invisible on the map. The new label's
  // position is copied from anchorId's own position (poiDisplayPosition, no
  // "tap the map to place a pin" UI in this demo) rather than picked by the
  // user. All three outcomes below (anchor not found, anchor has no position
  // to copy, duplicate newId) are reported back as normal states via
  // AndroidBridge.onDynamicPoiCreated, requestId echoed back, same
  // convention as loadCustomData/getCategories.
  createDynamicPoi(requestId, newId, anchorId, labelText) {
    if (!venue) return;
    const report = (status, extra) => {
      notify('onDynamicPoiCreated', requestId, JSON.stringify({ status, ...extra }));
    };
    const anchor = findPoi(anchorId);
    if (!anchor) {
      report('anchor-not-found');
      return;
    }
    const anchorPosition = poiDisplayPosition(anchor);
    if (!anchorPosition) {
      report('anchor-has-no-position');
      return;
    }
    try {
      const poi = venue.createPOI({ id: newId });
      const label = venue.createLabel({ poi, position: anchorPosition, width: 2, text: labelText });
      dynamicPoi = poi;
      dynamicLabel = label;
      report('created', { id: poi.id, text: label.text });
    } catch (error) {
      // createPOI's only documented failure mode is POIAlreadyExistsError
      // (see the SDK's Venue typings) — but that class isn't part of this
      // package's public exports, so it can't be caught with `instanceof`.
      // Its constructor name survives bundling unminified, so that's the
      // most reliable check available from here.
      if (error?.constructor?.name === 'POIAlreadyExistsError') {
        report('duplicate-id');
      } else {
        report('error', { message: String(error?.message ?? error) });
      }
    }
  },
  // Updates the tracked dynamic POI's label text — the real "modify" story
  // for a dynamic POI, since venue.updatePOI itself can only touch
  // categories, never anything visual. A no-op if nothing is tracked.
  updateDynamicPoiLabel(text) {
    if (!venue || !dynamicLabel) return;
    venue.updateLabel(dynamicLabel, { text });
  },
  // Removes the tracked dynamic POI via venue.removePOI, which cascades to
  // remove its attached label from the view too — no separate removeLabel
  // call needed. A no-op if nothing is tracked.
  removeDynamicPoi() {
    if (!venue || !dynamicPoi) return;
    venue.removePOI(dynamicPoi);
    dynamicPoi = null;
    dynamicLabel = null;
  },
};
