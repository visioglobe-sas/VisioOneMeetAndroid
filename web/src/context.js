// The page's loaded venue and view, shared by every feature module.
//
// ES module bindings are live: a module that does `import { venue, view } from '../context.js'`
// always reads their current value — null until main.js has loaded the map, then the loaded
// venue/view. Only main.js sets them.
export let venue = null;
export let view = null;

// The map's container element (see index.html).
export const container = document.querySelector('#content');

export function setVenue(loadedVenue) {
  venue = loadedVenue;
}

export function setView(createdView) {
  view = createdView;
}
