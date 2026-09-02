# Custom map server

## Description

Points the SDK at a different map server than the Visioglobe SaaS default, via `LoadOptions.baseURL`. Demonstrates that the SDK isn't locked to Visioglobe's own infrastructure — useful for clients with data-sovereignty or on-premise hosting requirements.

## SDK usage

`baseURL` is a `loadVenue` option, not a live property — it only takes effect when the venue is (re)loaded, so there is no `venue`/`view` setter to call on an already-loaded map. This demo re-navigates the WebView to re-run the whole load sequence with the new value:

```js
// The Android app appends ?baseUrl=<value> when it loads this page — every
// screen except this one omits it, so it always resolves to the SDK's own
// real default.
const DEFAULT_BASE_URL = 'https://mapserver.visioglobe.com/';
const baseURL = new URLSearchParams(window.location.search).get('baseUrl') || DEFAULT_BASE_URL;

async function main() {
  try {
    const visioOne = createVisioOne();
    venue = await visioOne.loadVenue({ hash, baseURL });
    view = await visioOne.createView(container, venue);
    // ...
    bridge?.onMapReady();
  } catch (error) {
    bridge?.onMapError(String(error?.message ?? error));
  }
}
```

Native call site — the "Reload" button doesn't call anything on an existing venue/view; it hands `FeatureMapScreen` a new value, which forces the WebView to be recreated from scratch against a new `?baseUrl=` query param:

```kotlin
key(mapHash, currentBaseUrl) {
    AndroidView(
        factory = { context ->
            // ...
            val encodedBaseUrl = URLEncoder.encode(currentBaseUrl, "UTF-8")
            loadUrl(
                "https://$ASSET_LOADER_DOMAIN/assets/www/index.html" +
                    "?hash=$mapHash&baseUrl=$encodedBaseUrl",
            )
        }.also { webView = it }
    )
}
```

Changing `currentBaseUrl` gives the `key()` block a new identity, so Compose disposes the old `WebView` and runs `factory` again — the same "recreate everything" idiom every feature screen already uses on first entry, just re-triggered on demand.

## Things to know

- **`baseURL` cannot be changed on an already-loaded venue.** It is only read once, when `loadVenue` runs — there is no equivalent of `setCurrentLocale`/`setUIPartVisible` for it. Trying a different server means reloading the venue from scratch.
- **An invalid or unreachable `baseURL` makes `loadVenue` reject with a `VenueNotFoundError`**, per the SDK's own documented behavior (the same error thrown for a bad map hash) — a plain, catchable `Error` subclass, not a hang. This demo surfaces it exactly like any other load failure, via `onMapError`.
- **There is no publicly reachable second Visioglobe map server to demonstrate against.** The default value (`https://mapserver.visioglobe.com/`) reloading successfully still proves the parameter is genuinely wired through — it's the exact value the SDK would use anyway if the parameter were omitted entirely.

## Learn more

- `LoadOptions` also has an `authorizationToken` field for a protected map server — the same "reload from scratch to change it" caveat applies, not demonstrated in this repo.
