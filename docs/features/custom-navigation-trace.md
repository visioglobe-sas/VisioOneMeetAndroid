# Custom Navigation Trace

## Description

Restyles the route line drawn by [Compute Navigation](compute-navigation.md) with custom colors via `venue.updateNavigationTrace(navigationTrace, options)` — the same `NavigationTrace` object created by `venue.createNavigationTrace()`, just given a `NavigationTraceUpdateOptions` object afterward. `displayMode`/thickness can't be changed this way — only colors (and `textureRepeat`/`animationSpeed`, which only apply to the `'textured'` display mode) — so a trace is always created in the SDK's own default look and only its colors can be adjusted afterward.

## SDK usage

```js
const navigation = venue.computeNavigation({ origin: originPoi, destination: destinationPoi });
const trace = venue.createNavigationTrace(navigation);
view.setCurrentNavigationTrace(trace);

venue.updateNavigationTrace(trace, {
  progressColor: '#E53935',
  progressOutlineColor: '#FFFFFF',
  progressFutureColor: '#F8C9C7',
  previewColor: '#F8C9C7',
  previewOutlineColor: '#FFFFFF',
});
```

Native call site and the JS side of the bridge:

```kotlin
private fun WebView.updateNavigationTraceStyle(colors: NavigationTraceColors) {
    val options = JSONObject().apply {
        put("progressColor", colors.progressColor)
        put("progressOutlineColor", colors.progressOutlineColor)
        put("progressFutureColor", colors.progressFutureColor)
        put("previewColor", colors.previewColor)
        put("previewOutlineColor", colors.previewOutlineColor)
    }
    evaluateJavascript("window.MapBridge.updateNavigationTraceStyle($options)", null)
}
```

```js
// window.MapBridge, JS side
updateNavigationTraceStyle(options) {
  if (!venue || !currentNavigationTrace) return;
  try {
    venue.updateNavigationTrace(currentNavigationTrace, options);
  } catch (error) {
    console.warn('updateNavigationTrace threw (trace styling still applied):', error);
  }
},
```

`NavigationTraceUpdateOptions` (`Navigation/NavigationTraceUpdateOptions.d.ts`) exposes several colors beyond the five used here (`progressOverlayColor`, `progressDisabledOverlayColor`, `progressDisabledColor`, `previewOverlayColor`) plus `textureRepeat`/`animationSpeed` — all optional, so a partial options object only touches the fields it names.

## Things to know

- **There is no "reset to default" call.** Colors are one-way: once changed, the only way back to the SDK's own look is to re-apply its documented defaults yourself (`Line.color` defaults to `'#0094F0'`, the inactive/preview segment to `'#C5C5C5'` — see the SDK's `Venue/Line.d.ts`).
- **`updateNavigationTrace` can throw internally even on a fully valid trace.** On this repo's shared demo venue it consistently throws `TypeError: Cannot read properties of undefined (reading 'material')` deep inside the SDK's own line-rendering pipeline — yet every color in the options object is still applied correctly before it throws. This was confirmed on the sibling Vue integration (same `@visioglobe/visioone` version, same demo venue) but **not independently reproduced live from this Android app** — this repo's bridge wraps the call in a `try`/`catch` regardless (see `updateNavigationTraceStyle` above), on the assumption that the same SDK bundle throws the same way behind any host. Since the color change is applied before the throw either way, treat it as a non-fatal, already-applied change rather than a failure signal.
- Colors only affect a trace that already exists — calling `updateNavigationTrace` before `createNavigationTrace`/`setCurrentNavigationTrace` has nothing to act on (the bridge above no-ops in that case). If you want a consistent look, re-apply the same options object right after creating each new trace, not just once.
- Field naming is easy to mix up: per the typings' own doc comments, `progressColor` is "the color of the active part of the Line" while `progressFutureColor` is "the color for the progress line that has not yet been walked" — two different-sounding descriptions that both plausibly mean "the part still ahead." Read `NavigationTraceUpdateOptions.d.ts`'s comments directly before assuming which one controls which segment from the name alone, and verify visually against a real venue rather than guessing.

## Learn more

See [Compute Navigation](compute-navigation.md) for how the `Navigation`/`NavigationTrace` pair is computed and displayed in the first place — this feature only adds a styling call on top of it.
