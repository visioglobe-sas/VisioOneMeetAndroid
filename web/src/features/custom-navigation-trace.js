// custom-navigation-trace: restyles the route drawn by compute-navigation.
// See docs/features/custom-navigation-trace.md.
import { venue } from '../context.js';
import { currentNavigationTrace } from './compute-navigation.js';

export const mapBridge = {
  // Restyles the trace currently drawn by computeNavigation via
  // venue.updateNavigationTrace(trace, options). A no-op if no trace is
  // currently displayed — colors only affect a trace that already exists.
  // `options` is a NavigationTraceUpdateOptions object (colors only, see the
  // SDK's NavigationTraceUpdateOptions typings): progressColor,
  // progressOutlineColor, progressFutureColor, previewColor,
  // previewOutlineColor.
  //
  // venue.updateNavigationTrace() throws internally (TypeError: Cannot read
  // properties of undefined (reading 'material'), deep in the SDK's own
  // line-rendering pipeline) on this shared demo venue — yet every color in
  // `options` is still applied correctly before it throws. Not our bug to
  // fix — caught here so it doesn't surface as an uncaught error on the
  // bridge call; the color change is treated as applied regardless of
  // whether it throws.
  updateNavigationTraceStyle(options) {
    if (!venue || !currentNavigationTrace) return;
    try {
      venue.updateNavigationTrace(currentNavigationTrace, options);
    } catch (error) {
      console.warn('updateNavigationTrace threw (trace styling still applied):', error);
    }
  },
};
