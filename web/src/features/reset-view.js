// reset-view: recenters the camera on the whole venue. See docs/features/reset-view.md.
import { view } from '../context.js';

export const mapBridge = {
  goToGlobal() {
    if (view) view.goToGlobal();
  },
};
