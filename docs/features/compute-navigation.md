# Itinéraire

## Description

Ajoute deux champs « From » / « To » (des Place ID) et un bouton « Itinerary » qui calculent un itinéraire entre les deux POI via `venue.computeNavigation(request)`, matérialisent la route sur la carte via `venue.createNavigationTrace(navigation)` + `view.setCurrentNavigationTrace(trace)`, et un bouton « Clear » qui la retire (`view.removeCurrentNavigationTrace()` + `venue.removeNavigationTrace(trace)`). C'est la cinquième commande ajoutée au pont natif→JS (`window.MapBridge` + `WebView.evaluateJavascript`), après `goToGlobal` (`reset-view`), `updateOccupancy` (`occupancy-simulated`), `goToPlace`/`clearPlace` (`goto-poi`) et `goToFloor` (`floor-selector`) — même mécanique, pas de nouvelle plomberie côté natif pour l'appel lui-même.

L'UX à deux champs + bouton reprend celle du sibling React Native (`ComputeNavigationOverlay.tsx` / `startItinerary` dans `useVisioMap.ts`/`visioOneHtml.ts`), qui sert de référence UI pour cette feature sur toutes les plateformes. `isAccessible` est fixé à `false` en dur, comme sur ce sibling — pas de case à cocher dans cette première version.

Contrairement aux commandes précédentes, celle-ci a aussi besoin d'un pont **JS→natif** pour les échecs : un Place ID invalide ou une paire inatteignable fait lever une exception à `venue.computeNavigation` (le SDK documente `InvalidNavigationRequestError` pour ce cas), et laisser ça silencieux comme `goToPlace`/`goToFloor` afficherait juste une carte inchangée sans aucune explication — un champ texte libre invite aux fautes de frappe bien plus qu'un sélecteur. Le bundle JS relaie donc l'échec vers `AndroidBridge.onNavigationError`, et une réussite subséquente l'efface via `AndroidBridge.onNavigationComputed`.

## Step by step

1. **Garder une référence à la trace active** côté JS (`web/src/main.js`), au même niveau que `venue`/`view`/`selectedPoi`, pour que `clearNavigation` sache quoi retirer :
   ```js
   let currentNavigationTrace = null;
   ```

2. **Ajouter `computeNavigation` et `clearNavigation` à `window.MapBridge`** :
   ```js
   window.MapBridge = {
     // ...
     computeNavigation(origin, destination, isAccessible) {
       if (!venue || !view) return;
       this.clearNavigation();
       try {
         const navigation = venue.computeNavigation({
           origin,
           destination,
           isAccessible,
           type: 'fastest',
           firstNodeAsIntersection: false,
           mergeFloorChangeInstructions: false,
         });
         currentNavigationTrace = venue.createNavigationTrace(navigation);
         view.setCurrentNavigationTrace(currentNavigationTrace);
         bridge?.onNavigationComputed();
       } catch (error) {
         bridge?.onNavigationError(String(error?.message ?? error));
       }
     },
     clearNavigation() {
       if (!venue || !view || !currentNavigationTrace) return;
       view.removeCurrentNavigationTrace();
       venue.removeNavigationTrace(currentNavigationTrace);
       currentNavigationTrace = null;
     },
   };
   ```
   `origin`/`destination` sont passés tels quels — `NavigationRequest.origin`/`.destination` acceptent un `POI`, une `Position`, **ou directement une chaîne d'id** (`POIOrIDOrPosition`, voir `NavigationRequest.d.ts`), donc pas besoin de résoudre les POI depuis leurs id avant l'appel, contrairement à `goToPlace` qui doit résoudre le POI lui-même parce que `view.goToPOI` attend un objet `POI`.

3. **Reconstruire le bundle web** — `cd web && npm run build`. Même piège que pour toutes les features précédentes : Gradle ne le fait jamais automatiquement, `app/src/main/assets/www/` doit être régénéré à la main.

4. **Ajouter les méthodes du pont côté Kotlin** (`FeatureMapScreen.kt`, classe `MapBridge`), et un état `navigationError` propagé à `sheetContent` comme quatrième argument (après `webView`, `lastPoiClick`, `floorSelector`) :
   ```kotlin
   private class MapBridge(
       // ...
       private val notifyNavigationComputed: () -> Unit,
       private val notifyNavigationError: (String) -> Unit,
   ) {
       @JavascriptInterface
       fun onNavigationComputed() = notifyNavigationComputed()

       @JavascriptInterface
       fun onNavigationError(message: String) = notifyNavigationError(message)
   }
   ```
   ```kotlin
   var navigationError by remember { mutableStateOf<String?>(null) }
   // ...
   notifyNavigationComputed = { mainHandler.post { navigationError = null } },
   notifyNavigationError = { message -> mainHandler.post { navigationError = message } },
   ```

5. **Ajouter les extensions d'appel** dans `FeatureOverlays.kt` :
   ```kotlin
   private fun WebView.computeNavigation(origin: String, destination: String, isAccessible: Boolean) {
       val script = "window.MapBridge.computeNavigation(" +
           "${JSONObject.quote(origin)}, ${JSONObject.quote(destination)}, $isAccessible)"
       evaluateJavascript(script, null)
   }

   private fun WebView.clearNavigation() {
       evaluateJavascript("window.MapBridge.clearNavigation()", null)
   }
   ```
   `isAccessible` (un `Boolean` Kotlin) est interpolé tel quel — `true`/`false` sont déjà des littéraux JS valides, pas besoin de `JSONObject.quote()` ici (qui est réservé aux chaînes).

