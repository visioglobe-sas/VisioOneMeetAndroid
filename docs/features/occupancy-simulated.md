# Occupation temps réel (données simulées)

## Description

Colore dynamiquement la surface d'un POI pour refléter un statut d'occupation (libre / bientôt occupé / occupé), via une nouvelle commande `updateOccupancy` ajoutée au pont natif→JS — **qui n'existait pas du tout avant cette feature** sur cette plateforme (seul un pont JS→natif existait, pour l'état de chargement).

Il n'y a pas de vrai capteur derrière : une boucle Kotlin (coroutine, `LaunchedEffect` + `delay`) fait tourner la couleur toutes les 2,5 secondes, en lieu et place d'un flux IoT réel.

## Step by step

1. **Ajouter la commande côté JS** (`web/src/main.js`) — la `venue` doit être hissée en variable de module (elle était locale à `main()`) pour être accessible depuis `window.MapBridge` :
   ```js
   let venue = null;

   window.MapBridge = {
     updateOccupancy(occupancy) {
       if (!venue) return;
       occupancy.forEach((entry) => {
         const poi = venue.pois.find((p) => p.id === entry.planId);
         if (!poi) return;
         poi.surfaces.forEach((surface) => {
           venue.updateSurface(surface, { color: entry.color });
         });
       });
     },
   };

   async function main() {
     // ...
     venue = await visioOne.loadVenue({ hash }); // plus de `const`
     // ...
   }
   ```
2. **Reconstruire le bundle web** — `cd web && npm run build`. Gradle ne le fait pas automatiquement (voir `GUIDE_INTEGRATEUR.md`) : sans ce rebuild, `app/src/main/assets/www/` contient encore l'ancien JS.
3. **Garder une référence au `WebView`** dans le composable (`var webView by remember { mutableStateOf<WebView?>(null) }`, assignée via `.also { webView = it }` dans le `factory` de l'`AndroidView`) — nécessaire pour appeler `evaluateJavascript` depuis un bouton, alors que jusqu'ici seul `addJavascriptInterface` (JS → natif) existait.
4. **Appeler la commande via `WebView.evaluateJavascript`**, arguments encodés en JSON via `org.json` (jamais de concaténation de texte brut) :
   ```kotlin
   private fun WebView.updateOccupancy(planId: String, color: String?) {
       val entry = JSONObject().apply {
           put("planId", planId)
           put("color", color ?: JSONObject.NULL)
       }
       evaluateJavascript("window.MapBridge.updateOccupancy(${JSONArray().put(entry)})", null)
   }
   ```
5. **Piloter la boucle depuis un `LaunchedEffect`**, clé sur (`simulatingOccupancy`, `placeId`, `webView`) — le `finally` s'exécute automatiquement quand l'effet est annulé (toggle off, changement de `placeId`, sortie de composition), ce qui garantit la réinitialisation de la couleur sans code de nettoyage manuel.

## Points d'attention

- **`org.json` (`JSONObject`/`JSONArray`) est disponible nativement sur Android** — pas besoin d'ajouter une dépendance JSON pour encoder les arguments en toute sécurité avant de les interpoler dans le script JS.
- **`color: null` doit devenir `JSONObject.NULL`**, pas `null` Kotlin — `JSONObject.put(key, null)` avec un `null` Kotlin brut a un comportement différent (voire lève une exception selon la surcharge) de l'objet sentinelle `JSONObject.NULL`, qui sérialise correctement en `null` JSON.
- **`evaluateJavascript` doit être appelé sur le thread principal** — c'est déjà le cas ici puisque l'appel part d'une coroutine Compose (`LaunchedEffect`), mais à garder en tête si cette commande est un jour déclenchée depuis un contexte différent.
- **`planId` doit être un vrai ID de POI de la carte chargée** — `venue.pois.find(...)` échoue silencieusement côté JS si l'ID ne correspond à rien, sans remonter d'erreur au natif.
- Ceci démontre la **mécanique** de mise à jour temps réel, pas une vraie intégration IoT — pour un cas client réel, remplacer la boucle simulée par un abonnement à la vraie source (websocket, polling d'API) sans toucher au pont ni au SDK.

## Pour aller plus loin

- Ce pont natif→JS (`window.MapBridge` + `evaluateJavascript`) est le point de départ pour câbler les autres fondamentaux encore ❌ sur cette plateforme (aller à un POI, changer d'étage, itinéraire) — voir `ROADMAP.md` du hub (`VisioOneHub`), Phase 0.
- Version "vrai capteur" : voir le `ROADMAP.md` du hub, feature "Suivi d'actifs connectés (IoT)" — hors scope tant qu'aucun flux IoT réel n'est disponible.
