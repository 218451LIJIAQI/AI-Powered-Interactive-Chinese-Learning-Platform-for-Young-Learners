import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'

import App from './App.vue'
import { i18n } from './i18n'
import { router } from './router'
import { pinia } from './stores'
import { useAuthStore } from './stores/authStore'
import './styles/main.css'

const app = createApp(App)

Object.entries(ElementPlusIconsVue).forEach(([key, component]) => {
  app.component(key, component)
})

app.use(ElementPlus)
app.use(pinia)
app.use(router)
app.use(i18n)

const authStore = useAuthStore(pinia)
if (authStore.token) {
  authStore.fetchCurrentUser().catch(() => {
    authStore.clearAuthState()
  })
} else {
  authStore.initialized = true
}

app.mount('#app')
