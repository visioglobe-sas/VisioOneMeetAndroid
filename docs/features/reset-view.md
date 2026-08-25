# Reset View

## Description

Recenters the camera on the whole venue via `view.goToGlobal()` — a `View` method that takes no arguments and animates the camera back to the venue's default overview.

## SDK usage

```kotlin
webView.evaluateJavascript("window.MapBridge.goToGlobal()", null)
```
```js
// window.MapBridge, JS side
goToGlobal() {
  if (view) view.goToGlobal();
}
```

`view` is the object returned by `visioOne.createView(container, venue)` — keep a reference to it once created, since most `View` methods (including this one) are called on it directly.

## Things to know

- Takes no arguments — no JSON-encoding needed here, unlike SDK calls that take structured data.
- The camera animates back immediately when called; there's no callback or event to await.
