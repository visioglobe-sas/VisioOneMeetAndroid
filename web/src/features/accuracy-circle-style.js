// accuracy-circle-style: recolors the tracked position's precision circle
// (the position itself comes from simulated-position). See
// docs/features/accuracy-circle-style.md.
import { view } from '../context.js';

// The { color, opacity } last applied via setPositionTrackerStyle,
// re-applied whenever tracking (re)starts because the SDK resets it to its
// defaults (#0094f0, 0.3) on every stop and offers no getter. null = never
// set (every other screen).
let positionTrackerStyle = null;

// Called by simulated-position's injectTrackedPosition on every tracking
// false -> true transition. A no-op on every other screen.
export function reapplyPositionTrackerStyle() {
  if (positionTrackerStyle) view.updatePositionTrackerGraphicOptions(positionTrackerStyle);
}

export const mapBridge = {
  // Recolors the tracked position's precision circle (not the arrow icon at
  // its center, which the SDK never recolors) via
  // view.updatePositionTrackerGraphicOptions({ color, opacity }). The SDK
  // only stores the options: they show up at the next injectTrackedPosition
  // call (every ~150 ms while the simulation runs, so it looks live). The
  // style is also kept here, since the SDK has no getter for it and resets
  // it whenever tracking stops — see reapplyPositionTrackerStyle above. An
  // invalid color throws (hex, rgba() or hsl() only): the style is then not
  // kept, and `false` is returned, which native reads from
  // evaluateJavascript's result to keep its own selection unchanged.
  setPositionTrackerStyle(color, opacity) {
    if (!view) return false;
    try {
      view.updatePositionTrackerGraphicOptions({ color, opacity });
      positionTrackerStyle = { color, opacity };
      return true;
    } catch (error) {
      console.error('updatePositionTrackerGraphicOptions failed', error);
      return false;
    }
  },
};
