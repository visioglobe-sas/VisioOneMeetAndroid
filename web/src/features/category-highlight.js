// category-highlight: highlights every POI of a category. See
// docs/features/category-highlight.md.
import { notify } from '../bridge.js';
import { venue } from '../context.js';

// The category id last highlighted via `highlightCategory`, so
// `clearCategoryHighlight` knows which POIs' surfaces to revert, and so a
// second `highlightCategory` call can revert it before highlighting the new
// selection (only one category highlighted at a time).
let highlightedCategoryId = null;

// Any clearly visible color works here — this isn't an SDK-mandated value,
// just a demo choice.
const CATEGORY_HIGHLIGHT_COLOR = '#FF6B00';

// `'initial'` (not `undefined`/omitting the key) is the SDK's own documented
// sentinel on SurfaceUpdateOptions.color to restore a surface's
// bundle-defined color — a no-op omission would not do the same thing.
function clearHighlightedCategory() {
  if (!venue || !highlightedCategoryId) return;
  venue.pois
    .filter((poi) => poi.categories.some((category) => category.id === highlightedCategoryId))
    .forEach((poi) => {
      poi.surfaces.forEach((surface) => {
        venue.updateSurface(surface, { color: 'initial' });
      });
    });
  highlightedCategoryId = null;
}

export const mapBridge = {
  // Sends the venue's full category list back to native in one round trip,
  // requestId echoed back so native can match this response to whichever
  // call triggered it, same convention as loadCustomData/resolvePositions.
  // Category is `{ readonly id: string }` — a raw internal identifier (a
  // numeric string on this shared demo map, e.g. "1".."11"), not itself
  // human-readable. The display name comes from
  // venue.translator.translateCategory(), same Translator lookup already
  // used for POI/floor names elsewhere. `id` is still what
  // filtering/highlighting must use; `label` is for display only.
  getCategories(requestId) {
    if (!venue) return;
    const locale = venue.currentLocale;
    const categories = venue.categories.map((category) => ({
      id: category.id,
      label: venue.translator.translateCategory(category, locale).name || category.id,
    }));
    notify('onCategoriesLoaded', requestId, JSON.stringify(categories));
  },
  // Highlights every POI belonging to categoryId by recoloring its
  // surface(s) via venue.updateSurface. There is no dedicated "highlight by
  // category" SDK method — this is built from the venue.pois /
  // poi.categories / poi.surfaces primitives. Reverts any previously
  // highlighted category first (see highlightedCategoryId above), so at
  // most one category is highlighted at a time. POIs with no surfaces
  // (e.g. point/marker-only outdoor POIs, `type: -1`) are simply
  // unaffected — expected, not a bug.
  highlightCategory(categoryId) {
    if (!venue) return;
    clearHighlightedCategory();
    venue.pois
      .filter((poi) => poi.categories.some((category) => category.id === categoryId))
      .forEach((poi) => {
        poi.surfaces.forEach((surface) => {
          venue.updateSurface(surface, { color: CATEGORY_HIGHLIGHT_COLOR });
        });
      });
    highlightedCategoryId = categoryId;
  },
  // Reverts the highlight applied by highlightCategory, if any.
  clearCategoryHighlight() {
    clearHighlightedCategory();
  },
};
