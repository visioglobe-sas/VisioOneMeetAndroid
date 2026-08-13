# Réagir au clic sur un POI

## Description

Réagit au tap d'un POI sur la carte en affichant son nom et son identifiant dans un panneau natif, via l'événement `poiclick` du SDK (`view.addEventListener('poiclick', ...)`). Contrairement aux features précédentes, ce n'est pas un bouton natif qui pilote la carte (pont natif→JS) : ici c'est la carte qui notifie le natif d'une interaction utilisateur (pont **JS→natif**), en étendant le pont `AndroidBridge` déjà utilisé pour `onMapReady`/`onMapError`.

Le nom affiché est résolu via `venue.translator.translatePOI(poi, venue.currentLocale).name` côté JS — le natif ne reçoit qu'une chaîne déjà traduite, jamais un objet `POI` brut (qui ne serait de toute façon pas sérialisable tel quel vers `@JavascriptInterface`).

## Step by step

1. **Écouter l'événement côté JS** (`web/src/main.js`), après la création de la `view` :
   ```js
   function onPoiClick(event) {
     if (!venue) return;
     const locale = venue.currentLocale;
     const pois = event.pois.map((poi) => ({
       id: poi.id,
       name: venue.translator.translatePOI(poi, locale).name,
     }));
     bridge?.onPoiClick(JSON.stringify(pois));
   }

   async function main() {
     // ...
     view = await visioOne.createView(container, venue);
     view.addEventListener('poiclick', onPoiClick);
     bridge?.onMapReady();
   }
   ```
   `event.pois` est un tableau (quasi toujours un seul élément) car l'événement porte tous les POIs sous le point tapé, pas un seul.

2. **Reconstruire le bundle web** — `cd web && npm run build`, comme pour toute modification de `web/`. Sans ça, `AndroidBridge.onPoiClick` n'est jamais appelé.

3. **Ajouter la méthode `@JavascriptInterface` côté natif** (`MapBridge`, dans `FeatureMapScreen.kt`) — c'est la première méthode Web→Natif ajoutée depuis `onMapReady`/`onMapError` :
   ```kotlin
   private class MapBridge(
       private val onReady: () -> Unit,
       private val onError: (String) -> Unit,
       private val notifyPoiClick: (String) -> Unit,
   ) {
       // ...
       @JavascriptInterface
       fun onPoiClick(payload: String) = notifyPoiClick(payload)
   }
   ```
   Le paramètre du constructeur est nommé `notifyPoiClick`, pas `onPoiClick` — lui donner le même nom que la méthode `@JavascriptInterface` fait planter la compilation Kotlin (« Type checking has run into a recursive problem »), le corps de la méthode résolvant l'appel comme récursif sur elle-même plutôt que sur le paramètre.

4. **Parser le JSON reçu** avec `org.json` (jamais de parsing manuel de chaîne) — ajouté dans `FeatureOverlays.kt` :
   ```kotlin
   data class PoiClickInfo(val id: String, val name: String)

   internal fun parsePoiClickPayload(json: String): List<PoiClickInfo> {
       val array = JSONArray(json)
       return List(array.length()) { index ->
           val entry = array.getJSONObject(index)
           PoiClickInfo(id = entry.getString("id"), name = entry.optString("name", ""))
       }
   }
   ```

5. **Opt-in explicite par écran** — `FeatureMapScreen` reçoit un flag `reactsToPoiClicks: Boolean = false`. Le bundle web envoie l'événement `poiclick` sur **tous** les écrans (c'est le même `main.js` partout), mais seul l'écran qui passe `reactsToPoiClicks = true` en tient compte :
   ```kotlin
   notifyPoiClick = { payload ->
       if (reactsToPoiClicks) {
           mainHandler.post {
               lastPoiClick = parsePoiClickPayload(payload)
               showControls = true // ouvre automatiquement la bottom sheet
           }
       }
   },
   ```
   Sans ce flag, taper un POI sur l'écran `reset-view` ou `occupancy-simulated` ouvrirait aussi leur bottom sheet — un effet de bord non désiré. `MainActivity.kt` passe `reactsToPoiClicks = feature == Feature.PoiClick`.

