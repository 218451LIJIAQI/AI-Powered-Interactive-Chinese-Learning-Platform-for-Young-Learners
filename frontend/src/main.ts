/**
 * Vue 3 + Pinia + Vue Router + Vue I18n bootstrap for the MandarinSpark
 * frontend.
 *
 * <p>Plugin install order matters:</p>
 * <ol>
 *   <li>Pinia first so stores are available to other plugins.</li>
 *   <li>i18n second so router-level guards / view components can
 *       resolve translated route titles.</li>
 *   <li>Router last so it can read the auth store and i18n during
 *       initial navigation.</li>
 * </ol>
 *
 * <h2>Auth-invalidation event</h2>
 * <p>The HTTP layer ({@code api/http.ts}) emits a
 * {@code mandarin-spark:auth-invalidated} CustomEvent when the backend
 * returns 401. This listener clears the session and, only when the
 * current route requires a role, redirects to {@code /auth} preserving
 * the original path for post-login restoration. Public routes are
 * left untouched so an expired session does not surprise a visitor
 * browsing the unauthenticated catalogue.</p>
 */
import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import { i18n } from './plugins/i18n'
import { initObservability } from './plugins/observability'
import { clearOfflineCaches } from './plugins/pwa'
import { router } from './router'
import { useAuthStore } from './stores/auth'
import './styles/main.css'

const app = createApp(App)
const pinia = createPinia()

initObservability(app, router)
app.use(pinia)
app.use(i18n)
app.use(router)

app.mount('#app')

window.addEventListener('mandarin-spark:auth-invalidated', () => {
  const auth = useAuthStore(pinia)
  const activeRoute = router.currentRoute.value
  const shouldRedirectToAuth = Boolean(activeRoute.meta.roles?.length)

  auth.clearSession()
  void clearOfflineCaches()

  if (shouldRedirectToAuth) {
    void router.replace({ name: 'auth', query: { redirect: activeRoute.fullPath } })
  }
})
