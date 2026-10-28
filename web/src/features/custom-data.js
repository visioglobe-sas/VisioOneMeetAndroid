// custom-data: reads the business key/value data attached to a POI. See
// docs/features/custom-data.md.
import { notify } from '../bridge.js';
import { venue } from '../context.js';
import { findPoi } from '../shared/poi.js';

export const mapBridge = {
  // Reloads CustomData from the server (venue.refreshCustomData — the cache
  // starts empty and is never refreshed on its own, see loadVenue) and then
  // reads the given POI's CustomData (venue.getPOICustomData), reporting the
  // result back to native via AndroidBridge.onCustomDataLoaded in one round
  // trip, same requestId-echo convention as resolvePositions. The
  // payload is `null` when placeId doesn't resolve to a POI at all — a case
  // getPOICustomData itself can't distinguish, since it always returns `{}`
  // (never null/undefined) whether the POI has no CustomData or the cache
  // hasn't been refreshed yet.
  //
  // refreshCustomData() itself can *reject* — not just resolve to an empty
  // cache — when the venue has no CustomData published on the server yet
  // (confirmed live: it 404s instead of resolving empty). That is still a
  // normal "no data" outcome for this demo, not an error, so the rejection
  // is swallowed here rather than propagated: getPOICustomData() already
  // degrades gracefully to `{}` against an unrefreshed/empty cache, so the
  // POI lookup below still produces the correct "no custom data" result
  // instead of leaving the bridge call (and the native UI waiting on it)
  // hanging forever on an unhandled rejection.
  async loadCustomData(requestId, placeId) {
    if (!venue) return;
    try {
      await venue.refreshCustomData();
    } catch (error) {
      console.warn('refreshCustomData failed, treating as no data available', error);
    }
    const poi = findPoi(placeId);
    const customData = poi ? venue.getPOICustomData(poi) : null;
    notify('onCustomDataLoaded', requestId, JSON.stringify(customData));
  },
};
