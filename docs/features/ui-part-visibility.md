# Masquage sélectif de l'UI

## Description

Ajoute une feuille modale (bottom sheet) contenant 5 interrupteurs — un par partie de l'UI par défaut du SDK (« Floor selector », « Navigation », « POI details », « Search », « User tracking ») — qui appellent `view.setUIPartVisible(uiPart, isVisible)` pour montrer/masquer immédiatement l'élément correspondant sur la carte, visible derrière la feuille. C'est la sixième commande ajoutée au pont natif→JS (`window.MapBridge` + `WebView.evaluateJavascript`), après `goToGlobal` (`reset-view`), `updateOccupancy` (`occupancy-simulated`), `goToPlace`/`clearPlace` (`goto-poi`), `goToFloor` (`floor-selector`) et `computeNavigation`/`clearNavigation` (`compute-navigation`) — même mécanique, pas de nouvelle plomberie côté natif.

Contrairement aux features précédentes, celle-ci ne pilote pas une action ponctuelle mais bascule un état persistant de la `view` : chaque interrupteur reflète (localement, côté Compose) la visibilité voulue de sa partie d'UI, et l'appel `setUIPartVisible` s'exécute à chaque bascule, pas seulement à l'ouverture de la feuille.

Le SDK n'expose que 5 valeurs `UIPart`, exactes et sensibles à la casse — `floorSelector`, `navigation`, `poiDetails`, `search`, `userTracking` (voir `View.ts`, type `UIPart`) — il n'y en a pas d'autres. Cette démo montre qu'un client intégrateur peut masquer sélectivement l'UI par défaut du SDK pour la remplacer par la sienne (mentionné comme prérequis dans `docs/features/floor-selector.md`, "Points d'attention" — cette feature en est la démonstration directe), sans devoir tout masquer d'un coup avec `view.showUI = false`.

## Step by step

1. **Ajouter `setUIPartVisible` à `window.MapBridge`** (`web/src/main.js`), à côté des autres commandes :
   ```js
   window.MapBridge = {
     // ...
     setUIPartVisible(uiPart, isVisible) {
       if (!view) return;
       view.setUIPartVisible(uiPart, isVisible);
     },
   };
   ```
   Pas de résolution d'id ni de recherche dans `venue`/`view` comme pour `goToPlace`/`goToFloor` : `uiPart` est directement la chaîne attendue par le SDK, passée telle quelle.

2. **Reconstruire le bundle web** — `cd web && npm run build`. Même piège que pour toutes les features précédentes : Gradle ne le fait jamais automatiquement, `app/src/main/assets/www/` doit être régénéré à la main.

3. **Ajouter l'extension d'appel côté `FeatureOverlays.kt`** :
   ```kotlin
   private fun WebView.setUiPartVisible(uiPart: String, isVisible: Boolean) {
       val script = "window.MapBridge.setUIPartVisible(${JSONObject.quote(uiPart)}, $isVisible)"
       evaluateJavascript(script, null)
   }
   ```

4. **Déclarer la liste des 5 parties avec leur libellé traduit**, une seule fois, réutilisée par la liste de contrôles :
   ```kotlin
   private val UI_PART_TOGGLES = listOf(
       "floorSelector" to R.string.ui_part_floor_selector_label,
       "navigation" to R.string.ui_part_navigation_label,
       "poiDetails" to R.string.ui_part_poi_details_label,
       "search" to R.string.ui_part_search_label,
       "userTracking" to R.string.ui_part_user_tracking_label,
   )
   ```

5. **Ajouter le contenu de la bottom sheet** (`UiPartVisibilityOverlay`) — une colonne de 5 lignes, chacune un libellé + un `Switch` Material3, l'état initial de chaque interrupteur à `true` (visible), aligné sur le défaut du SDK lui-même :
   ```kotlin
   @Composable
   fun UiPartVisibilityOverlay(webView: WebView?) {
       val visibility = remember {
           mutableStateMapOf(*UI_PART_TOGGLES.map { (uiPart, _) -> uiPart to true }.toTypedArray())
       }

       Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
           UI_PART_TOGGLES.forEach { (uiPart, labelRes) ->
               Row(
                   modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                   verticalAlignment = Alignment.CenterVertically,
               ) {
                   Text(text = stringResource(labelRes), modifier = Modifier.weight(1f))
                   Switch(
                       checked = visibility[uiPart] == true,
                       onCheckedChange = { isVisible ->
                           visibility[uiPart] = isVisible
                           webView?.setUiPartVisible(uiPart, isVisible)
                       },
                   )
               }
           }
       }
   }
   ```
   Chaque bascule appelle immédiatement `WebView.setUiPartVisible` : pas de bouton « Appliquer » séparé, l'effet est visible sur la carte dès le relâchement de l'interrupteur.

