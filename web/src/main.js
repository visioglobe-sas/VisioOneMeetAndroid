import { createVisioOne } from '@visioglobe/visioone';

// The Android app appends ?hash=<mapHash> when it loads this page (see
// VisioOneWebView.kt), so the same bundle can display any map without a rebuild.
const DEFAULT_HASH = 'kbae8e6c066cca4b02c2afac2bc963a643d87437a';
const hash = new URLSearchParams(window.location.search).get('hash') || DEFAULT_HASH;

const container = document.querySelector('#content');

// Optional bridge injected by MainActivity via WebView.addJavascriptInterface,
// used to reflect the map's loading state in the native Compose UI.
const bridge = window.AndroidBridge;

let venue = null;
let view = null;

// The POI last centered on via `goToPlace`, so `clearPlace` knows which
// surfaces to un-highlight. See docs/features/goto-poi.md.
let selectedPoi = null;

// The trace last drawn by `computeNavigation`, so `clearNavigation` knows
// what to remove. See docs/features/compute-navigation.md.
let currentNavigationTrace = null;

// The category id last highlighted via `highlightCategory`, so
// `clearCategoryHighlight` knows which POIs' surfaces to revert, and so a
// second `highlightCategory` call can revert it before highlighting the new
// selection (only one category highlighted at a time). See
// docs/features/category-highlight.md.
let highlightedCategoryId = null;

// Any clearly visible color works here — this isn't an SDK-mandated value,
// just a demo choice. See docs/features/category-highlight.md.
const CATEGORY_HIGHLIGHT_COLOR = '#FF6B00';