6. **Adapter le pattern bottom sheet existant** : la sheet modale (FAB + `ModalBottomSheet`, fond opaque, slide-up, dismissable) reste la même que pour `reset-view`/`occupancy-simulated`, mais son déclencheur change — au lieu d'attendre un tap sur le FAB, `FeatureMapScreen` bascule lui-même `showControls = true` dès qu'un `poiclick` arrive. Le FAB reste présent et fonctionnel : il rouvre la sheet pour revoir le dernier POI tapé (ou un texte d'accroche si aucun tap n'a encore eu lieu).

7. **Afficher le contenu** (`PoiClickOverlay` dans `FeatureOverlays.kt`) :
   ```kotlin
   @Composable
   fun PoiClickOverlay(pois: List<PoiClickInfo>) {
       // pois.isEmpty() → "Tap a POI on the map to see its details."
       // sinon → nom (ou id si le nom est vide) + "ID: <id>" par POI
   }
   ```

8. **Câblage par route** dans `MainActivity.kt` :
   ```kotlin
   sheetContent = { webView, lastPoiClick ->
       when (feature) {
           // ...
           Feature.PoiClick -> PoiClickOverlay(lastPoiClick)
           null -> Unit
       }
   }
   ```
   La signature de `sheetContent` gagne un second paramètre (`lastPoiClick: List<PoiClickInfo>`), passé par `FeatureMapScreen` en plus du `webView` déjà transmis pour les commandes natif→JS des autres features.

## Points d'attention

- **`event.pois` est un tableau, pas un seul POI** — même si dans l'immense majorité des cas un tap ne touche qu'un seul POI, le contrat du SDK autorise plusieurs POIs superposés au même point. Le payload JSON et `PoiClickInfo` sont donc conçus comme une liste dès le départ plutôt que rallongés après coup.
- **Ne jamais renvoyer l'objet `POI` brut au natif** — il contient des références circulaires et des méthodes, incompatibles avec `@JavascriptInterface` (qui n'accepte que des types simples). Toujours projeter côté JS vers un objet JSON minimal (`{ id, name }`) avant `JSON.stringify`.
- **La traduction du nom se fait côté JS, pas côté natif** — `venue.translator.translatePOI(poi, venue.currentLocale)` renvoie déjà la bonne langue ; le natif n'a pas connaissance des locales du SDK (distinctes de la locale Android utilisée pour le menu de features).
- **Piège de nommage Kotlin** : ne pas nommer le paramètre du constructeur de `MapBridge` comme la méthode `@JavascriptInterface` qui l'invoque (`onPoiClick`/`onPoiClick`) — voir étape 3. Le compilateur Kotlin lève une erreur de type-checking récursif, pas une erreur de shadowing explicite, ce qui la rend surprenante à déboguer.
- **L'événement est global au bundle web, l'opt-in est local à l'écran natif** — `view.addEventListener('poiclick', ...)` est enregistré une fois pour tout le cycle de vie de la `view`, quel que soit l'écran affiché. C'est le flag `reactsToPoiClicks` côté Compose qui borne l'effet visible à l'écran `poi-click`, pas une différence de comportement JS par écran.
- **`optString("name", "")` plutôt que `getString("name")`** : si jamais la traduction est vide (aucune locale définie pour ce POI), on préfère un nom vide affiché avec repli sur l'`id` (`poi.name.ifBlank { poi.id }`) plutôt qu'un crash de parsing JSON.
- **Reconstruire le bundle web est obligatoire** avant que l'événement ne remonte quoi que ce soit — même piège que `reset-view` et `occupancy-simulated` : Gradle ne rebuild jamais `web/` automatiquement.

## Pour aller plus loin

- Ce pont JS→natif étend `AndroidBridge` (jusqu'ici limité à `onMapReady`/`onMapError`) — c'est le point de départ pour relayer d'autres événements SDK (changement d'étage, sélection de POI via `selectedpoischange`, navigation) vers le natif, si un besoin apparaît.
- Une évolution naturelle serait de déclencher une action native depuis ce clic (ex. centrer la caméra sur le POI via `view.goToPOI`, en réutilisant le pont natif→JS de `reset-view`) — hors scope de cette feature, qui se limite à la **réaction visible** demandée par le catalogue.
