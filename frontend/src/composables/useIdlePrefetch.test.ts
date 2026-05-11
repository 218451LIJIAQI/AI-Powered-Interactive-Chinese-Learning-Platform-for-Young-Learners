import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { createApp, defineComponent, h, nextTick, type App } from 'vue'

import { useAuthStore, type CurrentUser, type UserRole } from '../stores/authStore'
import { useIdlePrefetch } from './useIdlePrefetch'

/**
 * Mounts the composable inside a real Vue app so onMounted/onBeforeUnmount/
 * watch all run their normal lifecycle. We avoid `@vue/test-utils` because
 * it isn't a project dependency.
 */
function mountWithComposable(): { app: App; root: HTMLElement } {
  const root = document.createElement('div')
  document.body.appendChild(root)
  const Host = defineComponent({
    setup() {
      useIdlePrefetch()
      return () => h('div')
    },
  })
  const app = createApp(Host)
  app.mount(root)
  return { app, root }
}

function makeUser(roles: UserRole[]): CurrentUser {
  return {
    id: 1,
    fullName: 'Test',
    email: 't@example.com',
    preferredLanguage: 'en',
    roles,
    studentProfile: null,
    teacherProfile: null,
  }
}

describe('useIdlePrefetch', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
    // Force the polyfill path (setTimeout) so fake timers control scheduling.
    vi.stubGlobal('requestIdleCallback', undefined)
    vi.stubGlobal('cancelIdleCallback', undefined)
    Object.defineProperty(navigator, 'connection', { value: undefined, configurable: true })
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    Object.defineProperty(navigator, 'connection', { value: undefined, configurable: true })
    document.body.innerHTML = ''
  })

  it('schedules a dashboard prefetch when a role is signed in', async () => {
    useAuthStore().currentUser = makeUser(['STUDENT'])
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout')

    const { app, root } = mountWithComposable()
    await nextTick()

    expect(setTimeoutSpy).toHaveBeenCalled()
    app.unmount()
    root.remove()
  })

  it('does not schedule prefetch when no role is signed in', async () => {
    useAuthStore().currentUser = null
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout')

    const { app, root } = mountWithComposable()
    await nextTick()

    expect(setTimeoutSpy).not.toHaveBeenCalled()
    app.unmount()
    root.remove()
  })

  it('skips prefetch on save-data connections', async () => {
    useAuthStore().currentUser = makeUser(['TEACHER'])
    Object.defineProperty(navigator, 'connection', {
      value: { saveData: true, effectiveType: '4g' },
      configurable: true,
    })
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout')

    const { app, root } = mountWithComposable()
    await nextTick()

    expect(setTimeoutSpy).not.toHaveBeenCalled()
    app.unmount()
    root.remove()
  })

  it('cancels pending idle work on unmount', async () => {
    useAuthStore().currentUser = makeUser(['STUDENT'])
    const clearTimeoutSpy = vi.spyOn(window, 'clearTimeout')

    const { app, root } = mountWithComposable()
    await nextTick()
    app.unmount()
    root.remove()

    expect(clearTimeoutSpy).toHaveBeenCalled()
  })
})
