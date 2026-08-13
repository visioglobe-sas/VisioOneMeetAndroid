# Aller à un lieu / POI

## Description

Ajoute un champ « Place ID » avec deux boutons, « Go » et « Clear », qui centrent/zooment la caméra sur le POI portant cet identifiant via `view.goToPOI(poi, animationOptions)`, et surlignent ses surfaces pour que le résultat reste visible une fois l'animation terminée. C'est la troisième commande ajoutée au pont natif→JS (`window.MapBridge` + `WebView.evaluateJavascript`), après `goToGlobal` (`reset-view`) et `updateOccupancy` (`occupancy-simulated`) — même mécanique, pas de nouvelle plomberie côté natif.

Contrairement à `goToGlobal`, `goToPlace` prend un argument scalaire (une chaîne, pas un tableau) : c'est la première commande du pont à illustrer ce cas, d'où l'usage de `JSONObject.quote()` plutôt que `JSONArray` côté Kotlin (voir "Points d'attention").

« Clear » ne déplace pas la caméra : il ne fait que retirer le surlignage posé par « Go », comme le `clearPlace` du sibling React Native (`GoToPoiOverlay.tsx` / `useVisioMap.ts`) dont l'UX est reprise ici.

## Step by step

1. **Garder une référence au POI actif** côté JS (`web/src/main.js`), au même niveau que `venue`/`view`, pour que `clearPlace` sache quelles surfaces retirer :
   ```js
   let selectedPoi = null;
   ```
2. **Ajouter `goToPlace` et `clearPlace` à `window.MapBridge`** :
   ```js
   window.MapBridge = {
     // ...
     goToPlace(placeId) {
       if (!venue || !view) return;
       const poi = venue.pois.find((p) => p.id === placeId);
       if (!poi) return;

       selectedPoi = poi;
       view.goToPOI(poi, {
         orientation: { pitch: 20 },
         padding: { top: 100, right: 100, bottom: 100, left: 100 },
       });
       poi.surfaces.forEach((surface) => {
         venue.updateSurface(surface, { selectionColor: view.surfaceSelectionColor });
       });
     },
     clearPlace() {
       if (!venue || !selectedPoi) return;
       selectedPoi.surfaces.forEach((surface) => {
         venue.updateSurface(surface, { selectionColor: 'default' });
       });
       selectedPoi = null;
     },
   };
   ```
   `'default'` (pas `undefined`/`null`) est la valeur documentée par le SDK (`SurfaceUpdateOptions.selectionColor: Color | 'default'`) pour revenir à la couleur de hover globale de la `view` plutôt qu'à une couleur arbitraire.
3. **Reconstruire le bundle web** — `cd web && npm run build`. Comme pour `reset-view` et `occupancy-simulated`, Gradle ne le fait jamais automatiquement : sans ce rebuild, `app/src/main/assets/www/` contient encore l'ancien JS et les boutons natifs appellent une commande qui n'existe pas côté JS.
4. **Ajouter les extensions Kotlin** dans `FeatureOverlays.kt`, à côté de `WebView.goToGlobal`/`WebView.updateOccupancy` :
   ```kotlin
   private fun WebView.goToPlace(placeId: String) {
       val script = "window.MapBridge.goToPlace(${JSONObject.quote(placeId)})"
       evaluateJavascript(script, null)
   }

   private fun WebView.clearPlace() {
       evaluateJavascript("window.MapBridge.clearPlace()", null)
   }
   ```
5. **Ajouter le contenu de la bottom sheet** (`GoToPoiOverlay` dans `FeatureOverlays.kt`) — un `OutlinedTextField` (« Place ID ») plus deux `Button` (« Go », désactivé si le champ est vide ; « Clear », toujours actif) dans une `Row`, même style que `OccupancySimulationOverlay` :
   ```kotlin
   @Composable
   fun GoToPoiOverlay(webView: WebView?) {
       var placeId by remember { mutableStateOf("") }

       Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
           OutlinedTextField(
               value = placeId,
               onValueChange = { placeId = it },
               label = { Text("Place ID") },
               singleLine = true,
               modifier = Modifier.weight(1f),
           )
           Button(onClick = { webView?.goToPlace(placeId.trim()) }, enabled = placeId.isNotBlank()) {
               Text("Go")
           }
           Button(onClick = { webView?.clearPlace() }) {
               Text("Clear")
           }
       }
   }
   ```
6. **Ajouter l'entrée au registre de features** (`Feature.kt` + `strings.xml`/`strings-fr.xml`) et **câbler la route** dans `MainActivity.kt` :
   ```kotlin
   Feature.GoToPoi -> GoToPoiOverlay(webView)
   ```

## Points d'attention

- **`goToPlace` prend une chaîne, pas un tableau** — contrairement à `updateOccupancy(occupancy)` qui reçoit déjà un `JSONArray`, `goToPlace(placeId)` attend un seul argument scalaire. `JSONArray().put(placeId)` produirait `window.MapBridge.goToPlace(["id"])`, ce qui casse la signature côté JS (le premier paramètre serait un tableau, pas une chaîne) — `JSONObject.quote(placeId)` encode correctement une chaîne JS isolée (guillemets + échappement), sans l'envelopper dans `[...]`.
- **Un `placeId` inconnu échoue silencieusement côté JS** (`venue.pois.find(...)` renvoie `undefined`, l'appel se transforme en no-op) — même comportement que `updateOccupancy`, aucune erreur ne remonte au natif. Un client réel voudrait probablement un retour visuel (toast, message d'erreur) sur un ID invalide ; hors scope de cette démo qui montre la mécanique, pas une UX de recherche complète.
- **« Clear » ne recentre pas la caméra** — il retire uniquement le surlignage posé par « Go ». C'est le choix fait par le sibling React Native (`clearPlace` dans `useVisioMap.ts`/`visioOneHtml.ts`) : combiner un reset de caméra reviendrait à dupliquer `reset-view`, une feature déjà démontrée séparément.
- **`selectionColor: 'default'`, pas `undefined`** — c'est la valeur documentée par le SDK (`Color | 'default'`) pour revenir à `view.surfaceSelectionColor` (la couleur de hover globale) plutôt qu'une couleur en dur ; passer `undefined` ou omettre la clé laisserait la couleur de sélection précédente en place selon l'implémentation du SDK.
- **Pas de marqueur image** — le sibling React Native crée en plus une icône flottante au-dessus du POI (`venue.createImage({ url: 'https://cdn-icons-png.flaticon.com/...' })`). Cette implémentation s'en tient au surlignage de surface (déjà utilisé par `occupancy-simulated`) pour rester cohérente avec le reste de l'app et éviter une dépendance à un CDN tiers dans une démo ; à ajouter si un client veut spécifiquement ce rendu.
- **Reconstruire le bundle web est obligatoire** avant que les boutons ne fassent quoi que ce soit — même piège que `reset-view` et `occupancy-simulated` : Gradle ne rebuild jamais `web/` automatiquement.

## Pour aller plus loin

- `goToPOI` accepte un second argument `AnimationOptions` (`duration`, `easing`, `padding`, `orientation`) — ici seuls `padding` et `orientation.pitch` sont fixés ; un client voudra probablement exposer `duration`/`easing` si l'animation doit matcher une charte d'animation existante.
- Ce pont natif→JS reste le point d'ancrage pour les prochains fondamentaux encore ❌ sur cette plateforme (changer d'étage, calculer un itinéraire) — voir le `ROADMAP.md` du hub (`VisioOneHub`).
