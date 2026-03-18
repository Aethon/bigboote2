import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * Vite configuration for the Bigboote Vue UI.
 *
 * Dev proxy rules:
 *   /api      → coordinator HTTP (port 8080) — REST API + WebSocket upgrade
 *   /internal → coordinator HTTP (port 8080) — internal/gateway API (Phase 12+)
 *
 * WebSocket connections (e.g. /api/v1/efforts/{id}/messaging) are handled by
 * the /api proxy rule with ws: true so the browser upgrade is forwarded correctly.
 *
 * Path alias @/ → src/ for clean imports throughout the project.
 *
 * See UX Design doc Section 2 and Phase 16 scaffold spec.
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
      '/internal': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
