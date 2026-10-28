# Camera projection

## Description

Switches the map between the default 3D **perspective** camera and a flat **orthographic** one, via the `cameraProjection` option of `visioOne.createView()`. The projection the view actually uses is read back from `view.cameraProjection`.

The projection can only be chosen when the view is created, so switching it means recreating the map: this demo reloads the WebView page with a query parameter that its web bundle maps to the `createView` option, which runs a fresh `loadVenue` + `createView` on every switch.

## SDK usage

JS side (`web/src/features/camera-projection-toggle.js`): read the requested projection from the page URL and pass it to `createView`, then report the projection the view was created with:

```js
// web/src/features/camera-projection-toggle.js
// Our own query parameter, NOT the SDK's `?orthographic` flag (see "Things to know").
const requestedCameraProjection = new URLSearchParams(window.location.search).get('cameraProjection');
const cameraProjection = requestedCameraProjection === 'orthographic' ? 'orthographic' : undefined; // undefined = SDK default 'perspective'

// This feature's createView options (merged by main.js).
export function viewOptions() {
  return cameraProjection ? { cameraProjection } : {};
}

// Read-only: the projection this view really uses.
export function onViewCreated() {
  notify('onCameraProjectionReported', view.cameraProjection);
}
```

```js
// web/src/main.js — every feature's viewOptions() is merged into the createView options
setVenue(await visioOne.loadVenue(loadOptions, loaderContainer));
const viewOptions = Object.assign({}, ...FEATURES.map((feature) => feature.viewOptions?.() ?? {}));
setView(
  await visioOne.createView(
    container,
    venue,
    Object.keys(viewOptions).length > 0 ? viewOptions : undefined,
  ),
);
FEATURES.forEach((feature) => feature.onViewCreated?.());
notify('onMapReady');
```

Signatures (SDK 1.1.0 typings):

- `ViewCreateOptions.cameraProjection?: 'perspective' | 'orthographic'`, default `'perspective'`.
- `View.cameraProjection: 'perspective' | 'orthographic'` (readonly).

Native side (Kotlin): there is no bridge call that changes the projection. Switching rebuilds the `WebView` against a new URL, and the reported value comes back through a `@JavascriptInterface` method:

```kotlin
// app/src/main/java/com/visioglobe/visioonemeet/bridge/MapWebView.kt — the page URL
fun pageUrl(): String {
    val query = if (isProtected) {
        "?protected=true"
    } else {
        "?vg_maphash=$mapHash&baseUrl=${URLEncoder.encode(baseUrl, "UTF-8")}" +
            (if (enableShadows) "&enableShadows=true" else "") +
            if (cameraProjection != DEFAULT_CAMERA_PROJECTION) "&cameraProjection=$cameraProjection" else ""
    }
    return "https://$ASSET_LOADER_DOMAIN/assets/www/index.html$query"
}

// app/src/main/java/com/visioglobe/visioonemeet/ui/FeatureMapScreen.kt — one WebView per page URL
val pageOptions = MapPageOptions(
    mapHash = mapHash,
    baseUrl = map.baseUrl,
    enableShadows = enableShadows,
    cameraProjection = map.cameraProjection,
    isProtected = protectedMap != null,
)
key(pageOptions, protectedMap) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val bridge = NativeBridge(/* ... */ onEvent = map::emit)
            createMapWebView(context, pageOptions, bridge).also { map.webView = it }
        },
        onRelease = { it.destroy() }, // don't leave the previous page (and its WebGL context) alive
    )
}

// app/src/main/java/com/visioglobe/visioonemeet/bridge/NativeBridge.kt
@JavascriptInterface
fun onCameraProjectionReported(projection: String) = emit(BridgeEvent.CameraProjectionReported(projection))
```

When `map.cameraProjection` changes (`map.reloadWithCameraProjection(newProjection)`), the page options, and so the `key`, change too. Compose then disposes of the old `WebView` and creates a new one, which loads the venue and creates the view again with the new option.

## Things to know

- **The projection is set when the view is created and can't be changed afterwards.** `cameraProjection` is only read by `createView`. `view.cameraProjection` is read-only and only reports the projection the view was created with. The SDK has no setter and no way to switch at runtime.
- **The SDK's own `?orthographic` page-URL parameter wins over the option.** The SDK reads a bare `?orthographic` flag from the page URL, and the flag only has to be present. It resolves options in the order "page URL > `createView` option > default", so if that flag is on the page URL, the view is orthographic whatever you pass to `createView`. It can be used instead of the option, for example to force orthographic with no code change. This demo uses its own `cameraProjection` parameter instead, so that the code decides the projection explicitly and `view.cameraProjection` always matches what the app asked for.
- **To recreate the view in the same page, unload and reload the venue too.** If you call `await visioOne.destroyView(view)` and then `visioOne.createView(container, sameVenue, { cameraProjection })` on the same venue, it fails: an unhandled `Error: Cannot fetch model` is thrown and the returned promise never resolves, so the map stays black. This sequence works:
  ```js
  await visioOne.destroyView(view);
  await visioOne.unloadVenue(venue);
  venue = await visioOne.loadVenue({ vg_maphash, baseURL });
  view = await visioOne.createView(container, venue, { cameraProjection });
  ```
  Reloading the WebView page, as this demo does, avoids the problem because each page gets a fresh SDK instance and venue.
- **A switch is a full map reload, and the app loses everything attached to the old view and venue.** This covers event listeners (`poiclick`, `currentfloorchanged`, tracking events, …), runtime POIs/labels/images/markers, surface styling, the computed navigation, the camera position and the selected floor. Register the listeners again and restore any state you need after the new `createView` resolves. Here, the whole of `main()` runs again on the new page. Expect a visible reload: about 5–11 s per switch on the emulator once the map assets are cached, and much longer on a cold network.
- **In orthographic mode, the SDK draws no sky and the WebGL canvas is transparent.** In perspective mode, the SDK draws its own light sky. In orthographic mode, whatever is behind the map container shows through. In this demo that's the page's `#ffffff` body background (we checked by painting the body red). Set an explicit background on the container, page or WebView if you want a specific color behind the map. A host page with a dark background will look "dark" in orthographic mode.
- **The orthographic view starts much further out** than the perspective one on the same venue (the whole site fits on screen), and it renders flat, with no vanishing point.

## Learn more

- [Custom map server](custom-base-url.md) is the other `loadVenue`/`createView`-time setting in this repo that needs a full reload to change.
