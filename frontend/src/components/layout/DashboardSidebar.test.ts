import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('virtual:pwa-register', () => ({ registerSW: vi.fn() }))

import { i18n } from '@/plugins/i18n'

import DashboardSidebar from './DashboardSidebar.vue'

const Empty = { template: '<div />' }

async function mountAt(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['/student/learn', '/student/practice', '/student/home'].map((routePath) => ({
      path: routePath,
      component: Empty,
      meta: { previewRole: 'STUDENT' as const },
    })),
  })
  await router.push(path)
  await router.isReady()
  return mount(DashboardSidebar, { global: { plugins: [createPinia(), i18n, router] } })
}

function activeLinks(wrapper: Awaited<ReturnType<typeof mountAt>>): string[] {
  return wrapper.findAll('a.sidebar-link.router-link-active').map((link) => link.attributes('href') ?? '')
}

describe('DashboardSidebar active link', () => {
  it('keeps the current page highlighted when the page adds query parameters', async () => {
    expect(activeLinks(await mountAt('/student/learn'))).toEqual(['/student/learn'])
    expect(activeLinks(await mountAt('/student/learn?lessonId=3'))).toEqual(['/student/learn'])
    expect(activeLinks(await mountAt('/student/practice?activity=matching'))).toEqual(['/student/practice'])
  })
})
