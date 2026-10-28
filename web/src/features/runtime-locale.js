// runtime-locale: reads and switches the venue's displayed language. setLocale
// is also used by add-locale. See docs/features/runtime-locale.md.
import { notify } from '../bridge.js';
import { venue } from '../context.js';

export const mapBridge = {
  // Reports the venue's currentLocale back to native in one round trip, so
  // the runtime-locale sheet can highlight whichever option is already
  // active when it opens. There is no locale-change event to listen for
  // instead, so it is called every time the panel opens. Same requestId-echo
  // convention as getCategories/loadCustomData, reusing the { status, locale }
  // shape of setLocale below so native can share one response parser for both.
  getCurrentLocale(requestId) {
    if (!venue) return;
    notify('onLocaleResolved', requestId, JSON.stringify({ status: 'ok', locale: venue.currentLocale }));
  },
  // Switches the map's displayed language via venue.setCurrentLocale, which
  // re-renders POI/label names (and the current View's UI/Navigation) with
  // the new locale by itself — no manual re-fetch of POI data needed, per
  // the SDK's own doc of setCurrentLocale/currentLocale. It is
  // Promise-based, so the resolved locale is only reported back to native
  // (onLocaleResolved, requestId echoed) once the switch actually completes,
  // same "wait for the async result" idiom as loadCustomData/
  // createDynamicPoi. In this SDK version setCurrentLocale never rejects —
  // it does not validate locale against venue.translator.allLocales, it just
  // sets currentLocale and re-renders — but the try/catch is kept for
  // forward-compatibility, same defensive pattern as computeNavigation.
  async setLocale(requestId, locale) {
    if (!venue) return;
    try {
      await venue.setCurrentLocale(locale);
      notify('onLocaleResolved', requestId, JSON.stringify({ status: 'ok', locale: venue.currentLocale }));
    } catch (error) {
      notify(
        'onLocaleResolved',
        requestId,
        JSON.stringify({ status: 'error', message: String(error?.message ?? error) }),
      );
    }
  },
};
