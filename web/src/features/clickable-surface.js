// clickable-surface: makes a POI's surfaces interactive. See
// docs/features/clickable-surface.md.
import { venue } from '../context.js';
import { findPoi } from '../shared/poi.js';

export const mapBridge = {
  // Makes the POI's surfaces clickable (or reverts them) via
  // venue.updateSurface's isInteractive flag. While interactive, the SDK
  // itself handles the hover/selection color swap on tap — no click
  // listener needed on our side for the coloring itself. 'initial' resets
  // the color to whatever the map bundle originally defined for it.
  setSurfaceInteractive(placeId, interactive) {
    if (!venue) return;
    const poi = findPoi(placeId);
    if (!poi) return;
    poi.surfaces.forEach((surface) => {
      venue.updateSurface(
        surface,
        interactive
          ? { isInteractive: true, color: '#2ECC71', hoverColor: '#F1C40F', selectionColor: '#E74C3C' }
          : { isInteractive: false, color: 'initial' },
      );
    });
  },
};
