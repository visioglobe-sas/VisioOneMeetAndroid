// custom-text-label: places free text on the map, owned by an anchor POI. See
// docs/features/custom-text-label.md.
import { notify } from '../bridge.js';
import { venue } from '../context.js';
import { findPoi, offsetFromAnchor, poiDisplayPosition } from '../shared/poi.js';

// The Label placed by placeTextLabel, tracked so
// updateTextLabel/setTextLabelRotation/removeTextLabel know what to act on.
// Only one at a time — a demo-side simplification, not an SDK limitation.
let customTextLabel = null;

// The SDK throws "Provided label is not registered" on a second removal, so
// the label is forgotten as soon as it is removed.
function removeCustomTextLabel() {
  if (!venue || !customTextLabel) return;
  venue.removeLabel(customTextLabel);
  customTextLabel = null;
}

export const mapBridge = {
  // Places a free-text Label owned by the anchor POI, at that POI's own
  // position (copied via poiDisplayPosition, same as createDynamicPoi /
  // placeIconImage) offset by offsetFromAnchor. `options` is
  // { text, color, width, height, rotation } — height is always passed
  // explicitly, since createLabel otherwise defaults it to width (a square
  // box). Text, anchor and anchor position are all validated BEFORE touching
  // the label already placed: a failed place leaves it on the map untouched.
  // Only once all three pass is the previous label removed (only one exists at
  // a time) and the new one created. The outcome (placed / text required /
  // anchor not found / anchor has no position / error) is reported back via
  // AndroidBridge.onTextLabelPlaced, requestId echoed back, same convention as
  // placeIconImage.
  placeTextLabel(requestId, anchorId, options) {
    if (!venue) return;
    const report = (status, extra) => {
      notify('onTextLabelPlaced', requestId, JSON.stringify({ status, ...extra }));
    };
    const text = String(options.text ?? '').trim();
    if (!text) {
      report('text-required');
      return;
    }
    const anchor = findPoi(anchorId);
    if (!anchor) {
      report('anchor-not-found');
      return;
    }
    // Our previous label may still be attached to this anchor: skip it, so a
    // re-place never copies (and re-offsets) our own label's position.
    const anchorPosition = poiDisplayPosition(anchor, [customTextLabel]);
    if (!anchorPosition) {
      report('anchor-has-no-position');
      return;
    }
    removeCustomTextLabel();
    const position = offsetFromAnchor(anchorPosition);
    try {
      customTextLabel = venue.createLabel({
        poi: anchor,
        position,
        width: options.width,
        height: options.height,
        text,
        color: options.color,
        rotation: options.rotation,
      });
      report('placed', { poiId: anchor.id });
    } catch (error) {
      report('error', { message: String(error?.message ?? error) });
    }
  },
  // Restyles the placed label live via venue.updateLabel. `options` may carry
  // text, color, and width+height — callers always send width and height
  // together, since updateLabel with width alone also sets height = width.
  // An empty text is ignored (createLabel would throw on it; native validates
  // first). A no-op if nothing is placed.
  updateTextLabel(options) {
    if (!venue || !customTextLabel) return;
    if (options.text !== undefined && !String(options.text).trim()) return;
    venue.updateLabel(customTextLabel, options);
  },
  // `rotation` is a create-only option (absent from LabelUpdateOptions), so a
  // rotation change removes the placed label and recreates it with the same
  // poi/position/size/text/color read back off the Label itself, plus the
  // new rotation. A no-op if nothing is placed.
  setTextLabelRotation(rotation) {
    if (!venue || !customTextLabel) return;
    const { poi, position, width, height, text, color } = customTextLabel;
    removeCustomTextLabel();
    customTextLabel = venue.createLabel({ poi, position, width, height, text, color, rotation });
  },
  // Removes the placed label via venue.removeLabel and forgets it — the SDK
  // throws "Provided label is not registered" on a second removal, so this
  // is a no-op if nothing is placed.
  removeTextLabel() {
    removeCustomTextLabel();
  },
};
