import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { studentLiveClasses } from '@/demo/data'
import { i18n } from '@/plugins/i18n'
import type { LiveClassResponse } from '@/types/api'

const api = vi.hoisted(() => ({
  liveClasses: vi.fn(),
  enrolLiveClass: vi.fn(),
  cancelLiveClassEnrolment: vi.fn(),
  joinLiveClass: vi.fn(),
}))

vi.mock('@/api/services', () => ({ studentApi: api, publicApi: { getCourseDetail: vi.fn() } }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: true }) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

import StudentClassesPage from './StudentClassesPage.vue'

const FUTURE_START = '2099-05-20T09:00:00Z'
const FUTURE_END = '2099-05-20T09:45:00Z'

/** Preview fixtures moved into the future so "not started yet" rules apply. */
function upcoming(): LiveClassResponse[] {
  return studentLiveClasses.map((item, index) => ({
    ...item,
    startTime: FUTURE_START.replace('20T', `${20 + index}T`),
    endTime: FUTURE_END.replace('20T', `${20 + index}T`),
  }))
}

function mountPage() {
  return mount(StudentClassesPage, { global: { plugins: [i18n] } })
}

function cardFor(wrapper: ReturnType<typeof mountPage>, title: string) {
  const card = wrapper.findAll('[data-testid="student-class-card"]').find((item) => item.text().includes(title))
  if (!card) throw new Error(`No card for ${title}`)
  return card
}

describe('StudentClassesPage per-class actions (FR-LIVE-05)', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
    vi.clearAllMocks()
    api.liveClasses.mockResolvedValue(upcoming())
  })

  it('lists every upcoming class with its own seats label and action', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const cards = wrapper.findAll('[data-testid="student-class-card"]')
    expect(cards).toHaveLength(studentLiveClasses.length)

    const greetings = cardFor(wrapper, 'Live Class - Greetings')
    expect(greetings.get('[data-testid="student-class-seats"]').text()).toBe('7 of 25 seats left')
    expect(greetings.get('[data-testid="student-class-action"]').attributes('data-action')).toBe('join')
    expect(greetings.find('[data-testid="student-class-cancel"]').exists()).toBe(true)

    const university = cardFor(wrapper, 'University Life')
    expect(university.get('[data-testid="student-class-seats"]').text()).toBe('3 of 20 seats left')
    expect(university.get('[data-testid="student-class-action"]').text()).toBe('Enrol')

    const full = cardFor(wrapper, 'Ordering Food')
    expect(full.get('[data-testid="student-class-seats"]').text()).toBe('Full')
    const fullAction = full.get('[data-testid="student-class-action"]')
    expect(fullAction.text()).toBe('Class full')
    expect(fullAction.attributes('disabled')).toBeDefined()

    const clinic = cardFor(wrapper, 'Pinyin and Tones Clinic')
    expect(clinic.get('[data-testid="student-class-seats"]').text()).toBe('No seat limit')
    expect(clinic.get('[data-testid="student-class-action"]').text()).toBe('Enrol')
  })

  it('enrols one class and replaces only that card with the returned response', async () => {
    const target = upcoming()[1]
    api.enrolLiveClass.mockResolvedValue({
      ...target,
      enrolled: true,
      canEnrol: false,
      enrolledCount: 18,
      remainingSeats: 2,
    })
    const wrapper = mountPage()
    await flushPromises()

    await cardFor(wrapper, 'University Life').get('[data-testid="student-class-action"]').trigger('click')
    await flushPromises()

    expect(api.enrolLiveClass).toHaveBeenCalledWith(target.id)
    expect(api.liveClasses).toHaveBeenCalledTimes(1)
    const card = cardFor(wrapper, 'University Life')
    expect(card.get('[data-testid="student-class-seats"]').text()).toBe('2 of 20 seats left')
    expect(card.text()).toContain('Enrolled')
    expect(card.find('[data-testid="student-class-cancel"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('You are enrolled in University Life.')
    expect(cardFor(wrapper, 'Pinyin and Tones Clinic').get('[data-testid="student-class-action"]').text()).toBe('Enrol')
  })

  it('asks for confirmation before cancelling an enrolment', async () => {
    const target = upcoming()[0]
    api.cancelLiveClassEnrolment.mockResolvedValue({
      ...target,
      enrolled: false,
      canEnrol: true,
      canJoin: false,
      enrolledCount: 17,
      remainingSeats: 8,
    })
    const wrapper = mountPage()
    await flushPromises()

    await cardFor(wrapper, 'Live Class - Greetings').get('[data-testid="student-class-cancel"]').trigger('click')
    expect(api.cancelLiveClassEnrolment).not.toHaveBeenCalled()
    await cardFor(wrapper, 'Live Class - Greetings')
      .get('[data-testid="student-class-confirm-cancel"]')
      .trigger('click')
    await flushPromises()

    expect(api.cancelLiveClassEnrolment).toHaveBeenCalledWith(target.id)
    const card = cardFor(wrapper, 'Live Class - Greetings')
    expect(card.get('[data-testid="student-class-seats"]').text()).toBe('8 of 25 seats left')
    expect(card.get('[data-testid="student-class-action"]').text()).toBe('Enrol')
  })

  it('shows the server message when a class fills up before enrolment', async () => {
    api.enrolLiveClass.mockRejectedValue({
      response: { status: 400, data: { message: 'This live class is already full.' } },
    })
    const wrapper = mountPage()
    await flushPromises()

    await cardFor(wrapper, 'Pinyin and Tones Clinic').get('[data-testid="student-class-action"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('This live class is already full.')
  })
})
