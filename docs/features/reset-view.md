# Reset View

## Description

Recenters the camera on the whole venue via `view.goToGlobal()` — a `View` method that takes no arguments and animates the camera back to the venue's default overview.

## SDK usage

```kotlin
// app/src/main/java/com/visioglobe/visioonemeet/features/ResetView.kt
private fun WebView.goToGlobal() = callBridge("goToGlobal")
```
```js
// web/src/features/reset-view.js
export const mapBridge = {
  goToGlobal() {
    if (view) view.goToGlobal();
  },
};
```

`view` is the object returned by `visioOne.createView(container, venue)` (in `web/src/main.js`, shared with every feature module through `web/src/context.js`) — keep a reference to it once created, since most `View` methods (including this one) are called on it directly.

## Things to know

- Takes no arguments — `callBridge` has nothing to JSON-encode here, unlike SDK calls that take structured data.
- The camera animates back immediately when called; there's no callback or event to await.
