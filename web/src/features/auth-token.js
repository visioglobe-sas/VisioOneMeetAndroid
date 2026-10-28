// auth-token (code sample): loading a map protected by an authorization token,
// and refreshing that token. No screen of this demo uses it — there is no
// protected demo map. See docs/features/auth-token.md.
import { bridge } from '../bridge.js';
import { venue } from '../context.js';

// Set when the Android app loads this page for a protected map (see
// app/src/main/java/com/visioglobe/visioonemeet/sample/ProtectedMapSample.kt). The page then
// asks the native side for the protected map's load options instead of loading the public demo
// map.
export const isProtectedMap = new URLSearchParams(window.location.search).get('protected') === 'true';

// The protected map's LoadOptions, pulled synchronously from the native side
// (getProtectedMapOptions in bridge/NativeBridge.kt): a JSON object
// { vg_maphash, baseURL, authorizationToken }. `authorizationToken` is sent by the SDK as
// `Authorization: Bearer <token>` on every map request, and is only enforced by the protected
// map server given as `baseURL`.
export function protectedLoadOptions() {
  const { vg_maphash, baseURL, authorizationToken } = JSON.parse(bridge.getProtectedMapOptions());
  return { vg_maphash, baseURL, authorizationToken };
}

// The SDK rejects `loadVenue` with an `UnauthorizedError` when the token is invalid, expired or
// missing — but SDK 1.1.0 does not export that class from its package entry (so no `instanceof`
// from here) and does not set `error.name` (it stays 'Error'). Its constructor name and its
// message are the only distinguishing traits; both are checked so the test survives a bundler
// that renames classes.
export function isUnauthorizedError(error) {
  return (
    error?.constructor?.name === 'UnauthorizedError' ||
    /authorization token/i.test(String(error?.message ?? ''))
  );
}

export const mapBridge = {
  // Token refresh entry point. Tokens are short-lived; call this with a fresh one before the
  // current one expires — it applies to every subsequent map request, no reload needed.
  setAuthorizationToken(freshToken) {
    if (venue) venue.setAuthorizationToken(freshToken);
  },
};
