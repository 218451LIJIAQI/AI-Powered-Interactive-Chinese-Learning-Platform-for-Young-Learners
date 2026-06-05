import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useResource } from './useResource'

const mocks = vi.hoisted(() => ({
  appEnv: {
    useDemoData: false,
    enablePreviewMode: false,
  },
  auth: {
    isPreviewSession: false,
  },
}))

vi.mock('@/config/env', () => ({
  appEnv: mocks.appEnv,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mocks.auth,
}))

function mountResource<T>(loader: () => Promise<T>, fallback?: (() => T) | T, immediate = true) {
  let resource: ReturnType<typeof useResource<T>> | undefined
  mount(
    defineComponent({
      setup() {
        resource = useResource(loader, fallback, immediate)
        return () => null
      },
    }),
  )

  if (!resource) {
    throw new Error('resource did not initialize')
  }

  return resource
}

describe('useResource guarded fallback behavior', () => {
  beforeEach(() => {
    mocks.appEnv.useDemoData = false
    mocks.appEnv.enablePreviewMode = false
    mocks.auth.isPreviewSession = false
  })

  it('does not render fallback fixtures for a normal non-preview API outage', async () => {
    const loader = vi.fn().mockRejectedValue({ request: {} })
    const resource = mountResource(loader, { message: 'demo fixture' })

    await flushPromises()

    expect(loader).toHaveBeenCalledOnce()
    expect(resource.data.value).toBeNull()
    expect(resource.usingFallback.value).toBe(false)
    expect(resource.error.value).toBe(
      'We cannot reach the learning service right now. Check your connection and try again.',
    )
    expect(resource.loading.value).toBe(false)
  })

  it('uses preview fixtures without issuing a live request when preview mode is active', async () => {
    mocks.appEnv.useDemoData = true
    mocks.appEnv.enablePreviewMode = true
    mocks.auth.isPreviewSession = true
    const fallback = { message: 'safe preview fixture' }
    const loader = vi.fn().mockResolvedValue({ message: 'live data' })
    const resource = mountResource(loader, () => fallback)

    await flushPromises()

    expect(loader).not.toHaveBeenCalled()
    expect(resource.data.value).toEqual(fallback)
    expect(resource.usingFallback.value).toBe(true)
    expect(resource.error.value).toBeNull()
    expect(resource.loading.value).toBe(false)
  })

  it('uses preview fixtures for anonymous public preview pages without issuing a live request', async () => {
    mocks.appEnv.useDemoData = true
    mocks.appEnv.enablePreviewMode = true
    mocks.auth.isPreviewSession = false
    const fallback = { message: 'anonymous preview fixture' }
    const loader = vi.fn().mockResolvedValue({ message: 'live data' })
    const resource = mountResource(loader, () => fallback)

    await flushPromises()

    expect(loader).not.toHaveBeenCalled()
    expect(resource.data.value).toEqual(fallback)
    expect(resource.usingFallback.value).toBe(true)
    expect(resource.error.value).toBeNull()
    expect(resource.loading.value).toBe(false)
  })

  it('loads live data on demand and clears prior errors on reload', async () => {
    const loader = vi
      .fn()
      .mockRejectedValueOnce({ response: { status: 500, data: {} } })
      .mockResolvedValueOnce({ count: 2 })
    const resource = mountResource(loader, undefined, false)

    expect(resource.loading.value).toBe(false)
    expect(resource.data.value).toBeNull()

    await resource.reload()
    expect(resource.error.value).toBe('The learning service is having trouble right now. Please try again shortly.')
    expect(resource.data.value).toBeNull()

    await resource.reload()
    expect(resource.error.value).toBeNull()
    expect(resource.data.value).toEqual({ count: 2 })
    expect(resource.usingFallback.value).toBe(false)
  })
})
