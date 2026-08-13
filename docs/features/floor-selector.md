# Sélection d'étage / bâtiment

## Description

Ajoute une liste native de boutons — un par étage du bâtiment courant (le premier bâtiment du venue aujourd'hui) — qui appellent `view.goToFloor(floor)` pour déplacer la caméra sur l'étage tapé, et surlignent l'étage actif. C'est la quatrième commande ajoutée au pont natif→JS (`window.MapBridge` + `WebView.evaluateJavascript`), après `goToGlobal` (`reset-view`), `updateOccupancy` (`occupancy-simulated`) et `goToPlace`/`clearPlace` (`goto-poi`) — même mécanique, pas de nouvelle plomberie côté natif.

Contrairement aux features précédentes, celle-ci a aussi besoin d'un pont **JS→natif** de données (pas seulement d'événements) : la liste des étages n'existe que côté JS (`venue.venueLayout.buildings[0].floors`), donc `web/src/main.js` la pousse une fois vers `AndroidBridge.onFloorsReady` juste après le chargement du venue, puis tient l'étage actif à jour via `AndroidBridge.onFloorChanged`, câblé sur l'événement SDK `currentfloorchanged`. C'est le même sens de communication que `AndroidBridge.onPoiClick` (`poi-click`), pas un nouveau mécanisme.

Le SDK affiche déjà **son propre** sélecteur d'étage par défaut sur la carte, sans aucun code applicatif (`UIPart` `'floorSelector'`, visible/masquable via `view.setUIPartVisible('floorSelector', false)`). Cette feature démontre qu'une app peut piloter le changement d'étage elle-même — utile pour un client qui masque l'UI par défaut du SDK (`showUI = false` ou `setUIPartVisible`) et veut son propre contrôle, intégré à sa propre charte graphique. Voir "Points d'attention" plus bas.

## Step by step

1. **Ajouter `goToFloor` à `window.MapBridge`** (`web/src/main.js`), à côté de `goToPlace`/`clearPlace` :
   ```js
   window.MapBridge = {
     // ...
     goToFloor(buildingId, floorId) {
       if (!venue || !view) return;
       const building = venue.venueLayout.buildings.find((b) => b.id === buildingId);
       if (!building) return;
       const floor = building.floors.find((f) => f.id === floorId);
       if (!floor) return;
       view.goToFloor(floor);
     },
   };
   ```
   `goToFloor` prend un objet `Floor` du SDK, pas un simple id — d'où la double résolution `buildings.find` puis `building.floors.find` avant d'appeler `view.goToFloor(floor)`.

2. **Pousser la liste des étages vers le natif une fois le venue chargé**, avec le nom traduit de chaque étage (`venue.translator.translateFloor`, même convention que `translatePOI` dans `poi-click`) et l'étage actif au moment du chargement (`view.currentFloor`) :
   ```js
   function buildingFloorsPayload(building) {
     const locale = venue.currentLocale;
     return {
       buildingId: building.id,
       currentFloorId: view.currentFloor?.id ?? null,
       floors: building.floors.map((floor) => ({
         id: floor.id,
         name: venue.translator.translateFloor(floor, locale).name,
         levelIndex: floor.levelIndex,
       })),
     };
   }

   // dans main(), après la création de la vue :
   const building = venue.venueLayout.buildings[0];
   if (building) {
     bridge?.onFloorsReady(JSON.stringify(buildingFloorsPayload(building)));
   }
   ```

3. **Tenir l'étage actif à jour** en écoutant l'événement SDK `currentfloorchanged` et en relayant le nouvel id (ou `null`) vers le natif :
   ```js
   function onCurrentFloorChanged(event) {
     bridge?.onFloorChanged(event.newFloor?.id ?? null);
   }
   // dans main() :
   view.addEventListener('currentfloorchanged', onCurrentFloorChanged);
   ```
   Les ids de `Floor` sont uniques sur tout le venue (documenté dans le typing SDK `Floor.d.ts`), donc comparer ce seul id côté natif suffit à savoir si l'étage actif fait partie de la liste affichée — pas besoin de comparer aussi `buildingId`.

4. **Reconstruire le bundle web** — `cd web && npm run build`. Même piège que pour toutes les features précédentes : Gradle ne le fait jamais automatiquement, `app/src/main/assets/www/` doit être régénéré à la main.

5. **Ajouter les méthodes du pont côté Kotlin** (`FeatureMapScreen.kt`, classe `MapBridge`) :
   ```kotlin
   private class MapBridge(
       // ...
       private val notifyFloorsReady: (String) -> Unit,
       private val notifyFloorChanged: (String?) -> Unit,
   ) {
       @JavascriptInterface
       fun onFloorsReady(payload: String) = notifyFloorsReady(payload)

       @JavascriptInterface
       fun onFloorChanged(floorId: String?) = notifyFloorChanged(floorId)
   }
   ```
   Et dans `FeatureMapScreen`, un état `floorSelector` mis à jour par ces deux callbacks, propagé à `sheetContent` comme troisième argument (après `webView` et `lastPoiClick`) :
   ```kotlin
   var floorSelector by remember { mutableStateOf(FloorSelectorState()) }
   // ...
   notifyFloorsReady = { payload -> mainHandler.post { floorSelector = parseFloorsReadyPayload(payload) } },
   notifyFloorChanged = { floorId -> mainHandler.post { floorSelector = floorSelector.copy(currentFloorId = floorId) } },
   ```

