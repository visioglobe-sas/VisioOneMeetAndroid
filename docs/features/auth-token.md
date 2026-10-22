# Protected map (authorization token)

> **Code sample only.** This repo has no protected map to load, so the app's "Protected map (token)" screen only shows a short explanation — it never loads anything. Every value in the sample is a **placeholder** you must replace with your own: the map hash (`YOUR_PROTECTED_MAP_HASH`) and the token (`YOUR_JWT_FROM_YOUR_BACKEND`, which must come from your own backend).

## Description

Loads a map protected by an authorization token: the map is served by Visioglobe's protected map server, which only answers requests carrying a valid token. Built on:

- `visioOne.loadVenue(options, loaderContainer)` with `LoadOptions.authorizationToken` and `LoadOptions.baseURL` (the protected server);
- `venue.setAuthorizationToken(freshToken)` to hand the venue a fresh token before the current one expires;
- the `UnauthorizedError` that `loadVenue` rejects with when the token is invalid, expired or missing.

The sample lives in:

- `app/src/main/java/com/visioglobe/visioonemeet/sample/ProtectedMapSample.kt` — the placeholders, a `ProtectedMapScreen` composable, the refresh entry point;
- `web/src/main.js` — `loadProtectedVenue`, `isUnauthorizedError`, `MapBridge.setAuthorizationToken`;
- `app/src/main/java/com/visioglobe/visioonemeet/ui/FeatureMapScreen.kt` — the two bridge methods (`getProtectedMapOptions`, `onAuthorizationError`).

## SDK usage

### Native side (Kotlin)

The placeholders, defined once where you configure the app:

```kotlin
const val PROTECTED_MAP_HASH = "YOUR_PROTECTED_MAP_HASH"
const val PROTECTED_MAP_BASE_URL = "https://mapserver-auth.visioglobe.com/"
// Issued by YOUR OWN backend (Visioglobe provides the signing secret and the claims to use).
// Never embed the signing secret in the app.
const val PROTECTED_MAP_TOKEN = "YOUR_JWT_FROM_YOUR_BACKEND"

data class ProtectedMapOptions(val mapHash: String, val baseUrl: String, val authorizationToken: String) {
    fun toJson(): String = JSONObject()
        .put("vg_maphash", mapHash)
        .put("baseURL", baseUrl)
        .put("authorizationToken", authorizationToken)
        .toString()
}
```

The page is loaded with `index.html?protected=true` — **no** `vg_maphash` in the URL, because the SDK lets a URL `vg_maphash` override the one in `LoadOptions`. The page then pulls the options synchronously from the object exposed with `addJavascriptInterface(..., "AndroidBridge")`:

```kotlin
@JavascriptInterface
fun getProtectedMapOptions(): String = protectedMapOptionsJson ?: "{}"

@JavascriptInterface
fun onAuthorizationError(message: String) = onAuthorizationError.invoke(message)
```

Token refresh entry point — call it with a fresh token before the current one expires:

```kotlin
fun WebView.refreshAuthorizationToken(freshToken: String) {
    evaluateJavascript("window.MapBridge.setAuthorizationToken(${JSONObject.quote(freshToken)})", null)
}
```

`ProtectedMapScreen` does it on a timer while the map is shown, with a placeholder `fetchTokenFromYourBackend()` you replace with a call to your backend:

```kotlin
LaunchedEffect(webView, isMapReady) {
    if (webView == null || !isMapReady) return@LaunchedEffect
    while (true) {
        delay(45.minutes)
        webView.refreshAuthorizationToken(fetchTokenFromYourBackend())
    }
}
```

### JS side (the page)

```js
import { createVisioOne } from '@visioglobe/visioone';

function loadProtectedVenue({ vg_maphash, baseURL, authorizationToken }) {
  return loadAndShowVenue({ vg_maphash, baseURL, authorizationToken }, container);
}

async function loadAndShowVenue(loadOptions, loaderContainer) {
  try {
    const visioOne = createVisioOne();
    venue = await visioOne.loadVenue(loadOptions, loaderContainer);
    view = await visioOne.createView(container, venue);
    // ...
    bridge?.onMapReady();
  } catch (error) {
    if (isUnauthorizedError(error)) {
      bridge?.onAuthorizationError(String(error?.message ?? error)); // token invalid, expired or missing
    } else {
      bridge?.onMapError(String(error?.message ?? error));
    }
  }
}

// On page start, in protected mode:
loadProtectedVenue(JSON.parse(window.AndroidBridge.getProtectedMapOptions()));

window.MapBridge = {
  setAuthorizationToken(freshToken) {
    if (venue) venue.setAuthorizationToken(freshToken);
  },
};
```

Recognizing the error:

```js
function isUnauthorizedError(error) {
  return (
    error?.constructor?.name === 'UnauthorizedError' ||
    /authorization token/i.test(String(error?.message ?? ''))
  );
}
```

## Things to know

- **Values in this repo are placeholders.** Replace `YOUR_PROTECTED_MAP_HASH` with your protected map's hash — a protected map has its own hash, distinct from public ones — and `YOUR_JWT_FROM_YOUR_BACKEND` with a token obtained from your backend.
- **The token is sent as `Authorization: Bearer <token>`** on every map request the SDK makes. It is only enforced by the protected map server, so it must be paired with `baseURL: 'https://mapserver-auth.visioglobe.com/'`; the default `https://mapserver.visioglobe.com/` does not check tokens.
- **Never embed the JWT signing secret in the app or the page.** Your backend signs the tokens (Visioglobe provides the secret and the claims to use); the app only receives ready-made tokens.
- **Tokens are short-lived** — the protected server accepts a lifetime of at most 1 hour. Requests made after expiry are rejected. Call `venue.setAuthorizationToken(freshToken)` before expiry: it applies to every subsequent request, no reload needed. Refreshing in time is your app's job; the SDK does not do it for you.
- **`UnauthorizedError` cannot be caught with `instanceof`.** SDK 1.1.0 declares the class in its typings but does not export it from the package entry (only `createVisioOne`/`VisioOne`), and it does not set `error.name` (it stays `"Error"`). Identify it by `error.constructor.name === 'UnauthorizedError'` (preserved by this repo's Vite build — check yours if your bundler renames classes) or by its message, `"This venue needs an Authorization token to be loaded"`. The SDK raises it when the server answers **403** to the map's `descriptor.json`.
- **The page origin must be allowlisted (CORS).** The protected server checks the request's `Origin` against a list Visioglobe configures for your account. In this app the page is served by `WebViewAssetLoader`, so its origin is **`https://appassets.androidx.local`** (the domain set in `FeatureMapScreen.kt`) — tell Visioglobe the origin(s) your app uses.
- **A rejected origin looks like a generic error, not `UnauthorizedError`.** The `Authorization` header makes the browser send a CORS preflight (`OPTIONS`) first. If the origin is not allowlisted, the preflight is refused (observed: `403` with no `Access-Control-Allow-Origin`), the SDK logs `VisioOne error: TypeError: Failed to fetch` and `loadVenue` rejects with a plain `Error('Cannot load the venue')`. That is exactly what this sample returns with its placeholder values from `https://appassets.androidx.local`. Only once the origin is accepted does a bad token surface as `UnauthorizedError`.

## Learn more

- [custom-base-url.md](custom-base-url.md) — `LoadOptions.baseURL`, the other half of the protected-map setup.
