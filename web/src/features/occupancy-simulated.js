// occupancy-simulated: recolors POI surfaces from an occupancy feed (simulated on the native
// side, which calls this on a timer). See docs/features/occupancy-simulated.md.
import { venue } from '../context.js';
import { findPoi } from '../shared/poi.js';

export const mapBridge = {
  // `occupancy` is an array of { planId, color }; `color: null` resets the surface to its normal
  // appearance.
  updateOccupancy(occupancy) {
    if (!venue) return;
    occupancy.forEach((entry) => {
      const poi = findPoi(entry.planId);
      if (!poi) return;
      poi.surfaces.forEach((surface) => {
        venue.updateSurface(surface, { color: entry.color });
      });
    });
  },
};
