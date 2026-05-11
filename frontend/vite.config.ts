import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    // Auto-imports Vue/router/i18n/pinia composables and (importantly here)
    // Element Plus *function* APIs like `ElMessage`, `ElMessageBox`,
    // `ElNotification`, `ElLoading` along with their CSS — so we no longer
    // need to ship the entire Element Plus bundle.
    AutoImport({
      imports: ['vue', 'vue-router', 'vue-i18n', 'pinia'],
      resolvers: [ElementPlusResolver()],
      dts: 'src/auto-imports.d.ts',
      eslintrc: { enabled: false },
    }),
    // Auto-resolves <el-*> components on demand. Only the components actually
    // referenced in templates land in the final bundle.
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts',
      dirs: [], // we don't want every local component auto-registered
    }),
  ],
  build: {
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
