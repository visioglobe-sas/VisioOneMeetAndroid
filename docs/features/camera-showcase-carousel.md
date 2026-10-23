# Camera showcase

## Description

An automatic, endless camera tour of the map, for kiosks and lobby screens: the camera flies from one POI to the next (four stops spread over three buildings of the demo map), pauses on each for a few seconds, and starts over. Touching the map pauses the tour at once, without the camera jumping anywhere, and the tour resumes on its own after 10 seconds without any touch.

It is built on the SDK's camera animations, `view.goToFloor()` and `view.goToPOI()`, and on the `AnimationPromise` they return. A zero-length `view.goToViewpoint()` interrupts a hop in place when the map is touched.

## SDK usage

The whole loop runs in the web page (`web/src/main.js`), not on the native side. The page lives exactly as long as the map screen, so there is always exactly one loop. It keeps running while native UI is shown over the map, and it ends with the `WebView`.

JS side: one hop is `goToFloor` then `goToPOI`, then a dwell. After every `await`, the loop checks that it is still the current run, and that the animation was not interrupted by another camera animation:

```js
const STOPS = ['B4-UL00-ID0009', 'B1-LL01-ID0152', 'B3-UL00-ID0064', 'B4-UL03-ID0015'];
let state = 'stopped'; // 'running' | 'paused' | 'stopped'
let stopIndex = 0;
let generation = 0;     // bumped on every start/pause/stop: a stale loop exits at its next check
let inFlight = null;    // AnimationPromise of the hop in flight, if any

async function step(animation, gen) {
  inFlight = animation;
  try { await animation; } finally { if (inFlight === animation) inFlight = null; }
  return gen === generation && !animation.isCanceled;
}

async function run(gen) {
  while (gen === generation) {
    const poi = venue.pois.find((p) => p.id === STOPS[stopIndex]);
    // Documented contract: goToFloor first when the POI is on another floor.
    if (!(await step(view.goToFloor(poi.floor, { duration: 800 }), gen))) return;
    if (!(await step(view.goToPOI(poi, {
      duration: 2500,
      easing: 'easeInOutQuad',                          // an animejs Penner easing name
      orientation: { heading: stopIndex * 90, pitch: 30 }, // degrees
    }), gen))) return;
    await new Promise((r) => setTimeout(r, 3000));      // dwell
    if (gen !== generation) return;
    stopIndex = (stopIndex + 1) % STOPS.length;
  }
}
```

Pausing on touch. Listen on the map container in the capture phase, so your handler runs before the SDK's own. Then interrupt the hop in flight by starting a zero-length camera animation on the current target, keeping the current heading and pitch:

```js
container.addEventListener('pointerdown', () => {
  if (state !== 'running') return;
  generation++;                       // the running loop exits at its next check
  if (inFlight) {
    view.goToViewpoint(
      { positions: [view.camera.target] },
      { duration: 0, orientation: { heading: 'initial', pitch: 'initial' } },
    );                                // interrupts the hop where it is (see "Things to know")
  }
  stopIndex = (stopIndex + 1) % STOPS.length; // resume from the next stop
  state = 'paused';
  // ...(re)start a 10 s timer, also restarted on pointerup/pointercancel, that calls run(++generation)
}, { capture: true });
```

Stop works the same way, but without the interruption: bump `generation`, clear the resume timer, and set `state = 'stopped'`. This demo lets the hop in flight finish, so the camera settles on a meaningful view instead of freezing halfway between two buildings.

Signatures (SDK 1.1.0 typings):

- `view.goToFloor(floor: Floor, animationOptions?: AnimationOptions): AnimationPromise`
- `view.goToPOI(poi: POI, animationOptions?: AnimationOptions): AnimationPromise`
- `view.goToViewpoint({ positions: Position[], targetAltitude?: number }, animationOptions?: AnimationOptions): AnimationPromise`
- `AnimationOptions`: `duration?` (ms), `easing?` (animejs Penner function name), `padding?`, `orientation?: { heading?: number | 'initial', pitch?: number | 'initial' }` (degrees; `'initial'` keeps the current value).
- `AnimationPromise extends Promise<void>`: `isCanceled: boolean`, `cancel(): void`.
- `view.camera.target: Position` (readonly): where the camera is currently looking on the current floor.

Native side (Kotlin): two commands, and one callback that reports every state change. A state change can also happen in the page on its own, after a touch or when the idle timer fires, so the native UI should render the last reported snapshot rather than keep its own copy:

