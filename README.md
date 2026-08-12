# VisioOne Meet Android

App Android (Kotlin + Jetpack Compose) qui affiche une carte [VisioOne](https://my.visioglobe.com/docs/VisioOne/docs/) dans une WebView.

## Comment ça marche

Le SDK VisioOne (`@visioglobe/visioone` sur npm) est un SDK JS/WebGL. Il est bundlé une fois avec Vite (dossier `web/`), et le résultat est copié dans `app/src/main/assets/www/`. L'app Android sert ensuite ces fichiers dans une `WebView` via `WebViewAssetLoader` (origine `https://appassets.androidx.local/...`), ce qui évite les soucis de CORS liés au chargement de modules ES depuis `file://`.

Le hash de la carte est passé en query param (`index.html?hash=...`) par `VisioOneMapScreen.kt`, donc changer de carte ne nécessite pas de rebuild du bundle web — seule la constante `DEFAULT_MAP_HASH` dans `MainActivity.kt` doit changer.

Un pont JS (`window.AndroidBridge`, voir `web/src/main.js`) notifie la Compose UI quand la carte est prête (`onMapReady`) ou en erreur (`onMapError`), pour afficher un loader / message d'erreur pendant le chargement (qui prend ~20-30s au premier lancement, le temps de récupérer les assets 3D depuis `mapserver.visioglobe.com`).

## Structure

```
app/                    Projet Android (Compose)
  src/main/assets/www/  Bundle web généré (voir web/), servi par la WebView
web/                    Petit projet Vite qui importe @visioglobe/visioone
```

## Build & run

```bash
./gradlew installDebug
```

## Mettre à jour le bundle web (nouvelle version du SDK, changement de web/src/main.js, etc.)

```bash
cd web
npm install
npm run build   # écrit directement dans ../app/src/main/assets/www
```
