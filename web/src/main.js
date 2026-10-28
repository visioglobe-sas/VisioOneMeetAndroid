import { createVisioOne } from '@visioglobe/visioone';
import { notify } from './bridge.js';
import { container, setVenue, setView, venue } from './context.js';
import * as iconMarker3d from './features/3d-icon-marker.js';
import * as accuracyCircleStyle from './features/accuracy-circle-style.js';
import * as addLocale from './features/add-locale.js';
import * as authToken from './features/auth-token.js';
import * as cameraLockOnPosition from './features/camera-lock-on-position.js';
import * as cameraProjectionToggle from './features/camera-projection-toggle.js';
import * as cameraShowcaseCarousel from './features/camera-showcase-carousel.js';
import * as categoryHighlight from './features/category-highlight.js';
import * as clickableSurface from './features/clickable-surface.js';
import * as computeNavigation from './features/compute-navigation.js';
import { baseURL } from './features/custom-base-url.js';
import * as customData from './features/custom-data.js';
import * as customNavigationTrace from './features/custom-navigation-trace.js';
import * as customTextLabel from './features/custom-text-label.js';
import * as customTheme from './features/custom-theme.js';
import * as dynamicPoiCrud from './features/dynamic-poi-crud.js';
import * as exploreMode from './features/explore-mode.js';
import * as floorSelector from './features/floor-selector.js';
import * as geofencing from './features/geofencing.js';
import * as gotoPoi from './features/goto-poi.js';
import * as htmlOverlayMarker from './features/html-overlay-marker.js';
import * as navigationExcludeModalities from './features/navigation-exclude-modalities.js';
import * as occupancySimulated from './features/occupancy-simulated.js';
import * as poiClick from './features/poi-click.js';
import * as resetView from './features/reset-view.js';
import * as runtimeLocale from './features/runtime-locale.js';
import * as simulatedPosition from './features/simulated-position.js';
import * as uiPartVisibility from './features/ui-part-visibility.js';

// The page: loads the venue, creates the view, and exposes window.MapBridge.
// Each feature's own code (its MapBridge commands, its view listeners, its state) is in
// features/<slug>.js; features with nothing to run in the page (custom-base-url's reload,
// native-ui-replacement, accessible-mode) reuse another feature's command or the load options.
//
// A feature module may export:
// - `mapBridge`: its native -> JS commands, merged into window.MapBridge below;
// - `onViewCreated()`: run once the view exists (view listeners, first state push to native);
// - `viewOptions()`: its createView options.
const FEATURES = [
  authToken,
  resetView,
  cameraShowcaseCarousel,
  occupancySimulated,
  poiClick,
  gotoPoi,
  floorSelector,
  computeNavigation,
  navigationExcludeModalities,
  customNavigationTrace,
  uiPartVisibility,
  exploreMode,
  simulatedPosition,
  accuracyCircleStyle,
  cameraLockOnPosition,
  clickableSurface,
  customData,
  categoryHighlight,
  dynamicPoiCrud,
  iconMarker3d,
  customTextLabel,
  htmlOverlayMarker,
  customTheme,
  runtimeLocale,
  addLocale,
  geofencing,
  cameraProjectionToggle,
];

// Native -> JS bridge: one method per command, called from Kotlin via
// WebView.evaluateJavascript() (see callBridge in app/.../bridge/CallBridge.kt). Kotlin
// JSON-encodes arguments before interpolating them into the generated script call, so what
// arrives here is already a real JS value (array/object), never a string to re-parse.
// Assembled synchronously, before the map loads, from every feature's `mapBridge`.
function assembleMapBridge(features) {
  const mapBridge = {};
  features.forEach((feature) => {
    Object.entries(feature.mapBridge ?? {}).forEach(([name, command]) => {
      if (name in mapBridge) throw new Error(`Duplicate MapBridge command: ${name}`);
      mapBridge[name] = command;
    });
  });
  return mapBridge;
}

window.MapBridge = assembleMapBridge(FEATURES);

// The Android app always appends ?vg_maphash=<mapHash> when it loads this page (see
// bridge/MapWebView.kt; the hash itself is DemoMap.MAP_HASH in model/DemoMap.kt), so the
// same bundle can display any map without a rebuild and there is no second copy here.
// `vg_maphash` is also the page-URL parameter the SDK (>= 1.0.6) itself reads to
// override `LoadOptions.vg_maphash`, so both always agree.
const hash = new URLSearchParams(window.location.search).get('vg_maphash');

// Loads a venue and creates the view, shared by the public demo map and the protected
// map sample (auth-token): every screen goes through the same view setup.
async function loadAndShowVenue(loadOptions, loaderContainer) {
  try {
    const visioOne = createVisioOne();
    setVenue(await visioOne.loadVenue(loadOptions, loaderContainer));
    const viewOptions = Object.assign({}, ...FEATURES.map((feature) => feature.viewOptions?.() ?? {}));
    setView(
      await visioOne.createView(
        container,
        venue,
        Object.keys(viewOptions).length > 0 ? viewOptions : undefined,
      ),
    );
    FEATURES.forEach((feature) => feature.onViewCreated?.());
    notify('onMapReady');
  } catch (error) {
    if (authToken.isUnauthorizedError(error)) {
      // auth-token: reported apart from other load errors — the fix is a fresh token, not a
      // different hash or server. See docs/features/auth-token.md.
      console.warn('[auth-token] UnauthorizedError:', error?.message);
      notify('onAuthorizationError', String(error?.message ?? error));
    } else {
      notify('onMapError', String(error?.message ?? error));
    }
  }
}

function main() {
  if (authToken.isProtectedMap) {
    return loadAndShowVenue(authToken.protectedLoadOptions(), container);
  }
  return loadAndShowVenue({ vg_maphash: hash, baseURL });
}

main();
