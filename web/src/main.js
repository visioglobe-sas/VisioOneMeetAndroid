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

// The zone POI last resolved by `resolveZone`, cached so `checkGeofence` has
// something to test the tracked position against without re-resolving the
// POI on every tick, and so `clearZone` knows which surfaces to revert. Only
// one zone is tracked at a time — a demo-side simplification, not an SDK
// limitation, same convention as `dynamicPoi`/`highlightedCategoryId`. See
// docs/features/geofencing.md.
let geofenceZone = null;

// Whether the last position checked by `checkGeofence` was inside
// `geofenceZone`, so a repeated tick at the same state doesn't re-issue the
// same `venue.updateSurface` call. See docs/features/geofencing.md.
let isInsideGeofenceZone = false;

// Any clearly visible "alert" color works here — this isn't an SDK-mandated
// value, just a demo choice, same convention as CATEGORY_HIGHLIGHT_COLOR.
// See docs/features/geofencing.md.
const GEOFENCE_ALERT_COLOR = '#E74C3C';

// The POI/Label pair created at runtime by createDynamicPoi, tracked so
// updateDynamicPoiLabel/removeDynamicPoi know what to act on. Only one
// dynamic POI is tracked at a time — a demo-side simplification, not an SDK
// limitation. See docs/features/dynamic-poi-crud.md.
let dynamicPoi = null;
let dynamicLabel = null;

// Fixed key/value dictionary demonstrated by add-locale: one predefined SDK
// UI key ('search-for-anything', overriding a string the SDK's own default
// UI would otherwise show) and one custom, app-defined key
// ('welcome-message', meaningless to the SDK itself — just a demo that the
// store is a general-purpose one, usable for the app's own strings too). See
// docs/features/add-locale.md.
const ADD_LOCALE_RESOURCES = {
  'search-for-anything': 'Busca lo que quieras',
  'welcome-message': '¡Bienvenido al mapa!',
};