6. **Ajouter le contenu de la bottom sheet** (`ComputeNavigationOverlay`) — deux `OutlinedTextField` (« From »/« To »), un bouton « Itinerary » (désactivé tant qu'un des deux champs est vide) et un bouton « Clear », plus un `Text` d'erreur affiché sous les boutons quand `navigationError != null` :
   ```kotlin
   @Composable
   fun ComputeNavigationOverlay(webView: WebView?, navigationError: String?) {
       var origin by remember { mutableStateOf("") }
       var destination by remember { mutableStateOf("") }

       Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
           Row(modifier = Modifier.fillMaxWidth()) {
               OutlinedTextField(value = origin, onValueChange = { origin = it }, label = { Text("From (place ID)") }, modifier = Modifier.weight(1f))
               OutlinedTextField(value = destination, onValueChange = { destination = it }, label = { Text("To (place ID)") }, modifier = Modifier.weight(1f))
           }
           Row(modifier = Modifier.fillMaxWidth()) {
               Button(onClick = { webView?.computeNavigation(origin.trim(), destination.trim(), false) }, enabled = origin.isNotBlank() && destination.isNotBlank()) {
                   Text("Itinerary")
               }
               Button(onClick = { webView?.clearNavigation() }) { Text("Clear") }
           }
           if (navigationError != null) {
               Text(text = navigationError, color = MaterialTheme.colorScheme.error)
           }
       }
   }
   ```

7. **Ajouter l'entrée au registre de features** (`Feature.kt` + `strings.xml`/`strings-fr.xml`) et **câbler la route** dans `MainActivity.kt` :
   ```kotlin
   Feature.ComputeNavigation -> ComputeNavigationOverlay(webView, navigationError)
   ```

## Points d'attention

- **`origin`/`destination` acceptent directement des id** — pas besoin de résoudre les POI côté JS comme le fait `goToPlace` : `NavigationRequest.origin`/`.destination` sont typés `POIOrIDOrPosition` (`POI | string | Position`, voir `NavigationRequest.d.ts`), donc passer les deux champs texte tels quels à `venue.computeNavigation` suffit.
- **Un échec ici n'est pas silencieux, contrairement aux autres commandes du pont** — `goToPlace`/`goToFloor`/`updateOccupancy` no-opent silencieusement sur un id inconnu (`.find(...)` renvoie `undefined`). `computeNavigation` peut carrément lever une exception (id invalide, pas de route possible entre les deux points) ; sans un canal d'erreur dédié, l'utilisateur se retrouverait face à une carte inchangée sans aucun indice sur ce qui a raté — un champ texte libre invite aux fautes de frappe bien plus qu'un sélecteur. D'où `AndroidBridge.onNavigationError`/`onNavigationComputed`, la première paire de callbacks JS→natif de cette app qui ne porte pas de payload de données mais un simple statut succès/échec.
- **La trace précédente est retirée avant d'en calculer une nouvelle** (`this.clearNavigation()` en tête de `computeNavigation`) — sans ça, appeler `view.setCurrentNavigationTrace` remplace bien la représentation *affichée* (une seule trace « courante » à la fois, voir `View.setCurrentNavigationTrace`), mais l'ancien objet `NavigationTrace` resterait alloué côté SDK sans être libéré via `venue.removeNavigationTrace`.
- **« Clear » est un no-op tant qu'aucun itinéraire n'a été calculé** — `clearNavigation` vérifie `currentNavigationTrace` avant d'appeler quoi que ce soit côté SDK, comme les autres commandes du pont sur une entrée absente.
- **Pas de case « accessible »** — le sibling React Native fixe `isAccessible` à `false` en dur et n'expose pas de bascule dans son overlay ; cette implémentation fait pareil pour rester alignée. Le SDK supporte déjà ce flag (`NavigationRequest.isAccessible`) ; une case à cocher est triviale à ajouter si un client veut la démontrer.
- **`type: 'fastest'`, `firstNodeAsIntersection: false`, `mergeFloorChangeInstructions: false`** — repris tels quels du sibling React Native pour rester cohérent entre les deux implémentations de référence. `NavigationRequestType` propose d'autres stratégies (voir `NavigationRequestType.d.ts`) non démontrées ici.
- **Reconstruire le bundle web est obligatoire** avant que les boutons ne fassent quoi que ce soit — même piège que toutes les features précédentes : Gradle ne rebuild jamais `web/` automatiquement.

## Pour aller plus loin

- `Navigation.instructions` (un tableau de `NavigationInstruction`, voir `NavigationInstruction.d.ts`) contient déjà tout ce qu'il faut pour une liste d'instructions textuelles façon GPS (« tournez à droite », distance, durée, étage) — non exploité ici, cette démo se limite à la trace dessinée sur la carte. Un client voudra probablement une liste défilante de ces instructions sous les champs.
- `venue.computeNavigationMultiDestination` existe aussi côté SDK pour un itinéraire avec plusieurs arrêts intermédiaires — hors scope de cette démo à deux champs.
- Ce pont natif→JS reste le point d'ancrage pour les prochains fondamentaux/catalogue dépendant d'un itinéraire déjà calculé (trace personnalisée, exclusion de modalités, mode accessible explicite) — voir le `ROADMAP.md` du hub (`VisioOneHub`).
