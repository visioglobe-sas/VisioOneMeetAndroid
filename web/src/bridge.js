// Web -> native: the bridge object injected by the Android app via
// WebView.addJavascriptInterface (class NativeBridge in app/.../bridge/NativeBridge.kt), exposed
// as window.AndroidBridge. It holds the map's load state callbacks (onMapReady/onMapError) plus
// one callback per feature result or state change (onPoiClick, onNavigationError,
// onCustomDataLoaded, ...).
export const bridge = window.AndroidBridge;

// Calls one native callback, e.g. notify('onCustomDataLoaded', requestId, json). Arguments are
// strings, numbers and booleans only: structured payloads are sent as JSON strings and parsed on
// the Kotlin side. A no-op when the page runs outside the app (no window.AndroidBridge).
export function notify(callback, ...args) {
  bridge?.[callback](...args);
}