// The SDK has no built-in geofencing/point-in-polygon primitive — this demo
// implements containment itself against `Surface.positions` (the public,
// WGS84 vertex list of a zone POI's surface, same coordinate space as
// `injectTrackedPosition`, so no conversion is needed). Standard ray-casting,
// treating latitude/longitude as planar y/x, which is accurate enough at
// building scale. See docs/features/geofencing.md.
function isPositionInsidePolygon(position, polygonPositions) {
  const { latitude: y, longitude: x } = position;
  let inside = false;
  for (let i = 0, j = polygonPositions.length - 1; i < polygonPositions.length; j = i++) {
    const xi = polygonPositions[i].longitude;
    const yi = polygonPositions[i].latitude;
    const xj = polygonPositions[j].longitude;
    const yj = polygonPositions[j].latitude;
    const intersects = yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi;
    if (intersects) inside = !inside;
  }
  return inside;
}

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
  // Switches the SDK's building-exploration mode by assigning
  // view.currentExploreMode. mode is one of exactly 3 case-sensitive values
  // ('global', 'building', 'floor' — see the SDK's ExploreMode type); an
  // unrecognized value is rejected by the SDK itself, this bridge does no
  // validation of its own, same convention as setUIPartVisible above. The
  // resulting change (including any change triggered another way, e.g. a
  // click while in "building" mode) is reported back via
  // onExploreModeChanged below, so this call itself does not report
  // anything — same "fire the command, listen for the echo" split as
  // goToFloor/onFloorChanged. See docs/features/explore-mode.md.
  setExploreMode(mode) {
    if (!view) return;
    view.currentExploreMode = mode;
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
  // Creates a POI at runtime, without republishing the map in VisioMapEditor,
  // and gives it a visual footprint by attaching a Label — a bare POI created
  // via venue.createPOI has no images/labels/lines/surfaces/markers of its
  // own, so it would otherwise be invisible on the map. The new label's
  // position is copied from anchorId's own first label/marker/image (no
  // "tap the map to place a pin" UI in this demo) rather than picked by the
  // user. All three outcomes below (anchor not found, anchor has no position
  // to copy, duplicate newId) are reported back as normal states via
  // AndroidBridge.onDynamicPoiCreated, requestId echoed back, same
  // convention as loadCustomData/getCategories. See
  // docs/features/dynamic-poi-crud.md.
  createDynamicPoi(requestId, newId, anchorId, labelText) {
    if (!venue) return;
    const report = (status, extra) => {
      bridge?.onDynamicPoiCreated(requestId, JSON.stringify({ status, ...extra }));
    };
    const anchor = venue.pois.find((p) => p.id === anchorId);
    if (!anchor) {
      report('anchor-not-found');
      return;
    }
    const anchorPosition = anchor.labels[0]?.position ?? anchor.markers[0]?.position ?? anchor.images[0]?.position;
    if (!anchorPosition) {
      report('anchor-has-no-position');
      return;
    }
    try {
      const poi = venue.createPOI({ id: newId });
      const label = venue.createLabel({ poi, position: anchorPosition, width: 2, text: labelText });
      dynamicPoi = poi;
      dynamicLabel = label;
      report('created', { id: poi.id, text: label.text });
    } catch (error) {
      // createPOI's only documented failure mode is POIAlreadyExistsError
      // (see the SDK's Venue.d.ts) — but that class isn't part of this
      // package's public exports, so it can't be caught with `instanceof`.
      // Its constructor name survives bundling unminified, so that's the
      // most reliable check available from here.
      if (error?.constructor?.name === 'POIAlreadyExistsError') {
        report('duplicate-id');
      } else {
        report('error', { message: String(error?.message ?? error) });
      }
    }
  },
  // Updates the tracked dynamic POI's label text — the real "modify" story
  // for a dynamic POI, since venue.updatePOI itself can only touch
  // categories, never anything visual. A no-op if nothing is tracked. See
  // docs/features/dynamic-poi-crud.md.
  updateDynamicPoiLabel(text) {
    if (!venue || !dynamicLabel) return;
    venue.updateLabel(dynamicLabel, { text });
  },
  // Removes the tracked dynamic POI via venue.removePOI, which cascades to
  // remove its attached label from the view too — no separate removeLabel
  // call needed. A no-op if nothing is tracked. See
  // docs/features/dynamic-poi-crud.md.
  removeDynamicPoi() {
    if (!venue || !dynamicPoi) return;
    venue.removePOI(dynamicPoi);
    dynamicPoi = null;
    dynamicLabel = null;
  },
  // Reports the venue's currentLocale back to native in one round trip, so
  // the runtime-locale sheet can highlight whichever option is already
  // active when it opens. There is no locale-change event to listen for
  // instead, so this is only ever called once, right when the sheet is
  // shown. Same requestId-echo convention as getCategories/loadCustomData,
  // reusing the { status, locale } shape of setLocale below so native can
  // share one response parser for both. See docs/features/runtime-locale.md.
  getCurrentLocale(requestId) {
    if (!venue) return;
    bridge?.onLocaleResolved(requestId, JSON.stringify({ status: 'ok', locale: venue.currentLocale }));
  },
  // Switches the map's displayed language via venue.setCurrentLocale, which
  // re-renders POI/label names (and the current View's UI/Navigation) with
  // the new locale by itself — no manual re-fetch of POI data needed, per
  // the SDK's own Venue.ts TSDoc on setCurrentLocale/currentLocale. It is
  // Promise-based, so the resolved locale is only reported back to native
  // (onLocaleResolved, requestId echoed) once the switch actually completes,
  // same "wait for the async result" idiom as loadCustomData/
  // createDynamicPoi. In this SDK version setCurrentLocale never rejects —
  // it does not validate locale against venue.translator.allLocales, it just
  // sets currentLocale and re-renders — but the try/catch is kept for
  // forward-compatibility, same defensive pattern as computeNavigation. See
  // docs/features/runtime-locale.md.
  async setLocale(requestId, locale) {
    if (!venue) return;
    try {
      await venue.setCurrentLocale(locale);
      bridge?.onLocaleResolved(requestId, JSON.stringify({ status: 'ok', locale: venue.currentLocale }));
    } catch (error) {
      bridge?.onLocaleResolved(
        requestId,
        JSON.stringify({ status: 'error', message: String(error?.message ?? error) }),
      );
    }
  },
  // Demonstrates venue.translator.addLocale as a fully generic, i18next-backed
  // key/value store, entirely separate from the venue's own POI/floor/
  // building/category translation data (parsed once at load from the
  // published map's own JSON and never touched by addLocale/translate) — see
  // docs/features/add-locale.md, "Things to know". Adds 'es' (never authored
  // in VisioMapEditor for this map) with ADD_LOCALE_RESOURCES above, then
  // reads each key straight back via translator.translate so the round trip
  // is provable without depending on any of the SDK's own UI parts being
  // visible. addLocale itself is synchronous and, per its own TSDoc,
  // undocumented to throw — the try/catch here is kept for
  // forward-compatibility only, same defensive idiom as setLocale above.
  addSpanishLocale(requestId) {
    if (!venue) return;
    try {
      venue.translator.addLocale('es', ADD_LOCALE_RESOURCES);
      const translations = {};
      Object.keys(ADD_LOCALE_RESOURCES).forEach((key) => {
        translations[key] = venue.translator.translate(key, 'es');
      });
      bridge?.onAddLocaleResolved(requestId, JSON.stringify({ status: 'ok', translations }));
    } catch (error) {
      bridge?.onAddLocaleResolved(
        requestId,
        JSON.stringify({ status: 'error', message: String(error?.message ?? error) }),
      );
    }
  },
  // Resolves placeId to a POI and caches its surfaces as the zone geofencing
  // checks against, reporting one of 'found'/'not-found'/'no-surface' back to
  // native via AndroidBridge.onZoneResolved, requestId echoed back, same
  // convention as resolvePositions/getCurrentLocale. A POI with no surfaces
  // (e.g. a point/marker-only outdoor POI) has no polygon to test a position
  // against, so it's reported as its own distinct outcome rather than
  // silently behaving like "always outside". See docs/features/geofencing.md.
  resolveZone(requestId, placeId) {
    if (!venue) return;
    const poi = venue.pois.find((p) => p.id === placeId);
    if (!poi) {
      geofenceZone = null;
      bridge?.onZoneResolved(requestId, 'not-found');
      return;
    }
    if (!poi.surfaces.length) {
      geofenceZone = null;
      bridge?.onZoneResolved(requestId, 'no-surface');
      return;
    }
    geofenceZone = { surfaces: poi.surfaces };
    isInsideGeofenceZone = false;
    bridge?.onZoneResolved(requestId, 'found');
  },
  // Reverts the alert color applied by checkGeofence below (if the tracked
  // position was ever inside the zone) and forgets the resolved zone. Called
  // when the simulation is stopped. See docs/features/geofencing.md.
  clearZone() {
    if (!venue || !geofenceZone) return;
    geofenceZone.surfaces.forEach((surface) => {
      venue.updateSurface(surface, { color: 'initial' });
    });
    geofenceZone = null;
    isInsideGeofenceZone = false;
  },
  // Tests the tracked position injected by injectTrackedPosition against the
  // zone resolved by resolveZone above (isPositionInsidePolygon — there is no
  // SDK method for this) and, on a state transition, recolors the zone's
  // surface(s) as a visual alert via venue.updateSurface — the same
  // "recolor a surface" primitive already used by
  // clickable-surface/category-highlight, not a dedicated alert/marker SDK
  // concept. Reports the current state back to native on every call via
  // AndroidBridge.onGeofenceStateChanged, not just on a transition, so a late
  // subscriber (e.g. the sheet reopening) still gets a correct value. A no-op
  // if no zone is resolved (e.g. Start pressed with an unresolved/invalid
  // Zone POI ID). See docs/features/geofencing.md.
  checkGeofence(latitude, longitude) {
    if (!venue || !geofenceZone) return;
    const isInside = geofenceZone.surfaces.some((surface) =>
      isPositionInsidePolygon({ latitude, longitude }, surface.positions),
    );
    if (isInside !== isInsideGeofenceZone) {
      isInsideGeofenceZone = isInside;
      geofenceZone.surfaces.forEach((surface) => {
        venue.updateSurface(surface, { color: isInside ? GEOFENCE_ALERT_COLOR : 'initial' });
      });
    }
    bridge?.onGeofenceStateChanged(isInside);
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

// Forwards the SDK's 'exploremodechanged' event to native as a bare mode
// string, so the explore-mode control can keep its highlighted option in
// sync even when the mode changes another way than its own buttons — most
// notably a click while in "building" mode, which the SDK auto-switches to
// "floor" (see the SDK's ExploreMode.ts). Also used to seed the control's
// initial highlight once, right after the view is created (see main()
// below) — currentExploreMode always starts at "global". See
// docs/features/explore-mode.md.
function onExploreModeChanged(event) {
  bridge?.onExploreModeChanged(event.currentExploreMode);
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
    view.addEventListener('exploremodechanged', onExploreModeChanged);
    // Only the first building's floors are exposed today — see
    // docs/features/floor-selector.md, "Points d'attention" for why a
    // building switcher is out of scope for this demo.
    const building = venue.venueLayout.buildings[0];
    if (building) {
      bridge?.onFloorsReady(JSON.stringify(buildingFloorsPayload(building)));
    }
    // Seeds the explore-mode control's initial highlight — there is no
    // "onReady" data push for it like onFloorsReady above, since
    // currentExploreMode always starts at "global" and needs no venue data
    // to report. See docs/features/explore-mode.md.
    bridge?.onExploreModeChanged(view.currentExploreMode);
    bridge?.onMapReady();
  } catch (error) {
    bridge?.onMapError(String(error?.message ?? error));
  }
}

main();
