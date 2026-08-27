# Runtime Locale

## Description

Switches the map's displayed language at runtime — POI/label names, and any on-map SDK UI (search, POI details, navigation instructions, etc.) — without reloading the WebView or republishing the map in VisioMapEditor. Built entirely on the venue-level locale API: `venue.currentLocale` (readonly) and `venue.setCurrentLocale(locale)`.

## SDK usage

```js
// web/src/main.js

// Reports the venue's currentLocale back to native in one round trip, so the
// runtime-locale sheet can highlight whichever option is already active when
// it opens. There is no locale-change event to listen for instead, so this
// is only ever called once, right when the sheet is shown. Reuses the
// { status, locale } response shape of setLocale below so native can share
// one response parser for both.
getCurrentLocale(requestId) {
  if (!venue) return;
  bridge?.onLocaleResolved(requestId, JSON.stringify({ status: 'ok', locale: venue.currentLocale }));
},

// Switches the map's displayed language via venue.setCurrentLocale, which
// re-renders POI/label names (and the current View's UI/Navigation) with the
// new locale by itself — no manual re-fetch of POI data needed.
async setLocale(requestId, locale) {
  if (!venue) return;
  try {
    await venue.setCurrentLocale(locale);
    bridge?.onLocaleResolved(requestId, JSON.stringify({ status: 'ok', locale: venue.currentLocale }));
  } catch (error) {
    bridge?.onLocaleResolved(
      requestId,
      JSON.stringify({ status: 'error', message: String(error?.message ?? error) }),
    );
  }
},
```

Native call sites:

```kotlin
// FeatureOverlays.kt
private fun WebView.getCurrentLocale(requestId: Int) {
    val script = "window.MapBridge.getCurrentLocale($requestId)"
    evaluateJavascript(script, null)
}

private fun WebView.setLocale(requestId: Int, locale: String) {
    val script = "window.MapBridge.setLocale($requestId, ${JSONObject.quote(locale)})"
    evaluateJavascript(script, null)
}
```

Both calls' results come back asynchronously on the native side through the same callback, echoing `requestId`, same convention as `custom-data.md`'s `loadCustomData`:

```kotlin
// FeatureMapScreen.kt
@JavascriptInterface
fun onLocaleResolved(requestId: Int, resultJson: String) =
    notifyLocaleResolved(requestId, resultJson)
```

`venue.currentLocale: string` (readonly) is the property read; `venue.setCurrentLocale(locale: string): Promise<void>` is the only mutating call. Available locales for a venue can be read from `venue.translator.allLocales: string[]`.

## Things to know

- **`setCurrentLocale` is Promise-based.** It returns `Promise<void>`, not a synchronous change — always wait for it to resolve before treating the switch as applied, same "wait for the async result" idiom as `custom-data.md`'s `refreshCustomData()`/`loadCustomData`.
- **No manual re-fetch of POI data is needed after switching.** Per the SDK's own TSDoc on `Venue.currentLocale`: "Labels will be displayed with 'text' field corresponding to their POI's LocaleEntry when it exists... When a View exists, each UI item (and the current Navigation) will use this locale to be displayed." The SDK re-renders labels and any live UI/Navigation itself as soon as `setCurrentLocale` resolves.
- **`setCurrentLocale` does not validate its argument against `allLocales`.** In the SDK version vendored by this repo, `setCurrentLocale` simply assigns the given string as the new `currentLocale` and re-renders — it never rejects, even for a locale the venue has no data for. Per the same TSDoc, a POI with no matching `LocaleEntry` for that locale just displays an empty label text rather than falling back to another locale or throwing. In practice this means an integrator should validate a locale against `venue.translator.allLocales` on their own side before calling `setCurrentLocale`, since the SDK won't do it for them.
- **`allLocales[0]` is the initial `currentLocale`.** A freshly loaded venue's `currentLocale` starts out as `venue.translator.allLocales[0]`, not necessarily a locale an integrator would consider "default" for display purposes — worth reading explicitly (e.g. via the `getCurrentLocale` round trip above) rather than assuming a particular starting value.
- **`allLocales` never includes `'default'`.** The SDK's `Translator.allLocales` getter explicitly filters out the `'default'` key from the map's underlying locale resources (confirmed by reading the SDK's own source, `Translator.parseLocales`) — so on this repo's shared demo map, `allLocales` is `['en', 'fr']`, not three entries. `'default'` is nonetheless a working value for `setCurrentLocale('default')`, and on this map it renders byte-identical POI/label text to `'fr'` (both are French) — confirmed directly against the published map payload. This demo hardcodes the two genuinely distinct choices, `en` and `fr`, rather than populating its language list from `allLocales` at runtime; an integrator working against a different venue should verify what each entry in that venue's own `allLocales` actually resolves to before building a locale switcher UI from it directly.

## Learn more

- `category-highlight.md` and `custom-data.md` both use the same `requestId`-echoed native↔JS round-trip pattern used here for `getCurrentLocale`/`setLocale`.
- `poi-click.md` and `floor-selector.md` both resolve display names via `venue.translator` (`translatePOI`/`translateFloor`) for the venue's `currentLocale` — the same `Translator` this feature switches the active locale of.
