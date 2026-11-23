import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/api/http'
import { deferred } from '@/test/helpers'
import type { UserRole } from '@/types/api'

const { auth, route, router, health, join } = vi.hoisted(() => ({
  health: vi.fn(),
  join: vi.fn(),
  auth: {
    logout: vi.fn(),
    restoreSession: vi.fn(),
    isAuthenticated: false,
    roles: [] as UserRole[],
    get activeRole() {
      return this.roles[0] ?? null
    },
  },
  router: { replace: vi.fn(), push: vi.fn() },
  route: { query: {} as Record<string, string> },
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  RouterLink: (await import('@/test/helpers')).routerLinkStub,
  useRoute: () => route,
  useRouter: () => router,
}))

vi.mock('@/api/services', () => ({ systemApi: { health } }))
vi.mock('@/api/classes', () => ({ studentClassesApi: { join } }))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))

import SystemStatusPage from './SystemStatusPage.vue'

/** The AppBrand stub shows where the logo leads. */
const AppBrandStub = { props: ['to'], template: '<a class="brand-stub" :href="to">MandarinSpark</a>' }

function mountPage(kind: 'not-found' | 'unauthorized' | 'maintenance'): VueWrapper {
  return mount(SystemStatusPage, {
    props: { kind },
    global: { stubs: { AppBrand: AppBrandStub, IconGlyph: true, LanguageSwitcher: true } },
  })
}

function signInAs(...roles: UserRole[]): void {
  auth.isAuthenticated = true
  auth.roles = roles
}

const actions = (wrapper: VueWrapper) =>
  wrapper.findAll('.btn').map((action) => [action.text(), action.attributes('href') ?? null])

beforeEach(() => {
  setAccessToken(null)
  auth.logout.mockReset()
  auth.restoreSession.mockReset()
  health.mockReset()
  join.mockReset()
  router.push.mockReset()
  router.replace.mockReset()
  auth.isAuthenticated = false
  auth.roles = []
  route.query = {}
})

describe('SystemStatusPage 404', () => {
  it('takes a signed-in learner back into their part of the app: home, Find classes and the logo', () => {
    signInAs('STUDENT')

    const wrapper = mountPage('not-found')

    expect(wrapper.get('h1').text()).toBe('This page could not be found')
    expect(wrapper.get('[data-testid="system-copy"]').text()).toBe(
      'The link may be old, or the page was moved or removed.',
    )
    expect(actions(wrapper)).toEqual([
      ['Go to my home', '/student/home'],
      ['Explore classes', JSON.stringify({ name: 'student-classes-find' })],
    ])
    expect(wrapper.get('[data-testid="system-home"]').classes()).toContain('btn-primary')
    expect(wrapper.get('.brand-stub').attributes('href')).toBe('/student/home')
  })

  it('takes a visitor to the public home and the public classes', () => {
    const wrapper = mountPage('not-found')

    expect(actions(wrapper)).toEqual([
      ['Return home', '/'],
      ['Explore classes', JSON.stringify({ name: 'classes' })],
    ])
    expect(wrapper.get('.brand-stub').attributes('href')).toBe('/')
  })

  it('sends a signed-in admin to their own home', () => {
    signInAs('ADMIN')

    const wrapper = mountPage('not-found')

    expect(wrapper.get('[data-testid="system-home"]').attributes('href')).toBe('/admin/dashboard')
    expect(wrapper.get('.brand-stub').attributes('href')).toBe('/admin/dashboard')
  })
})

