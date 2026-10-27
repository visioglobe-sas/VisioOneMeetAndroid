# Custom Theme

## Description

White-label styling of the map, live, without reloading anything:

- **3D scene theme** — `view.themes` lists the lighting themes the SDK ships (`['day', 'night', 'goldenHour']` in `@visioglobe/visioone` 1.0.9 and 1.1.0) and `view.setTheme(name)` applies one. A theme only changes the scene's lights (directional + ambient light color/position, and a material tint): `night` gives a bluish, darker scene, `goldenHour` a warm, muted one. It never touches the SDK's UI.
- **UI accent colors** — `view.accentColor` and `view.textAccentColor` are settable properties that recolor the SDK's own UI buttons (the POI card's "Go" button, "Add a step", "Remove step"…): background and text respectively.

The view must be created with `enableShadows: true` for themes to have any visible effect — see "Things to know".

The SDK public API (as of 1.1.0) has no option to change the UI font.

## SDK usage

```js
// web/src/main.js

// The Android app appends ?enableShadows=true to this page's URL on the
// custom-theme screen only.
const enableShadows = new URLSearchParams(window.location.search).get('enableShadows') === 'true';

const DEFAULT_THEME = 'day';
const DEFAULT_ACCENT_COLOR = '#0094F0';
const DEFAULT_TEXT_ACCENT_COLOR = '#FFFFFF';
let currentTheme = DEFAULT_THEME; // no getter in the SDK: track it yourself

// Reports the whole state to native after every command.
function reportThemeState(status, extra) {
  bridge?.onThemeStateChanged(JSON.stringify({
    status,
    themes: view?.themes ?? [],
    currentTheme,
    accentColor: String(view?.accentColor ?? DEFAULT_ACCENT_COLOR),
    textAccentColor: String(view?.textAccentColor ?? DEFAULT_TEXT_ACCENT_COLOR),
    ...extra,
  }));
}

async function main() {
  const visioOne = createVisioOne();
  venue = await visioOne.loadVenue({ vg_maphash: hash, baseURL });
  view = await visioOne.createView(container, venue, enableShadows ? { enableShadows: true } : undefined);
  // ...
}

window.MapBridge = {
  // ...
  getThemeState() {
    if (!view) return;
    reportThemeState('loaded');
  },
  setTheme(themeName) {
    if (!view) return;
    try {
      view.setTheme(themeName); // throws for an id not in view.themes
      currentTheme = themeName;
      reportThemeState('theme-applied');
    } catch (error) {
      reportThemeState('error', { message: String(error?.message ?? error) });
    }
  },
  setAccentColor(color) {
    if (!view) return;
    try {
      view.accentColor = color; // throws for a value that isn't a color
      reportThemeState('accent-applied');
    } catch (error) {
      reportThemeState('error', { message: String(error?.message ?? error) });
    }
  },
  setTextAccentColor(color) {
    if (!view) return;
    try {
      view.textAccentColor = color;
      reportThemeState('text-accent-applied');
    } catch (error) {
      reportThemeState('error', { message: String(error?.message ?? error) });
    }
  },
  resetTheme() {
    if (!view) return;
    try {
      view.setTheme(DEFAULT_THEME);
      currentTheme = DEFAULT_THEME;
      view.accentColor = DEFAULT_ACCENT_COLOR;
      view.textAccentColor = DEFAULT_TEXT_ACCENT_COLOR;
      reportThemeState('reset');
    } catch (error) {
      reportThemeState('error', { message: String(error?.message ?? error) });
    }
  },
};
```

Native side — the page URL carries the creation option, and each command is a one-line script call (string arguments are JSON-quoted before being interpolated into the script):

