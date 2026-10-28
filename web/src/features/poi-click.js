// poi-click: forwards the SDK's 'poiclick' event to the native side, so a
// screen can react to the user tapping a POI on the map. The payload is a
// JSON-encoded array (usually a single entry) of { id, name }, name being
// resolved via the venue's Translator for the venue's currentLocale so the
// native side never has to deal with raw/untranslated POI data. See
// docs/features/poi-click.md.
import { notify } from '../bridge.js';
import { venue, view } from '../context.js';

function onPoiClick(event) {
  if (!venue) return;
  const locale = venue.currentLocale;
  const pois = event.pois.map((poi) => ({
    id: poi.id,
    name: venue.translator.translatePOI(poi, locale).name,
  }));
  notify('onPoiClick', JSON.stringify(pois));
}

export function onViewCreated() {
  view.addEventListener('poiclick', onPoiClick);
}
