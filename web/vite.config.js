import { defineConfig } from 'vite';

// Bundled output is served by the Android app through WebViewAssetLoader at
// https://appassets.androidx.local/assets/www/, so every asset path must stay relative.
export default defineConfig({
  base: './',
  build: {
    outDir: '../app/src/main/assets/www',
    emptyOutDir: true,
  },
});
