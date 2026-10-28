// add-locale: adds a brand-new language at runtime. Switching to it uses
// runtime-locale's setLocale. See docs/features/add-locale.md.
import { notify } from '../bridge.js';
import { venue } from '../context.js';

// Fixed key/value dictionary demonstrated by add-locale: one predefined SDK
// UI key ('search-for-anything', overriding a string the SDK's own default
// UI would otherwise show) and one custom, app-defined key
// ('welcome-message', meaningless to the SDK itself — just a demo that the
// store is a general-purpose one, usable for the app's own strings too).
const ADD_LOCALE_RESOURCES = {
  'search-for-anything': 'Busca lo que quieras',
  'welcome-message': '¡Bienvenido al mapa!',
};

export const mapBridge = {
  // Demonstrates venue.translator.addLocale as a fully generic, i18next-backed
  // key/value store, entirely separate from the venue's own POI/floor/
  // building/category translation data (parsed once at load from the
  // published map's own JSON and never touched by addLocale/translate) — see
  // docs/features/add-locale.md, "Things to know". Adds 'es' (never authored
  // in VisioMapEditor for this map) with ADD_LOCALE_RESOURCES above, then
  // reads each key straight back via translator.translate so the round trip
  // is provable without depending on any of the SDK's own UI parts being
  // visible. addLocale itself is synchronous and, per its own TSDoc,
  // undocumented to throw — the try/catch here is kept for
  // forward-compatibility only, same defensive idiom as setLocale.
  addSpanishLocale(requestId) {
    if (!venue) return;
    try {
      venue.translator.addLocale('es', ADD_LOCALE_RESOURCES);
      const translations = {};
      Object.keys(ADD_LOCALE_RESOURCES).forEach((key) => {
        translations[key] = venue.translator.translate(key, 'es');
      });
      notify('onAddLocaleResolved', requestId, JSON.stringify({ status: 'ok', translations }));
    } catch (error) {
      notify(
        'onAddLocaleResolved',
        requestId,
        JSON.stringify({ status: 'error', message: String(error?.message ?? error) }),
      );
    }
  },
};