describe('SystemStatusPage 403', () => {
  it('says a lesson of a class not joined is for its members, with the way back and Join class (P3.02)', () => {
    signInAs('STUDENT')
    route.query = { redirect: '/student/classes/12/lessons/34' }

    const wrapper = mountPage('unauthorized')

    expect(wrapper.get('h1').text()).toBe('This lesson is for class members')
    expect(wrapper.get('[data-testid="system-copy"]').text()).toBe(
      'Lessons open after you join the class. Joining is free.',
    )
    expect(actions(wrapper)).toEqual([
      ['Back to class', JSON.stringify({ name: 'student-class', params: { classId: '12' } })],
      ['Join class', null],
    ])
    expect(wrapper.get('[data-testid="system-class"]').classes()).toContain('btn-secondary')
    expect(wrapper.get('[data-testid="system-join"]').classes()).toContain('btn-primary')
    // Nothing here signs the learner out.
    expect(wrapper.find('[data-testid="system-switch-account"]').exists()).toBe(false)
    expect(wrapper.get('.brand-stub').attributes('href')).toBe('/student/home')
  })

  it('joins the class and opens the lesson that was refused', async () => {
    signInAs('STUDENT')
    route.query = { redirect: '/student/classes/12/lessons/34' }
    const joined = deferred<void>()
    join.mockReturnValueOnce(joined.promise)
    const wrapper = mountPage('unauthorized')

    await wrapper.get('[data-testid="system-join"]').trigger('click')
    expect(wrapper.get('[data-testid="system-join"]').text()).toBe('Joining…')
    expect(wrapper.get('[data-testid="system-join"]').attributes('disabled')).toBeDefined()
    joined.resolve()
    await flushPromises()

    expect(join).toHaveBeenCalledExactlyOnceWith(12)
    expect(router.replace).toHaveBeenCalledWith('/student/classes/12/lessons/34')
  })

  it('says why the class could not be joined and stays', async () => {
    signInAs('STUDENT')
    route.query = { redirect: '/student/classes/12/lessons/34' }
    join.mockRejectedValueOnce(new Error('offline'))
    const wrapper = mountPage('unauthorized')

    await wrapper.get('[data-testid="system-join"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="system-join-error"]').text()).toBe('The class could not be joined. Try again.')
    expect(router.replace).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="system-join"]').text()).toBe('Join class')
  })

  it.each(['/student/classes/9007199254740993/lessons/34', '/student/classes/12/lessons/old'])(
    'does not offer to join a class from an invalid lesson link %s',
    (redirect) => {
      signInAs('STUDENT')
      route.query = { redirect }
      expect(mountPage('unauthorized').find('[data-testid="system-join"]').exists()).toBe(false)
      expect(join).not.toHaveBeenCalled()
    },
  )

  it('keeps the newer account on its own page when an earlier class join completes', async () => {
    signInAs('STUDENT')
    route.query = { redirect: '/student/classes/12/lessons/34' }
    const joined = deferred<void>()
    join.mockReturnValueOnce(joined.promise)
    const wrapper = mountPage('unauthorized')
    await wrapper.get('[data-testid="system-join"]').trigger('click')
    setAccessToken('new-account-after-class-join')
    joined.resolve()
    await flushPromises()
    expect(router.replace).not.toHaveBeenCalled()
  })

  it('offers home first, then another account, for a page of the other role', async () => {
    // An admin's link opened on a computer where a learner is signed in.
    signInAs('STUDENT')
    route.query = { redirect: '/admin/users' }
    auth.logout.mockResolvedValue(true)
    const wrapper = mountPage('unauthorized')

    expect(wrapper.get('[data-testid="system-copy"]').text()).toBe(
      'This page is part of another kind of account. Sign in with that account to open it.',
    )
    expect(wrapper.find('[data-testid="system-class"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="system-home"]').attributes('href')).toBe('/student/home')
    expect(wrapper.get('[data-testid="system-home"]').classes()).toContain('btn-primary')

    await wrapper.get('[data-testid="system-switch-account"]').trigger('click')
    await flushPromises()

    expect(auth.logout).toHaveBeenCalledOnce()
    // The other account opens the refused page after signing in.
    expect(router.push).toHaveBeenCalledWith({ path: '/auth', query: { redirect: '/admin/users' } })
    expect(auth.logout.mock.invocationCallOrder[0]).toBeLessThan(router.push.mock.invocationCallOrder[0] ?? 0)
  })

  it('does not take an admin to a learner class page', () => {
    signInAs('ADMIN')
    route.query = { redirect: '/student/classes/12/lessons/34' }

    const wrapper = mountPage('unauthorized')

    expect(wrapper.find('[data-testid="system-class"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="system-home"]').attributes('href')).toBe('/admin/dashboard')
  })

  it('lets an admin with a learner role join the refused lesson and keeps the admin home', async () => {
    signInAs('ADMIN', 'STUDENT')
    route.query = { redirect: '/student/classes/12/lessons/34' }
    const wrapper = mountPage('unauthorized')
    expect(wrapper.get('.brand-stub').attributes('href')).toBe('/admin/dashboard')
    await wrapper.get('[data-testid="system-join"]').trigger('click')
    await flushPromises()
    expect(join).toHaveBeenCalledExactlyOnceWith(12)
    expect(router.replace).toHaveBeenCalledWith('/student/classes/12/lessons/34')
  })

  it('offers sign in, with the refused page, and the landing page to a visitor', () => {
    route.query = { redirect: '/admin/users' }

    const wrapper = mountPage('unauthorized')

    expect(wrapper.find('[data-testid="system-switch-account"]').exists()).toBe(false)
    expect(actions(wrapper)).toEqual([
      ['Sign in', JSON.stringify({ path: '/auth', query: { redirect: '/admin/users' } })],
      ['Return home', '/'],
    ])
  })

  it('opens plain sign in without a refused page', () => {
    expect(mountPage('unauthorized').get('[data-testid="system-sign-in"]').attributes('href')).toBe('/auth')
  })

  it('does not call the health check', () => {
    mountPage('unauthorized')
    mountPage('not-found')

    expect(health).not.toHaveBeenCalled()
  })

  it.each(['new-session', 'leave'])('does not navigate after a pending sign-out and %s', async (change) => {
    const signOut = deferred<boolean>()
    auth.logout.mockReturnValueOnce(signOut.promise)
    signInAs('STUDENT')
    const wrapper = mountPage('unauthorized')
    await wrapper.get('[data-testid="system-switch-account"]').trigger('click')
    if (change === 'leave') wrapper.unmount()
    signOut.resolve(change === 'leave')
    await flushPromises()
    expect(router.push).not.toHaveBeenCalled()
    if (change !== 'leave') wrapper.unmount()
  })
})

