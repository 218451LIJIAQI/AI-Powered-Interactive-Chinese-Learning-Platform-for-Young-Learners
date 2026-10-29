import path from 'node:path'

import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts'],
    setupFiles: './src/test/setup.ts',
    // Each test starts with cleared mock calls, restored vi.spyOn spies and unstubbed globals.
    clearMocks: true,
    restoreMocks: true,
    unstubGlobals: true,
  },
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  server: {
    port: 5174,
    strictPort: true,
    proxy: {
      '/api/v1': {
        // The end-to-end tests point the proxy at their own backend (playwright.config.ts).
        target: process.env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (/\/node_modules\/(vue|@vue|vue-router|pinia|vue-i18n)\//.test(id)) return 'framework'
          if (id.includes('/node_modules/axios/')) return 'networking'
          if (id.includes('/node_modules/@lucide/vue/')) return 'icons'
        },
      },
    },
  },
})
