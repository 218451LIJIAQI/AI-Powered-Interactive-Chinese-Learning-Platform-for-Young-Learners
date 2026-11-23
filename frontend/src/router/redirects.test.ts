import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import type { RouteMeta, Router } from 'vue-router'

import { deferred } from '@/test/helpers'
import type { HealthStatusResponse } from '@/types/api'

import { createMaintenanceCheck, sessionExpiredRedirect } from './redirects'

function route(fullPath: string, meta: RouteMeta = {}) {
  return { fullPath, meta }
}

describe('sessionExpiredRedirect', () => {
  it('sends the user of a signed-in page to sign in, with the way back and the reason', () => {
    expect(sessionExpiredRedirect(route('/student/learn?unit=2', { roles: ['STUDENT'] }))).toEqual({
      name: 'auth',
      query: { redirect: '/student/learn?unit=2', reason: 'expired' },
    })
  })

  it('leaves a public page open', () => {
    expect(sessionExpiredRedirect(route('/classes'))).toBeNull()
    expect(sessionExpiredRedirect(route('/help', { roles: [] }))).toBeNull()
  })
})

describe('createMaintenanceCheck', () => {
  const UP: HealthStatusResponse = { status: 'UP', ai: 'AVAILABLE' }

  function setup(health: () => Promise<HealthStatusResponse>, routeName = 'student-home') {
    const currentRoute = ref({ name: routeName })
    const replace = vi.fn(async (to: { name: string }) => {
      currentRoute.value = { name: to.name }
    })
    const router = { currentRoute, replace } as unknown as Pick<Router, 'currentRoute' | 'replace'>
    return { check: createMaintenanceCheck(router, health), replace }
  }

  it('stays on the page when the health check answers UP (the 503 was about one feature)', async () => {
    const health = vi.fn().mockResolvedValue(UP)
    const { check, replace } = setup(health)

    await check()

    expect(health).toHaveBeenCalledOnce()
    expect(replace).not.toHaveBeenCalled()
  })

  it.each([
    ['does not answer', () => Promise.reject(new Error('503'))],
    ['answers DOWN', () => Promise.resolve<HealthStatusResponse>({ status: 'DOWN', ai: 'AVAILABLE' })],
  ])('opens the maintenance page when the health check %s', async (_case, answer) => {
    const { check, replace } = setup(vi.fn(answer))

    await check()

    expect(replace).toHaveBeenCalledExactlyOnceWith({ name: 'maintenance' })
  })

  it('shares one health check between requests that fail together, and checks again later', async () => {
    const answer = deferred<HealthStatusResponse>()
    const health = vi.fn().mockReturnValueOnce(answer.promise).mockResolvedValue(UP)
    const { check, replace } = setup(health)

    const first = check()
    const second = check()
    expect(health).toHaveBeenCalledOnce()
    answer.resolve(UP)
    await Promise.all([first, second])

    await check()
    expect(health).toHaveBeenCalledTimes(2)
    expect(replace).not.toHaveBeenCalled()
  })

  it('does nothing on the maintenance page itself, so it cannot loop', async () => {
    const health = vi.fn().mockRejectedValue(new Error('503'))
    const { check, replace } = setup(health, 'maintenance')

    await check()

    expect(health).not.toHaveBeenCalled()
    expect(replace).not.toHaveBeenCalled()
  })

  it('opens the maintenance page once, then leaves it alone while it is shown', async () => {
    const health = vi.fn().mockRejectedValue(new Error('503'))
    const { check, replace } = setup(health)

    await check()
    await check()

    expect(health).toHaveBeenCalledOnce()
    expect(replace).toHaveBeenCalledOnce()
  })

  it('survives a navigation that fails', async () => {
    const currentRoute = ref({ name: 'student-home' })
    const replace = vi.fn().mockRejectedValue(new Error('chunk failed'))
    const router = { currentRoute, replace } as unknown as Pick<Router, 'currentRoute' | 'replace'>
    const check = createMaintenanceCheck(router, () => Promise.reject(new Error('503')))

    await expect(check()).resolves.toBeUndefined()
  })
})
