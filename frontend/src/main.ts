import { createPinia } from 'pinia'
import { createApp } from 'vue'

import { AUTH_INVALIDATED_EVENT, SERVICE_UNAVAILABLE_EVENT } from './api/http'
import { systemApi } from './api/services'
import App from './App.vue'
import { i18n } from './plugins/i18n'
import { router } from './router'
import { createMaintenanceCheck, sessionExpiredRedirect } from './router/redirects'
import { useAuthStore } from './stores/auth'
import { storageKeys } from './utils/storage'
import './styles/main.css'

const pinia = createPinia()

createApp(App).use(pinia).use(i18n).use(router).mount('#app')

function handleAuthInvalidated(): void {
  const auth = useAuthStore(pinia)
  // Read before the session is cleared: a signed-in page sends its user to sign in again, a public page stays open.
  const redirect = sessionExpiredRedirect(router.currentRoute.value)

  auth.clearSession()

  if (redirect) {
    void router.replace(redirect).catch(() => undefined)
  }
}

window.addEventListener(AUTH_INVALIDATED_EVENT, handleAuthInvalidated)

// A sign-in or sign-out in another tab: a tab still showing the previous account ends its session (stores/auth.ts).
window.addEventListener('storage', (event) => {
  if (event.key === storageKeys.sessionOwner) useAuthStore(pinia).followOtherTabSession(event.newValue)
})

// An API 503 is confirmed with the health check before the maintenance page opens.
const checkMaintenance = createMaintenanceCheck(router, () => systemApi.health())
window.addEventListener(SERVICE_UNAVAILABLE_EVENT, () => void checkMaintenance())