6. **Ajouter l'extension d'appel et le modèle côté `FeatureOverlays.kt`** :
   ```kotlin
   private fun WebView.goToFloor(buildingId: String, floorId: String) {
       val script = "window.MapBridge.goToFloor(${JSONObject.quote(buildingId)}, ${JSONObject.quote(floorId)})"
       evaluateJavascript(script, null)
   }

   data class FloorInfo(val id: String, val name: String, val levelIndex: Int)

   data class FloorSelectorState(
       val buildingId: String? = null,
       val floors: List<FloorInfo> = emptyList(),
       val currentFloorId: String? = null,
   )
   ```
   `goToFloor` est la première commande du pont à prendre **deux** arguments scalaires (contre un seul pour `goToPlace`) — chacun encodé séparément avec `JSONObject.quote()`, sans tableau englobant.

7. **Ajouter le contenu de la bottom sheet** (`FloorSelectorOverlay`) — une colonne de boutons, triés du plus haut étage au plus bas (`sortedByDescending { it.levelIndex }`), l'étage actif rendu en `Button` plein (désactivé) et les autres en `OutlinedButton` cliquables :
   ```kotlin
   @Composable
   fun FloorSelectorOverlay(webView: WebView?, floorSelector: FloorSelectorState) {
       Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
           floorSelector.floors.sortedByDescending { it.levelIndex }.forEach { floor ->
               val isCurrent = floor.id == floorSelector.currentFloorId
               if (isCurrent) {
                   Button(onClick = {}, enabled = false) { Text(floor.name) }
               } else {
                   OutlinedButton(onClick = { floorSelector.buildingId?.let { webView?.goToFloor(it, floor.id) } }) {
                       Text(floor.name)
                   }
               }
           }
       }
   }
   ```

8. **Ajouter l'entrée au registre de features** (`Feature.kt` + `strings.xml`/`strings-fr.xml`) et **câbler la route** dans `MainActivity.kt` :
   ```kotlin
   Feature.FloorSelector -> FloorSelectorOverlay(webView, floorSelector)
   ```

## Points d'attention

- **`goToFloor` prend un objet `Floor`, pas un id** — contrairement à `goToPOI` qui prend directement le `POI` déjà résolu côté JS depuis un id, ici le pont doit faire la résolution lui-même (`buildings.find` puis `floors.find`) avant d'appeler le SDK. Un `buildingId`/`floorId` inconnu est un no-op silencieux, même convention que les autres commandes du pont.
- **Overlap avec le sélecteur d'étage natif du SDK** — le SDK affiche déjà son propre widget de sélection d'étage/bâtiment sur la carte (`UIPart = 'floorSelector'`), sans aucun code applicatif. Cette feature n'est donc pas nécessaire pour qu'un client puisse changer d'étage ; elle démontre qu'une app peut piloter le changement elle-même via `view.goToFloor`, pour les cas où le client masque l'UI par défaut du SDK (`view.setUIPartVisible('floorSelector', false)`, ou `view.showUI = false` pour tout masquer) et veut un contrôle intégré à sa propre charte graphique — un menu custom, une pilule flottante, etc. C'est explicitement pour ça que l'événement `currentfloorchanged` est relayé (voir point suivant) : même si l'utilisateur change d'étage via le widget SDK resté visible, la liste native reste synchronisée.
- **`currentfloorchanged` est écouté pour rester synchronisé même sans interaction avec cette UI** — pas seulement pour refléter les taps sur les boutons natifs eux-mêmes (ceux-ci pourraient se contenter d'un état local optimiste), mais aussi tout changement d'étage déclenché autrement : le widget SDK par défaut (si laissé visible), ou un `goToPOI` sur un POI situé à un autre étage (la doc du SDK précise que l'appelant est responsable d'appeler `goToFloor` avant `goToPOI`, sinon `currentFloor`/`currentBuilding` restent inchangés côté SDK — mais l'événement, lui, se déclenche dès que l'étage courant change réellement).
- **Un seul bâtiment démontré** — `web/src/main.js` n'expose que `venue.venueLayout.buildings[0]`. Un venue multi-bâtiments a autant de listes d'étages que de bâtiments (`Building.floors`), et `goToFloor(buildingId, floorId)` accepte déjà un `buildingId` pour cette raison, mais l'UI native ne propose pas encore de sélecteur de bâtiment — à ajouter si un client a un venue multi-bâtiments à démontrer (bouton "changer de bâtiment" au-dessus de la liste, ou tabs, selon le nombre de bâtiments).
- **Pas de nom d'étage garanti** — `venue.translator.translateFloor(floor, locale).name` retombe sur une chaîne vide si le venue n'a pas de traduction pour cet étage/cette locale (documenté par le SDK). Cette implémentation affiche alors un bouton avec un libellé vide plutôt que l'id brut de l'étage — un vrai client voudra probablement un fallback plus visible (l'id, ou "Level N" à partir de `levelIndex`).
- **Reconstruire le bundle web est obligatoire** avant que la liste n'affiche quoi que ce soit — même piège que toutes les features précédentes : Gradle ne rebuild jamais `web/` automatiquement.

## Pour aller plus loin

- `goToFloor` accepte un second argument `AnimationOptions` (`duration`, `easing`) — non utilisé ici, comme pour `goToGlobal` (`reset-view`).
- `goToBuilding(building, animationOptions)` existe aussi côté SDK (utilise l'étage par défaut du bâtiment, `Building.defaultFloorID`) — pertinent pour le sélecteur de bâtiment mentionné ci-dessus si ce venue en a plusieurs.
- Ce pont natif→JS reste le point d'ancrage pour les prochains fondamentaux encore ❌ sur cette plateforme (calculer un itinéraire) — voir le `ROADMAP.md` du hub (`VisioOneHub`).
