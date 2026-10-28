// explore-mode: switches the SDK's building-exploration mode, and reports
// every mode change back to native. See docs/features/explore-mode.md.
import { notify } from '../bridge.js';
import { view } from '../context.js';

// Forwards the SDK's 'exploremodechanged' event to native as a bare mode
// string, so the explore-mode control can keep its highlighted option in
// sync even when the mode changes another way than its own buttons — most
// notably a click while in "building" mode, which the SDK auto-switches to
// "floor" (see the SDK's ExploreMode typings).
function onExploreModeChanged(event) {
  notify('onExploreModeChanged', event.currentExploreMode);
}

export function onViewCreated() {
  view.addEventListener('exploremodechanged', onExploreModeChanged);
  // Seeds the explore-mode control's initial highlight — there is no
  // "onReady" data push for it like onFloorsReady, since currentExploreMode
  // always starts at "global" and needs no venue data to report.
  notify('onExploreModeChanged', view.currentExploreMode);
}

export const mapBridge = {
  // Switches the mode by assigning view.currentExploreMode. mode is one of
  // exactly 3 case-sensitive values ('global', 'building', 'floor' — see the
  // SDK's ExploreMode type); an unrecognized value is rejected by the SDK
  // itself, this bridge does no validation of its own, same convention as
  // setUIPartVisible. The resulting change (including any change triggered
  // another way, e.g. a click while in "building" mode) is reported back via
  // onExploreModeChanged above, so this call itself does not report
  // anything — same "fire the command, listen for the echo" split as
  // goToFloor/onFloorChanged.
  setExploreMode(mode) {
    if (!view) return;
    view.currentExploreMode = mode;
  },
};
