# Verrouillage caméra sur la position

## Description

Un bouton "recentrer sur moi" façon app GPS grand public : un `Switch` qui verrouille/déverrouille la caméra sur la position simulée en pilotant `view.lockCameraPositionOnTracking` (booléen), la même propriété qu'une vraie intégration de positionnement indoor utiliserait. C'est la huitième commande ajoutée au pont natif→JS (`window.MapBridge` + `WebView.evaluateJavascript`), après `resolvePositions`/`injectTrackedPosition`/`stopTrackedPosition` (`simulated-position`).

Cette feature dépend directement de `simulated-position` : verrouiller la caméra n'a de sens que sur une position déjà suivie, donc l'écran réutilise entièrement ses Origin/Destination POI ID, son slider de rayon et son bouton Start/Stop — voir "Points d'attention" pour comment le code partage cette logique sans la dupliquer.

## Step by step

1. **Ajouter la commande à `window.MapBridge`** (`web/src/main.js`) :
   ```js
   setCameraLockOnPosition(locked) {
     if (!view) return;
     view.lockCameraPositionOnTracking = locked;
   },
   ```
   Aucune vérification d'`allowTracking` n'est nécessaire ici : la propriété n'a simplement aucun effet visible tant qu'`allowTracking` n'est pas `true` (voir la doc du SDK sur `lockCameraPositionOnTracking`), pas d'exception levée contrairement à `injectTrackedPosition`.

2. **Reconstruire le bundle web** — `cd web && npm run build`. Même piège que pour toutes les features précédentes : Gradle ne le fait jamais automatiquement.

3. **Ajouter l'extension d'appel côté `FeatureOverlays.kt`** :
   ```kotlin
   private fun WebView.setCameraLockOnPosition(locked: Boolean) {
       evaluateJavascript("window.MapBridge.setCameraLockOnPosition($locked)", null)
   }
   ```
   `locked` est un booléen Kotlin, dont `toString()` produit directement un littéral JS valide (`true`/`false`) — pas de `JSONObject.quote()` nécessaire, même convention que les arguments numériques de `injectTrackedPosition`.

4. **Factoriser la logique de tracking de `simulated-position`** dans une fonction privée partagée, `TrackedPositionSimulationControls` (Origin/Destination fields, slider, bouton Start/Stop, boucle d'interpolation) qui accepte deux paramètres additionnels :
   - `onRunningChanged: (Boolean) -> Unit` — notifié à chaque transition start/stop, pour que l'appelant puisse réagir (ici : désactiver et réinitialiser le switch de verrouillage quand la simulation s'arrête).
   - `extraContent: @Composable () -> Unit` — rendu sous le bouton Start/Stop et son message d'erreur ; vide pour `SimulatedPositionOverlay`, le switch de verrouillage pour `CameraLockOnPositionOverlay`.

   `SimulatedPositionOverlay` devient un simple wrapper sans argument additionnel ; `CameraLockOnPositionOverlay` fournit son propre `Switch` :
   ```kotlin
   @Composable
   fun CameraLockOnPositionOverlay(webView: WebView?, positionsResolved: ResolvedPositionsPair?) {
       var isRunning by remember { mutableStateOf(false) }
       var isLocked by remember { mutableStateOf(false) }

       TrackedPositionSimulationControls(
           webView = webView,
           positionsResolved = positionsResolved,
           onRunningChanged = { running ->
               isRunning = running
               if (!running && isLocked) {
                   isLocked = false
                   webView?.setCameraLockOnPosition(false)
               }
           },
           extraContent = {
               Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                   Text(text = "Recenter camera on position", modifier = Modifier.weight(1f))
                   Switch(
                       checked = isLocked,
                       enabled = isRunning,
                       onCheckedChange = { locked ->
                           isLocked = locked
                           webView?.setCameraLockOnPosition(locked)
                       },
                   )
               }
           },
       )
   }
   ```

5. **Ajouter l'entrée au registre de features** (`Feature.kt` + `strings.xml`/`strings-fr.xml`) et **câbler la route** dans `MainActivity.kt` :
   ```kotlin
   Feature.CameraLockOnPosition -> CameraLockOnPositionOverlay(webView, positionsResolved)
   ```

## Points d'attention

- **Le switch est désactivé tant qu'aucune simulation ne tourne** (`enabled = isRunning`) — verrouiller la caméra sur rien n'a pas de sens, et ça évite un état incohérent (switch "on" affiché alors qu'`allowTracking` est encore `false`).
- **Le switch se réinitialise à chaque arrêt** — que ce soit un Stop explicite ou un "POI not found", `onRunningChanged(false)` repasse `isLocked` à `false` et rappelle `setCameraLockOnPosition(false)` côté JS, pour qu'un redémarrage recommence toujours déverrouillé (un opt-in délibéré à chaque fois, jamais un état qui traîne d'une session à l'autre). Même raisonnement que le rayon d'accuracy qui ne redémarre pas la boucle : pas besoin de relancer quoi que ce soit, juste de resynchroniser l'état affiché avec la réalité.
- **Factorisation plutôt que duplication** — `simulated-position` et cette feature partagent exactement la même mécanique de tracking (résolution de POI, boucle d'interpolation 150ms, gestion d'erreur "POI not found") ; dupliquer ces ~80 lignes dans deux composables aurait été un risque de bug (une correction faite dans l'un et oubliée dans l'autre), donc `TrackedPositionSimulationControls` porte cette logique une seule fois. Voir `FeatureOverlays.kt`.
- **Aucun effet visible sans mouvement notable de la position simulée** — si les deux POI choisis sont géographiquement proches (deux places de parking voisines, par exemple), le déplacement de caméra induit par le verrouillage peut être trop subtil pour être perçu à l'œil ; choisir deux POI clairement éloignés sur la carte pour une démonstration visuelle convaincante.
- **`lockCameraOrientationOnTracking` (orientation) reste hors scope** — le SDK expose aussi ce second flag pour verrouiller l'orientation de la caméra sur les données de capteur (boussole/gyroscope) injectées via `injectDeviceOrientation`, un troisième état "verrouillée position + orientation" en plus de "libre"/"verrouillée position". Cette démo se limite au verrouillage de position, qui ne dépend que de données déjà simulées ; l'orientation demanderait une source de données supplémentaire (réelle ou simulée) non couverte ici.
- **Reconstruire le bundle web est obligatoire** avant que quoi que ce soit ne fonctionne — même piège que toutes les features précédentes.

## Pour aller plus loin

- `view.lockCameraOrientationOnTracking` complèterait cette démo avec le troisième état ("verrouillée position + orientation") mentionné dans la référence technique du SDK, si un besoin client concret apparaît — voir le `ROADMAP.md` du hub (`VisioOneHub`).
- Le SDK ne documente aucun événement de "déverrouillage automatique" (ex. l'utilisateur pan/zoom manuellement pendant que la caméra est verrouillée) — à vérifier au cas par cas si un client a besoin de ce comportement type app GPS grand public (où toucher la carte déverrouille implicitement le suivi).
