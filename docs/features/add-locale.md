# Add Locale

## Description

Adds a brand-new locale at runtime — `'es'` (Spanish), never authored anywhere in VisioMapEditor for this map — via `venue.translator.addLocale(locale, resources)`, then reads two of its keys straight back via `venue.translator.translate(key, locale)` to prove the round trip worked. The fixed dictionary passed to `addLocale` mixes one of the SDK's own predefined UI keys (`'search-for-anything'`) with one custom, app-defined key (`'welcome-message'`, meaningless to the SDK itself) to demonstrate both use cases: overriding a built-in string, and using the same store for arbitrary app-side i18n.

## SDK usage

```js
// web/src/main.js
const ADD_LOCALE_RESOURCES = {
  'search-for-anything': 'Busca lo que quieras',
  'welcome-message': '¡Bienvenido al mapa!',
};

addSpanishLocale(requestId) {
  if (!venue) return;
  try {
    venue.translator.addLocale('es', ADD_LOCALE_RESOURCES);
    const translations = {};
    Object.keys(ADD_LOCALE_RESOURCES).forEach((key) => {
      translations[key] = venue.translator.translate(key, 'es');
    });
    bridge?.onAddLocaleResolved(requestId, JSON.stringify({ status: 'ok', translations }));
  } catch (error) {
    bridge?.onAddLocaleResolved(
      requestId,
      JSON.stringify({ status: 'error', message: String(error?.message ?? error) }),
    );
  }
},
```

Native call site:

```kotlin
// FeatureOverlays.kt
private fun WebView.addSpanishLocale(requestId: Int) {
    val script = "window.MapBridge.addSpanishLocale($requestId)"
    evaluateJavascript(script, null)
}
```

The result comes back asynchronously through the native bridge, echoing `requestId`, same convention as `runtime-locale.md`'s `getCurrentLocale`/`setLocale`:

```kotlin
// FeatureMapScreen.kt
@JavascriptInterface
fun onAddLocaleResolved(requestId: Int, resultJson: String) = notifyAddLocaleResolved(requestId, resultJson)
```

`venue.translator.addLocale(locale: string, resources: Resources): void` creates (or overwrites) a locale, where `Resources` is a flat `{[key: string]: string}` map. `venue.translator.translate(key: string, locale: string, context?): string` reads a value back for a given locale. The secondary "Switch to Spanish" button in this demo reuses `venue.setCurrentLocale('es')` (`runtime-locale`'s own call) verbatim, with no changes.

## Things to know

- **`addLocale` never touches POI/label/floor/building names.** It is backed by a generic [i18next](https://www.i18next.com/) resource bundle that is completely separate from the venue's own POI/floor/building/category translation data — the latter is parsed once at load from the published map's own JSON and exposed only through `translatePOI`/`translateFloor`/`translateBuilding`/`translateCategory`. Adding or overriding a locale via `addLocale` can never rename a POI or a floor on the map, no matter which key is used.
- **What `addLocale` *can* affect**: (a) the SDK's own predefined UI/Navigation strings, if the key you pass happens to match one of the exact keys listed in `addLocale`'s own TSDoc (e.g. `'search-for-anything'`, `'go'`, `'cancel'`, `'turnRight'`, `'changeFloor'`, …) — visible only if the corresponding SDK UI part is on screen and the locale is made active via `setCurrentLocale`; and (b) any other key, which is just as valid and entirely up to the calling app to define and read back — a fully generic key/value i18n store an integrator can reuse for their own strings, independent of anything SDK-visible.
- **`translate()` is the reliable proof, not the SDK's own UI.** Since a demo map may not have any of the SDK's default UI parts visible (or the visitor may never notice a one-word UI string change), this feature reads each key back immediately via `translator.translate(key, locale)` right after `addLocale` — that is the primary, always-working confirmation the round trip succeeded, regardless of what's rendered on screen.
- **Not saved across reloads.** Per `addLocale`'s own TSDoc, a locale added at runtime is not persisted — it must be re-added every time the venue/view is recreated (in this app, every screen navigation recreates the WebView from scratch, so this is a non-issue in practice, just worth knowing for a differently-structured app).
- **Complements, but is distinct from, `runtime-locale`.** `runtime-locale` switches between locales already authored for the venue in VisioMapEditor (`'en'`/`'fr'` on this repo's shared map) — it can change POI/label text because those locales actually have `LocaleEntry` data for them. `add-locale` instead adds a locale VisioMapEditor never knew about; `setCurrentLocale('es')` still works on it (see this demo's secondary button), but POI/label names fall back to `'default'` since this map has no `LocaleEntry` data for `'es'` — only the generic i18next keys added via `addLocale` actually change.

## Learn more

- [`runtime-locale.md`](./runtime-locale.md) — switching between locales already authored on the venue, the complementary scenario to this one.
- `Translator` also exposes `removeLocale(locale)` (undo an `addLocale`) and `getLocale(locale): Resources` (read back a locale's whole resource map at once) — both used by this SDK interface, neither built into this demo's UI.
