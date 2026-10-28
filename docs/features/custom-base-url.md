# Custom map server

## Description

Points the SDK at a different map server than the Visioglobe SaaS default, via `LoadOptions.baseURL`. Demonstrates that the SDK isn't locked to Visioglobe's own infrastructure — useful for clients with data-sovereignty or on-premise hosting requirements.

## SDK usage

`baseURL` is a `loadVenue` option, not a live property — it only takes effect when the venue is (re)loaded, so there is no `venue`/`view` setter to call on an already-loaded map. This demo re-navigates the WebView to re-run the whole load sequence with the new value:

```js
// web/src/features/custom-base-url.js — the Android app appends ?baseUrl=<value>
// when it loads this page; every other screen sends the SDK's own default.
const DEFAULT_BASE_URL = 'https://mapserver.visioglobe.com/';

export const baseURL = new URLSearchParams(window.location.search).get('baseUrl') || DEFAULT_BASE_URL;
```
```js
// web/src/main.js — the value is passed to loadVenue
async function loadAndShowVenue(loadOptions, loaderContainer) {
  try {
    const visioOne = createVisioOne();
    setVenue(await visioOne.loadVenue(loadOptions, loaderContainer));
    // ... createView
    notify('onMapReady');
  } catch (error) {
    // ...
    notify('onMapError', String(error?.message ?? error));
  }
}

function main() {
  // ... (auth-token's protected map aside)
  return loadAndShowVenue({ vg_maphash: hash, baseURL });
}
```

Native call site — the "Reload" button doesn't call anything on an existing venue/view; it sets a new `baseUrl` on the screen's map state, which forces the WebView to be recreated from scratch against a new `?baseUrl=` query param:

```kotlin
// app/src/main/java/com/visioglobe/visioonemeet/features/CustomBaseUrl.kt
Button(onClick = { map.reloadWithBaseUrl(baseUrl.trim()) }, enabled = baseUrl.isNotBlank()) { /* ... */ }
```
```kotlin
// app/src/main/java/com/visioglobe/visioonemeet/bridge/MapWebView.kt
fun pageUrl(): String {
    val query = if (isProtected) {
        "?protected=true"
    } else {
        "?vg_maphash=$mapHash&baseUrl=${URLEncoder.encode(baseUrl, "UTF-8")}" + /* ... */
    }
    return "https://$ASSET_LOADER_DOMAIN/assets/www/index.html$query"
}
```
```kotlin
// app/src/main/java/com/visioglobe/visioonemeet/ui/FeatureMapScreen.kt
val pageOptions = MapPageOptions(mapHash = mapHash, baseUrl = map.baseUrl, /* ... */)
key(pageOptions, protectedMap) {
    AndroidView(
        factory = { context ->
            // ...
            createMapWebView(context, pageOptions, bridge).also { map.webView = it }
        },
        onRelease = { it.destroy() },
    )
}
```

Changing `baseUrl` gives the `key()` block a new identity, so Compose disposes the old `WebView` and runs `factory` again — the same "recreate everything" idiom every feature screen already uses on first entry, just re-triggered on demand.

## Things to know

- **`baseURL` cannot be changed on an already-loaded venue.** It is only read once, when `loadVenue` runs — there is no equivalent of `setCurrentLocale`/`setUIPartVisible` for it. Trying a different server means reloading the venue from scratch.
- **An invalid or unreachable `baseURL` makes `loadVenue` reject with a `VenueNotFoundError`**, per the SDK's own documented behavior (the same error thrown for a bad map hash) — a plain, catchable `Error` subclass, not a hang. This demo surfaces it exactly like any other load failure, via `onMapError`.
- **There is no publicly reachable second Visioglobe map server to demonstrate against.** The default value (`https://mapserver.visioglobe.com/`) reloading successfully still proves the parameter is genuinely wired through — it's the exact value the SDK would use anyway if the parameter were omitted entirely.

## Learn more

- `LoadOptions` also has an `authorizationToken` field for a protected map server — see [auth-token.md](auth-token.md) (code sample). Unlike `baseURL`, the token can be replaced on a loaded venue via `venue.setAuthorizationToken()`.
