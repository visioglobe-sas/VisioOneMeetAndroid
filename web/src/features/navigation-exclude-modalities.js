// navigation-exclude-modalities: a compute-navigation variant whose route
// avoids the elevator. See docs/features/navigation-exclude-modalities.md.
import { drawNavigation } from './compute-navigation.js';

export const mapBridge = {
  // Same as computeNavigation, plus an `excludedAttributes` list — segment
  // particularities the computed route must avoid. On this shared demo venue
  // the elevator segment's attribute string is 'lift' (NOT 'elevator', despite
  // the wording of the SDK's own doc comment on `excludedAttributes` —
  // confirmed live). An empty array (excludeElevator === false) behaves
  // exactly like a plain computeNavigation call. A route that only exists
  // through the excluded attribute fails like any other unreachable pair
  // (InvalidNavigationRequestError → onNavigationError).
  computeNavigationExcludingModalities(origin, destination, isAccessible, excludeElevator) {
    drawNavigation({ origin, destination, isAccessible, excludedAttributes: excludeElevator ? ['lift'] : [] });
  },
};
