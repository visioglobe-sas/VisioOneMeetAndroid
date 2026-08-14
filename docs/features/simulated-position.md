# Position simulée

## Description

Anime une position simulée (un point + un cercle de précision) qui va-et-vient entre deux POI, via `view.injectTrackedPosition(positionTrackerOptions)` — la même API que celle utilisée par une vraie intégration de positionnement indoor (BLE/Wi-Fi/UWB), mais nourrie ici par une boucle Kotlin qui interpole linéairement entre deux points au lieu d'un flux de capteurs réels. C'est la septième commande ajoutée au pont natif→JS (`window.MapBridge` + `WebView.evaluateJavascript`), après `goToGlobal` (`reset-view`), `updateOccupancy` (`occupancy-simulated`), `goToPlace`/`clearPlace` (`goto-poi`), `goToFloor` (`floor-selector`), `computeNavigation`/`clearNavigation` (`compute-navigation`) et `setUIPartVisible` (`ui-part-visibility`).

Contrairement aux commandes précédentes, celle-ci a aussi besoin d'un pont **JS→natif** pour résoudre une donnée : les POI n'ont pas de champ latitude/longitude direct, il faut le lire sur leur premier marker/label/image (voir "Points d'attention"). `resolvePositions(requestId, originId, destinationId)` fait cette résolution côté JS pour les deux POI en un seul aller-retour, et renvoie le résultat à `AndroidBridge.onPositionsResolved`.

## Step by step

1. **Ajouter les 3 commandes à `window.MapBridge`** (`web/src/main.js`) :
   ```js
   window.MapBridge = {
     // ...
     resolvePositions(requestId, originId, destinationId) {
       const resolve = (poiId) => {
         const poi = venue?.pois.find((p) => p.id === poiId);
         const position = poi?.markers?.[0]?.position ?? poi?.labels?.[0]?.position ?? poi?.images?.[0]?.position;
         return position ? { latitude: position.latitude, longitude: position.longitude } : null;
       };
       bridge?.onPositionsResolved(
         requestId,
         JSON.stringify(resolve(originId)),
         JSON.stringify(resolve(destinationId)),
       );
     },
     injectTrackedPosition(latitude, longitude, precisionCircleRadius) {
       if (!view) return;
       view.allowTracking = true;
       view.injectTrackedPosition({ position: { latitude, longitude }, precisionCircleRadius });
     },
     stopTrackedPosition() {
       if (!view) return;
       view.allowTracking = false;
     },
   };
   ```
   `resolvePositions` returns nothing directly — the response travels back to native asynchronously via `AndroidBridge.onPositionsResolved`, echoing `requestId` so the caller can match a response to the Start press that triggered it (see step 4).

2. **Reconstruire le bundle web** — `cd web && npm run build`. Même piège que pour toutes les features précédentes : Gradle ne le fait jamais automatiquement, `app/src/main/assets/www/` doit être régénéré à la main.

3. **Ajouter les extensions d'appel côté `FeatureOverlays.kt`** :
   ```kotlin
   private fun WebView.resolvePositions(requestId: Int, originId: String, destinationId: String) {
       val script = "window.MapBridge.resolvePositions(" +
           "$requestId, ${JSONObject.quote(originId)}, ${JSONObject.quote(destinationId)})"
       evaluateJavascript(script, null)
   }

   private fun WebView.injectTrackedPosition(latitude: Double, longitude: Double, precisionCircleRadiusMeters: Double) {
       val script = "window.MapBridge.injectTrackedPosition($latitude, $longitude, $precisionCircleRadiusMeters)"
       evaluateJavascript(script, null)
   }

   private fun WebView.stopTrackedPosition() {
       evaluateJavascript("window.MapBridge.stopTrackedPosition()", null)
   }
   ```
   `latitude`/`longitude`/`precisionCircleRadiusMeters` are numeric, so `Double.toString()` is a valid JS number literal — no `JSONObject.quote()` needed here, same convention as `isAccessible` in `compute-navigation`.

4. **Ajouter le pont JS→natif côté `FeatureMapScreen.kt`** (`MapBridge` class + a new `ResolvedPositionsPair` state, propagated to `sheetContent` as its fifth argument):
   ```kotlin
   private class MapBridge(
       // ...
       private val notifyPositionsResolved: (Int, String, String) -> Unit,
   ) {
       @JavascriptInterface
       fun onPositionsResolved(requestId: Int, originJson: String, destinationJson: String) =
           notifyPositionsResolved(requestId, originJson, destinationJson)
   }
   ```
   ```kotlin
   var positionsResolved by remember { mutableStateOf<ResolvedPositionsPair?>(null) }
   // ...
   notifyPositionsResolved = { requestId, originJson, destinationJson ->
       mainHandler.post {
           positionsResolved = ResolvedPositionsPair(
               requestId = requestId,
               origin = parseResolvedPosition(originJson),
               destination = parseResolvedPosition(destinationJson),
           )
       }
   },
   ```
   `ResolvedPositionsPair`/`ResolvedPosition`/`parseResolvedPosition` live in `FeatureOverlays.kt`, next to the other bridge data classes/parsers (`FloorSelectorState`, `parseFloorsReadyPayload`, ...).

