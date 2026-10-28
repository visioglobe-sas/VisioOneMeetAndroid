// floor-selector: pushes the first building's floors to native once the view
// exists, keeps the current floor in sync, and moves the camera to a floor on
// request. See docs/features/floor-selector.md.
import { notify } from '../bridge.js';
import { venue, view } from '../context.js';

// Builds the payload sent to native for the floor-selector UI: the
// building's id (needed by goToFloor below) plus one entry per floor with a
// human-readable name — resolved via the venue's Translator for the venue's
// currentLocale, same convention as onPoiClick — and its levelIndex, so the
// native side can order the list top-down without SDK-specific knowledge.
// currentFloorId seeds the initial highlight and is kept up to date afterwards
// by onCurrentFloorChanged below.
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

// Forwards the SDK's 'currentfloorchanged' event to native as a bare floor id
// (or null, e.g. camera moved back outside), so the floor-selector list can
// keep its highlighted entry in sync even when the floor changes another way
// — the SDK's own default floor-selector widget, or a goToPOI on a POI of a
// different floor. Floor ids are unique across the whole venue (see the
// SDK's Floor typings), so the native side can match this against its own
// building-scoped floor list with no ambiguity. See
// docs/features/floor-selector.md, "Things to know".
function onCurrentFloorChanged(event) {
  notify('onFloorChanged', event.newFloor?.id ?? null);
}

export function onViewCreated() {
  view.addEventListener('currentfloorchanged', onCurrentFloorChanged);
  // Only the first building's floors are exposed today — see
  // docs/features/floor-selector.md, "Things to know" for why a
  // building switcher is out of scope for this demo.
  const building = venue.venueLayout.buildings[0];
  if (building) {
    notify('onFloorsReady', JSON.stringify(buildingFloorsPayload(building)));
  }
}

export const mapBridge = {
  // Moves the camera to the floor matching buildingId/floorId by calling
  // view.goToFloor(floor) — both ids are resolved from venue.venueLayout so a
  // stale/unknown id (e.g. building removed between two loads) is a silent
  // no-op, same convention as the other MapBridge commands.
  goToFloor(buildingId, floorId) {
    if (!venue || !view) return;
    const building = venue.venueLayout.buildings.find((b) => b.id === buildingId);
    if (!building) return;
    const floor = building.floors.find((f) => f.id === floorId);
    if (!floor) return;
    view.goToFloor(floor);
  },
};
