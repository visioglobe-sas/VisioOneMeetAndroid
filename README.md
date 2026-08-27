# VisioOne Meet Android

An Android app (Kotlin + Jetpack Compose) that displays a [VisioOne](https://my.visioglobe.com/docs/VisioOne/docs/) indoor map inside a `WebView`. VisioOne is a JS/WebGL SDK (`@visioglobe/visioone` on npm) — there's no native Android SDK for it, so the map is embedded via a small bundled web page rather than a native map view.

Use this repo as a starting point for embedding VisioOne in your own native Android app: it demonstrates the WebView/asset-loader setup, the native↔JS bridge, and a growing set of individual SDK features (see below).

## Setup

### Prerequisites

- Android Studio / the Android SDK, with `compileSdk` 36 installed.
- Node.js and npm, to build the web bundle.
- A VisioOne map hash from [my.visioglobe.com](https://my.visioglobe.com) — a 41-character alphanumeric ID for a map that has been "built" on the portal. Map assets load at runtime from `mapserver.visioglobe.com`, so a device/emulator needs internet access (the first cold load takes ~20-30s while 3D assets download).

### Configure your map

Set your own map hash in `app/src/main/java/com/visioglobe/visioonemeet/MainActivity.kt`:

```kotlin
private const val DEFAULT_MAP_HASH = "kbae8e6c066cca4b02c2afac2bc963a643d87437a"
```

The hash is passed to the web bundle as a URL query param (`index.html?hash=...`), so switching maps only means changing this constant — it does not require rebuilding the web bundle.

The [Custom Data](docs/features/custom-data.md) screen is the one exception: it always loads its own dedicated map hash regardless of `DEFAULT_MAP_HASH`, because the venue above has no CustomData published — see that feature's doc for why and which hash.

### Build, install & run

```bash
./gradlew installDebug
```

### Update the web bundle

The web bundle (`app/src/main/assets/www/`) is checked into the repo, not generated at Android build time — Gradle never rebuilds it for you. Rebuild it manually whenever you change `web/src/main.js` or bump the SDK version in `web/package.json`:

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

## How it works

The VisioOne SDK is bundled once with Vite (`web/`), and the build output is copied into `app/src/main/assets/www/`. The Android app serves these files inside a `WebView` via `WebViewAssetLoader` (origin `https://appassets.androidx.local/...`), which avoids the CORS issues that come from loading ES modules over `file://`.

A JS bridge (`window.AndroidBridge`, see `web/src/main.js`) notifies the Compose UI when the map is ready (`onMapReady`) or has failed (`onMapError`), driving a loading/error overlay while the map loads.

## Repo structure

```
app/                    Android project (Compose)
  src/main/assets/www/  Generated web bundle (see web/), served by the WebView
web/                    Small Vite project that imports @visioglobe/visioone
docs/features/          Per-feature SDK reference docs (see Features above)
```
