import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import { i18n } from './plugins/i18n'
import { router } from './router'
import { useAuthStore } from './stores/auth'
import './styles/main.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(i18n)
app.use(router)

app.mount('#app')

window.addEventListener('mandarin-spark:auth-invalidated', () => {
  const auth = useAuthStore(pinia)
  const activeRoute = router.currentRoute.value
  const shouldRedirectToAuth = Boolean(activeRoute.meta.roles?.length)

  auth.clearSession()

  if (shouldRedirectToAuth) {
    void router.replace({ name: 'auth', query: { redirect: activeRoute.fullPath } })
  }
})
