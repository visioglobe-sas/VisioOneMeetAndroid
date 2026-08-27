# Native UI Replacement

## Description

Demonstrates that an app's own native UI can fully replace one of the SDK's default on-map UI components — the "white-label" case for `setUIPartVisible` (see [`ui-part-visibility`](./ui-part-visibility.md)). This screen hides the SDK's own default floor-selector widget (`view.setUIPartVisible('floorSelector', false)`) as soon as the map is ready, and drives floor changes entirely from this repo's existing native floor picker (see [`floor-selector`](./floor-selector.md)) instead — reused as-is, not reimplemented.

A switch, "Show SDK's own floor selector", starts off (SDK widget hidden, only the native picker visible and functional) and can be flipped on to bring the SDK's own widget back, side by side with the native one, so both can be seen driving the same floor state live via the same `currentfloorchanged` event `floor-selector` already listens to.

## SDK usage

```js
setUIPartVisible(uiPart, isVisible) {
  if (!view) return;
  view.setUIPartVisible(uiPart, isVisible);
},
```

Native call site (same bridge helper `ui-part-visibility` uses, `uiPart` fixed to `'floorSelector'` here):

```kotlin
internal fun WebView.setUiPartVisible(uiPart: String, isVisible: Boolean) {
    val script = "window.MapBridge.setUIPartVisible(${JSONObject.quote(uiPart)}, $isVisible)"
    evaluateJavascript(script, null)
}
```

Two call sites use it on this screen: once as soon as the map is ready (`webView.setUiPartVisible("floorSelector", false)`, before the visitor ever opens the FAB's sheet, so the SDK widget is never shown by default), and again on every flip of the switch (`webView.setUiPartVisible("floorSelector", isVisible)`).

The floor picker itself is `floor-selector`'s existing native list — `view.goToFloor(floor)`, kept in sync with `currentfloorchanged` — reused verbatim here rather than reimplemented; see that doc's "SDK usage" section for its call sites.

## Things to know

- `setUIPartVisible('floorSelector', false)` only hides the SDK's *widget* — it has no effect on `view.goToFloor`, `view.currentFloor`, or the `currentfloorchanged` event. The reused native picker keeps working identically whether the SDK's own widget is shown or hidden, which is the actual point of this demo: the two are decoupled, not two views of one control.
- Because this hides the SDK widget as soon as the map is ready — not only once the visitor opens the FAB's sheet and touches the switch — there is no brief flash of the SDK's own widget before it disappears. A client building this against a `view` shared across screens (rather than one recreated per screen, as this demo app does) should apply the equivalent "hide by default" call as soon as their own `view` is ready, for the same reason.
- No SDK-side race exists between hiding the widget and the native picker becoming usable: both `setUIPartVisible` and `goToFloor` only require `view` to exist, and the native picker already tolerates the venue's floor list arriving asynchronously (`floor-selector` shows a placeholder until then, see that doc).

## Learn more

- [`ui-part-visibility`](./ui-part-visibility.md) — the general mechanism this feature is one specific, driven instance of (`setUIPartVisible`, all 5 `UIPart` values, toggled independently).
- [`floor-selector`](./floor-selector.md) — the native floor/building picker reused here, including how it stays in sync with `currentfloorchanged` regardless of which UI last drove a floor change.