```kotlin
// Loading the bridge page with shadows/lights enabled
webView.loadUrl(
    "https://appassets.androidx.local/assets/www/index.html" +
        "?vg_maphash=$mapHash&enableShadows=true",
)

// app/src/main/java/com/visioglobe/visioonemeet/ui/FeatureOverlays.kt — native bridge wrappers
private fun WebView.getThemeState() {
    evaluateJavascript("window.MapBridge.getThemeState()", null)
}

private fun WebView.setTheme(themeName: String) {
    evaluateJavascript("window.MapBridge.setTheme(${JSONObject.quote(themeName)})", null)
}

private fun WebView.setAccentColor(color: String) {
    evaluateJavascript("window.MapBridge.setAccentColor(${JSONObject.quote(color)})", null)
}

private fun WebView.setTextAccentColor(color: String) {
    evaluateJavascript("window.MapBridge.setTextAccentColor(${JSONObject.quote(color)})", null)
}

private fun WebView.resetTheme() {
    evaluateJavascript("window.MapBridge.resetTheme()", null)
}
```

Every outcome comes back through one bridge method, carrying a JSON snapshot such as `{"status":"theme-applied","themes":["day","night","goldenHour"],"currentTheme":"night","accentColor":"#E53935","textAccentColor":"#FFFFFF"}` — build your theme picker from `themes`, never from a hardcoded list:

```kotlin
// FeatureMapScreen.kt — exposed as window.AndroidBridge via addJavascriptInterface
@JavascriptInterface
fun onThemeStateChanged(stateJson: String) = notifyThemeStateChanged(stateJson)
```

`@JavascriptInterface` methods run on a WebView background thread: post to the main thread before touching UI state.

The SDK API exercised:

- `visioOne.createView(container: HTMLElement, venue: Venue, options?: ViewCreateOptions): Promise<View>` — `ViewCreateOptions.enableShadows?: boolean` ("enable shadows and lights rendering").
- `view.themes: string[]` (read-only).
- `view.setTheme(themeName: string): void` — `themeName` must be one of `view.themes`.
- `view.accentColor: Color` (default `'#0094f0'`) and `view.textAccentColor: Color` (default `'#ffffff'`) — hex, `rgb()`/`rgba()` or `hsl()` strings.

## Things to know

- **`setTheme` has no visible effect unless the view was created with `enableShadows: true`.** Themes only change the scene lights, which are only rendered with shadows/lights enabled: without the option, switching themes renders pixel-identical frames (even at `quality: 'high'`). `enableShadows` is a creation-time option — it cannot be turned on for an existing view — and the SDK documents shadows as "computationally expensive", so enable it only where you actually use themes and check performance on your low-end target devices.
- **`setTheme` with an unknown id throws** `Cannot apply given theme <id> as it isn't registered.` Only pass ids read from `view.themes`.
- **There is no getter for the current theme** (as of 1.1.0) — track it yourself. A view always starts on `day`.
- **Coming back to `day` doesn't exactly restore the initial lighting.** With `enableShadows: true`, the scene right after load and the scene after switching to another theme and back to `day` are not pixel-identical (the second is slightly darker); it's the same result whichever theme you came from. Calling `setTheme('day')` on a fresh view is a no-op. Only a view re-creation gets the exact initial look back.
- **Accent colors apply live to the SDK's UI** — no re-render or reload needed, even while a POI card is open. They do **not** change the color of a selected surface on the map: that's a separate property, `view.surfaceSelectionColor`.
- **An invalid color throws** `Invalid color format provided, it should be hex color, rgba color or hsl color.` and leaves the previous value in place.
- **Reading a color back returns the string you set** (e.g. `'#E53935'` as-is), while the defaults read back in lowercase (`'#0094f0'`, `'#ffffff'`) — compare case-insensitively.

## Learn more

- Both accent colors can also be set at load time, without any code, via the `accentColor` / `textAccentColor` page-URL parameters the SDK reads (`URLParameters`, e.g. `accentColor=rgb(252,3,15)`).
- `view.surfaceSelectionColor` — the color of a selected POI's surfaces, not covered by the accent colors.