// Native -> JS bridge: one method per command, called from Kotlin via
// WebView.evaluateJavascript(). Kotlin JSON-encodes arguments before
// interpolating them into the generated script call, so what arrives here
// is already a real JS value (array/object), never a string to re-parse.
window.MapBridge = {
  goToGlobal() {
    if (view) view.goToGlobal();
  },
  updateOccupancy(occupancy) {
    if (!venue) return;
    occupancy.forEach((entry) => {
      const poi = venue.pois.find((p) => p.id === entry.planId);
      if (!poi) return;
      poi.surfaces.forEach((surface) => {
        venue.updateSurface(surface, { color: entry.color });
      });
    });
  },
  // Centers/zooms the camera on the POI matching `placeId`, and highlights its
  // surfaces so the result is visible even once the animation settles. A
  // missing placeId (typo, wrong venue) is a silent no-op — mirrors the other
  // MapBridge commands (see updateOccupancy above), no error channel back to
  // native for this today.
  goToPlace(placeId) {
    if (!venue || !view) return;
    const poi = venue.pois.find((p) => p.id === placeId);
    if (!poi) return;

    selectedPoi = poi;
    view.goToPOI(poi, {
      orientation: { pitch: 20 },
      padding: { top: 100, right: 100, bottom: 100, left: 100 },
    });
    poi.surfaces.forEach((surface) => {
      venue.updateSurface(surface, { selectionColor: view.surfaceSelectionColor });
    });
  },
  // Reverts the highlight applied by goToPlace. Does not move the camera back
  // — "Clear" only undoes the visual marker, matching the RN sibling's
  // clearPlace (see docs/features/goto-poi.md, "Points d'attention").
  clearPlace() {
    if (!venue || !selectedPoi) return;
    selectedPoi.surfaces.forEach((surface) => {
      venue.updateSurface(surface, { selectionColor: 'default' });
    });
    selectedPoi = null;
  },
  // Moves the camera to the floor matching buildingId/floorId by calling
  // view.goToFloor(floor) — both ids are resolved from venue.venueLayout so a
  // stale/unknown id (e.g. building removed between two loads) is a silent
  // no-op, same convention as the other MapBridge commands. See
  // docs/features/floor-selector.md.
  goToFloor(buildingId, floorId) {
    if (!venue || !view) return;
    const building = venue.venueLayout.buildings.find((b) => b.id === buildingId);
    if (!building) return;
    const floor = building.floors.find((f) => f.id === floorId);
    if (!floor) return;
    view.goToFloor(floor);
  },
  // Computes a route between two POIs (given by id — `computeNavigation`
  // accepts a POI, a POI id string, or a Position, see the SDK's
  // NavigationRequest.d.ts; ids are used here since that's all the bottom
  // sheet's text fields collect) and draws it on the map via a
  // NavigationTrace, same request shape as the React Native sibling's
  // `startItinerary` (`visioOneHtml.ts`). Any previous trace is removed
  // first so consecutive itineraries don't stack. A bad/unreachable id
  // makes `computeNavigation` throw (see the SDK's
  // InvalidNavigationRequestError) — caught here and reported back to
  // native (`onNavigationError`) so the bottom sheet can show *something*
  // went wrong, unlike the other MapBridge commands (goToPlace, goToFloor)
  // which fail silently: those look up an id locally and no-op on a miss,
  // whereas an invalid itinerary request would otherwise leave the user
  // staring at an unchanged map with no clue why. `onNavigationComputed`
  // fires on success so native can clear a previously shown error. See
  // docs/features/compute-navigation.md.
  computeNavigation(origin, destination, isAccessible) {
    if (!venue || !view) return;
    this.clearNavigation();
    try {
      const navigation = venue.computeNavigation({
        origin,
        destination,
        isAccessible,
        type: 'fastest',
        firstNodeAsIntersection: false,
        mergeFloorChangeInstructions: false,
      });
      currentNavigationTrace = venue.createNavigationTrace(navigation);
      view.setCurrentNavigationTrace(currentNavigationTrace);
      bridge?.onNavigationComputed();
    } catch (error) {
      bridge?.onNavigationError(String(error?.message ?? error));
    }
  },
  // Removes the trace drawn by computeNavigation, if any — a no-op
  // otherwise (e.g. called before any itinerary was computed).
  clearNavigation() {
    if (!venue || !view || !currentNavigationTrace) return;
    view.removeCurrentNavigationTrace();
    venue.removeNavigationTrace(currentNavigationTrace);
    currentNavigationTrace = null;
  },
  // Shows/hides one of the SDK's own default UI overlays via
  // view.setUIPartVisible. uiPart is one of exactly 5 case-sensitive values
  // ('floorSelector', 'navigation', 'poiDetails', 'search', 'userTracking' —
  // see the SDK's View.ts UIPart type); an unrecognized value is rejected by
  // the SDK itself, this bridge does no validation of its own. See
  // docs/features/ui-part-visibility.md.
  setUIPartVisible(uiPart, isVisible) {
    if (!view) return;
    view.setUIPartVisible(uiPart, isVisible);
  },
  // Resolves the WGS84 position of two POIs by id in one round trip, and
  // reports both back to native via AndroidBridge.onPositionsResolved.
  // POIs carry no direct lat/lng field — it's read from the first
  // marker/label/image's `position` instead, whichever exists first. A
  // missing id (or a POI with none of markers/labels/images) resolves to
  // `null` for that slot rather than throwing, so native can tell "not
  // found" apart from a real position. `requestId` is echoed back as-is so
  // native can match this response to the Start press that triggered it.
  // See docs/features/simulated-position.md.
  resolvePositions(requestId, originId, destinationId) {
    const resolve = (poiId) => {
      const poi = venue?.pois.find((p) => p.id === poiId);
      const position = poi?.markers?.[0]?.position ?? poi?.labels?.[0]?.position ?? poi?.images?.[0]?.position;
      return position ? { latitude: position.latitude, longitude: position.longitude } : null;
    };
    bridge?.onPositionsResolved(
      requestId,
      JSON.stringify(resolve(originId)),
      JSON.stringify(resolve(destinationId)),
    );
  },
  // Injects/updates the simulated tracked position and its accuracy circle
  // (precisionCircleRadius, in meters) via view.injectTrackedPosition.
  // allowTracking must be true first — the SDK throws otherwise — so it's
  // set here on every call rather than once at Start, which costs nothing
  // once already true. See docs/features/simulated-position.md.
  injectTrackedPosition(latitude, longitude, precisionCircleRadius) {
    if (!view) return;
    view.allowTracking = true;
    view.injectTrackedPosition({ position: { latitude, longitude }, precisionCircleRadius });
  },
  // Removes the marker/circle injected above. There is no dedicated stop
  // method on the SDK — setting allowTracking back to false is how it's
  // cleared. See docs/features/simulated-position.md.
  stopTrackedPosition() {
    if (!view) return;
    view.allowTracking = false;
  },
  // Binds/unbinds the camera's focus to the tracked position injected above,
  // GPS-app "recenter on me" style, via view.lockCameraPositionOnTracking.
  // Has no visible effect until a position has been injected (allowTracking
  // must be true — see injectTrackedPosition above), same precondition the
  // SDK documents on the flag itself. See docs/features/camera-lock-on-position.md.
  setCameraLockOnPosition(locked) {
    if (!view) return;
    view.lockCameraPositionOnTracking = locked;
  },
  // Makes the POI's surfaces clickable (or reverts them) via
  // venue.updateSurface's isInteractive flag. While interactive, the SDK
  // itself handles the hover/selection color swap on tap — no click
  // listener needed on our side for the coloring itself. 'initial' resets
  // the color to whatever the map bundle originally defined for it. See
  // docs/features/clickable-surface.md.
  setSurfaceInteractive(placeId, interactive) {
    if (!venue) return;
    const poi = venue.pois.find((p) => p.id === placeId);
    if (!poi) return;
    poi.surfaces.forEach((surface) => {
      venue.updateSurface(
        surface,
        interactive
          ? { isInteractive: true, color: '#2ECC71', hoverColor: '#F1C40F', selectionColor: '#E74C3C' }
          : { isInteractive: false, color: 'initial' },
      );
    });
  },
  // Reloads CustomData from the server (venue.refreshCustomData — the cache
  // starts empty and is never refreshed on its own, see loadVenue) and then
  // reads the given POI's CustomData (venue.getPOICustomData), reporting the
  // result back to native via AndroidBridge.onCustomDataLoaded in one round
  // trip, same requestId-echo convention as resolvePositions above. The
  // payload is `null` when placeId doesn't resolve to a POI at all — a case
  // getPOICustomData itself can't distinguish, since it always returns `{}`
  // (never null/undefined) whether the POI has no CustomData or the cache
  // hasn't been refreshed yet. See docs/features/custom-data.md.
  //
  // refreshCustomData() itself can *reject* — not just resolve to an empty
  // cache — when the venue has no CustomData published on the server yet
  // (confirmed live: it 404s instead of resolving empty). That is still a
  // normal "no data" outcome for this demo, not an error, so the rejection
  // is swallowed here rather than propagated: getPOICustomData() already
  // degrades gracefully to `{}` against an unrefreshed/empty cache, so the
  // POI lookup below still produces the correct "no custom data" result
  // instead of leaving the bridge call (and the native UI waiting on it)
  // hanging forever on an unhandled rejection.
  async loadCustomData(requestId, placeId) {
    if (!venue) return;
    try {
      await venue.refreshCustomData();
    } catch (error) {
      console.warn('refreshCustomData failed, treating as no data available', error);
    }
    const poi = venue.pois.find((p) => p.id === placeId);
    const customData = poi ? venue.getPOICustomData(poi) : null;
    bridge?.onCustomDataLoaded(requestId, JSON.stringify(customData));
  },
  // Sends the venue's full category list back to native in one round trip,
  // requestId echoed back so native can match this response to whichever
  // call triggered it, same convention as loadCustomData/resolvePositions.
  // Category is `{ readonly id: string }` — a raw internal identifier (a
  // numeric string on this shared demo map, e.g. "1".."11"), not itself
  // human-readable — confirmed live. The display name comes from
  // venue.translator.translateCategory(), same Translator lookup already
  // used for POI/floor names elsewhere in this file. `id` is still what
  // filtering/highlighting must use; `label` is for display only. See
  // docs/features/category-highlight.md.
  getCategories(requestId) {
    if (!venue) return;
    const locale = venue.currentLocale;
    const categories = venue.categories.map((category) => ({
      id: category.id,
      label: venue.translator.translateCategory(category, locale).name || category.id,
    }));
    bridge?.onCategoriesLoaded(requestId, JSON.stringify(categories));
  },
  // Highlights every POI belonging to categoryId by recoloring its
  // surface(s) via venue.updateSurface. There is no dedicated "highlight by
  // category" SDK method — this is built from the venue.pois /
  // poi.categories / poi.surfaces primitives. Reverts any previously
  // highlighted category first (see highlightedCategoryId above), so at
  // most one category is highlighted at a time. POIs with no surfaces
  // (e.g. point/marker-only outdoor POIs, `type: -1`) are simply
  // unaffected — expected, not a bug. See docs/features/category-highlight.md.
  highlightCategory(categoryId) {
    if (!venue) return;
    this.clearCategoryHighlight();
    venue.pois
      .filter((poi) => poi.categories.some((category) => category.id === categoryId))
      .forEach((poi) => {
        poi.surfaces.forEach((surface) => {
          venue.updateSurface(surface, { color: CATEGORY_HIGHLIGHT_COLOR });
        });
      });
    highlightedCategoryId = categoryId;
  },
  // Reverts the highlight applied by highlightCategory, if any. `'initial'`
  // (not `undefined`/omitting the key) is the SDK's own documented sentinel
  // on SurfaceUpdateOptions.color to restore a surface's bundle-defined
  // color — a no-op omission would not do the same thing. See
  // docs/features/category-highlight.md.
  clearCategoryHighlight() {
    if (!venue || !highlightedCategoryId) return;
    venue.pois
      .filter((poi) => poi.categories.some((category) => category.id === highlightedCategoryId))
      .forEach((poi) => {
        poi.surfaces.forEach((surface) => {
          venue.updateSurface(surface, { color: 'initial' });
        });
      });
    highlightedCategoryId = null;
  },
};

