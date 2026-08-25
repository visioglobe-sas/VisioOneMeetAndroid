# Floor Selector

## Description

Moves the camera to a given floor via `view.goToFloor(floor)`, and keeps a native floor list in sync with the SDK's own floor state through the `currentfloorchanged` event. `goToFloor` takes a resolved `Floor` object, not an id, so the bridge resolves it via `venue.venueLayout.buildings.find(...)` → `building.floors.find(...)` before calling the SDK.

The SDK already ships its own default floor-selector widget on the map (`UIPart = 'floorSelector'`), with no application code required. This feature demonstrates driving floor changes from your own UI instead — relevant if you hide the SDK's default UI (`view.setUIPartVisible('floorSelector', false)`, or `view.showUI = false` for everything) and want a floor control that matches your own design.

## SDK usage

```js
goToFloor(buildingId, floorId) {
  if (!venue || !view) return;
  const building = venue.venueLayout.buildings.find((b) => b.id === buildingId);
  if (!building) return;
  const floor = building.floors.find((f) => f.id === floorId);
  if (!floor) return;
  view.goToFloor(floor);
},
```

Reading the floor list once, and staying in sync afterward:

```js
function buildingFloorsPayload(building) {
  const locale = venue.currentLocale;
  return {
    buildingId: building.id,
    currentFloorId: view.currentFloor?.id ?? null,
    floors: building.floors.map((floor) => ({
      id: floor.id,
      name: venue.translator.translateFloor(floor, locale).name,
      levelIndex: floor.levelIndex,
    })),
  };
}

// once, right after the venue/view are ready, for the first building:
bridge?.onFloorsReady(JSON.stringify(buildingFloorsPayload(venue.venueLayout.buildings[0])));

// kept in sync afterwards, including floor changes triggered by other means:
view.addEventListener('currentfloorchanged', (event) => {
  bridge?.onFloorChanged(event.newFloor?.id ?? null);
});
```

Native call site:

```kotlin
private fun WebView.goToFloor(buildingId: String, floorId: String) {
    val script = "window.MapBridge.goToFloor(${JSONObject.quote(buildingId)}, ${JSONObject.quote(floorId)})"
    evaluateJavascript(script, null)
}
```

## Things to know

- `goToFloor` takes a `Floor` object, not an id — unlike `goToPOI`, this requires a two-step lookup (`buildings.find` then `floors.find`) before calling the SDK. An unknown `buildingId`/`floorId` pair is a silent no-op.
- Listen to `currentfloorchanged` even if you only care about your own buttons: it also fires for floor changes triggered another way — the SDK's own floor-selector widget if left visible, or a `goToPOI` call that lands on a different floor. (The SDK documents that the caller is responsible for calling `goToFloor` before `goToPOI` if a floor change is actually needed — `currentFloor`/`currentBuilding` don't change on their own as a side effect of `goToPOI`.)
- `Floor` ids are unique across the whole venue (per the SDK's typings), so comparing just the floor id is enough to know whether the active floor belongs to the list you're displaying — no need to also compare `buildingId`.
- `venue.translator.translateFloor(floor, locale).name` can come back empty if no translation exists for that floor/locale — plan a fallback (e.g. the id, or a "Level N" built from `levelIndex`).
- Only `buildings[0]` is read here — a multi-building venue has one floor list per `Building`, and `goToFloor(buildingId, floorId)` already accepts a `buildingId` for exactly that reason.

## Learn more

- `goToFloor` also accepts an `AnimationOptions` second argument (`duration`, `easing`), not used here.
- `view.goToBuilding(building, animationOptions)` also exists and jumps to a building's default floor (`Building.defaultFloorID`) — relevant if you need a building selector too.
