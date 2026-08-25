# UI Part Visibility

## Description

Toggles individual pieces of the SDK's default on-map UI via `view.setUIPartVisible(uiPart, isVisible)`.

## SDK usage

```js
setUIPartVisible(uiPart, isVisible) {
  if (!view) return;
  view.setUIPartVisible(uiPart, isVisible);
},
```
```kotlin
private fun WebView.setUiPartVisible(uiPart: String, isVisible: Boolean) {
    val script = "window.MapBridge.setUIPartVisible(${JSONObject.quote(uiPart)}, $isVisible)"
    evaluateJavascript(script, null)
}
```

## Things to know

- The SDK exposes exactly 5 `UIPart` values, exact and case-sensitive (see the SDK's `View` typings): `floorSelector`, `navigation`, `poiDetails`, `search`, `userTracking`. A misspelled value (`poidetails`, `Search`, ...) isn't normalized by the SDK — the call fails silently or throws depending on SDK version, since this bridge passes the string through without validating it.
- Hiding `search` or `navigation` removes the *only* default entry point for those flows — unlike `floorSelector`, `poiDetails` or `userTracking`, which are just displays you can replace with native UI, `search` and `navigation` are also how a user triggers those SDK flows in the first place. If you hide them, provide your own search/navigation UI (see `compute-navigation.md` for the navigation case).
- `view.showUI` (boolean) hides/shows the SDK's entire default UI at once — more radical than `setUIPartVisible`, and relevant if you're replacing the SDK's UI entirely rather than keeping some parts of it.
- This demo doesn't read back `view.isUIPartVisible(uiPart)` to initialize toggle state, it just assumes everything starts visible. If you keep a single `view` instance alive across multiple screens (rather than recreating it), initialize any UI you build from that getter instead of assuming a hardcoded default.
- `setUIPartVisible` is a no-op before the `view` exists (i.e. before `createView` resolves), like every other bridge command in this repo.