6. **Ajouter l'entrée au registre de features** (`Feature.kt` + `strings.xml`/`strings-fr.xml`, titre + description + les 5 libellés d'interrupteurs) et **câbler la route** dans `MainActivity.kt` :
   ```kotlin
   Feature.UiPartVisibility -> UiPartVisibilityOverlay(webView)
   ```

## Points d'attention

- **N'appeler `setUIPartVisible` qu'une fois la vue/le venue chargés** — comme toutes les commandes du pont, l'appel JS ne fait rien tant que `view` est `null` (`main()` dans `web/src/main.js` ne l'assigne qu'après `visioOne.createView(...)`). Le FAB qui ouvre cette feuille n'apparaît de toute façon que lorsque `MapLoadState.Ready` est atteint (voir `FeatureMapScreen`), donc en pratique la feuille ne peut pas s'ouvrir avant que `view` existe — mais un futur appel programmatique (hors FAB) devrait respecter la même contrainte.
- **Les 5 valeurs sont exactes et sensibles à la casse** — `floorSelector`, `navigation`, `poiDetails`, `search`, `userTracking`. Ce sont les seules valeurs du type `UIPart` côté SDK (`View.ts`) ; une chaîne mal cassée (`poidetails`, `Search`, etc.) n'est pas normalisée par le SDK, donc l'appel `view.setUIPartVisible(...)` échouerait silencieusement ou lèverait une erreur selon la version du SDK — cette démo ne fait aucune validation côté pont, elle transmet la chaîne telle quelle.
- **Masquer `search` ou `navigation` retire le seul moyen client de déclencher ces flux SDK** — contrairement à `floorSelector`, `poiDetails` ou `userTracking`, qui sont des affichages remplaçables par une UI native équivalente (voir `floor-selector`, `poi-click`), `search` et `navigation` sont aussi les points d'entrée par défaut pour rechercher un lieu et calculer un itinéraire dans l'UI du SDK. Un client qui les masque doit fournir sa propre UI de recherche/navigation (voir `goto-poi` et `compute-navigation` pour des exemples de pont natif→JS qui font exactement ça). Dans cette démo, comme les 5 interrupteurs restent dans la même feuille, on peut toujours les réactiver depuis là pour retrouver l'UI par défaut du SDK.
- **État purement local, pas de lecture de `view.isUIPartVisible`** — les interrupteurs partent tous à `true` sans interroger l'état réel de la `view` au montage. C'est cohérent avec le pattern de l'app (`FeatureMapScreen` recrée une WebView neuve, donc une `view` neuve, à chaque visite d'écran — voir `CLAUDE.md` du hub, "Menu de navigation par feature") : l'état par défaut du SDK et l'état initial des interrupteurs sont donc toujours synchronisés en pratique. Un client qui garderait une même instance de `view` entre plusieurs écrans voudrait probablement initialiser les interrupteurs via `view.isUIPartVisible(uiPart)` plutôt qu'une valeur en dur.
- **Reconstruire le bundle web est obligatoire** avant que les interrupteurs n'aient le moindre effet — même piège que toutes les features précédentes : Gradle ne rebuild jamais `web/` automatiquement.

## Pour aller plus loin

- `view.showUI` (booléen) masque/affiche toute l'UI par défaut d'un coup — plus radical que `setUIPartVisible`, pertinent pour un client qui remplace entièrement l'UI du SDK plutôt que d'en garder certaines parties.
- Rien n'empêche de combiner cette feature avec `floor-selector` (masquer `floorSelector` puis piloter les étages avec la liste native) ou avec de futures features de recherche/navigation natives pour reconstituer une UI 100 % maison au-dessus de la carte.
