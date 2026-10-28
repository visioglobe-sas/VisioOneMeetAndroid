// ui-part-visibility: shows/hides one of the SDK's own default UI overlays.
// Also used by native-ui-replacement. See docs/features/ui-part-visibility.md.
import { view } from '../context.js';

export const mapBridge = {
  // uiPart is one of exactly 5 case-sensitive values ('floorSelector',
  // 'navigation', 'poiDetails', 'search', 'userTracking' — the SDK's UIPart
  // type); an unrecognized value is rejected by the SDK itself, this bridge
  // does no validation of its own.
  setUIPartVisible(uiPart, isVisible) {
    if (!view) return;
    view.setUIPartVisible(uiPart, isVisible);
  },
};
