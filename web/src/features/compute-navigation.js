// compute-navigation: computes a route between two POIs and draws it. Also
// used by its variants: navigation-exclude-modalities (drawNavigation),
// custom-navigation-trace (currentNavigationTrace) and accessible-mode
// (computeNavigation's isAccessible). See docs/features/compute-navigation.md.
import { notify } from '../bridge.js';
import { venue, view } from '../context.js';

// The trace last drawn by `computeNavigation`, so `clearNavigation` knows
// what to remove (and custom-navigation-trace what to restyle).
export let currentNavigationTrace = null;

// Computes `request` (origin, destination, isAccessible, optional
// excludedAttributes) and shows it as the view's current trace.
export function drawNavigation(request) {
  if (!venue || !view) return;
  try {
    clearNavigationTrace();
    const navigation = venue.computeNavigation({
      type: 'fastest',
      firstNodeAsIntersection: false,
      mergeFloorChangeInstructions: false,
      ...request,
    });
    currentNavigationTrace = venue.createNavigationTrace(navigation);
    view.setCurrentNavigationTrace(currentNavigationTrace);
    notify('onNavigationComputed');
  } catch (error) {
    notify('onNavigationError', String(error?.message ?? error));
  }
}

function clearNavigationTrace() {
  if (!view || !currentNavigationTrace) return;
  currentNavigationTrace = null;
  view.removeCurrentNavigationTrace();
}

export const mapBridge = {
  // Computes a route between two POIs (given by id — `computeNavigation`
  // accepts a POI, a POI id string, or a Position, see the SDK's
  // NavigationRequest typings; ids are used here since that's all the native
  // From/To fields collect) and draws it on the map via a NavigationTrace.
  // Any previous trace is removed first so consecutive itineraries don't
  // stack. A bad/unreachable id makes `computeNavigation` throw (the SDK's
  // InvalidNavigationRequestError) — caught and reported back to native
  // (`onNavigationError`) so the panel can show what went wrong instead of
  // leaving the map silently unchanged. `onNavigationComputed` fires on
  // success so native can clear a previously shown error.
  computeNavigation(origin, destination, isAccessible) {
    drawNavigation({ origin, destination, isAccessible });
  },
  // Removes the trace drawn by computeNavigation, if any — a no-op
  // otherwise (e.g. called before any itinerary was computed).
  // view.removeCurrentNavigationTrace() is the only call needed: it also
  // unregisters the trace, so a following venue.removeNavigationTrace() on it
  // throws "Cannot remove Navigation as it is unregistered" (and leaves the
  // view unable to show another trace).
  clearNavigation() {
    clearNavigationTrace();
  },
};
