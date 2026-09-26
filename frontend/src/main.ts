import { createPinia } from 'pinia'
import { createApp } from 'vue'

import { AUTH_INVALIDATED_EVENT } from './api/http'
import App from './App.vue'
import { i18n } from './plugins/i18n'
import { initObservability } from './plugins/observability'
import { clearOfflineCaches } from './plugins/pwa'
import { router } from './router'
import { useAuthStore } from './stores/auth'
import { readPersistedLocale } from './utils/storage'
import './styles/main.css'

document.documentElement.lang = readPersistedLocale()

const app = createApp(App)
const pinia = createPinia()

initObservability(app, router)
app.use(pinia)
app.use(i18n)
app.use(router)

app.mount('#app')

function handleAuthInvalidated(): void {
  const auth = useAuthStore(pinia)
  const activeRoute = router.currentRoute.value
  const shouldRedirectToAuth = Boolean(activeRoute.meta.roles?.length)

  auth.clearSession()
  void clearOfflineCaches()

  if (shouldRedirectToAuth) {
    void router.replace({ name: 'auth', query: { redirect: activeRoute.fullPath } })
  }
}

window.addEventListener(AUTH_INVALIDATED_EVENT, handleAuthInvalidated)
