# Réinitialiser la vue

## Description

Ajoute un bouton natif « Reset view » qui recentre la caméra sur l'ensemble du venue, via `view.goToGlobal()`. Réutilise le pont natif→JS (`window.MapBridge` + `WebView.evaluateJavascript`) mis en place pour `occupancy-simulated` — c'est la deuxième commande ajoutée à ce pont, sans nouvelle mécanique côté natif.

Contrairement à `updateOccupancy`, cette commande ne prend aucun argument : c'est un simple appel de méthode sur l'objet `view` (celui retourné par `visioOne.createView(...)`), pas sur la `venue`.

## Step by step

1. **Hisser `view` en variable de module** (`web/src/main.js`), au même niveau que `venue` :
   ```js
   let venue = null;
   let view = null;
   ```
2. **Ajouter `goToGlobal` à `window.MapBridge`** :
   ```js
   window.MapBridge = {
     goToGlobal() {
       if (view) view.goToGlobal();
     },
     updateOccupancy(occupancy) {
       // ...
     },
   };
   ```
3. **Stocker la valeur retournée par `createView`** dans `main()` (jusqu'ici la valeur était ignorée) :
   ```js
   venue = await visioOne.loadVenue({ hash });
   view = await visioOne.createView(container, venue); // plus de `await visioOne.createView(...)` seul
   ```
4. **Reconstruire le bundle web** — `cd web && npm run build`. Gradle ne le fait pas automatiquement (voir `GUIDE_INTEGRATEUR.md`) : sans ce rebuild, `app/src/main/assets/www/` contient encore l'ancien JS et le bouton natif appelle une commande qui n'existe pas côté JS.
5. **Ajouter l'extension Kotlin** dans `FeatureOverlays.kt`, à côté de `WebView.updateOccupancy` :
   ```kotlin
   private fun WebView.goToGlobal() {
       evaluateJavascript("window.MapBridge.goToGlobal()", null)
   }
   ```
6. **Ajouter le bouton** dans son propre overlay, aligné en haut à droite pour ne pas empiéter sur le panneau de simulation d'occupation (aligné en bas) :
   ```kotlin
   @Composable
   fun BoxScope.ResetViewOverlay(webView: WebView?) {
       Button(
           onClick = { webView?.goToGlobal() },
           modifier = Modifier
               .align(Alignment.TopEnd)
               .padding(12.dp),
       ) {
           Text(stringResource(R.string.feature_reset_view_title))
       }
   }
   ```
   `FeatureMapScreen.kt` (le composable partagé qui héberge la WebView) invoque cet overlay via son paramètre `overlay`, une fois la carte à l'état `MapLoadState.Ready` — le câblage par route/slug se fait dans `MainActivity.kt`.

## Points d'attention

- **Reconstruire le bundle web est obligatoire** avant que le bouton ne fasse quoi que ce soit — même piège que pour `occupancy-simulated` : Gradle ne rebuild jamais `web/` automatiquement, seul `npm run build` le fait (il écrit directement dans `app/src/main/assets/www`).
- **Le bouton n'apparaît qu'à `MapLoadState.Ready`** : à ce stade, `webView` est garanti non-null (assigné via `.also { webView = it }` dans le `factory` de l'`AndroidView`, appelé avant que `onMapReady()` ne puisse déclencher ce state) — pas besoin de vérifier sa nullité autrement que par l'opérateur `?.` défensif habituel.
- **`goToGlobal` ne prend pas d'argument** — pas besoin de JSON-encoder quoi que ce soit ici, contrairement à `updateOccupancy`.

## Pour aller plus loin

Ce pont (`window.MapBridge` + `evaluateJavascript`) reste le point d'ancrage pour les prochains fondamentaux encore ❌ sur cette plateforme (aller à un POI, changer d'étage, calculer un itinéraire) — voir le `ROADMAP.md` du hub (`VisioOneHub`).
