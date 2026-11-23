import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { deferred } from '@/test/helpers'
import { mountAtRoute } from '@/test/routes'
import type { ClassPreview } from '@/types/classes'

const detail = vi.hoisted(() => vi.fn())
const session = vi.hoisted(() => ({ role: null as 'ADMIN' | 'STUDENT' | null }))
vi.mock('@/api/classes', () => ({ publicClassesApi: { detail } }))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ roles: session.role ? [session.role] : [] }),
}))

import PublicClassDetailPage from './PublicClassDetailPage.vue'

function preview(id: number): ClassPreview {
  return {
    summary: {
      id,
      title: `Class ${id}`,
      description: null,
      topicName: null,
      bestAfterUnit: null,
      lessonCount: 0,
    },
    units: [],
  }
}

const campusLife: ClassPreview = {
  summary: {
    id: 1,
    title: 'Mandarin for Campus Life',
    description: 'Campus scenarios for exchange students: meeting classmates, the canteen and finding your way.',
    topicName: 'Study or exchange',
    bestAfterUnit: 3,
    lessonCount: 2,
  },
  units: [
    { id: 5, title: 'First Conversations', lessons: [{ id: 9, title: 'Meeting Classmates' }] },
    { id: 6, title: 'Campus Life', lessons: [{ id: 10, title: 'Finding Places' }] },
  ],
}

const LOCKED_NOTE = '[data-testid="pub-class-locked-note"]'

async function mountClass(classPreview: ClassPreview) {
  detail.mockResolvedValueOnce(classPreview)
  return (await mountAtRoute(PublicClassDetailPage, `/classes/${classPreview.summary.id}`)).wrapper
}

beforeEach(() => {
  detail.mockReset()
  session.role = null
})

describe('PublicClassDetailPage', () => {
  it('says what the class is, that it is free and where it fits in Learn, with no teacher and no live sessions', async () => {
    const wrapper = await mountClass(campusLife)

    expect(wrapper.get('h1').text()).toBe('Mandarin for Campus Life')
    expect(wrapper.text()).toContain('Campus scenarios for exchange students')
    expect(wrapper.get('[data-testid="pub-class-facts"]').text()).toBe(
      'Study or exchange · Free · best after Unit 3 of Learn',
    )
    expect(wrapper.text()).not.toMatch(/teacher|verified|live session/i)
  })

  it('lists what is inside, every lesson locked, and tells a visitor the lessons open with a free account', async () => {
    const wrapper = await mountClass(campusLife)

    expect(wrapper.get(LOCKED_NOTE).text()).toBe('Create a free account to open the lessons.')
    const inside = wrapper.get('[aria-labelledby="pub-class-units-title"]')
    expect(inside.findAll('h3').map((title) => title.text())).toEqual([
      'Unit 1: First Conversations',
      'Unit 2: Campus Life',
    ])
    expect(inside.findAll('.pub-class-lesson').map((row) => row.text())).toEqual([
      'Meeting Classmates',
      'Finding Places',
    ])
  })

  it('directs a signed-in learner to the class without assuming their membership', async () => {
    session.role = 'STUDENT'
    const wrapper = await mountClass(campusLife)

    expect(wrapper.get(LOCKED_NOTE).text()).toBe('Open the class to join or continue learning.')
  })

  it('says nothing about locks while the class has no lessons to show', async () => {
    const wrapper = await mountClass(preview(1))

    expect(wrapper.find(LOCKED_NOTE).exists()).toBe(false)
    expect(wrapper.text()).toContain('Lessons are on their way.')
  })

  it('brings a visitor back to this class after creating an account', async () => {
    const wrapper = await mountClass(preview(4))

    const register = wrapper.get('[data-testid="pub-class-register"]')
    expect(register.text()).toBe('Create account to start')
    expect(register.attributes('href')).toBe('/auth?tab=register&redirect=/student/classes/4')
  })

  it('opens the class for a signed-in learner', async () => {
    session.role = 'STUDENT'
    const wrapper = await mountClass(preview(4))

    expect(wrapper.find('[data-testid="pub-class-register"]').exists()).toBe(false)
    expect(wrapper.get('a.btn-primary').attributes('href')).toBe('/student/classes/4')
  })

  it('gives an administrator the management link for this class', async () => {
    session.role = 'ADMIN'
    const wrapper = await mountClass(campusLife)
    expect(wrapper.get('a.btn-primary').text()).toBe('Manage class')
    expect(wrapper.get('a.btn-primary').attributes('href')).toBe(`/admin/content/classes/${campusLife.summary.id}`)
    expect(wrapper.find(LOCKED_NOTE).exists()).toBe(false)
  })

  it.each(['success', 'failure'] as const)('clears the previous class and handles a new preview %s', async (result) => {
    detail.mockResolvedValueOnce(preview(1))
    const { wrapper, router } = await mountAtRoute(PublicClassDetailPage, '/classes/1')
    expect(wrapper.get('h1').text()).toBe('Class 1')

    const next = deferred<ClassPreview>()
    detail.mockReturnValueOnce(next.promise)
    await router.push('/classes/2')
    await flushPromises()
    expect(detail).toHaveBeenLastCalledWith(2)
    expect(wrapper.text()).not.toContain('Class 1')

    if (result === 'success') next.resolve(preview(2))
    else next.reject(new Error('Unavailable'))
    await flushPromises()
    if (result === 'failure') {
      const error = wrapper.get('[role="alert"]')
      expect(error.get('h2').text()).toBe('This class could not be loaded')
      detail.mockResolvedValueOnce(preview(2))
      await error.get('button').trigger('click')
      await flushPromises()
      expect(detail).toHaveBeenLastCalledWith(2)
    }
    expect(wrapper.get('h1').text()).toBe('Class 2')
    expect(wrapper.text()).not.toContain('Class 1')
  })
})
