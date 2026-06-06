import path from 'node:path'

import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'

function isNodeModule(id: string, packageName: string): boolean {
  return id.includes(`/node_modules/${packageName}/`)
}

export default defineConfig({
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg'],
      manifest: {
        name: 'MandarinSpark Chinese Learning',
        short_name: 'MandarinSpark',
        description: 'AI-powered interactive Chinese learning for young learners.',
        theme_color: '#f97316',
        background_color: '#fff7ed',
        display: 'standalone',
        start_url: '/',
        scope: '/',
        icons: [
          {
            src: '/favicon.svg',
            sizes: 'any',
            type: 'image/svg+xml',
            purpose: 'any maskable',
          },
        ],
      },
      workbox: {
        cleanupOutdatedCaches: true,
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api\//, /^\/actuator\//],
        globPatterns: ['**/*.{js,css,html,svg,ico,woff2}'],
        runtimeCaching: [
          {
            urlPattern: ({ sameOrigin, url }) => sameOrigin && !url.pathname.startsWith('/api/'),
            handler: 'StaleWhileRevalidate',
            options: {
              cacheName: 'mandarin-spark-static-v1',
              expiration: {
                maxEntries: 80,
                maxAgeSeconds: 60 * 60 * 24 * 7,
              },
            },
          },
        ],
      },
    }),
  ],
  test: {
    environment: 'jsdom',
    environmentOptions: {
      jsdom: {
        url: 'http://localhost:3000/',
      },
    },
    exclude: ['node_modules/**', 'dist/**'],
    globals: true,
    setupFiles: './src/test/setup.ts',
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5174,
    strictPort: true,
    proxy: {
      '/api/v1': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (
            isNodeModule(id, 'vue') ||
            isNodeModule(id, '@vue') ||
            isNodeModule(id, 'vue-router') ||
            isNodeModule(id, 'pinia') ||
            isNodeModule(id, 'vue-i18n')
          ) {
            return 'framework'
          }
          if (isNodeModule(id, 'axios')) {
            return 'networking'
          }
          if (isNodeModule(id, '@lucide/vue')) {
            return 'icons'
          }
          return undefined
        },
      },
    },
  },
})
