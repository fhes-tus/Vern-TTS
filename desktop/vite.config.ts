import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';

// Ship the installed PDF.js support data offline, including its upstream notices.
// The development server exposes only this explicit asset map, never arbitrary paths.
function pdfAssets(): import('vite').Plugin {
  const assets = new Map<string, Buffer>();
  for (const directory of ['cmaps', 'standard_fonts', 'wasm']) {
    const root = resolve('node_modules/pdfjs-dist', directory);
    for (const filename of readdirSync(root)) {
      assets.set(`${directory}/${filename}`, readFileSync(resolve(root, filename)));
    }
  }
  assets.set('LICENSE', readFileSync(resolve('node_modules/pdfjs-dist/LICENSE')));
  return {
    name: 'vern-offline-pdf-assets',
    configureServer(server: import('vite').ViteDevServer) {
      server.middlewares.use('/pdfjs', (request, response, next) => {
        const key = request.url?.split('?')[0].replace(/^\//, '') ?? '';
        const bytes = assets.get(key);
        if (!bytes) {
          next();
          return;
        }
        response.setHeader(
          'Content-Type',
          key.endsWith('.wasm')
            ? 'application/wasm'
            : key.endsWith('.js')
              ? 'text/javascript'
              : 'application/octet-stream',
        );
        response.end(bytes);
      });
    },
    generateBundle() {
      for (const [name, source] of assets)
        this.emitFile({ type: 'asset', fileName: `pdfjs/${name}`, source });
    },
  };
}

export default defineConfig({
  plugins: [react(), pdfAssets()],
  clearScreen: false,
  server: {
    host: '127.0.0.1',
    port: 1420,
    strictPort: true,
    watch: { ignored: ['**/.tools/**', '**/src-tauri/**', '**/.artifacts/**'] },
  },
  build: { target: ['es2022', 'chrome105', 'safari15'] },
});