5. **Ajouter le contenu de la bottom sheet** (`SimulatedPositionOverlay`) — deux champs Origin/Destination POI ID, un `Slider` pour le rayon (1-20m, 5m par défaut) et un bouton Start/Stop, même pattern de bascule que `OccupancySimulationOverlay` :
   - **Start** appelle `webView.resolvePositions(requestId, originId, destinationId)` et attend la réponse via un `LaunchedEffect(positionsResolved)` — si un des deux slots revient `null`, affiche "POI not found" et n'active rien ; sinon lance un second `LaunchedEffect(isRunning, activePositions, webView)` qui boucle toutes les 150ms, interpole linéairement entre origine et destination (va-et-vient géré par une fraction 0↔1 qui inverse de sens à chaque borne), et appelle `injectTrackedPosition` à chaque tick avec le rayon **lu à chaque itération** (`radiusMeters` n'est pas une clé de l'effet, donc bouger le slider pendant que ça tourne change juste la valeur utilisée au tick suivant, sans relancer la boucle).
   - **Stop** repasse `isRunning` à `false`, ce qui annule l'effet et déclenche son bloc `finally` → `stopTrackedPosition()`. Quitter l'écran (qui détruit la WebView) a le même effet par annulation de coroutine.

6. **Ajouter l'entrée au registre de features** (`Feature.kt` + `strings.xml`/`strings-fr.xml`) et **câbler la route** dans `MainActivity.kt` :
   ```kotlin
   Feature.SimulatedPosition -> SimulatedPositionOverlay(webView, positionsResolved)
   ```

## Points d'attention

- **`injectTrackedPosition` exige `view.allowTracking = true` au préalable** — l'appeler alors que `allowTracking` est encore `false` lève une exception côté SDK. Le pont JS le met à `true` à chaque appel plutôt qu'une seule fois au Start : ça ne coûte rien une fois déjà à `true`, et ça évite un état "armé mais pas encore vrai" si `injectTrackedPosition` était un jour appelé d'un autre chemin de code.
- **Pas de méthode `stop` dédiée** — contrairement à `clearPlace`/`clearNavigation` qui retirent explicitement ce qu'ils ont ajouté, il n'existe aucune `view.removeTrackedPosition()` ou équivalent côté SDK. Repasser `view.allowTracking` à `false` est la façon documentée de faire disparaître le point et son cercle de précision.
- **Les POI n'ont pas de champ latitude/longitude direct** — `venue.pois.find(...)` renvoie un `POI` dont ni `markers`, ni `labels`, ni `images` ne sont garantis non-vides individuellement ; la position vient du premier de ceux qui existe (`poi.markers[0]?.position ?? poi.labels[0]?.position ?? poi.images[0]?.position`), tous trois portant un `Position` `{ latitude, longitude, altitude? }` de la même forme que celle attendue par `injectTrackedPosition` — pas de conversion nécessaire. Un POI sans aucun des trois, ou un id introuvable, résout à `null` côté JS et surface "POI not found" côté natif, même convention d'erreur que `compute-navigation`.
- **C'est une position simulée, pilotée par l'app — pas du positionnement indoor réel.** Aucun capteur (BLE/Wi-Fi/UWB) n'est impliqué ; `view.injectTrackedPosition` est exactement l'API qu'une vraie intégration de positionnement appellerait avec les coordonnées d'un vrai capteur, mais ici les coordonnées viennent d'une interpolation linéaire Kotlin entre deux POI. Voir `CLAUDE.md` du hub (`VisioOneHub`), section "Scope boundaries" : le positionnement indoor réel reste hors scope tant qu'aucun besoin commercial concret n'apparaît, seule la version simulée est un candidat de démo valide.
- **Le rayon ne s'applique qu'au tick suivant** — bouger le `Slider` pendant que la simulation tourne ne redémarre pas la boucle ; la nouvelle valeur de `radiusMeters` est simplement lue à la prochaine itération (150ms plus tard au pire), pas de latence perceptible en pratique mais pas un changement instantané non plus.
- **`requestId` protège contre une réponse obsolète** — `resolvePositions` est asynchrone (aller-retour JS→natif) ; si un Start venait à être suivi d'un second avant que le premier n'ait répondu, `requestId` permet à l'overlay d'ignorer une réponse qui ne correspond plus à la tentative en cours. Les deux appels étant quasi instantanés en pratique, ce cas ne devrait pas se produire, mais coûte peu à couvrir.
- **Reconstruire le bundle web est obligatoire** avant que quoi que ce soit ne fonctionne — même piège que toutes les features précédentes : Gradle ne rebuild jamais `web/` automatiquement.

## Pour aller plus loin

- `view.updatePositionTrackerGraphicOptions({ color, opacity })` permet de personnaliser l'apparence du point/cercle (couleur de marque, opacité) — non utilisé ici pour rester au plus simple, mais trivial à ajouter si un client veut aligner ce marqueur sur sa charte graphique.
- `injectTrackedPosition` accepte un second argument `AnimationOptions`, non utilisé ici — sans lui, chaque nouveau point est un saut instantané plutôt qu'une transition animée ; à 150ms d'intervalle et avec des pas courts, le mouvement reste visuellement fluide malgré tout.
- `view.lockCameraOrientationOnTracking`/`lockCameraPositionOnTracking` (booléens, actifs uniquement quand `allowTracking = true`) permettraient de faire suivre la caméra au point simulé — pertinent pour une démo "vue à la première personne", hors scope de cette première version qui laisse la caméra libre.
- Ce pont natif→JS reste le point d'ancrage pour une future intégration de positionnement indoor réel (BLE/Wi-Fi/UWB) si un besoin commercial concret apparaît — voir le `ROADMAP.md` du hub (`VisioOneHub`), "Hors scope actuel".
