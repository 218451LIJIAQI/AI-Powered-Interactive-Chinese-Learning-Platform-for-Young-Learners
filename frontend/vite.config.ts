import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  build: {
    // Demo builds stay under a single vendor bundle plus app bundle, which is
    // acceptable for an FYP. The manual chunks below split the heaviest
    // dependencies into their own files so initial parse is snappier.
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        manualChunks(id: string) {
          if (!id.includes('node_modules')) {
            return undefined
          }

          if (id.includes('echarts')) {
            return 'vendor-echarts'
          }

          if (id.includes('element-plus') || id.includes('@element-plus')) {
            return 'vendor-element-plus'
          }

          if (
            id.includes('/vue/') ||
            id.includes('/vue-router/') ||
            id.includes('/vue-i18n/') ||
            id.includes('/@vue/') ||
            id.includes('/pinia/')
          ) {
            return 'vendor-vue'
          }

          return undefined
        },
      },
    },
  },
})