```kotlin
// The stop POI ids live on the native side (DemoMap.CAROUSEL_STOP_IDS in model/DemoMap.kt) and are
// passed on Start; the page keeps them as its STOPS list.
val stops = JSONArray(listOf("B4-UL00-ID0009", "B1-LL01-ID0152", "B3-UL00-ID0064", "B4-UL03-ID0015"))
webView.evaluateJavascript("window.MapBridge.carouselStart($stops)", null) // from the first stop, or right away when paused
webView.evaluateJavascript("window.MapBridge.carouselStop()", null)

class MapBridge(private val onCarouselState: (String) -> Unit /* , ... */) {
    // {"state":"running"|"paused"|"stopped","stopIndex":1,"stopCount":4,"poiId":"B1-LL01-ID0152","resumeAfterMs":10000}
    @JavascriptInterface
    fun onCarouselStateChanged(stateJson: String) = onCarouselState(stateJson) // called on a binder thread: post to main
}
```

## Things to know

- **A user drag does not interrupt an SDK camera animation.** The animation keeps running to its end, so the user ends up fighting the camera. The SDK fires no "camera moved by the user" event either. To let a touch win, detect it yourself, for example with `pointerdown` on the map container in the capture phase, and stop driving the camera.
- **Starting a new camera animation interrupts the running one in place.** The running animation's promise resolves at once with `isCanceled === true`, and the camera continues from where it was, without moving to the old destination. Check `isCanceled` after every `await` in a loop: otherwise, an animation started elsewhere (your own code or the SDK's default UI) would be followed by your next hop as if nothing had happened. This is what the pause relies on. With a zero-length `goToViewpoint` on `view.camera.target` and `orientation: { heading: 'initial', pitch: 'initial' }`, the camera stays within one rendered frame of where it was when the finger went down. Measured on the emulator, mid-hop (SDK 1.1.0): the target moved about 12 m, the heading changed by about 3°, and the altitude dropped from 184.6 m to 181.6 m, while the hop's destination was 10 m above the POI with heading 90°. After that, the user's drag pans the map normally.
- **`AnimationPromise.cancel()` stops the camera where it is.** It resolves the promise right away with `isCanceled === true` and leaves the camera in the state it is in at the moment of the call. The public typing of `cancel()` says it "puts the animation target in its final state": that wording is wrong. The SDK core implementation (1.1.0) documents and implements the opposite, and live measurements confirm it: inside this Android `WebView`, after `cancel()` mid-`goToPOI` and mid-`goToFloor`, the camera stayed where it was (for example, 188.8 m altitude and 29° heading, against a destination at 10 m and 90°), and `isCanceled` was `true`. This demo pauses with a zero-length `goToViewpoint` instead (see above); both stop the camera in place.
- **Known SDK issue (1.1.0): the camera can break after a drag on an underground floor.** Observed on another platform: after `goToPOI` with `orientation: { heading: 90 or 180, pitch: 45 }` on the underground POI `B1-LL01-ID0152` (floor `B1-LL1`, camera at its 10 m minimum altitude), one or two user drags turned `view.camera.heading` into `NaN` and `pitch` into `0`, and the map rendered black until the view was reloaded. It is not systematically reproducible; pitch 30 did not trigger it in the attempts made, so the demo uses `pitch: 30`.
- **Call `goToFloor` before `goToPOI`** when the POI is on another floor, or in another building. `goToPOI`'s own documentation says the caller is responsible for this, otherwise the current floor/building stays active. In 1.1.0, `goToPOI` alone did switch floors in our tests, but that is not the documented contract.
- **`orientation`** sets the heading and pitch the camera ends the animation with, in degrees (`heading` relative to North; `pitch` 0 = straight down, 90 = horizon). `'initial'` keeps the current value. Without it, the SDK picks a context-dependent default. Here, each stop gets its own heading (0°, 90°, 180°, 270°), so the tour turns around the venue.
- **`duration` is a target, not a guarantee.** On the emulator, an 800 ms `goToFloor` took 800 to 1,400 ms to resolve, and a 2,500 ms `goToPOI` took 2,500 to 2,950 ms. To give each stop the same on-screen time, chain on the promises, not on timers.

## Learn more

- [Go to POI](goto-poi.md): a single `view.goToPOI()` call with `orientation` and `padding`.
- [Floor Selector](floor-selector.md): `view.goToFloor()` and the `currentfloorchanged` event.
- [Reset View](reset-view.md): `view.goToGlobal()`, the other `AnimationPromise`-returning camera move.
