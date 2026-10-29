import { mount } from '@vue/test-utils'
import type { Component } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

const Blank = { template: '<div />' }

/** The admin routes the ops admin pages link to, each rendering nothing. */
function createAdminTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin/dashboard', name: 'admin-dashboard', component: Blank },
      { path: '/admin/users', name: 'admin-users', component: Blank },
      { path: '/admin/users/applications', name: 'admin-teacher-applications', component: Blank },
      { path: '/admin/content', name: 'admin-content-review', component: Blank },
      { path: '/admin/ai', name: 'admin-ai-reports', component: Blank },
      { path: '/admin/ai/usage', name: 'admin-ai-usage', component: Blank },
      { path: '/admin/support', name: 'admin-support', component: Blank },
      { path: '/admin/analytics', name: 'admin-analytics', component: Blank },
      { path: '/admin/audit-log', name: 'admin-audit-log', component: Blank },
    ],
  })
}

/**
 * Mounts an admin page at `path` with a real router; dialogs and toasts render in place (Teleport stubbed) so the
 * wrapper finds them.
 */
export async function mountAdminPage(page: Component, path: string) {
  const router = createAdminTestRouter()
  await router.push(path)
  await router.isReady()
  const wrapper = mount(page, { global: { plugins: [router], stubs: { teleport: true } } })
  return { wrapper, router }
}