// Builds the payload sent to native for the floor-selector UI: the
// building's id (needed by goToFloor above) plus one entry per floor with a
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
// SDK's Floor.d.ts), so the native side can match this against its own
// building-scoped floor list with no ambiguity. See
// docs/features/floor-selector.md, "Points d'attention".
function onCurrentFloorChanged(event) {
  bridge?.onFloorChanged(event.newFloor?.id ?? null);
}

// Web -> Native bridge: forwards the SDK's 'poiclick' event to the native side,
// so a screen can react to the user tapping a POI on the map (see
// docs/features/poi-click.md). The payload is a JSON-encoded array (usually a
// single entry) of { id, name }, name being resolved via the venue's
// Translator for the venue's currentLocale so the native side never has to
// deal with raw/untranslated POI data.
function onPoiClick(event) {
  if (!venue) return;
  const locale = venue.currentLocale;
  const pois = event.pois.map((poi) => ({
    id: poi.id,
    name: venue.translator.translatePOI(poi, locale).name,
  }));
  bridge?.onPoiClick(JSON.stringify(pois));
}

async function main() {
  try {
    const visioOne = createVisioOne();
    venue = await visioOne.loadVenue({ hash });
    view = await visioOne.createView(container, venue);
    view.addEventListener('poiclick', onPoiClick);
    view.addEventListener('currentfloorchanged', onCurrentFloorChanged);
    // Only the first building's floors are exposed today — see
    // docs/features/floor-selector.md, "Points d'attention" for why a
    // building switcher is out of scope for this demo.
    const building = venue.venueLayout.buildings[0];
    if (building) {
      bridge?.onFloorsReady(JSON.stringify(buildingFloorsPayload(building)));
    }
    bridge?.onMapReady();
  } catch (error) {
    bridge?.onMapError(String(error?.message ?? error));
  }
}

main();
