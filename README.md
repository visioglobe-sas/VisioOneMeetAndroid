# VisioOne Meet Android

An Android app (Kotlin + Jetpack Compose) that displays a [VisioOne](https://my.visioglobe.com/docs/VisioOne/docs/) indoor map inside a `WebView`. VisioOne is a JS/WebGL SDK (`@visioglobe/visioone` on npm) — there's no native Android SDK for it, so the map is embedded via a small bundled web page rather than a native map view.

Use this repo as a starting point for embedding VisioOne in your own native Android app: it demonstrates the WebView/asset-loader setup, the native↔JS bridge, and a growing set of individual SDK features (see below).

## Setup

### Prerequisites

- Android Studio / the Android SDK, with `compileSdk` 36 installed.
- Node.js and npm, to build the web bundle.
- A VisioOne map hash from [my.visioglobe.com](https://my.visioglobe.com) — a 41-character alphanumeric ID for a map that has been "built" on the portal. Map assets load at runtime from `mapserver.visioglobe.com`, so a device/emulator needs internet access (the first cold load takes ~20-30s while 3D assets download).

### Configure your map

Set your own map hash in `app/src/main/java/com/visioglobe/visioonemeet/model/DemoMap.kt`:

```kotlin
const val MAP_HASH = "kbae8e6c066cca4b02c2afac2bc963a643d87437a"
```

The same file holds every demo POI id the feature screens prefill their input fields with (`GOTO_POI_ID`, `ITINERARY_FROM`/`ITINERARY_TO`, `TRACKING_FROM`/`TRACKING_TO`, `GEOFENCE_ZONE_ID`, `ANCHOR_POI_ID`, `CAROUSEL_STOP_IDS`...): they are ids of Visioglobe's shared demo map, so replace them with ids of your own map too.

The hash is passed to the web bundle as a URL query param (`index.html?vg_maphash=...`, the same name as the SDK's `LoadOptions.vg_maphash`), so switching maps only means changing this constant — it does not require rebuilding the web bundle.

The [Custom Data](docs/features/custom-data.md) screen is the one exception: it always loads its own dedicated map hash regardless of `MAP_HASH` (`CUSTOM_DATA_MAP_HASH`, same file), because the venue above has no CustomData published — see that feature's doc for why and which hash.

### Build, install & run

```bash
./gradlew installDebug
```

### Update the web bundle

The web bundle (`app/src/main/assets/www/`) is checked into the repo, not generated at Android build time — Gradle never rebuilds it for you. Rebuild it manually whenever you change anything under `web/src/` or bump the SDK version in `web/package.json`:

```bash
cd web
npm install
npm run build   # writes directly into ../app/src/main/assets/www
```

## Features

Each feature below is a self-contained screen in the app demonstrating one VisioOne SDK capability. Every doc focuses on the SDK call itself — signature, behavior, gotchas — rather than this app's UI plumbing.

- [Reset View](docs/features/reset-view.md) — recenter the camera on the whole venue via `view.goToGlobal()`.
- [Occupancy Overlay (Simulated Data)](docs/features/occupancy-simulated.md) — color a POI's surfaces to reflect a live occupancy status via `venue.updateSurface()`.
- [POI Click](docs/features/poi-click.md) — react natively to a tap on a POI via the SDK's `poiclick` event.
- [Go to POI](docs/features/goto-poi.md) — center the camera on a POI by id via `view.goToPOI()` and highlight its surfaces.
- [Floor Selector](docs/features/floor-selector.md) — drive floor changes from a native UI via `view.goToFloor()`, in sync with the `currentfloorchanged` event.
- [Compute Navigation](docs/features/compute-navigation.md) — compute and draw a route between two POIs via `venue.computeNavigation()`.
- [UI Part Visibility](docs/features/ui-part-visibility.md) — toggle individual pieces of the SDK's default on-map UI via `view.setUIPartVisible()`.
- [Native UI Replacement](docs/features/native-ui-replacement.md) — hide the SDK's default floor selector via `view.setUIPartVisible('floorSelector', false)` and drive floors entirely from the app's own native picker.
- [Simulated Position](docs/features/simulated-position.md) — animate a simulated tracked position via `view.injectTrackedPosition()`, the same API a real indoor-positioning integration would use.
- [Camera Lock on Position](docs/features/camera-lock-on-position.md) — lock the camera onto a tracked position via `view.lockCameraPositionOnTracking`.
- [Clickable Surface](docs/features/clickable-surface.md) — make a POI's surface interactive, with the SDK managing hover/selection colors via `venue.updateSurface()`.
- [Custom Data](docs/features/custom-data.md) — read business key/value data attached to a POI via `venue.refreshCustomData()` and `venue.getPOICustomData()`.
- [Category Highlight](docs/features/category-highlight.md) — highlight every POI in a chosen category by combining `venue.categories`/`poi.categories` with `venue.updateSurface()`.
- [Dynamic POI CRUD](docs/features/dynamic-poi-crud.md) — create, update and remove a POI at runtime via `venue.createPOI()`/`venue.createLabel()`/`venue.updateLabel()`/`venue.removePOI()`, without republishing the map.
- [Runtime Locale](docs/features/runtime-locale.md) — switch the map's displayed language live via `venue.setCurrentLocale()`, without reloading or republishing.
- [Explore Mode](docs/features/explore-mode.md) — switch between global, exploded building "carousel" and single-floor views via `view.currentExploreMode`, in sync with the `exploremodechanged` event.
- [Add Locale](docs/features/add-locale.md) — add a brand-new language at runtime via `venue.translator.addLocale()`, never authored in VisioMapEditor for this map.
- [Geofencing](docs/features/geofencing.md) — trigger a visual alert when a simulated tracked position enters a zone defined by a POI's surface, via a hand-rolled point-in-polygon check against `Surface.positions`.
- [Custom Map Server](docs/features/custom-base-url.md) — point the SDK at a different map server than the Visioglobe SaaS default via `LoadOptions.baseURL`, reloading the venue from scratch to apply it.
- [Custom Navigation Trace](docs/features/custom-navigation-trace.md) — restyle the route line drawn by Compute Navigation with a chosen color palette via `venue.updateNavigationTrace()`.
- [Exclude Navigation Modalities](docs/features/navigation-exclude-modalities.md) — compute a route that avoids the elevator via `venue.computeNavigation()`'s `excludedAttributes` option.
- [Accessible Mode](docs/features/accessible-mode.md) — compute a route that avoids stairs via `venue.computeNavigation()`'s `isAccessible` option.
- [3D Icon Marker](docs/features/3d-icon-marker.md) — place a custom image at a spot on the 3D map via `venue.createImage()`, switching its orientation live via `venue.updateImage()` and removing it via `venue.removeImage()`.
- [Custom Text Label](docs/features/custom-text-label.md) — place your own free text on the map via `venue.createLabel()`, restyling it live via `venue.updateLabel()` and removing it via `venue.removeLabel()`.
- [HTML Overlay Marker](docs/features/html-overlay-marker.md) — pin a tappable HTML badge on the map, never hidden by 3D geometry, via `venue.createMarker()`, toggling it via `venue.updateMarker()` and removing it via `venue.removeMarker()`; taps are forwarded to native over the bridge.
- [Custom Theme](docs/features/custom-theme.md) — switch the 3D scene's lighting theme via `view.setTheme()` (built from `view.themes`, view created with `enableShadows: true`) and recolor the SDK's own UI buttons live via `view.accentColor`/`view.textAccentColor`.
- [Accuracy Circle Style](docs/features/accuracy-circle-style.md) — recolor the tracked position's precision circle live via `view.updatePositionTrackerGraphicOptions()`, with the circle's radius set per `view.injectTrackedPosition()` call.
- [Camera Projection](docs/features/camera-projection-toggle.md) — switch between a 3D perspective and a flat orthographic camera via `createView()`'s `cameraProjection` option (creation-time only, read back from `view.cameraProjection`), recreating the map on each switch.
- [Camera Showcase](docs/features/camera-showcase-carousel.md) — run an endless automatic camera tour of the map for kiosks via chained `view.goToFloor()`/`view.goToPOI()` `AnimationPromise`s, pausing in place on any touch (zero-length `view.goToViewpoint()`) and resuming after 10 s idle.
- [Protected Map (token)](docs/features/auth-token.md) — **code sample** (placeholder values, no protected demo map): load a map protected by an authorization token via `loadVenue()`'s `authorizationToken` + `baseURL` options, refresh it via `venue.setAuthorizationToken()`, and report an `UnauthorizedError` apart from other load errors.

## How it works

The VisioOne SDK is bundled once with Vite (`web/`), and the build output is copied into `app/src/main/assets/www/`. The Android app serves these files inside a `WebView` via `WebViewAssetLoader` (origin `https://appassets.androidx.local/...`), which avoids the CORS issues that come from loading ES modules over `file://`. The map is recreated on every feature screen.

The app and the page talk over two channels:

- **Native → JS**: Kotlin calls `window.MapBridge.<command>(...)` through `WebView.evaluateJavascript()`, always via one helper, `WebView.callBridge(method, args...)` (`bridge/CallBridge.kt`). It JSON-encodes every argument with `org.json` (`JSONObject.quote`, `JSONObject`, `JSONArray`) before putting it in the script — never raw string concatenation, which would allow JS injection. `window.MapBridge` is assembled by `web/src/main.js` from the commands each `web/src/features/<slug>.js` exports, each calling the real SDK on the page's `venue`/`view`.
- **JS → native**: the page calls `window.AndroidBridge.<callback>(...)` (`notify(...)` in `web/src/bridge.js`), an object registered with `WebView.addJavascriptInterface()` (class `NativeBridge` in `bridge/NativeBridge.kt`): `onMapReady`/`onMapError` drive the loading and error overlay, and one callback per feature reports results and SDK events (`onPoiClick`, `onNavigationError`, `onCustomDataLoaded`, ...). These callbacks run on a WebView background thread; each one is posted to the main thread as a typed `BridgeEvent` (`bridge/BridgeEvents.kt`), which the feature screens collect. Arguments are strings, numbers and booleans only: structured payloads are sent as JSON strings and parsed on the Kotlin side, by the feature that uses them.
- A command whose result comes back asynchronously passes a `requestId` that the callback echoes, so a stale response is never mistaken for the current one.

Per-feature state (field values, the object placed on the map, a running simulation) is kept at screen level, outside the bottom sheet, which is disposed every time it closes: reopening the panel always shows the map's current state.

If you enable code shrinking (`isMinifyEnabled = true`), keep the `@JavascriptInterface` methods, e.g. `-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }` in `proguard-rules.pro`.

## Where is the code of feature X?

In two files named after the feature's slug, one per side — `docs/features/goto-poi.md` documents:

- `app/src/main/java/com/visioglobe/visioonemeet/features/GoToPoi.kt` — the native side: its `WebView.xxx()` bridge calls, the parsing of its results, its screen-level state, its screen (`GoToPoiScreen`) and the panel shown in the bottom sheet;
- `web/src/features/goto-poi.js` — the JS side: its `window.MapBridge` commands, its view listeners and its own state, calling the SDK.

The Kotlin file name is the slug in PascalCase (`3d-icon-marker` is `IconMarker3d.kt`). A few features reuse another feature's code rather than repeat it, and say so at the top of their file:

- the tracked-position simulation (session, loop, Start/Stop panel) lives in `SimulatedPosition.kt` and is reused by `camera-lock-on-position`, `geofencing` and `accuracy-circle-style`;
- the route itself is `compute-navigation`'s (`ComputeNavigation.kt`, `compute-navigation.js`), reused by `custom-navigation-trace`, `navigation-exclude-modalities` and `accessible-mode` (which has no JS of its own);
- `native-ui-replacement` reuses `ui-part-visibility`'s and `floor-selector`'s calls, `add-locale` reuses `runtime-locale`'s `setLocale`, and `3d-icon-marker`, `custom-text-label` and `html-overlay-marker` reuse `goto-poi`'s;
- `custom-base-url` has no command: it only sets a load option (`web/src/features/custom-base-url.js`), and the screen reloads the map.

## Repo structure

```
app/                                   Android project (Compose)
  src/main/java/com/visioglobe/visioonemeet/
    MainActivity.kt                    Navigation: feature menu -> one screen per feature
    model/DemoMap.kt                   Map hash and demo POI ids (replace with your own)
    model/Feature.kt                   Feature menu registry
    features/<Feature>.kt              One file per feature (see above)
    features/FeatureScreens.kt         Feature -> its screen
    bridge/MapWebView.kt               WebView, WebViewAssetLoader, page URL and load options
    bridge/NativeBridge.kt             JS -> native: the @JavascriptInterface callbacks
    bridge/BridgeEvents.kt             One event per callback, collected by the feature screens
    bridge/CallBridge.kt               Native -> JS: callBridge() and its argument encoding
    ui/FeatureMapScreen.kt             Screen shell: top bar, map, loading/error, FAB, bottom sheet
    ui/FeatureMenuScreen.kt            Feature menu
    ui/components/                     Shared controls: itinerary form, option buttons, color chip
    sample/ProtectedMapSample.kt       auth-token code sample
  src/main/assets/www/                 Generated web bundle (see web/), served by the WebView
web/                                   Small Vite project that imports @visioglobe/visioone
  src/main.js                          Loads the venue, creates the view, assembles window.MapBridge
  src/features/<slug>.js               One module per feature (see above)
  src/bridge.js, src/context.js        notify() to native; the page's live venue/view
  src/shared/                          POI position and point-in-polygon helpers
docs/features/                         Per-feature SDK reference docs (see Features above)
```