describe('SystemStatusPage maintenance', () => {
  const RETRY = '[data-testid="system-retry"]'
  const STILL_DOWN = '[data-testid="system-still-down"]'

  it('says calmly that MandarinSpark is being updated and that progress is safe, with one Try again button', () => {
    const wrapper = mountPage('maintenance')

    expect(wrapper.get('h1').text()).toBe('We’re updating MandarinSpark')
    // No end time is known, so none is promised.
    expect(wrapper.get('[data-testid="system-copy"]').text()).toBe(
      'We’ll be back soon. Your progress and streak are safe.',
    )
    expect(wrapper.findAll('.btn')).toHaveLength(1)
    expect(wrapper.get(RETRY).text()).toBe('Try again')
    expect(wrapper.get(RETRY).classes()).toContain('btn-primary')
    // The note that answers "Try again" is a live region that starts empty.
    expect(wrapper.get(STILL_DOWN).attributes('role')).toBe('status')
    expect(wrapper.get(STILL_DOWN).text()).toBe('')
    expect(health).not.toHaveBeenCalled()
  })

  it.each([
    ['STUDENT', '/student/home'],
    ['ADMIN', '/admin/dashboard'],
    [null, '/'],
  ] as const)('opens the home of %s when the health check answers UP', async (role, home) => {
    auth.isAuthenticated = role !== null
    auth.roles = role ? [role] : []
    health.mockResolvedValue({ status: 'UP', ai: 'UNAVAILABLE' })
    const wrapper = mountPage('maintenance')

    await wrapper.get(RETRY).trigger('click')
    await flushPromises()

    expect(health).toHaveBeenCalledOnce()
    expect(router.replace).toHaveBeenCalledExactlyOnceWith(home)
    expect(wrapper.get(STILL_DOWN).text()).toBe('')
    // Only a page without a session asks for one.
    expect(auth.restoreSession).toHaveBeenCalledTimes(role === null ? 1 : 0)
  })

  it('restores a session that could not be restored during the outage, then opens that user’s home', async () => {
    health.mockResolvedValue({ status: 'UP', ai: 'AVAILABLE' })
    auth.restoreSession.mockImplementation(async () => {
      signInAs('STUDENT')
    })
    const wrapper = mountPage('maintenance')

    await wrapper.get(RETRY).trigger('click')
    await flushPromises()

    expect(auth.restoreSession).toHaveBeenCalledOnce()
    expect(router.replace).toHaveBeenCalledExactlyOnceWith('/student/home')
  })

  it.each([
    ['fails', () => Promise.reject({ response: { status: 503, data: {} } })],
    ['answers DOWN', () => Promise.resolve({ status: 'DOWN', ai: 'AVAILABLE' })],
  ])('stays and says it is still unavailable when the health check %s', async (_case, answer) => {
    health.mockImplementation(answer)
    const wrapper = mountPage('maintenance')

    await wrapper.get(RETRY).trigger('click')
    await flushPromises()

    expect(router.replace).not.toHaveBeenCalled()
    expect(wrapper.get(STILL_DOWN).text()).toBe('Still unavailable. Try again in a few minutes.')
    expect(wrapper.get(RETRY).attributes('disabled')).toBeUndefined()

    // The note clears while the next check runs, so its return is announced again.
    health.mockReturnValueOnce(new Promise(() => undefined))
    await wrapper.get(RETRY).trigger('click')
    expect(wrapper.get(STILL_DOWN).text()).toBe('')
  })

  it('is busy while it checks and ignores a second click', async () => {
    const check = deferred<{ status: string; ai: string }>()
    health.mockReturnValueOnce(check.promise)
    const wrapper = mountPage('maintenance')

    const retry = wrapper.get<HTMLButtonElement>(RETRY).element
    retry.click()
    retry.click()
    await flushPromises()
    const button = wrapper.get(RETRY)
    expect(button.text()).toBe('Checking…')
    expect(button.attributes('disabled')).toBeDefined()
    expect(button.attributes('aria-busy')).toBe('true')
    await button.trigger('click')
    expect(health).toHaveBeenCalledOnce()

    check.resolve({ status: 'UP', ai: 'AVAILABLE' })
    await flushPromises()
    expect(router.replace).toHaveBeenCalledOnce()
  })

  it('does not navigate when the page was left while the check ran', async () => {
    const check = deferred<{ status: string; ai: string }>()
    health.mockReturnValueOnce(check.promise)
    const wrapper = mountPage('maintenance')
    await wrapper.get(RETRY).trigger('click')

    wrapper.unmount()
    check.resolve({ status: 'UP', ai: 'AVAILABLE' })
    await flushPromises()

    expect(router.replace).not.toHaveBeenCalled()
  })

  it('keeps a new retry busy when a previous visit receives its health response', async () => {
    const first = deferred<{ status: string }>()
    const second = deferred<{ status: string }>()
    health.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const wrapper = mountPage('maintenance')
    await wrapper.get(RETRY).trigger('click')
    await wrapper.setProps({ kind: 'not-found' })
    await wrapper.setProps({ kind: 'maintenance' })
    await wrapper.get(RETRY).trigger('click')
    expect(health).toHaveBeenCalledTimes(2)

    first.resolve({ status: 'UP' })
    await flushPromises()
    expect(auth.restoreSession).not.toHaveBeenCalled()
    expect(router.replace).not.toHaveBeenCalled()
    expect(wrapper.get(RETRY).attributes('disabled')).toBeDefined()

    second.resolve({ status: 'DOWN' })
    await flushPromises()
    expect(wrapper.get(STILL_DOWN).text()).toBe('Still unavailable. Try again in a few minutes.')
    expect(wrapper.get(RETRY).attributes('disabled')).toBeUndefined()
  })

  it('reports still unavailable if navigating after recovery fails (e.g., lazy chunk load error)', async () => {
    health.mockResolvedValue({ status: 'UP', ai: 'AVAILABLE' })
    auth.isAuthenticated = true
    auth.roles = ['STUDENT']
    router.replace.mockRejectedValueOnce(new Error('Failed to fetch dynamically imported module'))
    const wrapper = mountPage('maintenance')

    await wrapper.get(RETRY).trigger('click')
    await flushPromises()

    expect(wrapper.get(STILL_DOWN).text()).toBe('Still unavailable. Try again in a few minutes.')
    expect(wrapper.get(RETRY).attributes('disabled')).toBeUndefined()
  })
})
