// custom-theme: switches the 3D scene's lighting theme and recolors the SDK's
// own UI buttons, reporting the whole theme state to native after every
// command. See docs/features/custom-theme.md.
import { notify } from '../bridge.js';
import { view } from '../context.js';

// `?enableShadows=true` is appended by the Android app on this feature's
// screen only — `setTheme` changes the scene lights, which are only rendered
// with shadows/lights enabled (no visible effect otherwise). Every other
// screen omits it and keeps the SDK default (off, shadows being
// "computationally expensive").
const enableShadows = new URLSearchParams(window.location.search).get('enableShadows') === 'true';

// This feature's createView options (merged by main.js).
export function viewOptions() {
  return enableShadows ? { enableShadows: true } : {};
}

// The SDK defaults (the View typings' `@defaultValue`s, and the theme a
// view starts with). There is no current-theme getter in the SDK, so the
// applied theme is tracked here.
const DEFAULT_THEME = 'day';
const DEFAULT_ACCENT_COLOR = '#0094F0';
const DEFAULT_TEXT_ACCENT_COLOR = '#FFFFFF';
let currentTheme = DEFAULT_THEME;

// Reports the whole custom-theme state to native after every command — the
// JS side is the single source of truth, so the native sheet can be closed
// (to tap a place and see its card) and reopened without losing track of
// the current selection. `status` names what just happened.
function reportThemeState(status, extra) {
  notify(
    'onThemeStateChanged',
    JSON.stringify({
      status,
      themes: view?.themes ?? [],
      currentTheme,
      accentColor: String(view?.accentColor ?? DEFAULT_ACCENT_COLOR),
      textAccentColor: String(view?.textAccentColor ?? DEFAULT_TEXT_ACCENT_COLOR),
      ...extra,
    }),
  );
}

export const mapBridge = {
  // Reports the theme list + current theme/accent colors so the native sheet
  // can build its theme control from `view.themes` (never a hardcoded list)
  // and render its current selection. Called when the sheet opens.
  getThemeState() {
    if (!view) return;
    reportThemeState('loaded');
  },
  // Applies a scene theme. Only changes the 3D lights, which are only
  // rendered when the view was created with `enableShadows: true` (this
  // screen's `?enableShadows=true`). Throws for an id not in `view.themes`.
  setTheme(themeName) {
    if (!view) return;
    try {
      view.setTheme(themeName);
      currentTheme = themeName;
      reportThemeState('theme-applied');
    } catch (error) {
      reportThemeState('error', { message: String(error?.message ?? error) });
    }
  },
  // Recolors the SDK's own UI buttons live (POI card "Go", "Add a step"…).
  // Throws for a value that isn't a hex / rgb(a) / hsl color.
  setAccentColor(color) {
    if (!view) return;
    try {
      view.accentColor = color;
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
  // Back to the SDK defaults: the 'day' theme and the default accent colors.
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
