# Explore Mode

## Description

Switches the SDK's building-exploration mode via the `view.currentExploreMode` settable property, and keeps a native control in sync with the SDK's own state through the `exploremodechanged` event — same "drive it + listen for the echo" shape as [Floor Selector](floor-selector.md)'s `currentfloorchanged`.

There are 3 modes:

- **`'global'`** — the normal outside view. Camera movement in/out of a building opens/closes it; when navigating a specific floor, moving the camera outside that floor closes the building.
- **`'building'`** — the outside is hidden and every opened building is shown as a "carousel": an exploded, cross-section-like view of its floors. The active floor can be picked with the mouse wheel or by sliding the pointer up/down on the screen. A click switches to `'floor'` mode, making the clicked floor "current".
- **`'floor'`** — only the current floor is shown.

## SDK usage

```js
setExploreMode(mode) {
  if (!view) return;
  view.currentExploreMode = mode;
},
```

Staying in sync afterward — including changes triggered by map interaction, not just this call:

```js
view.addEventListener('exploremodechanged', (event) => {
  bridge?.onExploreModeChanged(event.currentExploreMode);
});
```

Native call site:

```kotlin
private fun WebView.setExploreMode(mode: String) {
    val script = "window.MapBridge.setExploreMode(${JSONObject.quote(mode)})"
    evaluateJavascript(script, null)
}
```

## Things to know

- `currentExploreMode` is a plain settable property (`ExploreMode = 'global' | 'building' | 'floor'`), not a method — assign it directly, there's no `setExploreMode` on the SDK itself (that name only exists on this demo's own JS bridge object).
- `'building'` mode auto-switches to `'floor'` on a click, per the SDK's own documented behavior (`ExploreMode.ts`) — this is not something the app triggers itself, it's the SDK reacting to the pointer event. `exploremodechanged` is the only way to observe it, since it happens without any app-side call.
- Switching to `'building'` or `'floor'` mode does not require a building to already be open: if none is (e.g. starting from `'global'` with the camera outside), the SDK falls back to the venue's first building — confirmed against the SDK's `BuildingNavigator`/`FloorNavigator` implementation (`enterNavigator`). This is what makes the carousel effect trivial to trigger for a demo, with no building-selection UI needed first.
- Listen to `exploremodechanged` even if you only care about your own buttons: besides the `'building'` → `'floor'` auto-transition above, `view.goToGlobal()` also sets `currentExploreMode` back to `'global'` once its animation completes (see `View.goToGlobal`'s TSDoc) — always resync the native highlight from the event's `currentExploreMode`, never assume it matches whatever the last tapped button was.
- The event payload (`ExploreModeEvent`) also carries `venue` and `view`, unused by this demo — only `currentExploreMode` is read.

## Learn more

- `view.goToGlobal(animationOptions)` also sets `currentExploreMode` back to `'global'` once its camera animation completes — see [Reset View](reset-view.md).
- `view.goToBuilding(building, animationOptions)` / `view.goToFloor(floor, animationOptions)` move the camera directly to a specific building/floor; they don't by themselves change `currentExploreMode` the way assigning the property does — see [Floor Selector](floor-selector.md).
