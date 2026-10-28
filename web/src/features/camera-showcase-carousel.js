// camera-showcase-carousel: an endless camera tour over a fixed list of POIs
// (one per stop, across 3 buildings of the shared demo map), for kiosks and
// lobby screens. The whole loop lives here, in the page — not in the native
// bottom sheet, which is disposed every time it closes (and has to be closed
// to see the map) — so there is always exactly one loop, it keeps running
// with the sheet closed, and it dies with the WebView when the screen is
// left. Every state change (Start/Stop, a touch on the map, the idle timer)
// is pushed to native via AndroidBridge.onCarouselStateChanged, so the sheet
// renders from that snapshot whenever it reopens. See
// docs/features/camera-showcase-carousel.md.
import { notify } from '../bridge.js';
import { container, venue, view } from '../context.js';

// The stops (POI ids) are passed by native on Start (DemoMap.CAROUSEL_STOP_IDS in
// model/DemoMap.kt), so the list lives in one place.
let carouselStopPoiIds = [];
const CAROUSEL_FLOOR_DURATION_MS = 800;
const CAROUSEL_POI_DURATION_MS = 2500;
const CAROUSEL_DWELL_MS = 3000;
const CAROUSEL_RESUME_AFTER_MS = 10000;
let carouselState = 'stopped'; // 'running' | 'paused' | 'stopped'
let carouselStopIndex = 0;
// Bumped on every start/pause/stop: a loop iteration only goes on while the
// generation it was started with is still the current one, so a stale loop
// (from before a pause or a Stop) can never schedule another hop.
let carouselGeneration = 0;
// The AnimationPromise of the hop in flight (null between hops/while
// dwelling), so a touch can interrupt it in place.
let carouselAnimation = null;
let carouselResumeTimer = null;
let carouselTouchListenerInstalled = false;

function reportCarouselState() {
  notify(
    'onCarouselStateChanged',
    JSON.stringify({
      state: carouselState,
      stopIndex: carouselStopIndex,
      stopCount: carouselStopPoiIds.length,
      poiId: carouselStopPoiIds[carouselStopIndex],
      resumeAfterMs: CAROUSEL_RESUME_AFTER_MS,
    }),
  );
}

function carouselDelay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// Awaits one SDK camera animation and tells whether the loop may go on: not
// if it was paused/stopped meanwhile (generation changed), nor if the
// animation was interrupted by another camera animation (`isCanceled`) —
// starting a new animation while one runs resolves the running one's promise
// with isCanceled === true.
async function carouselAwait(animation, generation) {
  carouselAnimation = animation;
  try {
    await animation;
  } finally {
    if (carouselAnimation === animation) carouselAnimation = null;
  }
  return generation === carouselGeneration && !animation.isCanceled;
}

async function runCarousel(generation) {
  while (generation === carouselGeneration) {
    const poi = venue.pois.find((p) => p.id === carouselStopPoiIds[carouselStopIndex]);
    if (poi) {
      reportCarouselState();
      // goToPOI's own doc: the caller must goToFloor first when the POI is on
      // another floor (1.1.0 did switch floors with goToPOI alone in our
      // tests, but that is not the documented contract).
      if (!(await carouselAwait(view.goToFloor(poi.floor, { duration: CAROUSEL_FLOOR_DURATION_MS }), generation))) {
        carouselInterrupted(generation);
        return;
      }
      const poiAnimation = view.goToPOI(poi, {
        duration: CAROUSEL_POI_DURATION_MS,
        easing: 'easeInOutQuad',
        orientation: { heading: carouselStopIndex * 90, pitch: 30 },
      });
      if (!(await carouselAwait(poiAnimation, generation))) {
        carouselInterrupted(generation);
        return;
      }
      await carouselDelay(CAROUSEL_DWELL_MS);
      if (generation !== carouselGeneration) return;
    }
    carouselStopIndex = (carouselStopIndex + 1) % carouselStopPoiIds.length;
  }
}

// A hop came back with isCanceled while this loop was still the current one:
// some other camera animation took over (e.g. one started by the SDK's own
// UI). Treated like a touch — pause, and resume later.
function carouselInterrupted(generation) {
  if (generation === carouselGeneration) pauseCarousel();
}

// Interrupts the hop in flight *in place*: starting a new camera animation
// cancels the running one without moving the camera to the old destination.
// A zero-length goToViewpoint on the current camera target, keeping the
// current heading and pitch ('initial'), is that "new animation".
// (AnimationPromise.cancel() would also stop the camera where it is and
// resolve with isCanceled === true; its public typing wrongly says it puts
// the target in its final state, but the SDK core implementation and live
// measurements say otherwise. This demo simply uses the new-animation route.)
function interruptCarouselAnimation() {
  if (!carouselAnimation) return;
  carouselAnimation = null;
  view.goToViewpoint(
    { positions: [view.camera.target] },
    { duration: 0, orientation: { heading: 'initial', pitch: 'initial' } },
  );
}

function scheduleCarouselResume() {
  clearTimeout(carouselResumeTimer);
  carouselResumeTimer = setTimeout(() => {
    carouselResumeTimer = null;
    if (carouselState === 'paused') startCarousel();
  }, CAROUSEL_RESUME_AFTER_MS);
}

function startCarousel() {
  clearTimeout(carouselResumeTimer);
  carouselResumeTimer = null;
  carouselState = 'running';
  runCarousel(++carouselGeneration);
}

// The resumed tour goes on from the stop *after* the one in flight/being
// shown when the tour was paused.
function pauseCarousel() {
  carouselGeneration++;
  interruptCarouselAnimation();
  carouselStopIndex = (carouselStopIndex + 1) % carouselStopPoiIds.length;
  carouselState = 'paused';
  scheduleCarouselResume();
  reportCarouselState();
}

// A user drag during an SDK camera animation does *not* interrupt it (the
// user ends up fighting the camera), and the SDK fires no "camera moved by
// the user" event — so touches are detected here, on the map container, in
// the capture phase (before the SDK's own handlers). Any touch pauses a
// running tour at once; while paused, every touch start/end restarts the
// idle countdown, so the tour only resumes after 10 s with no touch.
function onCarouselTouch() {
  if (carouselState === 'running') {
    pauseCarousel();
  } else if (carouselState === 'paused') {
    scheduleCarouselResume();
  }
}

function installCarouselTouchListener() {
  if (carouselTouchListenerInstalled) return;
  carouselTouchListenerInstalled = true;
  ['pointerdown', 'pointerup', 'pointercancel'].forEach((type) => {
    container.addEventListener(type, onCarouselTouch, { capture: true });
  });
}

export const mapBridge = {
  // Start — from the first stop after a Stop, or right away from the next
  // stop while paused. A no-op while already running, so a double tap never
  // starts a second loop.
  carouselStart(stopPoiIds) {
    if (!venue || !view || carouselState === 'running') return;
    if (Array.isArray(stopPoiIds) && stopPoiIds.length) carouselStopPoiIds = stopPoiIds;
    if (!carouselStopPoiIds.length) return;
    if (carouselState === 'stopped') carouselStopIndex = 0;
    installCarouselTouchListener();
    startCarousel();
  },
  // Stop. Only stops *scheduling* hops — the hop in
  // flight (if any) is left to end on its own, so the camera settles on a
  // meaningful view instead of freezing mid-flight (cancel() is not used:
  // it would stop the camera where it is, mid-flight).
  carouselStop() {
    carouselGeneration++;
    clearTimeout(carouselResumeTimer);
    carouselResumeTimer = null;
    carouselState = 'stopped';
    reportCarouselState();
  },
};
