import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { isUnreachableFailure, resolveFallbackMode, useResource } from './useResource'

const mocks = vi.hoisted(() => ({
  appEnv: {
    useDemoData: false,
    enablePreviewMode: false,
  },
  auth: {
    isPreviewSession: false,
    isAuthenticated: false,
  },
}))

vi.mock('@/config/env', () => ({
  appEnv: mocks.appEnv,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mocks.auth,
}))

const wrappers: VueWrapper[] = []

function enablePreviewFallbacks(isPreviewSession = true): void {
  mocks.appEnv.useDemoData = true
  mocks.appEnv.enablePreviewMode = true
  mocks.auth.isPreviewSession = isPreviewSession
}

function mountResource<T>(loader: () => Promise<T>, fallback?: (() => T) | T, immediate = true) {
  let resource: ReturnType<typeof useResource<T>> | undefined
  const wrapper = mount(
    defineComponent({
      setup() {
        resource = useResource(loader, fallback, immediate)
        return () => null
      },
    }),
  )
  wrappers.push(wrapper)

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
    mocks.auth.isAuthenticated = false
  })

  afterEach(() => {
    wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
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
    enablePreviewFallbacks()
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
    enablePreviewFallbacks(false)
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

  it('never serves fixtures to a real signed-in user while the API answers, even in a preview build', async () => {
    enablePreviewFallbacks(false)
    mocks.auth.isAuthenticated = true
    const loader = vi.fn().mockResolvedValue({ message: 'live data' })
    const resource = mountResource(loader, { message: 'demo fixture' })

    await flushPromises()

    expect(loader).toHaveBeenCalledOnce()
    expect(resource.data.value).toEqual({ message: 'live data' })
    expect(resource.usingFallback.value).toBe(false)
  })

  it('shows HTTP errors (e.g. 404) to a real signed-in user instead of demo data', async () => {
    enablePreviewFallbacks(false)
    mocks.auth.isAuthenticated = true
    const loader = vi.fn().mockRejectedValue({ response: { status: 404, data: { message: 'Lesson was not found.' } } })
    const resource = mountResource(loader, { message: 'demo fixture' })

    await flushPromises()

    expect(resource.data.value).toBeNull()
    expect(resource.usingFallback.value).toBe(false)
    expect(resource.error.value).toBe('Lesson was not found.')
  })

  it('falls back to the preview fixture for a signed-in user only when the API is unreachable', async () => {
    enablePreviewFallbacks(false)
    mocks.auth.isAuthenticated = true
    const loader = vi.fn().mockRejectedValue({ request: {} })
    const resource = mountResource(loader, { message: 'demo fixture' })

    await flushPromises()

    expect(loader).toHaveBeenCalledOnce()
    expect(resource.data.value).toEqual({ message: 'demo fixture' })
    expect(resource.usingFallback.value).toBe(true)
  })
})

describe('resolveFallbackMode rule', () => {
  const base = { hasFallback: true, useDemoData: true, enablePreviewMode: true, hasRealSession: false }

  it('uses fixtures immediately only in a preview build without a real session', () => {
    expect(resolveFallbackMode(base)).toBe('immediate')
    expect(resolveFallbackMode({ ...base, hasRealSession: true })).toBe('onUnreachable')
  })

  it('never uses fixtures outside a full preview build or without a fixture', () => {
    expect(resolveFallbackMode({ ...base, useDemoData: false })).toBe('none')
    expect(resolveFallbackMode({ ...base, enablePreviewMode: false })).toBe('none')
    expect(resolveFallbackMode({ ...base, hasFallback: false })).toBe('none')
    expect(resolveFallbackMode({ ...base, enablePreviewMode: false, hasRealSession: true })).toBe('none')
  })

  it('treats only failures without an HTTP response as unreachable', () => {
    expect(isUnreachableFailure({ request: {} })).toBe(true)
    expect(isUnreachableFailure({ request: {}, response: { status: 404 } })).toBe(false)
    expect(isUnreachableFailure({ response: { status: 500 } })).toBe(false)
    expect(isUnreachableFailure(null)).toBe(false)
  })
})
