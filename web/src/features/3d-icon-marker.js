// 3d-icon-marker: places an image on the 3D map, owned by an anchor POI. See
// docs/features/3d-icon-marker.md.
import { notify } from '../bridge.js';
import { venue } from '../context.js';
import { findPoi, poiDisplayPosition } from '../shared/poi.js';

// The image shown by 3d-icon-marker: a 96x96 PNG (blue disc + white star)
// inlined as a data URL, so the demo needs no hosted asset.
const ICON_DATA_URL =
  'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAGAAAABgCAYAAADimHc4AAACBklEQVR42u3dWW4CMRBFUVbLarOn5JsIqRlsv6FuSfmE0O+IwS7bfbtR1GXdf34/+qMOhg2KcehgGIU+HsMt+DEQ7sHXQqQFXwWRHn4sQkvwkRCt4dsjtAdvDTEtfCuEqeFbIAgv/H/NQzAKfx6CYfhzEIzD70cQf+FFAGxFAEAIEBJ+J4LB7+04gKUIAAgBTEackQBfI4SG34MAgBDAaMIrHuAjBACEAGZTvhUAbyEAIAQwbHrUALyEAAAAcwGMe68V4V8iHAjJHeAI7mqAb0oNcOK1vwYgCP+di1E/334EMcDVRSmewx5gd60COPk6K94Bzy7u1OP4CAqrNQCCQdJ4gAcE0Uh1dPgrASYirB0Ri+dsRoa/A6AdYt+ckNksZn3wpwDSIc7NiprP69cFrwJwBdH3BUo6YLlN+qI2ZGZ7EgAA+AgqD99/ueKA8K0Q0veAxSMk7v+tQnABULQrvQZhQgBVv9gCQb00cXVoUQgOa0MBEALsCitvFlSEMBpAvT9gd0g5TRgATAGMe8Ru/ydul+TJULIBNiGMBlDvlFcE4rkGSISgCsICwOG0FGUIuQDCdaM121ydTs1S/wrJDP8gQN3pu44nJzo1RXLC56h6Ts4FAAROUCd8ELiLBuGDwJ2UCB8E7qhH8BMR4orgQZgdfjJEbRE8ELODd8KgBBjUQRSKuqo/fm7G7BpJ/VcAAAAASUVORK5CYII=';

// Images created at runtime by placeIconImage, keyed by the id the native
// side chose, so updateIconImageOrientation/removeIconImage know what to act
// on.
const iconImages = new Map();

function removeIconImageById(imageId) {
  const image = iconImages.get(imageId);
  if (!venue || !image) return;
  venue.removeImage(image);
  iconImages.delete(imageId);
}

export const mapBridge = {
  // Places an Image owned by the anchor POI itself, at that POI's own
  // position (copied via poiDisplayPosition, same as createDynamicPoi), 6x6
  // meters, oriented per orientationType ('fixed' | 'flat' | 'facing'). Any
  // image previously placed under the same imageId is removed first, so only
  // one exists per id. The outcome (placed / anchor not found / anchor has no
  // position / error) is reported back via AndroidBridge.onIconImagePlaced,
  // requestId echoed back, same convention as createDynamicPoi.
  placeIconImage(requestId, imageId, anchorId, orientationType) {
    if (!venue) return;
    const report = (status, extra) => {
      notify('onIconImagePlaced', requestId, JSON.stringify({ status, ...extra }));
    };
    const anchor = findPoi(anchorId);
    if (!anchor) {
      report('anchor-not-found');
      return;
    }
    // Our own previous image (once placed, one of anchor.images) is skipped.
    const position = poiDisplayPosition(anchor, [iconImages.get(imageId)]);
    if (!position) {
      report('anchor-has-no-position');
      return;
    }
    removeIconImageById(imageId);
    try {
      const image = venue.createImage({
        poi: anchor,
        position,
        width: 6,
        height: 6,
        url: ICON_DATA_URL,
        orientationType,
      });
      iconImages.set(imageId, image);
      report('placed', { poiId: anchor.id });
    } catch (error) {
      report('error', { message: String(error?.message ?? error) });
    }
  },
  // Switches a placed image's orientation live via venue.updateImage. A
  // no-op if nothing is placed under imageId.
  updateIconImageOrientation(imageId, orientationType) {
    const image = iconImages.get(imageId);
    if (!venue || !image) return;
    venue.updateImage(image, { orientationType });
  },
  // Removes a placed image via venue.removeImage. A no-op if nothing is
  // placed under imageId.
  removeIconImage(imageId) {
    removeIconImageById(imageId);
  },
};
