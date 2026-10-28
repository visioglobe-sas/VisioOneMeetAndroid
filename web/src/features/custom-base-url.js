// custom-base-url: the map server the venue is loaded from (LoadOptions.baseURL).
// The Android app appends `?baseUrl=<value>` when it loads this page; every
// other screen omits it, so it resolves to the SDK's own default
// LoadOptions.baseURL — a no-op for every other feature, not just a fallback.
// The value can't change on a loaded venue: the app recreates the page to
// switch servers. See docs/features/custom-base-url.md.
const DEFAULT_BASE_URL = 'https://mapserver.visioglobe.com/';

export const baseURL = new URLSearchParams(window.location.search).get('baseUrl') || DEFAULT_BASE_URL;
